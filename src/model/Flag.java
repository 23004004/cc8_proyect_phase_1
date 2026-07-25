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

    public Flag withPosition(int newX, int newY) {
        return new Flag(newX, newY, status, carrierId);
    }

    public Flag withStatus(FlagStatus newStatus) {
        return new Flag(x, y, newStatus, carrierId);
    }

    public Flag withCarrierId(int newCarrierId) {
        return new Flag(x, y, status, newCarrierId);
    }
}
