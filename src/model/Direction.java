package model;

public enum Direction {
    UP(-1, 0),
    DOWN(1, 0),
    LEFT(0, -1),
    RIGHT(0, 1);

    private final int rowDelta;
    private final int columnDelta;

    Direction(int rowDelta, int columnDelta) {
        this.rowDelta = rowDelta;
        this.columnDelta = columnDelta;
    }

    public int rowDelta() {
        return rowDelta;
    }

    public int columnDelta() {
        return columnDelta;
    }

    public boolean isOpposite(Direction other) {
        if (other == null) {
            return false;
        }
        return rowDelta + other.rowDelta == 0 && columnDelta + other.columnDelta == 0;
    }
}
