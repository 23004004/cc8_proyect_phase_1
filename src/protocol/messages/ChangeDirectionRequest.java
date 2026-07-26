package protocol.messages;

import protocol.core.MessageType;
import protocol.core.ProtocolMessage;

import model.Direction;

public record ChangeDirectionRequest(int playerId, Direction direction) implements ProtocolMessage {
    @Override
    public MessageType type() {
        return MessageType.INPUT;
    }
}
