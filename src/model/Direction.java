package model;

public enum Direction {
    NONE(0x00, 0, 0),
    UP(0x01, 0, -1),
    DOWN(0x02, 0, 1),
    LEFT(0x03, -1, 0),
    RIGHT(0x04, 1, 0);

    private final int code;
    private final int xSign;
    private final int ySign;

    Direction(int code, int xSign, int ySign) {
        this.code = code;
        this.xSign = xSign;
        this.ySign = ySign;
    }

    public int code() {
        return code;
    }

    public int xSign() {
        return xSign;
    }

    public int ySign() {
        return ySign;
    }

    public static Direction fromCode(int code) {
        for (Direction direction : values()) {
            if (direction.code == code) {
                return direction;
            }
        }
        throw new IllegalArgumentException("Dirección desconocida: " + code);
    }
}
