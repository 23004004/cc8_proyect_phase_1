package model;

public record Flag(
        Position position,
        FlagStatus status,
        String carrierId
) {
    public Flag {
        if (position == null) {
            throw new IllegalArgumentException("position must not be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
    }

    public Flag withPosition(Position newPosition) {
        return new Flag(newPosition, status, carrierId);
    }

    public Flag withStatus(FlagStatus newStatus) {
        return new Flag(position, newStatus, carrierId);
    }

    public Flag withCarrierId(String newCarrierId) {
        return new Flag(position, status, newCarrierId);
    }
}
