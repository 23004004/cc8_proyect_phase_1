package protocol.messages;

import protocol.core.MessageType;
import protocol.core.ProtocolMessage;

public record InteractRequest(int playerId) implements ProtocolMessage {
    @Override
    public MessageType type() {
        return MessageType.INTERACT;
    }
}
