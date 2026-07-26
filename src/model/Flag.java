package model;

public record Flag(
        int x,
        int y,
        FlagStatus status,
        int carrierId
) {
    public Flag {
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
    }
}
