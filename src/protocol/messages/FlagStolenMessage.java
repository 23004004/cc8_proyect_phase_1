package protocol.messages;

import protocol.core.MessageType;
import protocol.core.ProtocolMessage;

public record FlagStolenMessage(long tick, int previousCarrierId, int newCarrierId) implements ProtocolMessage {
    @Override
    public MessageType type() {
        return MessageType.FLAG_STOLEN;
    }
}
