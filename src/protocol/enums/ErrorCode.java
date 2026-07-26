package protocol.enums;

public enum ErrorCode {
    INVALID_MESSAGE(0x01),
    INVALID_ENCODING(0x02),
    INVALID_INPUT(0x03),
    UNKNOWN_PLAYER(0x04),
    GAME_NOT_STARTED(0x05),
    GAME_ALREADY_STARTED(0x06),
    GAME_FINISHED(0x07),
    UNSUPPORTED_PROTOCOL_VERSION(0x08);

    private final int code;

    ErrorCode(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static ErrorCode fromCode(int code) {
        for (ErrorCode value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        throw new IllegalArgumentException("Código de error desconocido: " + code);
    }
}
