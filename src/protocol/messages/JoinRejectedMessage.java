package protocol.messages;

import protocol.core.MessageType;
import protocol.core.ProtocolMessage;
import protocol.enums.JoinRejectedReason;

public record JoinRejectedMessage(JoinRejectedReason reason) implements ProtocolMessage {
    @Override
    public MessageType type() {
        return MessageType.JOIN_REJECTED;
    }
}
