package protocol;

import protocol.dto.FlagDto;
import protocol.dto.PlayerDto;
import protocol.dto.PositionDto;

import java.util.List;

public record GameStartedMessage(
        String protocolVersion,
        String gameId,
        int rows,
        int columns,
        int movementIntervalMs,
        int protectionTimeMs,
        List<PositionDto> obstacles,
        FlagDto flag,
        List<PlayerDto> players
) implements ProtocolMessage {
    @Override
    public MessageType type() {
        return MessageType.GAME_STARTED;
    }
}
