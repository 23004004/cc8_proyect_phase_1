package protocol.core;

public enum MessageType {
    DISCOVER_REQUEST(0x01),
    DISCOVER_RESPONSE(0x02),
    JOIN(0x10),
    INPUT(0x11),
    INTERACT(0x12),
    LEAVE(0x13),
    JOIN_ACCEPTED(0x20),
    JOIN_REJECTED(0x21),
    LOBBY_STATE(0x22),
    GAME_COUNTDOWN(0x23),
    GAME_STARTED(0x24),
    GAME_STATE(0x25),
    FLAG_PICKED_UP(0x26),
    FLAG_STOLEN(0x27),
    PLAYER_DISCONNECTED(0x28),
    GAME_OVER(0x29),
    ERROR(0x2A);

    private final int code;

    MessageType(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static MessageType fromCode(int code) {
        for (MessageType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("Tipo de mensaje desconocido: " + code);
    }
}
