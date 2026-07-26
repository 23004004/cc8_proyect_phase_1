package engine;

import protocol.messages.GameOverMessage;
import protocol.messages.GameStateMessage;
import protocol.core.ProtocolMessage;

import java.util.List;

public record GameTickResult(long tick, List<ProtocolMessage> events, GameStateMessage stateMessage, GameOverMessage gameOverMessage) {
    public GameTickResult {
        events = List.copyOf(events);
    }
}
