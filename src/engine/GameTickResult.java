package engine;

import protocol.GameOverMessage;
import protocol.GameStateMessage;
import protocol.ProtocolMessage;

import java.util.List;

public record GameTickResult(long tick, List<ProtocolMessage> events, GameStateMessage stateMessage, GameOverMessage gameOverMessage) {
    public GameTickResult {
        events = List.copyOf(events);
    }

    public boolean finished() {
        return gameOverMessage != null;
    }
}
