package protocol;

import model.GameStatus;
import protocol.dto.PlayerDto;

import java.util.List;

public record LobbyStateMessage(GameStatus state, List<PlayerDto> players) implements ProtocolMessage {
    public LobbyStateMessage {
        players = List.copyOf(players);
    }

    @Override
    public MessageType type() {
        return MessageType.LOBBY_STATE;
    }
}
