package protocol.mapping;

import model.Flag;
import model.Game;
import model.Player;
import protocol.dto.FlagDto;
import protocol.dto.PlayerDto;
import protocol.messages.GameStartedMessage;
import protocol.messages.GameStateMessage;
import protocol.messages.LobbyStateMessage;

import java.util.Comparator;
import java.util.List;

public final class GameMessageMapper {
    private GameMessageMapper() {
    }

    public static LobbyStateMessage toLobbyStateMessage(Game game) {
        return new LobbyStateMessage(game.status(), playersWithNames(game));
    }

    public static GameStartedMessage toGameStartedMessage(Game game) {
        return new GameStartedMessage(
                scaled(game.config().mapSize()),
                scaled(game.config().circleRadius()),
                scaled(game.config().playerRadius()),
                scaled(game.config().playerSpeed()),
                scaled(game.config().interactionRadius()),
                game.config().tickIntervalMs(),
                toFlagDto(game.flag()),
                playersWithNames(game)
        );
    }

    public static GameStateMessage toGameStateMessage(Game game) {
        return new GameStateMessage(game.tick(), toFlagDto(game.flag()), playersWithoutNames(game));
    }

    private static List<PlayerDto> playersWithNames(Game game) {
        return game.players().stream()
                .sorted(Comparator.comparingInt(Player::playerId))
                .map(player -> new PlayerDto(player.playerId(), player.name(), player.x(), player.y(), player.direction(), player.hasFlag()))
                .toList();
    }

    private static List<PlayerDto> playersWithoutNames(Game game) {
        return game.players().stream()
                .sorted(Comparator.comparingInt(Player::playerId))
                .map(player -> new PlayerDto(player.playerId(), "", player.x(), player.y(), player.direction(), player.hasFlag()))
                .toList();
    }

    private static FlagDto toFlagDto(Flag flag) {
        return new FlagDto(flag.x(), flag.y(), flag.status(), flag.carrierId());
    }

    private static int scaled(int value) {
        return value * 100;
    }
}
