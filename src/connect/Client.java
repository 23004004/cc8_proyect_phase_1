package connect;

import model.Direction;
import model.GameConfig;
import protocol.messages.ChangeDirectionRequest;
import protocol.messages.ErrorMessage;
import protocol.messages.FlagPickedUpMessage;
import protocol.messages.FlagStolenMessage;
import protocol.messages.GameCountdownMessage;
import protocol.messages.GameOverMessage;
import protocol.messages.GameStartedMessage;
import protocol.messages.GameStateMessage;
import protocol.messages.InteractRequest;
import protocol.messages.JoinAcceptedMessage;
import protocol.messages.JoinRejectedMessage;
import protocol.messages.JoinRequest;
import protocol.messages.LeaveRequest;
import protocol.messages.LobbyStateMessage;
import protocol.messages.PlayerDisconnectedMessage;
import protocol.core.ProtocolCodec;
import protocol.core.ProtocolMessage;
import view.PanelGame;
import view.ServerDiscoveryDialog;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InterfaceAddress;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public final class Client {
    private static final Path CONFIG_PATH = Path.of("config", "server.properties");
    private final ClientEventLogger eventLogger = new ClientEventLogger();
    private final ProtocolCodec codec = new ProtocolCodec();
    private final GameConfig discoveryConfig = GameConfigLoader.load(CONFIG_PATH, GameConfig.defaults());

    public void start() {
        while (true) {
            ClientSelection selection = chooseServerWithDiscovery();
            if (selection == null) {
                return;
            }
            ServerChoice choice = selection.server();
            System.out.println("Servidor seleccionado: " + choice.name() + " en " + choice.host() + ":" + choice.port()
                    + " (" + choice.playerCount() + "/" + choice.maximumPlayers() + ")");
            if (!start(choice.host(), choice.port(), selection.playerName())) {
                return;
            }
        }
    }

    public void start(String host, int port) {
        start(host, port, askPlayerName());
    }

    private boolean start(String host, int port, String name) {
        ClientWindow window = null;
        AtomicBoolean returnToDiscovery = new AtomicBoolean(false);
        try {
            name = normalizePlayerName(name);
            eventLogger.info("CONNECTING host=" + host + " port=" + port + " name=" + name);
            try (Socket socket = openSocket(host, port);
                 LineConnection connection = new LineConnection(socket)) {
                PanelGame panel = new PanelGame();
                window = createWindow(panel);
                AtomicInteger playerIdRef = new AtomicInteger(0);
                AtomicBoolean closing = new AtomicBoolean(false);
                CountDownLatch finished = new CountDownLatch(1);

                connection.sendMessage(new JoinRequest(name));
                eventLogger.info("JOIN_SENT host=" + host + " port=" + port + " name=" + name);
                installReturnToDiscoveryButton(window.returnButton(), panel, connection, playerIdRef, closing, finished, returnToDiscovery);
                installCloseHandler(window.frame(), connection, playerIdRef, closing, finished);
                installKeyboardControls(window.frame(), connection, playerIdRef, panel);

                JButton returnButton = window.returnButton();
                Thread reader = new Thread(() -> readLoop(connection, panel, playerIdRef, returnButton, finished), "client-reader");
                reader.setDaemon(true);
                reader.start();

                panel.applyStatusText("Conectado. Esperando JOIN_ACCEPTED.");
                System.out.println("Controles: W/A/S/D para moverse, R o espacio para interactuar, cerrar ventana para salir.");
                finished.await();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            eventLogger.warning("CLIENT_INTERRUPTED");
        } catch (IOException ex) {
            eventLogger.warning("CONNECTION_FAILED error=" + ex.getMessage());
            System.out.println("No se pudo conectar al servidor: " + ex.getMessage());
        } finally {
            if (window != null) {
                window.frame().dispose();
            }
            eventLogger.info("CLIENT_STOPPED");
        }
        return returnToDiscovery.get();
    }

    private ClientWindow createWindow(PanelGame panel) {
        JFrame frame = new JFrame("Captura la Bandera");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        JButton returnButton = new JButton("Volver a listado de servidores");
        returnButton.setEnabled(false);
        JPanel root = new JPanel(new BorderLayout());
        root.add(panel, BorderLayout.CENTER);
        root.add(returnButton, BorderLayout.SOUTH);
        frame.setContentPane(root);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        return new ClientWindow(frame, returnButton);
    }

    private Socket openSocket(String host, int port) throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 5000);
        return socket;
    }

    private ClientSelection chooseServerWithDiscovery() {
        DiscoveryManager discoveryManager = new DiscoveryManager();
        AtomicReference<ServerDiscoveryDialog> dialogRef = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                ServerDiscoveryDialog dialog = new ServerDiscoveryDialog(
                        null,
                        () -> discoveryManager.restart(dialogRef.get())
                );
                dialogRef.set(dialog);
                discoveryManager.start(dialog);
                dialog.setVisible(true);
            });
            ServerDiscoveryDialog dialog = dialogRef.get();
            if (dialog == null || dialog.selectedServer() == null) {
                return null;
            }
            return new ClientSelection(dialog.selectedServer(), normalizePlayerName(dialog.playerName()));
        } catch (Exception ex) {
            eventLogger.warning("DISCOVERY_DIALOG_FAILED error=" + ex.getMessage());
            ServerChoice server = discoverSingleServer(discoveryPorts(discoveryConfig));
            return server == null ? null : new ClientSelection(server, askPlayerName());
        } finally {
            discoveryManager.stop();
        }
    }

    private List<Integer> discoveryPorts(GameConfig config) {
        List<Integer> ports = new ArrayList<>();
        addPort(ports, config.discoveryPort());
        addExtraPorts(ports, config.extraDiscoveryPorts());
        return ports;
    }

    private void addPort(List<Integer> ports, int port) {
        if (port > 0 && port <= 65535 && !ports.contains(port)) {
            ports.add(port);
        }
    }

    private void addExtraPorts(List<Integer> ports, String rawPorts) {
        if (rawPorts == null || rawPorts.isBlank()) {
            return;
        }
        for (String rawPort : rawPorts.split(",")) {
            try {
                addPort(ports, Integer.parseInt(rawPort.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private ServerChoice discoverSingleServer(List<Integer> discoveryPorts) {
        for (int discoveryPort : discoveryPorts) {
            try (DatagramSocket socket = openDiscoverySocket(discoveryPort)) {
                socket.setSoTimeout(1200);
                sendDiscoverRequests(socket, discoveryPort);
                byte[] buffer = new byte[512];
                DatagramPacket responsePacket = new DatagramPacket(buffer, buffer.length);
                socket.receive(responsePacket);
                ProtocolCodec.DiscoverResponse response = codec.deserializeDiscoverResponse(responsePacket.getData(), responsePacket.getLength());
                return toServerChoice(responsePacket, response, discoveryPort);
            } catch (SocketTimeoutException ex) {
                // Try the next compatibility port.
            } catch (IOException | RuntimeException ex) {
                eventLogger.warning("DISCOVERY_FAILED port=" + discoveryPort + " error=" + ex.getMessage());
            }
        }
        return null;
    }

    private DatagramSocket openDiscoverySocket(int discoveryPort) throws SocketException {
        DatagramSocket socket = new DatagramSocket(null);
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress(discoveryPort));
        socket.setBroadcast(true);
        return socket;
    }

    private Set<InetAddress> discoveryTargets() throws IOException {
        Set<InetAddress> addresses = new LinkedHashSet<>();
        addresses.add(InetAddress.getByName("255.255.255.255"));
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces != null && interfaces.hasMoreElements()) {
            NetworkInterface networkInterface = interfaces.nextElement();
            if (!networkInterface.isUp() || networkInterface.isLoopback() || networkInterface.isVirtual()) {
                continue;
            }
            for (InterfaceAddress interfaceAddress : networkInterface.getInterfaceAddresses()) {
                InetAddress broadcast = interfaceAddress.getBroadcast();
                if (broadcast != null) {
                    addresses.add(broadcast);
                }
            }
        }
        return addresses;
    }

    private void sendDiscoverRequests(DatagramSocket socket, int discoveryPort) throws IOException {
        byte[] request = codec.serializeDiscoverRequest();
        for (InetAddress address : discoveryTargets()) {
            try {
                socket.send(new DatagramPacket(request, request.length, address, discoveryPort));
            } catch (IOException ignored) {
            }
        }
    }

    private ServerChoice toServerChoice(DatagramPacket packet, ProtocolCodec.DiscoverResponse response, int discoveryPort) {
        return new ServerChoice(
                packet.getAddress().getHostAddress(),
                response.tcpPort(),
                response.serverName(),
                response.gameId(),
                response.state().name() + " UDP:" + discoveryPort,
                response.playerCount(),
                response.maximumPlayers()
        );
    }

    private void readLoop(LineConnection connection, PanelGame panel, AtomicInteger playerIdRef, JButton returnButton, CountDownLatch finished) {
        try {
            while (true) {
                ProtocolMessage message = connection.readMessage();
                if (message == null) {
                    panel.applyStatusText("Conexión cerrada por el servidor.");
                    return;
                }
                if (message instanceof JoinAcceptedMessage accepted) {
                    playerIdRef.set(accepted.playerId());
                    panel.setLocalPlayerId(accepted.playerId());
                    panel.applyStatusText("Conexión exitosa. Jugador " + accepted.playerId());
                    eventLogger.info("JOIN_ACCEPTED gameId=" + accepted.gameId() + " playerId=" + accepted.playerId());
                } else if (message instanceof JoinRejectedMessage rejected) {
                    panel.applyStatusText("JOIN rechazado: " + rejected.reason());
                    eventLogger.warning("JOIN_REJECTED reason=" + rejected.reason());
                    return;
                } else if (message instanceof LobbyStateMessage lobby) {
                    panel.applyLobbyState(lobby);
                    updateReturnButton(returnButton, panel);
                } else if (message instanceof GameCountdownMessage countdown) {
                    panel.applyStatusText("Inicia en " + countdown.secondsRemaining() + "...");
                } else if (message instanceof GameStartedMessage started) {
                    panel.applyGameStarted(started);
                    updateReturnButton(returnButton, panel);
                    eventLogger.info("GAME_STARTED players=" + started.players().size());
                } else if (message instanceof GameStateMessage state) {
                    panel.applyGameState(state);
                } else if (message instanceof FlagPickedUpMessage pickedUp) {
                    panel.applyStatusText("Bandera tomada por " + pickedUp.playerId());
                    eventLogger.info("FLAG_PICKED_UP tick=" + pickedUp.tick() + " playerId=" + pickedUp.playerId());
                } else if (message instanceof FlagStolenMessage stolen) {
                    panel.applyStatusText("Bandera robada: " + stolen.previousCarrierId() + " -> " + stolen.newCarrierId());
                    eventLogger.info("FLAG_STOLEN tick=" + stolen.tick() + " previousCarrierId=" + stolen.previousCarrierId() + " newCarrierId=" + stolen.newCarrierId());
                } else if (message instanceof PlayerDisconnectedMessage disconnected) {
                    panel.applyStatusText("Jugador desconectado: " + disconnected.playerId());
                } else if (message instanceof GameOverMessage gameOver) {
                    panel.applyGameOver(gameOver);
                    updateReturnButton(returnButton, panel);
                    showGameOverDialog(gameOver);
                    eventLogger.info("GAME_OVER winnerId=" + gameOver.winnerId() + " winnerName=" + gameOver.winnerName());
                } else if (message instanceof ErrorMessage error) {
                    panel.applyStatusText("Error: " + error.code());
                    eventLogger.warning("SERVER_ERROR code=" + error.code() + " description=" + error.description());
                }
            }
        } catch (IOException | RuntimeException ex) {
            eventLogger.warning("READ_LOOP_STOPPED error=" + ex.getMessage());
        } finally {
            finished.countDown();
        }
    }

    private void showGameOverDialog(GameOverMessage gameOver) {
        SwingUtilities.invokeLater(() -> {
            String winnerName = gameOver.winnerName() == null ? "" : gameOver.winnerName().trim();
            String message = "Fin de la partida";
            if (gameOver.winnerId() > 0 && !winnerName.isBlank()) {
                message += "\n\nGanador: #" + gameOver.winnerId() + " - " + winnerName;
            }
            JOptionPane optionPane = new JOptionPane(message, JOptionPane.INFORMATION_MESSAGE);
            javax.swing.JDialog dialog = optionPane.createDialog(null, "Partida finalizada");
            Timer closeTimer = new Timer(5000, event -> dialog.dispose());
            closeTimer.setRepeats(false);
            closeTimer.start();
            dialog.setVisible(true);
        });
    }

    private void updateReturnButton(JButton returnButton, PanelGame panel) {
        SwingUtilities.invokeLater(() -> returnButton.setEnabled(panel.canReturnToServerList()));
    }

    private String askPlayerName() {
        try {
            String value = JOptionPane.showInputDialog(null, "Nombre del jugador:", "Jugador");
            return normalizePlayerName(value);
        } catch (RuntimeException ex) {
            return "Jugador";
        }
    }

    private String normalizePlayerName(String name) {
        String value = name == null ? "" : name.trim();
        return value.isBlank() ? "Jugador" : value;
    }

    private void installKeyboardControls(JFrame frame, LineConnection connection, AtomicInteger playerIdRef, PanelGame panel) {
        Object movementLock = new Object();
        Deque<Direction> pressedDirections = new ArrayDeque<>();
        AtomicReference<Direction> sentDirectionRef = new AtomicReference<>(Direction.NONE);
        bindMovementKey(frame, "W", "up", Direction.UP, connection, playerIdRef, panel, movementLock, pressedDirections, sentDirectionRef);
        bindMovementKey(frame, "A", "left", Direction.LEFT, connection, playerIdRef, panel, movementLock, pressedDirections, sentDirectionRef);
        bindMovementKey(frame, "S", "down", Direction.DOWN, connection, playerIdRef, panel, movementLock, pressedDirections, sentDirectionRef);
        bindMovementKey(frame, "D", "right", Direction.RIGHT, connection, playerIdRef, panel, movementLock, pressedDirections, sentDirectionRef);
        bindInteract(frame, "pressed R", "interact-r", connection, playerIdRef, panel);
        bindInteract(frame, "SPACE", "interact-space", connection, playerIdRef, panel);
    }

    private void bindMovementKey(
            JFrame frame,
            String key,
            String actionSuffix,
            Direction direction,
            LineConnection connection,
            AtomicInteger playerIdRef,
            PanelGame panel,
            Object movementLock,
            Deque<Direction> pressedDirections,
            AtomicReference<Direction> sentDirectionRef
    ) {
        bindMovementAction(frame, "pressed " + key, "move-" + actionSuffix, () -> {
            synchronized (movementLock) {
                // La ultima tecla presionada tiene prioridad mientras siga sostenida.
                pressedDirections.remove(direction);
                pressedDirections.addLast(direction);
                return direction;
            }
        }, connection, playerIdRef, panel, sentDirectionRef);
        bindMovementAction(frame, "released " + key, "stop-" + actionSuffix, () -> {
            synchronized (movementLock) {
                pressedDirections.remove(direction);
                Direction current = pressedDirections.peekLast();
                return current == null ? Direction.NONE : current;
            }
        }, connection, playerIdRef, panel, sentDirectionRef);
    }

    private void bindMovementAction(JFrame frame, String keyStroke, String actionName, DirectionSupplier directionSupplier, LineConnection connection, AtomicInteger playerIdRef, PanelGame panel, AtomicReference<Direction> sentDirectionRef) {
        JComponent root = frame.getRootPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(keyStroke), actionName);
        root.getActionMap().put(actionName, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                Direction direction = directionSupplier.get();
                if (sentDirectionRef.get() == direction) {
                    return;
                }
                int playerId = playerIdRef.get();
                if (playerId <= 0) {
                    panel.applyStatusText("Esperando JOIN_ACCEPTED.");
                    return;
                }
                try {
                    connection.sendMessage(new ChangeDirectionRequest(playerId, direction));
                    sentDirectionRef.set(direction);
                    panel.applyLocalDirection(direction);
                    eventLogger.info("INPUT_SENT playerId=" + playerId + " direction=" + direction);
                } catch (IOException ex) {
                    panel.applyStatusText("No se pudo enviar dirección.");
                }
            }
        });
    }

    private void bindInteract(JFrame frame, String keyStroke, String actionName, LineConnection connection, AtomicInteger playerIdRef, PanelGame panel) {
        JComponent root = frame.getRootPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(keyStroke), actionName);
        root.getActionMap().put(actionName, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                int playerId = playerIdRef.get();
                if (playerId <= 0) {
                    panel.applyStatusText("Esperando JOIN_ACCEPTED.");
                    return;
                }
                try {
                    connection.sendMessage(new InteractRequest(playerId));
                    eventLogger.info("INTERACT_SENT playerId=" + playerId);
                } catch (IOException ex) {
                    panel.applyStatusText("No se pudo enviar interacción.");
                }
            }
        });
    }

    private void installReturnToDiscoveryButton(
            JButton returnButton,
            PanelGame panel,
            LineConnection connection,
            AtomicInteger playerIdRef,
            AtomicBoolean closing,
            CountDownLatch finished,
            AtomicBoolean returnToDiscovery
    ) {
        returnButton.addActionListener(event -> {
            if (!panel.canReturnToServerList()) {
                return;
            }
            returnToDiscovery.set(true);
            closeClient(connection, playerIdRef, closing, finished);
        });
    }

    private void installCloseHandler(JFrame frame, LineConnection connection, AtomicInteger playerIdRef, AtomicBoolean closing, CountDownLatch finished) {
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                closeClient(connection, playerIdRef, closing, finished);
            }
        });
    }

    private void closeClient(LineConnection connection, AtomicInteger playerIdRef, AtomicBoolean closing, CountDownLatch finished) {
        if (closing.compareAndSet(false, true)) {
            try {
                int playerId = playerIdRef.get();
                if (playerId > 0) {
                    // LEAVE evita que el servidor mantenga al jugador en el lobby.
                    connection.sendMessage(new LeaveRequest(playerId));
                    eventLogger.info("LEAVE_SENT playerId=" + playerId);
                }
            } catch (IOException ignored) {
            }
            try {
                connection.close();
            } catch (IOException ignored) {
            }
            finished.countDown();
        }
    }

    private final class DiscoveryManager {
        private final List<DiscoveryController> controllers = new ArrayList<>();

        private void start(ServerDiscoveryDialog dialog) {
            restart(dialog);
        }

        private void restart(ServerDiscoveryDialog dialog) {
            stop();
            if (dialog == null) {
                return;
            }
            GameConfig config = discoveryConfig
                    .withDiscoveryPort(dialog.discoveryPort())
                    .withExtraDiscoveryPorts(dialog.extraDiscoveryPorts());
            Consumer<List<ServerChoice>> updateConsumer = dialog::updateServers;
            for (int discoveryPort : discoveryPorts(config)) {
                DiscoveryController controller = new DiscoveryController(discoveryPort, config);
                controller.onUpdate(updateConsumer);
                controllers.add(controller);
                controller.start();
            }
        }

        private void stop() {
            for (DiscoveryController controller : controllers) {
                controller.stop();
            }
            controllers.clear();
        }
    }

    private final class DiscoveryController {
        private final int discoveryPort;
        private final GameConfig config;
        private final AtomicBoolean running = new AtomicBoolean(false);
        private final AtomicBoolean forceRequest = new AtomicBoolean(true);
        private final Map<String, ServerChoice> serversByKey = new ConcurrentHashMap<>();
        private volatile Consumer<List<ServerChoice>> updateConsumer = servers -> {
        };
        private volatile Thread thread;

        private DiscoveryController(int discoveryPort, GameConfig config) {
            this.discoveryPort = discoveryPort;
            this.config = config;
        }

        private void onUpdate(Consumer<List<ServerChoice>> updateConsumer) {
            this.updateConsumer = updateConsumer == null ? servers -> {
            } : updateConsumer;
        }

        private void start() {
            if (!running.compareAndSet(false, true)) {
                return;
            }
            thread = new Thread(this::run, "client-discovery");
            thread.setDaemon(true);
            thread.start();
        }

        private void stop() {
            running.set(false);
            Thread current = thread;
            if (current != null) {
                current.interrupt();
            }
        }

        private void run() {
            try (DatagramSocket socket = openDiscoverySocket(discoveryPort)) {
                socket.setSoTimeout(250);
                long nextBroadcastAt = 0L;
                while (running.get()) {
                    long now = System.currentTimeMillis();
                    boolean forced = forceRequest.getAndSet(false);
                    if (forced || now >= nextBroadcastAt) {
                        sendDiscoverRequests(socket);
                        nextBroadcastAt = now + 500L;
                    }
                    receiveOne(socket);
                }
            } catch (IOException ex) {
                eventLogger.warning("DISCOVERY_LOOP_FAILED error=" + ex.getMessage());
            }
        }

        private void sendDiscoverRequests(DatagramSocket socket) throws IOException {
            Client.this.sendDiscoverRequests(socket, discoveryPort);
        }

        private void receiveOne(DatagramSocket socket) throws IOException {
            byte[] buffer = new byte[512];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(packet);
                ProtocolCodec.DiscoverResponse response = codec.deserializeDiscoverResponse(packet.getData(), packet.getLength());
                ServerChoice server = toServerChoice(packet, response, discoveryPort);
                ServerChoice previous = serversByKey.put(server.key(), server);
                if (!server.equals(previous)) {
                    updateConsumer.accept(new ArrayList<>(serversByKey.values()));
                }
            } catch (SocketTimeoutException ignored) {
            } catch (RuntimeException ex) {
                // Other projects may use incompatible UDP payloads on the same port.
            }
        }
    }

    private record ClientSelection(ServerChoice server, String playerName) {
    }

    private record ClientWindow(JFrame frame, JButton returnButton) {
    }

    @FunctionalInterface
    private interface DirectionSupplier {
        Direction get();
    }
}
