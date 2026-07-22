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
import java.util.Set;
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
        pendingDirections.remove(playerId);
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

        List<Player> snapshotPlayers = orderedPlayers();
        Map<String, Player> resolvedPlayers = new LinkedHashMap<>();
        for (Player player : snapshotPlayers) {
            Direction newDirection = directionUpdates.get(player.playerId());
            resolvedPlayers.put(player.playerId(), newDirection == null ? player : player.withDirection(newDirection));
        }

        List<Player> orderedResolvedPlayers = new ArrayList<>(resolvedPlayers.values());
        orderedResolvedPlayers.sort(Comparator.comparing(Player::playerId));

        Map<Position, Player> occupiedPositions = occupiedPositions(orderedResolvedPlayers);
        Set<Position> obstacles = new HashSet<>(game.obstacles());
        Map<String, MoveIntent> intents = buildMoveIntents(orderedResolvedPlayers);
        Map<Position, List<MoveIntent>> targetGroups = groupByTarget(intents);
        List<ProtocolMessage> events = new ArrayList<>();
        Set<String> blockedPlayers = new HashSet<>();
        Flag flag = game.flag();

        if (flag != null && flag.status() == FlagStatus.CARRIED && flag.carrierId() != null) {
            Player carrier = resolvedPlayers.get(flag.carrierId());
            if (carrier != null && !carrier.isProtected(nowEpochMillis)) {
                List<MoveIntent> attackers = targetGroups.getOrDefault(carrier.position(), List.of());
                List<MoveIntent> validAttackers = new ArrayList<>();
                for (MoveIntent intent : attackers) {
                    if (!intent.player().playerId().equals(carrier.playerId())
                            && !intent.player().hasFlag()
                            && !blockedPlayers.contains(intent.player().playerId())) {
                        validAttackers.add(intent);
                    }
                }
                validAttackers.sort(Comparator.comparing(intent -> intent.player().playerId()));
                if (!validAttackers.isEmpty()) {
                    MoveIntent chosenAttacker = validAttackers.get(0);
                    Player attacker = chosenAttacker.player().withHasFlag(true).withProtectionUntil(nowEpochMillis + game.config().protectionTimeMs());
                    Player updatedCarrier = carrier.withHasFlag(false);
                    resolvedPlayers.put(attacker.playerId(), attacker);
                    resolvedPlayers.put(updatedCarrier.playerId(), updatedCarrier);
                    game.setFlag(new Flag(carrier.position(), FlagStatus.CARRIED, attacker.playerId()));
                    events.add(new FlagStolenMessage(
                            ProtocolVersion.V1_0,
                            game.gameId(),
                            game.tick(),
                            carrier.playerId(),
                            attacker.playerId(),
                            game.config().protectionTimeMs()
                    ));
                    for (MoveIntent intent : validAttackers) {
                        blockedPlayers.add(intent.player().playerId());
                    }
                    blockedPlayers.add(carrier.playerId());
                }
            }
        }

        boolean winnerDeclared = false;
        for (MoveIntent intent : intents.values()) {
            Player player = resolvedPlayers.get(intent.player().playerId());
            if (player == null || blockedPlayers.contains(player.playerId())) {
                continue;
            }

            Position target = intent.target();
            if (isBlockedByBorderExit(game, player, target)) {
                continue;
            }

            if (!target.isInside(game.config().rows(), game.config().columns())) {
                if (player.hasFlag() && player.position().isOnBorder(game.config().rows(), game.config().columns())) {
                    winnerDeclared = true;
                    Player exitingPlayer = player.withPosition(target).withInsideBoard(false);
                    resolvedPlayers.put(exitingPlayer.playerId(), exitingPlayer);
                    game.setStatus(GameStatus.FINISHED);
                    game.setFlag(new Flag(target, FlagStatus.OUTSIDE, player.playerId()));
                    events.add(new GameOverMessage(
                            ProtocolVersion.V1_0,
                            game.gameId(),
                            player.playerId(),
                            player.name(),
                            GameOverReason.EXITED_WITH_FLAG
                    ));
                    break;
                }
                continue;
            }

            if (obstacles.contains(target)) {
                continue;
            }

            Player occupant = occupiedPositions.get(target);
            if (occupant != null) {
                blockedPlayers.add(player.playerId());
                continue;
            }

            List<MoveIntent> sameTarget = targetGroups.get(target);
            if (sameTarget != null && sameTarget.size() > 1) {
                blockedPlayers.add(player.playerId());
                continue;
            }

            Player moved = player.withPosition(target).withInsideBoard(true);
            resolvedPlayers.put(player.playerId(), moved);
        }

        if (!winnerDeclared) {
            flag = game.flag();
            if (flag != null && (flag.status() == FlagStatus.AVAILABLE || flag.status() == FlagStatus.DROPPED)) {
                List<Player> pickupCandidates = new ArrayList<>();
                for (Player player : resolvedPlayers.values()) {
                    if (player.connected() && player.insideBoard() && player.position().equals(flag.position())) {
                        pickupCandidates.add(player);
                    }
                }
                pickupCandidates.sort(Comparator.comparing(Player::playerId));
                if (!pickupCandidates.isEmpty()) {
                    Player carrier = pickupCandidates.get(0).withHasFlag(true);
                    resolvedPlayers.put(carrier.playerId(), carrier);
                    game.setFlag(new Flag(flag.position(), FlagStatus.CARRIED, carrier.playerId()));
                    events.add(new FlagPickedUpMessage(
                            ProtocolVersion.V1_0,
                            game.gameId(),
                            game.tick(),
                            carrier.playerId()
                    ));
                }
            }
        }

        updateCarriedFlagPosition(resolvedPlayers);

        for (Player player : resolvedPlayers.values()) {
            game.updatePlayer(player);
        }

        game.incrementTick();
        GameStateMessage stateMessage = GameMessageMapper.toGameStateMessage(game, nowEpochMillis);
        return new GameTickResult(game.tick(), List.copyOf(events), stateMessage, game.status() == GameStatus.FINISHED);
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

    private void updateCarriedFlagPosition(Map<String, Player> resolvedPlayers) {
        Flag currentFlag = game.flag();
        if (currentFlag == null || currentFlag.status() != FlagStatus.CARRIED || currentFlag.carrierId() == null) {
            return;
        }

        Player carrier = resolvedPlayers.get(currentFlag.carrierId());
        if (carrier != null) {
            game.setFlag(new Flag(carrier.position(), FlagStatus.CARRIED, carrier.playerId()));
        }
    }

    private Map<String, MoveIntent> buildMoveIntents(List<Player> players) {
        Map<String, MoveIntent> intents = new LinkedHashMap<>();
        for (Player player : players) {
            if (!player.connected()) {
                continue;
            }
            intents.put(player.playerId(), new MoveIntent(player, player.position().move(player.direction())));
        }
        return intents;
    }

    private Map<Position, List<MoveIntent>> groupByTarget(Map<String, MoveIntent> intents) {
        Map<Position, List<MoveIntent>> grouped = new HashMap<>();
        for (MoveIntent intent : intents.values()) {
            grouped.computeIfAbsent(intent.target(), key -> new ArrayList<>()).add(intent);
        }
        for (List<MoveIntent> group : grouped.values()) {
            group.sort(Comparator.comparing(intent -> intent.player().playerId()));
        }
        return grouped;
    }

    private record MoveIntent(Player player, Position target) {
    }
}
