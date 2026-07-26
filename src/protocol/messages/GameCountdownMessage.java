package protocol.messages;

import protocol.core.MessageType;
import protocol.core.ProtocolMessage;

public record GameCountdownMessage(int secondsRemaining) implements ProtocolMessage {
    @Override
    public MessageType type() {
        return MessageType.GAME_COUNTDOWN;
    }
}
