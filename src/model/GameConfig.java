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
        String extraDiscoveryPorts,
        boolean radminScanEnabled,
        int radminScanIntervalMs,
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
        if (radminScanIntervalMs < 1000) {
            radminScanIntervalMs = 5000;
        }
        extraDiscoveryPorts = extraDiscoveryPorts == null ? "" : extraDiscoveryPorts.trim();
        serverName = serverName == null || serverName.isBlank() ? "Captura la Bandera" : serverName.trim();
    }

    public static GameConfig defaults() {
        return new GameConfig(2000, 500, 15, 80, 220, 60, 50, 5, 100, 5000, 5001, "", false, 5000, "Captura la Bandera");
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
                extraDiscoveryPorts,
                radminScanEnabled,
                radminScanIntervalMs,
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
                extraDiscoveryPorts,
                radminScanEnabled,
                radminScanIntervalMs,
                newServerName
        );
    }

    public GameConfig withDiscoveryPort(int newDiscoveryPort) {
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
                newDiscoveryPort,
                extraDiscoveryPorts,
                radminScanEnabled,
                radminScanIntervalMs,
                serverName
        );
    }

    public GameConfig withExtraDiscoveryPorts(String newExtraDiscoveryPorts) {
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
                newExtraDiscoveryPorts,
                radminScanEnabled,
                radminScanIntervalMs,
                serverName
        );
    }

    public GameConfig withRadminScanEnabled(boolean newRadminScanEnabled) {
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
                extraDiscoveryPorts,
                newRadminScanEnabled,
                radminScanIntervalMs,
                serverName
        );
    }
}
