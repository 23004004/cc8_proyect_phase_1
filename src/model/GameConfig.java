package model;

public record GameConfig(
        int mapSize,
        int circleRadius,
        int playerRadius,
        int spawnMargin,
        int playerSpeed,
        int interactionRadius,
        int tickIntervalMs,
        int countdownSeconds,
        int maximumPlayers,
        int serverPort,
        int discoveryPort,
        String serverName
) {
    public GameConfig {
        if (mapSize <= 0 || circleRadius <= 0 || playerRadius <= 0 || spawnMargin < 0 || playerSpeed <= 0
                || interactionRadius <= 0 || tickIntervalMs <= 0 || countdownSeconds < 0) {
            throw new IllegalArgumentException("Los parámetros de juego deben ser positivos.");
        }
        if (maximumPlayers <= 0 || maximumPlayers > 100) {
            throw new IllegalArgumentException("maximumPlayers debe estar entre 1 y 100.");
        }
        if (serverPort <= 0 || serverPort > 65535 || discoveryPort <= 0 || discoveryPort > 65535) {
            throw new IllegalArgumentException("Los puertos deben estar entre 1 y 65535.");
        }
        serverName = serverName == null || serverName.isBlank() ? "Captura la Bandera" : serverName.trim();
    }

    public static GameConfig defaults() {
        return new GameConfig(2000, 500, 15, 80, 220, 60, 50, 5, 100, 5000, 5001, "Captura la Bandera");
    }

    public GameConfig withServerPort(int newServerPort) {
        return new GameConfig(
                mapSize,
                circleRadius,
                playerRadius,
                spawnMargin,
                playerSpeed,
                interactionRadius,
                tickIntervalMs,
                countdownSeconds,
                maximumPlayers,
                newServerPort,
                discoveryPort,
                serverName
        );
    }

    public GameConfig withServerName(String newServerName) {
        return new GameConfig(
                mapSize,
                circleRadius,
                playerRadius,
                spawnMargin,
                playerSpeed,
                interactionRadius,
                tickIntervalMs,
                countdownSeconds,
                maximumPlayers,
                serverPort,
                discoveryPort,
                newServerName
        );
    }
}
