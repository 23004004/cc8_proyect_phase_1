package protocol.messages;

import protocol.core.MessageType;
import protocol.core.ProtocolMessage;

public record JoinRequest(String name) implements ProtocolMessage {
    @Override
    public MessageType type() {
        return MessageType.JOIN;
    }
}
