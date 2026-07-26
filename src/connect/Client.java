package connect;

import model.Direction;
import model.GameConfig;
import protocol.ChangeDirectionRequest;
import protocol.ErrorMessage;
import protocol.FlagPickedUpMessage;
import protocol.FlagStolenMessage;
import protocol.GameCountdownMessage;
import protocol.GameOverMessage;
import protocol.GameStartedMessage;
import protocol.GameStateMessage;
import protocol.InteractRequest;
import protocol.JoinAcceptedMessage;
import protocol.JoinRejectedMessage;
import protocol.JoinRequest;
import protocol.LeaveRequest;
import protocol.LobbyStateMessage;
import protocol.PlayerDisconnectedMessage;
import protocol.ProtocolCodec;
import protocol.ProtocolMessage;
import view.PanelGame;
import view.ServerDiscoveryDialog;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
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
import java.util.ArrayList;
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
    private final ClientEventLogger eventLogger = new ClientEventLogger();
    private final ProtocolCodec codec = new ProtocolCodec();

    public void start() {
        ClientSelection selection = chooseServerWithDiscovery();
        if (selection == null) {
            return;
        }
        ServerChoice choice = selection.server();
        System.out.println("Servidor seleccionado: " + choice.name() + " en " + choice.host() + ":" + choice.port()
                + " (" + choice.playerCount() + "/" + choice.maximumPlayers() + ")");
        start(choice.host(), choice.port(), selection.playerName());
    }

    public void start(String host, int port) {
        start(host, port, askPlayerName());
    }

    private void start(String host, int port, String name) {
        JFrame frame = null;
        try {
            name = normalizePlayerName(name);
            eventLogger.info("CONNECTING host=" + host + " port=" + port + " name=" + name);
            try (Socket socket = openSocket(host, port);
                 LineConnection connection = new LineConnection(socket)) {
                PanelGame panel = new PanelGame();
                frame = createFrame(panel);
                AtomicInteger playerIdRef = new AtomicInteger(0);
                AtomicBoolean closing = new AtomicBoolean(false);
                CountDownLatch finished = new CountDownLatch(1);

                connection.sendMessage(new JoinRequest(name));
                eventLogger.info("JOIN_SENT host=" + host + " port=" + port + " name=" + name);
                installCloseHandler(frame, connection, playerIdRef, closing, finished);
                installKeyboardControls(frame, connection, playerIdRef, panel);

                Thread reader = new Thread(() -> readLoop(connection, panel, playerIdRef, finished), "client-reader");
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
            if (frame != null) {
                frame.dispose();
            }
            eventLogger.info("CLIENT_STOPPED");
        }
    }

    private JFrame createFrame(PanelGame panel) {
        JFrame frame = new JFrame("Captura la Bandera");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.setContentPane(panel);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        return frame;
    }

    private Socket openSocket(String host, int port) throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 5000);
        return socket;
    }

    private ClientSelection chooseServerWithDiscovery() {
        List<Integer> discoveryPorts = discoveryPorts();
        List<DiscoveryController> controllers = new ArrayList<>();
        AtomicReference<ServerDiscoveryDialog> dialogRef = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                ServerDiscoveryDialog dialog = new ServerDiscoveryDialog(
                        null,
                        () -> controllers.forEach(DiscoveryController::requestNow),
                        scanning -> {
                        }
                );
                dialogRef.set(dialog);
                Consumer<List<ServerChoice>> updateConsumer = dialog::updateServers;
                for (int discoveryPort : discoveryPorts) {
                    DiscoveryController controller = new DiscoveryController(discoveryPort);
                    controller.onUpdate(updateConsumer);
                    controllers.add(controller);
                    controller.start();
                }
                dialog.setVisible(true);
            });
            ServerDiscoveryDialog dialog = dialogRef.get();
            if (dialog == null || dialog.selectedServer() == null) {
                return null;
            }
            return new ClientSelection(dialog.selectedServer(), normalizePlayerName(dialog.playerName()));
        } catch (Exception ex) {
            eventLogger.warning("DISCOVERY_DIALOG_FAILED error=" + ex.getMessage());
            ServerChoice server = discoverSingleServer(discoveryPorts);
            return server == null ? null : new ClientSelection(server, askPlayerName());
        } finally {
            controllers.forEach(DiscoveryController::stop);
        }
    }

    private List<Integer> discoveryPorts() {
        List<Integer> ports = new ArrayList<>();
        addPort(ports, GameConfig.defaults().discoveryPort());
        addPort(ports, 5000);
        addPort(ports, 5001);
        return ports;
    }

    private void addPort(List<Integer> ports, int port) {
        if (port > 0 && port <= 65535 && !ports.contains(port)) {
            ports.add(port);
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
                addLocalSubnetTargets(addresses, interfaceAddress);
            }
        }
        return addresses;
    }

    private void addLocalSubnetTargets(Set<InetAddress> addresses, InterfaceAddress interfaceAddress) {
        InetAddress address = interfaceAddress.getAddress();
        if (!(address instanceof java.net.Inet4Address)) {
            return;
        }
        byte[] raw = address.getAddress();
        int base = ((raw[0] & 0xFF) << 24) | ((raw[1] & 0xFF) << 16) | ((raw[2] & 0xFF) << 8);
        int self = raw[3] & 0xFF;
        for (int host = 1; host <= 254; host++) {
            if (host == self) {
                continue;
            }
            int value = base | host;
            try {
                addresses.add(InetAddress.getByAddress(new byte[]{
                        (byte) ((value >>> 24) & 0xFF),
                        (byte) ((value >>> 16) & 0xFF),
                        (byte) ((value >>> 8) & 0xFF),
                        (byte) (value & 0xFF)
                }));
            } catch (IOException ignored) {
            }
        }
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

    private void readLoop(LineConnection connection, PanelGame panel, AtomicInteger playerIdRef, CountDownLatch finished) {
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
                } else if (message instanceof GameCountdownMessage countdown) {
                    panel.applyStatusText("Inicia en " + countdown.secondsRemaining() + "...");
                } else if (message instanceof GameStartedMessage started) {
                    panel.applyGameStarted(started);
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
        bindDirection(frame, "pressed W", "move-up", Direction.UP, connection, playerIdRef, panel);
        bindDirection(frame, "pressed A", "move-left", Direction.LEFT, connection, playerIdRef, panel);
        bindDirection(frame, "pressed S", "move-down", Direction.DOWN, connection, playerIdRef, panel);
        bindDirection(frame, "pressed D", "move-right", Direction.RIGHT, connection, playerIdRef, panel);
        bindDirection(frame, "released W", "stop-up", Direction.NONE, connection, playerIdRef, panel);
        bindDirection(frame, "released A", "stop-left", Direction.NONE, connection, playerIdRef, panel);
        bindDirection(frame, "released S", "stop-down", Direction.NONE, connection, playerIdRef, panel);
        bindDirection(frame, "released D", "stop-right", Direction.NONE, connection, playerIdRef, panel);
        bindInteract(frame, "pressed R", "interact-r", connection, playerIdRef, panel);
        bindInteract(frame, "SPACE", "interact-space", connection, playerIdRef, panel);
    }

    private void bindDirection(JFrame frame, String keyStroke, String actionName, Direction direction, LineConnection connection, AtomicInteger playerIdRef, PanelGame panel) {
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
                    connection.sendMessage(new ChangeDirectionRequest(playerId, direction));
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

    private void installCloseHandler(JFrame frame, LineConnection connection, AtomicInteger playerIdRef, AtomicBoolean closing, CountDownLatch finished) {
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                if (closing.compareAndSet(false, true)) {
                    try {
                        int playerId = playerIdRef.get();
                        if (playerId > 0) {
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
        });
    }

    private final class DiscoveryController {
        private final int discoveryPort;
        private final AtomicBoolean running = new AtomicBoolean(false);
        private final AtomicBoolean forceRequest = new AtomicBoolean(true);
        private final Map<String, ServerChoice> serversByKey = new ConcurrentHashMap<>();
        private volatile Consumer<List<ServerChoice>> updateConsumer = servers -> {
        };
        private volatile Thread thread;

        private DiscoveryController(int discoveryPort) {
            this.discoveryPort = discoveryPort;
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

        private void requestNow() {
            forceRequest.set(true);
        }

        private void run() {
            try (DatagramSocket socket = openDiscoverySocket(discoveryPort)) {
                socket.setSoTimeout(250);
                long nextRequestAt = 0L;
                while (running.get()) {
                    long now = System.currentTimeMillis();
                    if (forceRequest.getAndSet(false) || now >= nextRequestAt) {
                        sendDiscoverRequests(socket);
                        nextRequestAt = now + 1000L;
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
                serversByKey.put(server.key(), server);
                updateConsumer.accept(new ArrayList<>(serversByKey.values()));
                eventLogger.info("DISCOVER_RESPONSE host=" + server.host() + " port=" + server.port() + " name=" + server.name());
            } catch (SocketTimeoutException ignored) {
            } catch (RuntimeException ex) {
                eventLogger.warning("DISCOVERY_RESPONSE_IGNORED error=" + ex.getMessage());
            }
        }
    }

    private record ClientSelection(ServerChoice server, String playerName) {
    }
}
