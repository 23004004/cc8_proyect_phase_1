package protocol.messages;

import protocol.core.MessageType;
import protocol.core.ProtocolMessage;

public record JoinAcceptedMessage(int playerId, int gameId) implements ProtocolMessage {
    @Override
    public MessageType type() {
        return MessageType.JOIN_ACCEPTED;
    }
}
