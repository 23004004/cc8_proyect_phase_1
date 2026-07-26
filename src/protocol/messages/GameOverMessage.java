package protocol.messages;

import protocol.core.MessageType;
import protocol.core.ProtocolMessage;
import protocol.enums.GameOverReason;

public record GameOverMessage(int winnerId, String winnerName, GameOverReason reason) implements ProtocolMessage {
    @Override
    public MessageType type() {
        return MessageType.GAME_OVER;
    }
}
