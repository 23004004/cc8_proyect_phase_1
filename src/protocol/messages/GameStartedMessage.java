package protocol.messages;

import protocol.core.MessageType;
import protocol.core.ProtocolMessage;

import protocol.dto.FlagDto;
import protocol.dto.PlayerDto;

import java.util.List;

public record GameStartedMessage(
        int mapSize,
        int circleRadius,
        int playerRadius,
        int playerSpeed,
        int interactionRadius,
        int tickIntervalMs,
        FlagDto flag,
        List<PlayerDto> players
) implements ProtocolMessage {
    public GameStartedMessage {
        players = List.copyOf(players);
    }

    @Override
    public MessageType type() {
        return MessageType.GAME_STARTED;
    }
}
