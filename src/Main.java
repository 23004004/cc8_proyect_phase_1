import connect.Client;
import connect.GameConfigLoader;
import connect.Server;
import model.GameConfig;

import javax.swing.JOptionPane;
import java.nio.file.Path;

public final class Main {
    private static final Path SERVER_CONFIG_PATH = Path.of("config", "server.properties");
    private static final String USAGE = """
            Uso:
              java Main server [port]
              java Main client [host] [port]
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

        try {
            switch (mode) {
                case SERVER -> runServer(parseOptionalPort(args));
                case CLIENT -> runClient(parseClientHost(args), parseClientPort(args));
            }
        } catch (IllegalArgumentException | IllegalStateException ex) {
            System.out.println(ex.getMessage());
        }
    }

    private static Mode parseMode(String[] args) {
        if (args == null || args.length < 1 || args.length > 3) {
            return null;
        }

        return switch (args[0].trim().toLowerCase()) {
            case "server" -> Mode.SERVER;
            case "client" -> Mode.CLIENT;
            default -> null;
        };
    }

    private static String parseClientHost(String[] args) {
        if (args == null || args.length < 2) {
            return null;
        }

        if (args.length == 2) {
            if (isInteger(args[1])) {
                return "127.0.0.1";
            }
            return args[1].trim();
        }

        return args[1].trim();
    }

    private static Integer parseClientPort(String[] args) {
        if (args == null || args.length < 2) {
            return null;
        }

        if (args.length == 2) {
            if (!isInteger(args[1])) {
                return null;
            }
            return parsePort(args[1]);
        }

        return parsePort(args[2]);
    }

    private static Integer parseOptionalPort(String[] args) {
        if (args == null || args.length < 2) {
            return null;
        }
        return parsePort(args[1]);
    }

    private static Integer parsePort(String raw) {
        try {
            int port = Integer.parseInt(raw.trim());
            if (port <= 0 || port > 65535) {
                throw new IllegalArgumentException("El puerto debe estar entre 1 y 65535.");
            }
            return port;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("El puerto debe ser numérico.");
        }
    }

    private static boolean isInteger(String value) {
        try {
            Integer.parseInt(value.trim());
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static void runServer(Integer portOverride) {
        GameConfig config = GameConfigLoader.load(SERVER_CONFIG_PATH, GameConfig.defaults());
        if (portOverride != null) {
            config = config.withServerPort(portOverride);
        }
        config = config.withServerName(askServerName(config.serverName()));
        System.out.println("Configuración del servidor: " + SERVER_CONFIG_PATH);
        new Server(config).start();
    }

    private static void runClient(String host, Integer portOverride) {
        if (host == null && portOverride == null) {
            new Client().start();
            return;
        }
        if (host == null) {
            host = "127.0.0.1";
        }
        int port = portOverride == null ? GameConfig.defaults().serverPort() : portOverride;
        new Client().start(host, port);
    }

    private enum Mode {
        SERVER,
        CLIENT
    }

    private static String askServerName(String defaultName) {
        try {
            String value = JOptionPane.showInputDialog(null, "Nombre del servidor:", defaultName);
            if (value == null || value.trim().isBlank()) {
                return defaultName;
            }
            return value.trim();
        } catch (RuntimeException ex) {
            return defaultName;
        }
    }
}
