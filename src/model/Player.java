package model;

public record Player(
        String playerId,
        String name,
        int row,
        int column,
        Direction direction,
        boolean connected,
        boolean insideBoard,
        boolean hasFlag,
        long protectedUntilEpochMillis
) {
    public Player {
        if (playerId == null || playerId.isBlank()) {
            throw new IllegalArgumentException("playerId must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (direction == null) {
            throw new IllegalArgumentException("direction must not be null");
        }
    }

    public Position position() {
        return new Position(row, column);
    }

    public boolean isProtected(long nowEpochMillis) {
        return nowEpochMillis < protectedUntilEpochMillis;
    }

    public Player withPosition(Position position) {
        return new Player(playerId, name, position.row(), position.column(), direction, connected, insideBoard, hasFlag, protectedUntilEpochMillis);
    }

    public Player withDirection(Direction newDirection) {
        return new Player(playerId, name, row, column, newDirection, connected, insideBoard, hasFlag, protectedUntilEpochMillis);
    }

    public Player withConnected(boolean newConnected) {
        return new Player(playerId, name, row, column, direction, newConnected, insideBoard, hasFlag, protectedUntilEpochMillis);
    }

    public Player withInsideBoard(boolean newInsideBoard) {
        return new Player(playerId, name, row, column, direction, connected, newInsideBoard, hasFlag, protectedUntilEpochMillis);
    }

    public Player withHasFlag(boolean newHasFlag) {
        return new Player(playerId, name, row, column, direction, connected, insideBoard, newHasFlag, protectedUntilEpochMillis);
    }

    public Player withProtectionUntil(long newProtectedUntilEpochMillis) {
        return new Player(playerId, name, row, column, direction, connected, insideBoard, hasFlag, newProtectedUntilEpochMillis);
    }
}
