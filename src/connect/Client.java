package connect;

import model.Direction;
import model.GameConfig;
import protocol.ChangeDirectionRequest;
import protocol.ErrorMessage;
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

import javax.swing.JFrame;
import javax.swing.WindowConstants;
import java.io.IOException;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicReference;

public final class Client {
    public void start() {
        start("127.0.0.1", GameConfig.defaults().serverPort());
    }

    public void start(String host, int port) {
        PanelGame panel = new PanelGame();
        JFrame frame = createFrame(panel);

        try (Scanner scanner = new Scanner(System.in)) {
            System.out.print("Nombre del jugador: ");
            String name = readNonBlankLine(scanner, "Jugador");

            try (Socket socket = new Socket(host, port);
                 LineConnection connection = new LineConnection(socket)) {

                AtomicReference<String> playerIdRef = new AtomicReference<>();
                AtomicReference<String> gameIdRef = new AtomicReference<>();

                connection.sendMessage(new JoinRequest(ProtocolVersion.V1_0, name));

                Thread reader = new Thread(() -> readLoop(connection, panel, playerIdRef, gameIdRef), "client-reader");
                reader.setDaemon(true);
                reader.start();

                System.out.println("Comandos: up, down, left, right, leave");
                while (socket.isConnected() && !socket.isClosed() && scanner.hasNextLine()) {
                    String command = scanner.nextLine().trim().toLowerCase();
                    if (command.isBlank()) {
                        continue;
                    }
                    if ("leave".equals(command)) {
                        sendLeave(connection, playerIdRef.get(), gameIdRef.get());
                        break;
                    }

                    Direction direction = parseDirection(command);
                    if (direction == null) {
                        System.out.println("Comando no válido.");
                        continue;
                    }

                    String playerId = playerIdRef.get();
                    String gameId = gameIdRef.get();
                    if (playerId == null || gameId == null) {
                        System.out.println("Esperando JOIN_ACCEPTED.");
                        continue;
                    }

                    connection.sendMessage(new ChangeDirectionRequest(ProtocolVersion.V1_0, gameId, playerId, direction));
                }
            }
        } catch (IOException ex) {
            System.out.println("No se pudo conectar al servidor: " + ex.getMessage());
        } finally {
            frame.dispose();
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

    private void readLoop(LineConnection connection, PanelGame panel, AtomicReference<String> playerIdRef, AtomicReference<String> gameIdRef) {
        try {
            while (true) {
                ProtocolMessage message = connection.readMessage();
                if (message == null) {
                    System.out.println("Conexión cerrada por el servidor.");
                    return;
                }

                if (message instanceof GameStartedMessage gameStarted) {
                    panel.applyGameStarted(gameStarted);
                } else if (message instanceof GameStateMessage gameState) {
                    panel.applyGameState(gameState);
                } else if (message instanceof GameOverMessage gameOver) {
                    panel.applyGameOver(gameOver);
                } else if (message instanceof PlayerDisconnectedMessage disconnected) {
                    panel.applyStatusText("Jugador desconectado: " + disconnected.playerId());
                } else if (message instanceof JoinRejectedMessage joinRejected) {
                    panel.applyStatusText("Join rechazado: " + joinRejected.reason());
                } else if (message instanceof ErrorMessage errorMessage) {
                    panel.applyStatusText("Error: " + errorMessage.code());
                }

                System.out.println(message);
                if (message instanceof JoinAcceptedMessage joinAccepted) {
                    playerIdRef.set(joinAccepted.playerId());
                    gameIdRef.set(joinAccepted.gameId());
                    System.out.println("Asignado playerId: " + joinAccepted.playerId());
                }
            }
        } catch (IOException | IllegalArgumentException ex) {
            System.out.println("Lectura detenida: " + ex.getMessage());
        }
    }

    private String readNonBlankLine(Scanner scanner, String fallback) {
        if (!scanner.hasNextLine()) {
            return fallback;
        }
        String value = scanner.nextLine().trim();
        return value.isBlank() ? fallback : value;
    }

    private Direction parseDirection(String command) {
        return switch (command) {
            case "up", "w" -> Direction.UP;
            case "down", "s" -> Direction.DOWN;
            case "left", "a" -> Direction.LEFT;
            case "right", "d" -> Direction.RIGHT;
            default -> null;
        };
    }

    private void sendLeave(LineConnection connection, String playerId, String gameId) throws IOException {
        if (playerId != null && gameId != null) {
            connection.sendMessage(new LeaveRequest(ProtocolVersion.V1_0, gameId, playerId));
        }
    }
}
