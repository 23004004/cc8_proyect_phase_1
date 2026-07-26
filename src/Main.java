import connect.Client;
import connect.GameConfigLoader;
import connect.Server;
import model.GameConfig;
import view.ServerConfigDialog;

import java.nio.file.Path;

public final class Main {
    private static final Path SERVER_CONFIG_PATH = Path.of("config", "server.properties");
    private static final String USAGE = """
            Uso:
              java Main server [tcpPort] [udpDiscoveryPort] [extraDiscoveryPorts]
              java Main client [host] [port]
            """;

    private Main() {
        // Utility class.
    }

    public static void main(String[] args) {
        LaunchOptions options = parseLaunchOptions(args);
        if (options == null) {
            System.out.print(USAGE);
            return;
        }

        try {
            switch (options.mode()) {
                case SERVER -> runServer(options.port(), options.discoveryPort(), options.extraDiscoveryPorts());
                case CLIENT -> runClient(options.host(), options.port());
            }
        } catch (IllegalArgumentException | IllegalStateException ex) {
            System.out.println(ex.getMessage());
        }
    }

    private static LaunchOptions parseLaunchOptions(String[] args) {
        // Centraliza el parseo para que server/client no vuelvan a interpretar args.
        Mode mode = parseMode(args);
        if (mode == null) {
            return null;
        }
        return switch (mode) {
            case SERVER -> new LaunchOptions(mode, null, parseOptionalPort(args), parseOptionalDiscoveryPort(args), parseOptionalExtraDiscoveryPorts(args));
            case CLIENT -> new LaunchOptions(mode, parseClientHost(args), parseClientPort(args), null, null);
        };
    }

    private static Mode parseMode(String[] args) {
        if (args == null || args.length < 1 || args.length > 4) {
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

    private static Integer parseOptionalDiscoveryPort(String[] args) {
        if (args == null || args.length < 3 || !"server".equalsIgnoreCase(args[0])) {
            return null;
        }
        return parsePort(args[2]);
    }

    private static String parseOptionalExtraDiscoveryPorts(String[] args) {
        if (args == null || args.length < 4 || !"server".equalsIgnoreCase(args[0])) {
            return null;
        }
        return args[3].trim();
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

    private static void runServer(Integer portOverride, Integer discoveryPortOverride, String extraDiscoveryPortsOverride) {
        GameConfig config = GameConfigLoader.load(SERVER_CONFIG_PATH, GameConfig.defaults());
        if (portOverride != null) {
            config = config.withServerPort(portOverride);
        }
        if (discoveryPortOverride != null) {
            config = config.withDiscoveryPort(discoveryPortOverride);
        }
        if (extraDiscoveryPortsOverride != null) {
            config = config.withExtraDiscoveryPorts(extraDiscoveryPortsOverride);
        }
        if (portOverride == null && discoveryPortOverride == null && extraDiscoveryPortsOverride == null) {
            config = ServerConfigDialog.showDialog(config);
            if (config == null) {
                return;
            }
        }
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

    private record LaunchOptions(Mode mode, String host, Integer port, Integer discoveryPort, String extraDiscoveryPorts) {
    }
}
