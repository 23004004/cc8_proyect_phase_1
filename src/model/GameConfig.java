package model;

public record GameConfig(
        int rows,
        int columns,
        int obstaclePercentage,
        int movementIntervalMs,
        int protectionTimeMs,
        int maximumPlayers,
        int centralFlagAreaPercentage,
        int serverPort
) {
    public GameConfig {
        if (rows <= 0) {
            throw new IllegalArgumentException("rows must be positive");
        }
        if (columns <= 0) {
            throw new IllegalArgumentException("columns must be positive");
        }
        if (obstaclePercentage < 0 || obstaclePercentage > 100) {
            throw new IllegalArgumentException("obstaclePercentage must be between 0 and 100");
        }
        if (movementIntervalMs <= 0) {
            throw new IllegalArgumentException("movementIntervalMs must be positive");
        }
        if (protectionTimeMs < 0) {
            throw new IllegalArgumentException("protectionTimeMs must be non-negative");
        }
        if (maximumPlayers <= 0) {
            throw new IllegalArgumentException("maximumPlayers must be positive");
        }
        if (centralFlagAreaPercentage < 0 || centralFlagAreaPercentage > 100) {
            throw new IllegalArgumentException("centralFlagAreaPercentage must be between 0 and 100");
        }
        if (serverPort <= 0 || serverPort > 65535) {
            throw new IllegalArgumentException("serverPort must be between 1 and 65535");
        }
    }

    public static GameConfig defaults() {
        return new GameConfig(20, 20, 10, 200, 1000, 50, 30, 5000);
    }

    public GameConfig withServerPort(int newServerPort) {
        return new GameConfig(
                rows,
                columns,
                obstaclePercentage,
                movementIntervalMs,
                protectionTimeMs,
                maximumPlayers,
                centralFlagAreaPercentage,
                newServerPort
        );
    }
}
