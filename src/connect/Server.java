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
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
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
            System.out.println("Servidor escuchando en el puerto " + config.serverPort() + ".");
            System.out.println("Escribe 'start' para iniciar la partida o 'stop' para salir.");

            while (!serverSocket.isClosed()) {
                Socket socket = serverSocket.accept();
                clientExecutor.submit(new ClientHandler(socket));
            }
        } catch (IOException ex) {
            if (!isShuttingDown()) {
                throw new IllegalStateException("No se pudo iniciar el servidor", ex);
            }
        } finally {
            shutdownExecutors();
        }
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
        if (matchStarted.get() || game.status() != GameStatus.WAITING) {
            return;
        }
        if (game.players().isEmpty()) {
            System.out.println("No hay jugadores conectados.");
            return;
        }

        game.setStatus(GameStatus.STARTING);
        initializer.initialize(game);
        matchStarted.set(true);

        broadcast(GameMessageMapper.toGameStartedMessage(game));
        game.setStatus(GameStatus.RUNNING);
        tickFuture = tickExecutor.scheduleAtFixedRate(
                this::runTickSafely,
                0L,
                config.movementIntervalMs(),
                TimeUnit.MILLISECONDS
        );

        System.out.println("Partida iniciada.");
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
            broadcast(result.events());
        }
        broadcast(result.stateMessage());
        if (result.finished()) {
            ScheduledFuture<?> current = tickFuture;
            if (current != null) {
                current.cancel(false);
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

        private ClientHandler(Socket socket) throws IOException {
            this.context = new ClientContext(new LineConnection(socket));
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
            } catch (IllegalArgumentException ex) {
                context.send(new ErrorMessage(ProtocolVersion.V1_0, ErrorCode.INVALID_MESSAGE, ex.getMessage()));
            } catch (IOException ex) {
                handleUnexpectedDisconnect();
            } finally {
                context.closeQuietly();
            }
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
                return;
            }
            if (joinRequest.name() == null || joinRequest.name().isBlank()) {
                context.send(new JoinRejectedMessage(ProtocolVersion.V1_0, JoinRejectedReason.INVALID_NAME));
                return;
            }
            if (game.status() != GameStatus.WAITING || matchStarted.get()) {
                context.send(new JoinRejectedMessage(ProtocolVersion.V1_0, JoinRejectedReason.GAME_ALREADY_STARTED));
                return;
            }
            if (game.players().size() >= config.maximumPlayers()) {
                context.send(new JoinRejectedMessage(ProtocolVersion.V1_0, JoinRejectedReason.GAME_FULL));
                return;
            }

            String playerId = nextPlayerId();
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
            context.send(new JoinAcceptedMessage(ProtocolVersion.V1_0, playerId, game.gameId()));
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
            session.submitDirectionChange(changeDirection.playerId(), changeDirection.direction());
        }

        private void handleLeave(LeaveRequest leaveRequest) {
            if (!game.gameId().equals(leaveRequest.gameId()) || !context.playerId().equals(leaveRequest.playerId())) {
                context.send(new ErrorMessage(ProtocolVersion.V1_0, ErrorCode.UNKNOWN_PLAYER, "Salida inválida."));
                return;
            }
            disconnectAndBroadcast();
        }

        private void handleUnexpectedDisconnect() {
            if (context.joined()) {
                disconnectAndBroadcast();
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
