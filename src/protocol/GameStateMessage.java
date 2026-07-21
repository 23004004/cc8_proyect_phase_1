package protocol;

import protocol.dto.FlagDto;
import protocol.dto.PlayerDto;

import java.util.List;

public record GameStateMessage(
        String protocolVersion,
        String gameId,
        long tick,
        List<PlayerDto> players,
        FlagDto flag
) implements ProtocolMessage {
    @Override
    public MessageType type() {
        return MessageType.GAME_STATE;
    }
}
