package model;

public record Position(int row, int column) {
    public Position move(Direction direction) {
        return new Position(row + direction.rowDelta(), column + direction.columnDelta());
    }

    public boolean isInside(int rows, int columns) {
        return row >= 0 && row < rows && column >= 0 && column < columns;
    }

    public boolean isOnBorder(int rows, int columns) {
        if (!isInside(rows, columns)) {
            return false;
        }
        return row == 0 || column == 0 || row == rows - 1 || column == columns - 1;
    }
}
