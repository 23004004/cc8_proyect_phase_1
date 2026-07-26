package connect;

import model.GameConfig;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class GameConfigLoader {
    private GameConfigLoader() {
    }

    public static GameConfig load(Path path, GameConfig defaults) {
        if (path == null || !Files.exists(path)) {
            return defaults;
        }

        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException ex) {
            throw new IllegalArgumentException("No se pudo leer la configuración " + path + ": " + ex.getMessage(), ex);
        }

        return new GameConfig(
                intProperty(properties, "mapSize", defaults.mapSize()),
                intProperty(properties, "circleRadius", defaults.circleRadius()),
                intProperty(properties, "playerRadius", defaults.playerRadius()),
                intProperty(properties, "spawnMargin", defaults.spawnMargin()),
                intProperty(properties, "playerSpeed", defaults.playerSpeed()),
                intProperty(properties, "interactionRadius", defaults.interactionRadius()),
                intProperty(properties, "tickIntervalMs", defaults.tickIntervalMs()),
                intProperty(properties, "countdownSeconds", defaults.countdownSeconds()),
                intProperty(properties, "maximumPlayers", defaults.maximumPlayers()),
                intProperty(properties, "serverPort", defaults.serverPort()),
                intProperty(properties, "discoveryPort", defaults.discoveryPort()),
                stringProperty(properties, "extraDiscoveryPorts", defaults.extraDiscoveryPorts()),
                booleanProperty(properties, "radminScanEnabled", defaults.radminScanEnabled()),
                intProperty(properties, "radminScanIntervalMs", defaults.radminScanIntervalMs()),
                stringProperty(properties, "serverName", defaults.serverName())
        );
    }

    private static int intProperty(Properties properties, String key, int defaultValue) {
        String raw = properties.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("La propiedad " + key + " debe ser numérica.", ex);
        }
    }

    private static String stringProperty(Properties properties, String key, String defaultValue) {
        String raw = properties.getProperty(key);
        return raw == null || raw.isBlank() ? defaultValue : raw.trim();
    }

    private static boolean booleanProperty(Properties properties, String key, boolean defaultValue) {
        String raw = properties.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(raw.trim());
    }
}
