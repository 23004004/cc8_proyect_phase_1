package connect;

import model.Direction;
import model.GameConfig;
import protocol.ChangeDirectionRequest;
import protocol.ErrorMessage;
import protocol.FlagPickedUpMessage;
import protocol.FlagStolenMessage;
import protocol.GameOverMessage;
import protocol.GameStartedMessage;
import protocol.GameStateMessage;
import protocol.JoinAcceptedMessage;
import protocol.JoinRejectedMessage;
import protocol.JoinRequest;
import protocol.PlayerDisconnectedMessage;
import protocol.LeaveRequest;
import protocol.ProtocolMessage;
import protocol.ProtocolVersion;
import view.PanelGame;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.KeyStroke;
import javax.swing.WindowConstants;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class Client {
    private final ClientEventLogger eventLogger = new ClientEventLogger();

    public void start() {
        start("127.0.0.1", GameConfig.defaults().serverPort());
    }

    public void start(String host, int port) {
        JFrame frame = null;
        try (Scanner scanner = new Scanner(System.in)) {
            System.out.print("Nombre del jugador: ");
            String name = readNonBlankLine(scanner, "Jugador");

            System.out.println("Conectando a " + host + ":" + port + " como " + name + "...");
            eventLogger.info("CONNECTING host=" + host + " port=" + port + " name=" + name);
            try (Socket socket = openSocket(host, port);
                 LineConnection connection = new LineConnection(socket)) {

                PanelGame panel = new PanelGame();
                frame = createFrame(panel);
                AtomicReference<String> playerIdRef = new AtomicReference<>();
                AtomicReference<String> gameIdRef = new AtomicReference<>();
                AtomicReference<Direction> lastDirectionRef = new AtomicReference<>(Direction.DOWN);
                AtomicBoolean closing = new AtomicBoolean(false);
                CountDownLatch finished = new CountDownLatch(1);

                connection.sendMessage(new JoinRequest(ProtocolVersion.V1_0, name));
                eventLogger.info("JOIN_SENT host=" + host + " port=" + port + " name=" + name);
                System.out.println("Conexión TCP establecida. JOIN enviado, esperando confirmación del servidor...");
                installCloseHandler(frame, connection, playerIdRef, gameIdRef, closing, finished);
                installKeyboardControls(frame, panel, connection, playerIdRef, gameIdRef, lastDirectionRef);

                Thread reader = new Thread(() -> readLoop(connection, panel, playerIdRef, gameIdRef, finished), "client-reader");
                reader.setDaemon(true);
                reader.start();

                panel.applyStatusText("Conectado. Esperando inicio de partida.");
                System.out.println("Controles: W/A/S/D para cambiar dirección, R o espacio para intentar contacto, cerrar ventana para salir.");
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

    private void readLoop(
            LineConnection connection,
            PanelGame panel,
            AtomicReference<String> playerIdRef,
            AtomicReference<String> gameIdRef,
            CountDownLatch finished
    ) {
        try {
            while (true) {
                ProtocolMessage message = connection.readMessage();
                if (message == null) {
                    System.out.println("Conexión cerrada por el servidor.");
                    return;
                }

                if (message instanceof GameStartedMessage gameStarted) {
                    panel.applyGameStarted(gameStarted);
                    eventLogger.info("GAME_STARTED gameId=" + gameStarted.gameId() + " rows=" + gameStarted.rows() + " columns=" + gameStarted.columns());
                    System.out.println("Partida iniciada. Usa W/A/S/D en la ventana.");
                } else if (message instanceof GameStateMessage gameState) {
                    panel.applyGameState(gameState);
                } else if (message instanceof GameOverMessage gameOver) {
                    panel.applyGameOver(gameOver);
                    eventLogger.info("GAME_OVER gameId=" + gameOver.gameId() + " winnerId=" + gameOver.winnerId() + " winnerName=" + gameOver.winnerName() + " reason=" + gameOver.reason());
                    System.out.println("Partida finalizada. Ganador: " + gameOver.winnerName() + " (" + gameOver.winnerId() + ").");
                } else if (message instanceof PlayerDisconnectedMessage disconnected) {
                    panel.applyStatusText("Jugador desconectado: " + disconnected.playerId());
                    eventLogger.info("PLAYER_DISCONNECTED gameId=" + disconnected.gameId() + " playerId=" + disconnected.playerId());
                    System.out.println("Jugador desconectado: " + disconnected.playerId());
                } else if (message instanceof FlagPickedUpMessage pickedUp) {
                    panel.applyStatusText("Bandera tomada por " + pickedUp.playerId());
                    eventLogger.info("FLAG_PICKED_UP gameId=" + pickedUp.gameId() + " tick=" + pickedUp.tick() + " playerId=" + pickedUp.playerId());
                } else if (message instanceof FlagStolenMessage stolen) {
                    panel.applyStatusText("Bandera robada: " + stolen.previousCarrierId() + " -> " + stolen.newCarrierId());
                    eventLogger.info("FLAG_STOLEN gameId=" + stolen.gameId() + " tick=" + stolen.tick() + " previousCarrierId=" + stolen.previousCarrierId() + " newCarrierId=" + stolen.newCarrierId());
                } else if (message instanceof JoinRejectedMessage joinRejected) {
                    panel.applyStatusText("Join rechazado: " + joinRejected.reason());
                    eventLogger.warning("JOIN_REJECTED reason=" + joinRejected.reason());
                    System.out.println("JOIN rechazado: " + joinRejected.reason());
                    return;
                } else if (message instanceof ErrorMessage errorMessage) {
                    panel.applyStatusText("Error: " + errorMessage.code());
                    eventLogger.warning("SERVER_ERROR code=" + errorMessage.code() + " description=" + errorMessage.description());
                    System.out.println("Error del servidor: " + errorMessage.code());
                }

                if (message instanceof JoinAcceptedMessage joinAccepted) {
                    playerIdRef.set(joinAccepted.playerId());
                    gameIdRef.set(joinAccepted.gameId());
                    panel.setLocalPlayerId(joinAccepted.playerId());
                    panel.applyStatusText("Conexión exitosa. Jugador " + joinAccepted.playerId());
                    eventLogger.info("JOIN_ACCEPTED gameId=" + joinAccepted.gameId() + " playerId=" + joinAccepted.playerId());
                    System.out.println("Conexión exitosa. Asignado playerId: " + joinAccepted.playerId());
                }
            }
        } catch (IOException | IllegalArgumentException ex) {
            eventLogger.warning("READ_LOOP_STOPPED error=" + ex.getMessage());
            System.out.println("Lectura detenida: " + ex.getMessage());
        } finally {
            eventLogger.info("READ_LOOP_FINISHED");
            finished.countDown();
        }
    }

    private String readNonBlankLine(Scanner scanner, String fallback) {
        if (!scanner.hasNextLine()) {
            return fallback;
        }
        String value = scanner.nextLine().trim();
        return value.isBlank() ? fallback : value;
    }

    private void installKeyboardControls(
            JFrame frame,
            PanelGame panel,
            LineConnection connection,
            AtomicReference<String> playerIdRef,
            AtomicReference<String> gameIdRef,
            AtomicReference<Direction> lastDirectionRef
    ) {
        bindDirection(frame, "pressed W", "move-up", Direction.UP, connection, playerIdRef, gameIdRef, lastDirectionRef, panel);
        bindDirection(frame, "pressed A", "move-left", Direction.LEFT, connection, playerIdRef, gameIdRef, lastDirectionRef, panel);
        bindDirection(frame, "pressed S", "move-down", Direction.DOWN, connection, playerIdRef, gameIdRef, lastDirectionRef, panel);
        bindDirection(frame, "pressed D", "move-right", Direction.RIGHT, connection, playerIdRef, gameIdRef, lastDirectionRef, panel);
        bindAction(frame, "pressed R", "action-r", connection, playerIdRef, gameIdRef, lastDirectionRef, panel);
        bindAction(frame, "SPACE", "action-space", connection, playerIdRef, gameIdRef, lastDirectionRef, panel);
    }

    private void bindDirection(
            JFrame frame,
            String keyStroke,
            String actionName,
            Direction direction,
            LineConnection connection,
            AtomicReference<String> playerIdRef,
            AtomicReference<String> gameIdRef,
            AtomicReference<Direction> lastDirectionRef,
            PanelGame panel
    ) {
        JComponent root = frame.getRootPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(keyStroke), actionName);
        root.getActionMap().put(actionName, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                lastDirectionRef.set(direction);
                sendDirection(connection, playerIdRef.get(), gameIdRef.get(), direction, panel);
            }
        });
    }

    private void bindAction(
            JFrame frame,
            String keyStroke,
            String actionName,
            LineConnection connection,
            AtomicReference<String> playerIdRef,
            AtomicReference<String> gameIdRef,
            AtomicReference<Direction> lastDirectionRef,
            PanelGame panel
    ) {
        JComponent root = frame.getRootPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(keyStroke), actionName);
        root.getActionMap().put(actionName, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                sendDirection(connection, playerIdRef.get(), gameIdRef.get(), lastDirectionRef.get(), panel);
            }
        });
    }

    private void installCloseHandler(
            JFrame frame,
            LineConnection connection,
            AtomicReference<String> playerIdRef,
            AtomicReference<String> gameIdRef,
            AtomicBoolean closing,
            CountDownLatch finished
    ) {
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                if (closing.compareAndSet(false, true)) {
                    try {
                        sendLeave(connection, playerIdRef.get(), gameIdRef.get());
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

    private void sendDirection(
            LineConnection connection,
            String playerId,
            String gameId,
            Direction direction,
            PanelGame panel
    ) {
        if (playerId == null || gameId == null) {
            eventLogger.warning("DIRECTION_IGNORED reason=WAITING_JOIN direction=" + direction);
            panel.applyStatusText("Esperando JOIN_ACCEPTED.");
            return;
        }
        try {
            connection.sendMessage(new ChangeDirectionRequest(ProtocolVersion.V1_0, gameId, playerId, direction));
            eventLogger.info("CHANGE_DIRECTION_SENT gameId=" + gameId + " playerId=" + playerId + " direction=" + direction);
            panel.applyLocalDirection(direction);
        } catch (IOException ex) {
            eventLogger.warning("CHANGE_DIRECTION_FAILED gameId=" + gameId + " playerId=" + playerId + " direction=" + direction + " error=" + ex.getMessage());
            panel.applyStatusText("No se pudo enviar dirección.");
        }
    }

    private void sendLeave(LineConnection connection, String playerId, String gameId) throws IOException {
        if (playerId != null && gameId != null) {
            connection.sendMessage(new LeaveRequest(ProtocolVersion.V1_0, gameId, playerId));
            eventLogger.info("LEAVE_SENT gameId=" + gameId + " playerId=" + playerId);
        }
    }
}
