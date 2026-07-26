package protocol.messages;

import protocol.core.MessageType;
import protocol.core.ProtocolMessage;
import protocol.enums.ErrorCode;

public record ErrorMessage(ErrorCode code, String description) implements ProtocolMessage {
    @Override
    public MessageType type() {
        return MessageType.ERROR;
    }
}
