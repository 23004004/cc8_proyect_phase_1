package protocol;

import model.Direction;

public record ChangeDirectionRequest(
        String protocolVersion,
        String gameId,
        String playerId,
        Direction direction
) implements ProtocolMessage {
    @Override
    public MessageType type() {
        return MessageType.CHANGE_DIRECTION;
    }
}
