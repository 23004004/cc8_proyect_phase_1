package protocol;

import model.Flag;
import model.Game;
import model.Player;
import protocol.dto.FlagDto;
import protocol.dto.PlayerDto;
import protocol.dto.PositionDto;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class GameMessageMapper {
    private GameMessageMapper() {
    }

    public static GameStartedMessage toGameStartedMessage(Game game, long nowEpochMillis) {
        return new GameStartedMessage(
                ProtocolVersion.V1_0,
                game.gameId(),
                game.config().rows(),
                game.config().columns(),
                game.config().movementIntervalMs(),
                game.config().protectionTimeMs(),
                toPositionDtos(game.obstacles()),
                toFlagDto(game.flag()),
                toPlayerDtos(game.players(), nowEpochMillis)
        );
    }

    public static GameStateMessage toGameStateMessage(Game game, long nowEpochMillis) {
        return new GameStateMessage(
                ProtocolVersion.V1_0,
                game.gameId(),
                game.tick(),
                toPlayerDtos(game.players(), nowEpochMillis),
                toFlagDto(game.flag())
        );
    }

    public static FlagDto toFlagDto(Flag flag) {
        if (flag == null) {
            return null;
        }
        return new FlagDto(
                flag.position().row(),
                flag.position().column(),
                flag.status(),
                flag.carrierId()
        );
    }

    public static List<PlayerDto> toPlayerDtos(List<Player> players, long nowEpochMillis) {
        List<Player> orderedPlayers = new ArrayList<>(players);
        orderedPlayers.sort(Comparator.comparing(Player::playerId));

        List<PlayerDto> result = new ArrayList<>(orderedPlayers.size());
        for (Player player : orderedPlayers) {
            result.add(new PlayerDto(
                    player.playerId(),
                    player.name(),
                    player.row(),
                    player.column(),
                    player.direction(),
                    player.insideBoard(),
                    player.hasFlag(),
                    player.isProtected(nowEpochMillis)
            ));
        }
        return result;
    }

    public static List<PositionDto> toPositionDtos(List<model.Position> positions) {
        List<PositionDto> result = new ArrayList<>(positions.size());
        for (model.Position position : positions) {
            result.add(new PositionDto(position.row(), position.column()));
        }
        return result;
    }
}
