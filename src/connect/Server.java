package connect;

import engine.GameInitializer;
import engine.GameSession;
import engine.GameTickResult;
import model.Direction;
import model.Game;
import model.GameConfig;
import model.GameStatus;
import model.Player;
import protocol.ErrorCode;
import protocol.ErrorMessage;
import protocol.ProtocolDecodeException;
import protocol.GameMessageMapper;
import protocol.JoinAcceptedMessage;
import protocol.JoinRejectedMessage;
import protocol.JoinRejectedReason;
import protocol.LeaveRequest;
import protocol.MessageType;
import protocol.ProtocolMessage;
import protocol.ProtocolVersion;
import protocol.ChangeDirectionRequest;
import protocol.JoinRequest;
import protocol.PlayerDisconnectedMessage;

import java.io.IOException;
import java.net.BindException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
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
    private final Map<String, ClientContext> clientsByPlayerId;
    private final ExecutorService clientExecutor;
    private final ScheduledExecutorService tickExecutor;
    private final AtomicInteger playerSequence;
    private final AtomicBoolean matchStarted;

    private volatile ServerSocket serverSocket;
    private volatile ScheduledFuture<?> tickFuture;

    public Server(GameConfig config) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.game = new Game("GAME-001", config);
        this.session = new GameSession(game);
        this.initializer = new GameInitializer();
        this.eventLogger = new ServerEventLogger();
        this.clientsByPlayerId = new ConcurrentHashMap<>();
        this.clientExecutor = Executors.newCachedThreadPool();
        this.tickExecutor = Executors.newSingleThreadScheduledExecutor();
        this.playerSequence = new AtomicInteger(0);
        this.matchStarted = new AtomicBoolean(false);
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(config.serverPort())) {
            this.serverSocket = serverSocket;
            startConsoleControl();
            printConnectionInfo();
            eventLogger.info("SERVER_STARTED port=" + config.serverPort());
            System.out.println("Escribe 'start' para iniciar la partida o 'stop' para salir.");

            while (!serverSocket.isClosed()) {
                Socket socket = serverSocket.accept();
                acceptClient(socket);
            }
        } catch (BindException ex) {
            throw new IllegalStateException(
                    "El puerto " + config.serverPort() + " ya está en uso. Prueba con otro, por ejemplo: make run-server PORT=5001",
                    ex
            );
        } catch (IOException ex) {
            if (!isShuttingDown()) {
                throw new IllegalStateException(
                        "No se pudo iniciar el servidor en el puerto " + config.serverPort() + ": " + ex.getMessage(),
                        ex
                );
            }
        } finally {
            shutdownExecutors();
        }
    }

    private void printConnectionInfo() {
        System.out.println("Servidor escuchando en el puerto " + config.serverPort() + ".");
        System.out.println("Conexiones locales: 127.0.0.1:" + config.serverPort());

        List<String> addresses = localIpv4Addresses();
        if (addresses.isEmpty()) {
            System.out.println("Conexiones en red local: no se detectaron interfaces IPv4 útiles.");
            return;
        }

        System.out.println("Conexiones en red local:");
        for (String address : addresses) {
            System.out.println("  " + address + ":" + config.serverPort());
        }
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
                        String hostAddress = inetAddress.getHostAddress();
                        if (!addresses.contains(hostAddress)) {
                            addresses.add(hostAddress);
                        }
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
                while (!isShuttingDown() && scanner.hasNextLine()) {
                    String command = scanner.nextLine().trim().toLowerCase();
                    switch (command) {
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

    private synchronized void startMatch() {
        long nowEpochMillis = System.currentTimeMillis();
        ProtocolMessage gameStartedMessage;
        synchronized (session) {
            if (matchStarted.get() || game.status() != GameStatus.WAITING) {
                eventLogger.warning("START_IGNORED status=" + game.status() + " matchStarted=" + matchStarted.get());
                return;
            }
            if (game.players().isEmpty()) {
                System.out.println("No hay jugadores conectados.");
                eventLogger.warning("START_REJECTED reason=NO_PLAYERS");
                return;
            }

            game.setStatus(GameStatus.STARTING);
            initializer.initialize(game);
            matchStarted.set(true);
            gameStartedMessage = GameMessageMapper.toGameStartedMessage(game, nowEpochMillis);
            eventLogger.info("MATCH_STARTING gameId=" + game.gameId() + " players=" + game.players().size());
        }

        broadcast(gameStartedMessage);
        synchronized (session) {
            if (game.status() == GameStatus.STARTING) {
                game.setStatus(GameStatus.RUNNING);
            }
        }
        tickFuture = tickExecutor.scheduleAtFixedRate(
                this::runTickSafely,
                0L,
                config.movementIntervalMs(),
                TimeUnit.MILLISECONDS
        );

        System.out.println("Partida iniciada.");
        eventLogger.info("MATCH_RUNNING gameId=" + game.gameId());
    }

    private void runTickSafely() {
        try {
            runTick();
        } catch (RuntimeException ex) {
            ex.printStackTrace(System.err);
        }
    }

    private void runTick() {
        GameTickResult result = session.tick(System.currentTimeMillis());
        if (!result.events().isEmpty()) {
            logGameEvents(result.events());
            broadcast(result.events());
        }
        broadcast(result.stateMessage());
        if (result.finished()) {
            ScheduledFuture<?> current = tickFuture;
            if (current != null) {
                current.cancel(false);
            }
            eventLogger.info("MATCH_FINISHED gameId=" + game.gameId() + " tick=" + result.tick());
        }
    }

    private void logGameEvents(List<ProtocolMessage> events) {
        for (ProtocolMessage event : events) {
            switch (event) {
                case protocol.FlagPickedUpMessage pickedUp ->
                        eventLogger.info("FLAG_PICKED_UP gameId=" + pickedUp.gameId() + " tick=" + pickedUp.tick() + " playerId=" + pickedUp.playerId());
                case protocol.FlagStolenMessage stolen ->
                        eventLogger.info("FLAG_STOLEN gameId=" + stolen.gameId() + " tick=" + stolen.tick() + " previousCarrierId=" + stolen.previousCarrierId() + " newCarrierId=" + stolen.newCarrierId());
                case protocol.GameOverMessage gameOver ->
                        eventLogger.info("GAME_OVER gameId=" + gameOver.gameId() + " winnerId=" + gameOver.winnerId() + " winnerName=" + gameOver.winnerName() + " reason=" + gameOver.reason());
                case protocol.PlayerDisconnectedMessage disconnected ->
                        eventLogger.info("PLAYER_DISCONNECTED gameId=" + disconnected.gameId() + " playerId=" + disconnected.playerId());
                default -> eventLogger.info("EVENT type=" + event.type());
            }
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
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
        }
        shutdownExecutors();
    }

    private void shutdownExecutors() {
        clientExecutor.shutdownNow();
        tickExecutor.shutdownNow();
    }

    private boolean isShuttingDown() {
        ServerSocket current = serverSocket;
        return current != null && current.isClosed();
    }

    private String nextPlayerId() {
        return String.format("P%02d", playerSequence.incrementAndGet());
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
                while (true) {
                    ProtocolMessage message = context.connection.readMessage();
                    if (message == null) {
                        handleUnexpectedDisconnect();
                        return;
                    }
                    handleMessage(message);
                }
            } catch (ProtocolDecodeException ex) {
                handleProtocolDecodeException(ex);
            } catch (IllegalArgumentException ex) {
                eventLogger.warning("CLIENT_MESSAGE_ERROR remote=" + remoteAddress + " playerId=" + context.playerId() + " error=" + ex.getMessage());
                context.send(new ErrorMessage(ProtocolVersion.V1_0, ErrorCode.INVALID_MESSAGE, ex.getMessage()));
            } catch (IOException ex) {
                eventLogger.warning("CLIENT_IO_ERROR remote=" + remoteAddress + " playerId=" + context.playerId() + " error=" + ex.getMessage());
                handleUnexpectedDisconnect();
            } finally {
                context.closeQuietly();
            }
        }

        private void handleProtocolDecodeException(ProtocolDecodeException ex) {
            ErrorCode code = ex.errorCode();
            eventLogger.warning("PROTOCOL_ERROR remote=" + remoteAddress + " playerId=" + context.playerId() + " code=" + code + " message=" + ex.getMessage());
            if (ex.messageType() == MessageType.JOIN && code == ErrorCode.UNSUPPORTED_PROTOCOL_VERSION) {
                context.send(new JoinRejectedMessage(ProtocolVersion.V1_0, JoinRejectedReason.UNSUPPORTED_PROTOCOL_VERSION));
                return;
            }
            context.send(new ErrorMessage(ProtocolVersion.V1_0, code, ex.getMessage()));
        }

        private void handleMessage(ProtocolMessage message) throws IOException {
            if (message instanceof JoinRequest joinRequest) {
                handleJoin(joinRequest);
                return;
            }

            if (!context.joined()) {
                context.send(new ErrorMessage(ProtocolVersion.V1_0, ErrorCode.GAME_NOT_STARTED, "Debes enviar JOIN primero."));
                return;
            }

            if (message instanceof ChangeDirectionRequest changeDirection) {
                handleChangeDirection(changeDirection);
                return;
            }

            if (message instanceof LeaveRequest leaveRequest) {
                handleLeave(leaveRequest);
                return;
            }

            context.send(new ErrorMessage(ProtocolVersion.V1_0, ErrorCode.INVALID_MESSAGE, "Tipo de mensaje no soportado."));
        }

        private void handleJoin(JoinRequest joinRequest) throws IOException {
            if (context.joined()) {
                context.send(new ErrorMessage(ProtocolVersion.V1_0, ErrorCode.GAME_ALREADY_STARTED, "Ya existe una sesión asociada a esta conexión."));
                return;
            }

            if (!ProtocolVersion.V1_0.equals(joinRequest.protocolVersion())) {
                context.send(new JoinRejectedMessage(ProtocolVersion.V1_0, JoinRejectedReason.UNSUPPORTED_PROTOCOL_VERSION));
                eventLogger.warning("JOIN_REJECTED remote=" + remoteAddress + " reason=UNSUPPORTED_PROTOCOL_VERSION name=" + joinRequest.name());
                return;
            }
            if (joinRequest.name() == null || joinRequest.name().isBlank()) {
                context.send(new JoinRejectedMessage(ProtocolVersion.V1_0, JoinRejectedReason.INVALID_NAME));
                eventLogger.warning("JOIN_REJECTED remote=" + remoteAddress + " reason=INVALID_NAME");
                return;
            }
            String playerId;
            JoinRejectedReason rejectedReason = null;
            synchronized (session) {
                if (game.status() != GameStatus.WAITING || matchStarted.get()) {
                    rejectedReason = JoinRejectedReason.GAME_ALREADY_STARTED;
                    playerId = null;
                } else if (game.players().size() >= config.maximumPlayers()) {
                    rejectedReason = JoinRejectedReason.GAME_FULL;
                    playerId = null;
                } else {
                    playerId = nextPlayerId();
                    Player player = new Player(
                            playerId,
                            joinRequest.name().trim(),
                            -1,
                            -1,
                            Direction.DOWN,
                            true,
                            false,
                            false,
                            0L
                    );
                    game.addPlayer(player);
                    context.joined(playerId);
                    clientsByPlayerId.put(playerId, context);
                }
            }
            if (rejectedReason != null) {
                context.send(new JoinRejectedMessage(ProtocolVersion.V1_0, rejectedReason));
                eventLogger.warning("JOIN_REJECTED remote=" + remoteAddress + " reason=" + rejectedReason + " name=" + joinRequest.name().trim());
                return;
            }
            context.send(new JoinAcceptedMessage(ProtocolVersion.V1_0, playerId, game.gameId()));
            eventLogger.info("JOIN_ACCEPTED remote=" + remoteAddress + " gameId=" + game.gameId() + " playerId=" + playerId + " name=" + joinRequest.name().trim());
        }

        private void handleChangeDirection(ChangeDirectionRequest changeDirection) {
            if (!game.gameId().equals(changeDirection.gameId())) {
                context.send(new ErrorMessage(ProtocolVersion.V1_0, ErrorCode.INVALID_MESSAGE, "gameId incorrecto."));
                return;
            }
            if (!context.playerId().equals(changeDirection.playerId())) {
                context.send(new ErrorMessage(ProtocolVersion.V1_0, ErrorCode.UNKNOWN_PLAYER, "playerId no corresponde a esta conexión."));
                return;
            }
            GameStatus status;
            synchronized (session) {
                status = game.status();
            }
            if (status != GameStatus.RUNNING) {
                ErrorCode code = status == GameStatus.FINISHED ? ErrorCode.GAME_FINISHED : ErrorCode.GAME_NOT_STARTED;
                context.send(new ErrorMessage(ProtocolVersion.V1_0, code, "La partida no está en ejecución."));
                return;
            }
            session.submitDirectionChange(changeDirection.playerId(), changeDirection.direction());
            eventLogger.info("CHANGE_DIRECTION gameId=" + changeDirection.gameId() + " playerId=" + changeDirection.playerId() + " direction=" + changeDirection.direction());
        }

        private void handleLeave(LeaveRequest leaveRequest) {
            if (!game.gameId().equals(leaveRequest.gameId()) || !context.playerId().equals(leaveRequest.playerId())) {
                context.send(new ErrorMessage(ProtocolVersion.V1_0, ErrorCode.UNKNOWN_PLAYER, "Salida inválida."));
                return;
            }
            eventLogger.info("LEAVE gameId=" + leaveRequest.gameId() + " playerId=" + leaveRequest.playerId());
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
            String playerId = context.playerId();
            if (playerId != null) {
                clientsByPlayerId.remove(playerId);
                List<ProtocolMessage> events = session.disconnectPlayer(playerId, System.currentTimeMillis());
                broadcast(events);
            }
            context.markClosed();
            context.closeQuietly();
        }
    }

    private final class ClientContext {
        private final LineConnection connection;
        private volatile String playerId;
        private volatile boolean joined;
        private final AtomicBoolean closed;

        private ClientContext(LineConnection connection) {
            this.connection = connection;
            this.closed = new AtomicBoolean(false);
        }

        private boolean joined() {
            return joined;
        }

        private String playerId() {
            return playerId;
        }

        private void joined(String playerId) {
            this.playerId = playerId;
            this.joined = true;
        }

        private void send(ProtocolMessage message) {
            try {
                connection.sendMessage(message);
            } catch (IOException ignored) {
                markClosed();
            }
        }

        private void markClosed() {
            closed.set(true);
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
