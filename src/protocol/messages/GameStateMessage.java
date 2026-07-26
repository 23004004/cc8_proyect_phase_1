package protocol.messages;

import protocol.core.MessageType;
import protocol.core.ProtocolMessage;

import protocol.dto.FlagDto;
import protocol.dto.PlayerDto;

import java.util.List;

public record GameStateMessage(long tick, FlagDto flag, List<PlayerDto> players) implements ProtocolMessage {
    public GameStateMessage {
        players = List.copyOf(players);
    }

    @Override
    public MessageType type() {
        return MessageType.GAME_STATE;
    }
}
