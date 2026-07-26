package protocol.enums;

public enum GameOverReason {
    EXITED_CIRCLE_WITH_FLAG(0x01);

    private final int code;

    GameOverReason(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
