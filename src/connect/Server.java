package connect;

import engine.GameInitializer;
import engine.GameSession;
import engine.GameTickResult;
import model.Direction;
import model.Game;
import model.GameConfig;
import model.GameStatus;
import model.Player;
import protocol.ChangeDirectionRequest;
import protocol.ErrorCode;
import protocol.ErrorMessage;
import protocol.GameCountdownMessage;
import protocol.GameMessageMapper;
import protocol.InteractRequest;
import protocol.JoinAcceptedMessage;
import protocol.JoinRejectedMessage;
import protocol.JoinRejectedReason;
import protocol.JoinRequest;
import protocol.LeaveRequest;
import protocol.ProtocolCodec;
import protocol.ProtocolDecodeException;
import protocol.ProtocolMessage;
import view.ServerDashboard;

import java.io.IOException;
import java.awt.GraphicsEnvironment;
import java.net.BindException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InterfaceAddress;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class Server {
    private final GameConfig config;
    private final Game game;
    private final GameSession session;
    private final GameInitializer initializer;
    private final ServerEventLogger eventLogger;
    private final ConcurrentHashMap<Integer, ClientContext> clientsByPlayerId;
    private final ExecutorService clientExecutor;
    private final ScheduledExecutorService tickExecutor;
    private final AtomicInteger playerSequence;
    private final AtomicBoolean shuttingDown;
    private final ProtocolCodec codec;

    private volatile ServerSocket serverSocket;
    private final List<DatagramSocket> discoverySockets = new java.util.concurrent.CopyOnWriteArrayList<>();
    private volatile ScheduledFuture<?> tickFuture;
    private volatile ServerDashboard dashboard;

    public Server(GameConfig config) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.game = new Game(1, config);
        this.session = new GameSession(game);
        this.initializer = new GameInitializer();
        this.eventLogger = new ServerEventLogger();
        this.clientsByPlayerId = new ConcurrentHashMap<>();
        this.clientExecutor = Executors.newCachedThreadPool();
        this.tickExecutor = Executors.newSingleThreadScheduledExecutor();
        this.playerSequence = new AtomicInteger(0);
        this.shuttingDown = new AtomicBoolean(false);
        this.codec = new ProtocolCodec();
    }

    public void start() {
        try (ServerSocket tcpServer = new ServerSocket(config.serverPort())) {
            this.serverSocket = tcpServer;
            startDashboard();
            startConsoleControl();
            startDiscoveryResponder();
            printConnectionInfo();
            eventLogger.info("SERVER_STARTED tcpPort=" + config.serverPort() + " discoveryPort=" + config.discoveryPort());
            System.out.println("Escribe 'start' para iniciar la partida o 'stop' para salir.");
            while (!tcpServer.isClosed()) {
                acceptClient(tcpServer.accept());
            }
        } catch (BindException ex) {
            throw new IllegalStateException("El puerto " + config.serverPort() + " ya está en uso.", ex);
        } catch (IOException ex) {
            if (!shuttingDown.get()) {
                throw new IllegalStateException("No se pudo iniciar el servidor: " + ex.getMessage(), ex);
            }
        } finally {
            shutdownExecutors();
        }
    }

    private void startDiscoveryResponder() {
        for (int discoveryPort : discoveryPorts()) {
            startDiscoveryResponder(discoveryPort);
        }
    }

    private List<Integer> discoveryPorts() {
        List<Integer> ports = new ArrayList<>();
        addPort(ports, config.discoveryPort());
        addPort(ports, 5000);
        addPort(ports, 5001);
        return ports;
    }

    private void addPort(List<Integer> ports, int port) {
        if (port > 0 && port <= 65535 && !ports.contains(port)) {
            ports.add(port);
        }
    }

    private void startDiscoveryResponder(int discoveryPort) {
        Thread thread = new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket(null)) {
                socket.setReuseAddress(true);
                socket.bind(new InetSocketAddress(discoveryPort));
                discoverySockets.add(socket);
                socket.setBroadcast(true);
                socket.setSoTimeout(250);
                byte[] buffer = new byte[512];
                long nextBeaconAt = 0L;
                while (!shuttingDown.get()) {
                    long now = System.currentTimeMillis();
                    if (game.status() == GameStatus.WAITING && now >= nextBeaconAt) {
                        sendDiscoveryBeacons(socket, discoveryPort);
                        nextBeaconAt = now + 1000L;
                    }
                    try {
                        DatagramPacket request = new DatagramPacket(buffer, buffer.length);
                        socket.receive(request);
                        if (game.status() != GameStatus.WAITING || !codec.isDiscoverRequest(request.getData(), request.getLength())) {
                            continue;
                        }
                        byte[] response = discoveryResponse();
                        DatagramPacket packet = new DatagramPacket(response, response.length, request.getAddress(), request.getPort());
                        socket.send(packet);
                        eventLogger.info("DISCOVER_RESPONSE remote=" + request.getAddress().getHostAddress());
                    } catch (SocketTimeoutException ignored) {
                    }
                }
            } catch (SocketException ex) {
                if (!shuttingDown.get()) {
                    eventLogger.warning("DISCOVERY_FAILED port=" + discoveryPort + " error=" + ex.getMessage());
                }
            } catch (IOException ex) {
                if (!shuttingDown.get()) {
                    eventLogger.warning("DISCOVERY_IO_ERROR port=" + discoveryPort + " error=" + ex.getMessage());
                }
            } finally {
                discoverySockets.removeIf(DatagramSocket::isClosed);
            }
        }, "server-discovery-" + discoveryPort);
        thread.setDaemon(true);
        thread.start();
    }

    private void sendDiscoveryBeacons(DatagramSocket socket, int discoveryPort) {
        try {
            byte[] response = discoveryResponse();
            for (InetAddress address : broadcastAddresses()) {
                try {
                    socket.send(new DatagramPacket(response, response.length, address, discoveryPort));
                } catch (IOException ex) {
                    // Some networks reject 255.255.255.255 but accept interface broadcasts.
                }
            }
        } catch (IOException ex) {
            eventLogger.warning("DISCOVER_BEACON_FAILED error=" + ex.getMessage());
        }
    }

    private byte[] discoveryResponse() {
        return codec.serializeDiscoverResponse(
                game.gameId(),
                config.serverName(),
                config.serverPort(),
                game.status(),
                game.players().size(),
                config.maximumPlayers()
        );
    }

    private Set<InetAddress> broadcastAddresses() throws IOException {
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

    private void startDashboard() {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }
        javax.swing.SwingUtilities.invokeLater(() -> {
            try {
                ServerDashboard serverDashboard = new ServerDashboard(config.serverName(), config.serverPort(), config.discoveryPort());
                serverDashboard.setStartAction(this::startMatch);
                serverDashboard.setStopAction(this::shutdown);
                dashboard = serverDashboard;
                refreshDashboard();
                serverDashboard.setVisible(true);
            } catch (RuntimeException ex) {
                eventLogger.warning("SERVER_DASHBOARD_FAILED error=" + ex.getMessage());
            }
        });
    }

    private void refreshDashboard() {
        ServerDashboard current = dashboard;
        if (current != null) {
            current.update(game.status(), game.players());
        }
    }

    private void printConnectionInfo() {
        System.out.println("Servidor TCP escuchando en " + config.serverPort() + ". Discovery UDP en " + config.discoveryPort() + ".");
        System.out.println("Conexión local: 127.0.0.1:" + config.serverPort());
        for (String address : localIpv4Addresses()) {
            System.out.println("Red local: " + address + ":" + config.serverPort());
        }
    }

    private List<String> localIpv4Addresses() {
        List<String> addresses = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (!networkInterface.isUp() || networkInterface.isLoopback() || networkInterface.isVirtual()) {
                    continue;
                }
                Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
                while (inetAddresses.hasMoreElements()) {
                    InetAddress inetAddress = inetAddresses.nextElement();
                    if (inetAddress instanceof Inet4Address && !inetAddress.isLoopbackAddress()) {
                        addresses.add(inetAddress.getHostAddress());
                    }
                }
            }
        } catch (IOException ignored) {
        }
        return addresses;
    }

    private void startConsoleControl() {
        Thread consoleThread = new Thread(() -> {
            try (Scanner scanner = new Scanner(System.in)) {
                while (!shuttingDown.get() && scanner.hasNextLine()) {
                    switch (scanner.nextLine().trim().toLowerCase()) {
                        case "start" -> startMatch();
                        case "stop", "exit", "quit" -> shutdown();
                        default -> {
                        }
                    }
                }
            }
        }, "server-console");
        consoleThread.setDaemon(true);
        consoleThread.start();
    }

    private void acceptClient(Socket socket) {
        String remoteAddress = String.valueOf(socket.getRemoteSocketAddress());
        eventLogger.info("TCP_ACCEPTED remote=" + remoteAddress);
        try {
            clientExecutor.submit(new ClientHandler(socket, remoteAddress));
        } catch (IOException ex) {
            eventLogger.warning("TCP_HANDLER_FAILED remote=" + remoteAddress + " error=" + ex.getMessage());
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private synchronized void startMatch() {
        if (game.status() != GameStatus.WAITING) {
            eventLogger.warning("START_IGNORED status=" + game.status());
            return;
        }
        if (game.players().isEmpty()) {
            System.out.println("No hay jugadores conectados.");
            return;
        }
        game.setStatus(GameStatus.STARTING);
        broadcast(GameMessageMapper.toLobbyStateMessage(game));
        refreshDashboard();
        tickExecutor.execute(this::runCountdownAndStart);
    }

    private void runCountdownAndStart() {
        try {
            for (int remaining = config.countdownSeconds(); remaining >= 1; remaining--) {
                broadcast(new GameCountdownMessage(remaining));
                eventLogger.info("GAME_COUNTDOWN seconds=" + remaining);
                TimeUnit.SECONDS.sleep(1);
            }
            synchronized (session) {
                initializer.initialize(game);
                game.setStatus(GameStatus.RUNNING);
            }
            refreshDashboard();
            broadcast(GameMessageMapper.toGameStartedMessage(game));
            tickFuture = tickExecutor.scheduleAtFixedRate(this::runTickSafely, 0L, config.tickIntervalMs(), TimeUnit.MILLISECONDS);
            eventLogger.info("MATCH_RUNNING players=" + game.players().size());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private void runTickSafely() {
        try {
            GameTickResult result = session.tick();
            if (!result.events().isEmpty()) {
                broadcast(result.events());
            }
            broadcast(result.stateMessage());
            if (result.gameOverMessage() != null) {
                broadcast(result.gameOverMessage());
                ScheduledFuture<?> current = tickFuture;
                if (current != null) {
                    current.cancel(false);
                }
                eventLogger.info("MATCH_FINISHED tick=" + result.tick() + " winnerId=" + result.gameOverMessage().winnerId());
                refreshDashboard();
            }
        } catch (RuntimeException ex) {
            eventLogger.warning("TICK_FAILED error=" + ex.getMessage());
        }
    }

    private void broadcast(ProtocolMessage message) {
        broadcast(List.of(message));
    }

    private void broadcast(List<? extends ProtocolMessage> messages) {
        List<ClientContext> snapshot = new ArrayList<>(clientsByPlayerId.values());
        for (ClientContext context : snapshot) {
            for (ProtocolMessage message : messages) {
                context.send(message);
            }
        }
    }

    private synchronized void shutdown() {
        if (!shuttingDown.compareAndSet(false, true)) {
            return;
        }
        for (DatagramSocket socket : discoverySockets) {
            closeQuietly(socket);
        }
        closeQuietly(serverSocket);
        ServerDashboard current = dashboard;
        if (current != null) {
            javax.swing.SwingUtilities.invokeLater(current::dispose);
        }
        shutdownExecutors();
    }

    private void shutdownExecutors() {
        clientExecutor.shutdownNow();
        tickExecutor.shutdownNow();
    }

    private void closeQuietly(DatagramSocket socket) {
        if (socket != null) {
            socket.close();
        }
    }

    private void closeQuietly(ServerSocket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private int nextPlayerId() {
        return playerSequence.incrementAndGet();
    }

    private final class ClientHandler implements Runnable {
        private final ClientContext context;
        private final String remoteAddress;

        private ClientHandler(Socket socket, String remoteAddress) throws IOException {
            this.context = new ClientContext(new LineConnection(socket));
            this.remoteAddress = remoteAddress;
        }

        @Override
        public void run() {
            try {
                while (!shuttingDown.get()) {
                    ProtocolMessage message = context.connection.readMessage();
                    if (message == null) {
                        handleUnexpectedDisconnect();
                        return;
                    }
                    handleMessage(message);
                }
            } catch (ProtocolDecodeException ex) {
                context.send(new ErrorMessage(ex.errorCode(), ex.getMessage()));
                eventLogger.warning("PROTOCOL_ERROR remote=" + remoteAddress + " code=" + ex.errorCode() + " message=" + ex.getMessage());
            } catch (IOException ex) {
                eventLogger.warning("CLIENT_IO_ERROR remote=" + remoteAddress + " playerId=" + context.playerId() + " error=" + ex.getMessage());
                handleUnexpectedDisconnect();
            } finally {
                context.closeQuietly();
            }
        }

        private void handleMessage(ProtocolMessage message) {
            if (message instanceof JoinRequest joinRequest) {
                handleJoin(joinRequest);
            } else if (!context.joined()) {
                context.send(new ErrorMessage(ErrorCode.UNKNOWN_PLAYER, "Debes enviar JOIN primero."));
            } else if (message instanceof ChangeDirectionRequest input) {
                handleInput(input);
            } else if (message instanceof InteractRequest interact) {
                handleInteract(interact);
            } else if (message instanceof LeaveRequest leave) {
                handleLeave(leave);
            } else {
                context.send(new ErrorMessage(ErrorCode.INVALID_MESSAGE, "Tipo de mensaje no soportado."));
            }
        }

        private void handleJoin(JoinRequest joinRequest) {
            if (context.joined()) {
                context.send(new ErrorMessage(ErrorCode.INVALID_MESSAGE, "La conexión ya tiene jugador."));
                return;
            }
            String name = joinRequest.name() == null ? "" : joinRequest.name().trim();
            byte[] nameBytes = name.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            JoinRejectedReason rejectedReason = null;
            int playerId = 0;
            synchronized (session) {
                if (game.status() != GameStatus.WAITING) {
                    rejectedReason = JoinRejectedReason.GAME_ALREADY_STARTED;
                } else if (game.players().size() >= config.maximumPlayers()) {
                    rejectedReason = JoinRejectedReason.GAME_FULL;
                } else if (name.isBlank() || nameBytes.length > 20) {
                    rejectedReason = JoinRejectedReason.INVALID_NAME;
                } else {
                    playerId = nextPlayerId();
                    Player player = new Player(playerId, name, 0, 0, Direction.NONE, true, false);
                    game.addPlayer(player);
                    context.joined(playerId);
                    clientsByPlayerId.put(playerId, context);
                }
            }
            if (rejectedReason != null) {
                context.send(new JoinRejectedMessage(rejectedReason));
                eventLogger.warning("JOIN_REJECTED remote=" + remoteAddress + " reason=" + rejectedReason + " name=" + name);
                return;
            }
            context.send(new JoinAcceptedMessage(playerId, game.gameId()));
            broadcast(GameMessageMapper.toLobbyStateMessage(game));
            refreshDashboard();
            eventLogger.info("JOIN_ACCEPTED remote=" + remoteAddress + " playerId=" + playerId + " name=" + name);
        }

        private void handleInput(ChangeDirectionRequest input) {
            if (input.playerId() != context.playerId()) {
                context.send(new ErrorMessage(ErrorCode.UNKNOWN_PLAYER, "playerId no corresponde a esta conexión."));
                return;
            }
            if (game.status() != GameStatus.RUNNING) {
                context.send(new ErrorMessage(game.status() == GameStatus.FINISHED ? ErrorCode.GAME_FINISHED : ErrorCode.GAME_NOT_STARTED, "La partida no está en ejecución."));
                return;
            }
            session.submitDirectionChange(input.playerId(), input.direction());
            eventLogger.info("INPUT playerId=" + input.playerId() + " direction=" + input.direction());
        }

        private void handleInteract(InteractRequest interact) {
            if (interact.playerId() != context.playerId()) {
                context.send(new ErrorMessage(ErrorCode.UNKNOWN_PLAYER, "playerId no corresponde a esta conexión."));
                return;
            }
            if (game.status() != GameStatus.RUNNING) {
                context.send(new ErrorMessage(game.status() == GameStatus.FINISHED ? ErrorCode.GAME_FINISHED : ErrorCode.GAME_NOT_STARTED, "La partida no está en ejecución."));
                return;
            }
            session.submitInteraction(interact.playerId());
            eventLogger.info("INTERACT playerId=" + interact.playerId());
        }

        private void handleLeave(LeaveRequest leave) {
            if (leave.playerId() != context.playerId()) {
                context.send(new ErrorMessage(ErrorCode.UNKNOWN_PLAYER, "Salida inválida."));
                return;
            }
            eventLogger.info("LEAVE playerId=" + leave.playerId());
            disconnectAndBroadcast();
        }

        private void handleUnexpectedDisconnect() {
            if (context.joined()) {
                disconnectAndBroadcast();
            } else {
                eventLogger.info("CLIENT_DISCONNECTED_BEFORE_JOIN remote=" + remoteAddress);
            }
        }

        private void disconnectAndBroadcast() {
            int playerId = context.playerId();
            if (playerId > 0) {
                clientsByPlayerId.remove(playerId);
                List<ProtocolMessage> events = session.disconnectPlayer(playerId);
                broadcast(events);
                if (game.status() == GameStatus.WAITING || game.status() == GameStatus.STARTING) {
                    broadcast(GameMessageMapper.toLobbyStateMessage(game));
                }
                refreshDashboard();
            }
            context.closeQuietly();
        }
    }

    private static final class ClientContext {
        private final LineConnection connection;
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private volatile int playerId;
        private volatile boolean joined;

        private ClientContext(LineConnection connection) {
            this.connection = connection;
        }

        private boolean joined() {
            return joined;
        }

        private int playerId() {
            return playerId;
        }

        private void joined(int playerId) {
            this.playerId = playerId;
            this.joined = true;
        }

        private void send(ProtocolMessage message) {
            try {
                connection.sendMessage(message);
            } catch (IOException ignored) {
                closeQuietly();
            }
        }

        private void closeQuietly() {
            if (closed.compareAndSet(false, true)) {
                try {
                    connection.close();
                } catch (IOException ignored) {
                }
            }
        }
    }
}
