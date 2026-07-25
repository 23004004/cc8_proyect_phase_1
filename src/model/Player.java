package model;

public record Player(
        int playerId,
        String name,
        int x,
        int y,
        Direction direction,
        boolean connected,
        boolean hasFlag
) {
    public Player {
        if (playerId <= 0 || playerId > 65535) {
            throw new IllegalArgumentException("playerId fuera de rango");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (direction == null) {
            throw new IllegalArgumentException("direction must not be null");
        }
    }

    public Player withPosition(int newX, int newY) {
        return new Player(playerId, name, newX, newY, direction, connected, hasFlag);
    }

    public Player withDirection(Direction newDirection) {
        return new Player(playerId, name, x, y, newDirection, connected, hasFlag);
    }

    public Player withConnected(boolean newConnected) {
        return new Player(playerId, name, x, y, direction, newConnected, hasFlag);
    }

    public Player withHasFlag(boolean newHasFlag) {
        return new Player(playerId, name, x, y, direction, connected, newHasFlag);
    }
}
