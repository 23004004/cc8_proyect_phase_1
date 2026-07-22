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
                intProperty(properties, "rows", defaults.rows()),
                intProperty(properties, "columns", defaults.columns()),
                intProperty(properties, "obstaclePercentage", defaults.obstaclePercentage()),
                intProperty(properties, "movementIntervalMs", defaults.movementIntervalMs()),
                intProperty(properties, "protectionTimeMs", defaults.protectionTimeMs()),
                intProperty(properties, "maximumPlayers", defaults.maximumPlayers()),
                intProperty(properties, "centralFlagAreaPercentage", defaults.centralFlagAreaPercentage()),
                intProperty(properties, "serverPort", defaults.serverPort())
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
}
