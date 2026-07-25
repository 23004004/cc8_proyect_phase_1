package model;

public enum FlagStatus {
    AVAILABLE(0x01),
    CARRIED(0x02),
    DROPPED(0x03),
    OUTSIDE(0x04);

    private final int code;

    FlagStatus(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static FlagStatus fromCode(int code) {
        for (FlagStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Estado de bandera desconocido: " + code);
    }
}
