package engine;

import model.Direction;
import model.Flag;
import model.FlagStatus;
import model.Game;
import model.GameStatus;
import model.Player;
import model.Position;
import protocol.ErrorMessage;
import protocol.FlagPickedUpMessage;
import protocol.FlagStolenMessage;
import protocol.GameMessageMapper;
import protocol.GameOverMessage;
import protocol.GameOverReason;
import protocol.GameStateMessage;
import protocol.PlayerDisconnectedMessage;
import protocol.ProtocolMessage;
import protocol.ProtocolVersion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class GameSession {
    private final Game game;
    private final Map<String, Direction> pendingDirections;

    public GameSession(Game game) {
        this.game = Objects.requireNonNull(game, "game must not be null");
        this.pendingDirections = new ConcurrentHashMap<>();
    }

    public synchronized Game game() {
        return game;
    }

    public void submitDirectionChange(String playerId, Direction direction) {
        if (playerId == null || playerId.isBlank() || direction == null) {
            return;
        }
        pendingDirections.put(playerId, direction);
    }

    public synchronized List<ProtocolMessage> disconnectPlayer(String playerId, long nowEpochMillis) {
        Player player = game.removePlayer(playerId);
        if (player == null) {
            return List.of(error("UNKNOWN_PLAYER", "Jugador desconocido."));
        }

        List<ProtocolMessage> events = new ArrayList<>();
        events.add(new PlayerDisconnectedMessage(ProtocolVersion.V1_0, game.gameId(), playerId));

        if (player.hasFlag()) {
            game.setFlag(new Flag(
                    player.position(),
                    FlagStatus.DROPPED,
                    null
            ));
        }

        return List.copyOf(events);
    }

    public synchronized GameTickResult tick(long nowEpochMillis) {
        if (game.status() != GameStatus.RUNNING) {
            return new GameTickResult(
                    game.tick(),
                    List.of(),
                    GameMessageMapper.toGameStateMessage(game, nowEpochMillis),
                    game.status() == GameStatus.FINISHED
            );
        }

        Map<String, Direction> directionUpdates = new HashMap<>(pendingDirections);
        pendingDirections.clear();

        List<Player> orderedPlayers = orderedPlayers();
        for (Player player : orderedPlayers) {
            Direction newDirection = directionUpdates.get(player.playerId());
            if (newDirection != null) {
                game.updatePlayer(player.withDirection(newDirection));
            }
        }

        orderedPlayers = orderedPlayers();
        Map<Position, Player> occupiedPositions = occupiedPositions(orderedPlayers);
        HashSet<Position> obstacles = new HashSet<>(game.obstacles());

        Map<String, MoveIntent> intents = new LinkedHashMap<>();
        Map<Position, List<MoveIntent>> targetGroups = new HashMap<>();
        Flag currentFlag = game.flag();

        for (Player player : orderedPlayers) {
            if (!player.connected()) {
                continue;
            }
            MoveIntent intent = new MoveIntent(player, player.position().move(player.direction()));
            intents.put(player.playerId(), intent);
            targetGroups.computeIfAbsent(intent.target(), key -> new ArrayList<>()).add(intent);
        }

        List<ProtocolMessage> events = new ArrayList<>();
        boolean flagStolen = false;

        for (MoveIntent intent : intents.values()) {
            Player player = intent.player();
            Position target = intent.target();

            if (isBlockedByBorderExit(game, player, target)) {
                continue;
            }

            if (!target.isInside(game.config().rows(), game.config().columns())) {
                if (player.hasFlag() && player.position().isOnBorder(game.config().rows(), game.config().columns())) {
                    Player exitingPlayer = player.withPosition(target).withInsideBoard(false);
                    game.updatePlayer(exitingPlayer);
                    game.setStatus(GameStatus.FINISHED);
                    game.setFlag(new Flag(target, FlagStatus.OUTSIDE, player.playerId()));
                    events.add(new GameOverMessage(
                            ProtocolVersion.V1_0,
                            game.gameId(),
                            player.playerId(),
                            player.name(),
                            GameOverReason.EXITED_WITH_FLAG
                    ));
                }
                continue;
            }

            if (obstacles.contains(target)) {
                continue;
            }

            Player occupant = occupiedPositions.get(target);
            if (occupant == null) {
                List<MoveIntent> sameTarget = targetGroups.get(target);
                if (sameTarget != null && sameTarget.size() > 1) {
                    continue;
                }
                Player moved = player.withPosition(target).withInsideBoard(true);
                game.updatePlayer(moved);
                continue;
            }

            if (currentFlag != null
                    && occupant.hasFlag()
                    && !occupant.isProtected(nowEpochMillis)
                    && !player.hasFlag()
                    && !flagStolen) {
                Player attacker = player.withHasFlag(true).withProtectionUntil(nowEpochMillis + game.config().protectionTimeMs());
                Player carrier = occupant.withHasFlag(false);
                game.updatePlayer(attacker);
                game.updatePlayer(carrier);
                game.setFlag(new Flag(occupant.position(), FlagStatus.CARRIED, attacker.playerId()));
                events.add(new FlagStolenMessage(
                        ProtocolVersion.V1_0,
                        game.gameId(),
                        game.tick(),
                        occupant.playerId(),
                        attacker.playerId(),
                        game.config().protectionTimeMs()
                ));
                flagStolen = true;
            }
        }

        if (game.status() == GameStatus.FINISHED) {
            game.incrementTick();
            return new GameTickResult(
                    game.tick(),
                    List.copyOf(events),
                    GameMessageMapper.toGameStateMessage(game, nowEpochMillis),
                    true
            );
        }

        orderedPlayers = orderedPlayers();
        Flag flag = game.flag();
        if (flag != null && (flag.status() == FlagStatus.AVAILABLE || flag.status() == FlagStatus.DROPPED)) {
            for (Player player : orderedPlayers) {
                if (player.position().equals(flag.position()) && player.connected()) {
                    Player carrier = player.withHasFlag(true).withProtectionUntil(nowEpochMillis + game.config().protectionTimeMs());
                    game.updatePlayer(carrier);
                    game.setFlag(new Flag(flag.position(), FlagStatus.CARRIED, carrier.playerId()));
                    events.add(new FlagPickedUpMessage(
                            ProtocolVersion.V1_0,
                            game.gameId(),
                            game.tick(),
                            carrier.playerId()
                    ));
                    break;
                }
            }
        }

        game.incrementTick();
        GameStateMessage stateMessage = GameMessageMapper.toGameStateMessage(game, nowEpochMillis);
        return new GameTickResult(game.tick(), List.copyOf(events), stateMessage, false);
    }

    private boolean isBlockedByBorderExit(Game game, Player player, Position target) {
        return !target.isInside(game.config().rows(), game.config().columns())
                && !(player.hasFlag() && player.position().isOnBorder(game.config().rows(), game.config().columns()));
    }

    private List<Player> orderedPlayers() {
        List<Player> players = new ArrayList<>(game.players());
        players.sort(Comparator.comparing(Player::playerId));
        return players;
    }

    private Map<Position, Player> occupiedPositions(List<Player> players) {
        Map<Position, Player> occupied = new HashMap<>();
        for (Player player : players) {
            if (player.insideBoard()) {
                occupied.put(player.position(), player);
            }
        }
        return occupied;
    }

    private ErrorMessage error(String code, String description) {
        return new ErrorMessage(ProtocolVersion.V1_0, protocol.ErrorCode.valueOf(code), description);
    }

    private record MoveIntent(Player player, Position target) {
    }
}
