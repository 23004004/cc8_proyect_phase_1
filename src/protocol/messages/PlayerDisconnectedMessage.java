package protocol.messages;

import protocol.core.MessageType;
import protocol.core.ProtocolMessage;

public record PlayerDisconnectedMessage(int playerId) implements ProtocolMessage {
    @Override
    public MessageType type() {
        return MessageType.PLAYER_DISCONNECTED;
    }
}
