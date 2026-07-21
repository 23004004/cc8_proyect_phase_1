import connect.Client;
import connect.Server;
import model.GameConfig;

public final class Main {
    private static final String USAGE = """
            Uso:
              java Main server
              java Main client
            """;

    private Main() {
        // Utility class.
    }

    public static void main(String[] args) {
        Mode mode = parseMode(args);
        if (mode == null) {
            System.out.print(USAGE);
            return;
        }

        switch (mode) {
            case SERVER -> runServer();
            case CLIENT -> runClient();
        }
    }

    private static Mode parseMode(String[] args) {
        if (args == null || args.length != 1) {
            return null;
        }

        return switch (args[0].trim().toLowerCase()) {
            case "server" -> Mode.SERVER;
            case "client" -> Mode.CLIENT;
            default -> null;
        };
    }

    private static void runServer() {
        new Server(GameConfig.defaults()).start();
    }

    private static void runClient() {
        new Client().start();
    }

    private enum Mode {
        SERVER,
        CLIENT
    }
}
