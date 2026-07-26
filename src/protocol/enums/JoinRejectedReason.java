package protocol.enums;

public enum JoinRejectedReason {
    GAME_ALREADY_STARTED(0x01),
    GAME_FULL(0x02),
    INVALID_NAME(0x03),
    UNSUPPORTED_PROTOCOL_VERSION(0x04);

    private final int code;

    JoinRejectedReason(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static JoinRejectedReason fromCode(int code) {
        for (JoinRejectedReason value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        throw new IllegalArgumentException("Motivo JOIN_REJECTED desconocido: " + code);
    }
}
