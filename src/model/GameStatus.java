package model;

public enum GameStatus {
    WAITING(0x01),
    STARTING(0x02),
    RUNNING(0x03),
    FINISHED(0x04),
    CANCELLED(0x05);

    private final int code;

    GameStatus(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static GameStatus fromCode(int code) {
        for (GameStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Estado de partida desconocido: " + code);
    }
}
