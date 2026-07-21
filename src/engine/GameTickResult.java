package engine;

import protocol.GameStateMessage;
import protocol.ProtocolMessage;

import java.util.List;

public record GameTickResult(
        long tick,
        List<ProtocolMessage> events,
        GameStateMessage stateMessage,
        boolean finished
) {
}
