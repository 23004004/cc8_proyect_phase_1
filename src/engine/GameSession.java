package engine;

import model.Direction;
import model.Flag;
import model.FlagStatus;
import model.Game;
import model.GameStatus;
import model.Player;
import protocol.ErrorCode;
import protocol.ErrorMessage;
import protocol.FlagPickedUpMessage;
import protocol.FlagStolenMessage;
import protocol.GameMessageMapper;
import protocol.GameOverMessage;
import protocol.GameOverReason;
import protocol.GameStateMessage;
import protocol.PlayerDisconnectedMessage;
import protocol.ProtocolMessage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class GameSession {
    private final Game game;
    private final Map<Integer, Direction> pendingDirections;
    private final Set<Integer> pendingInteractions;

    public GameSession(Game game) {
        this.game = Objects.requireNonNull(game, "game must not be null");
        this.pendingDirections = new ConcurrentHashMap<>();
        this.pendingInteractions = ConcurrentHashMap.newKeySet();
    }

    public synchronized Game game() {
        return game;
    }

    public void submitDirectionChange(int playerId, Direction direction) {
        if (playerId > 0 && direction != null) {
            pendingDirections.put(playerId, direction);
        }
    }

    public void submitInteraction(int playerId) {
        if (playerId > 0) {
            pendingInteractions.add(playerId);
        }
    }

    public synchronized List<ProtocolMessage> disconnectPlayer(int playerId) {
        pendingDirections.remove(playerId);
        pendingInteractions.remove(playerId);
        Player player = game.removePlayer(playerId);
        if (player == null) {
            return List.of(new ErrorMessage(ErrorCode.UNKNOWN_PLAYER, "Jugador desconocido."));
        }

        List<ProtocolMessage> events = new ArrayList<>();
        events.add(new PlayerDisconnectedMessage(playerId));
        if (player.hasFlag()) {
            game.setFlag(new Flag(player.x(), player.y(), FlagStatus.DROPPED, 0));
        }
        return List.copyOf(events);
    }

    public synchronized GameTickResult tick() {
        if (game.status() != GameStatus.RUNNING) {
            return new GameTickResult(game.tick(), List.of(), GameMessageMapper.toGameStateMessage(game), null);
        }

        Map<Integer, Direction> directionUpdates = new HashMap<>(pendingDirections);
        pendingDirections.clear();
        Set<Integer> interactions = new HashSet<>(pendingInteractions);
        pendingInteractions.clear();

        for (Player player : orderedPlayers()) {
            Direction direction = directionUpdates.getOrDefault(player.playerId(), player.direction());
            Player directed = player.withDirection(direction);
            game.updatePlayer(move(directed));
        }

        game.incrementTick();
        List<ProtocolMessage> events = resolveInteractions(interactions);
        updateCarriedFlagPosition();
        GameOverMessage gameOver = evaluateVictory();
        GameStateMessage stateMessage = GameMessageMapper.toGameStateMessage(game);
        return new GameTickResult(game.tick(), events, stateMessage, gameOver);
    }

    private Player move(Player player) {
        if (!player.connected() || player.direction() == Direction.NONE) {
            return player;
        }
        int step = (int) Math.round(game.config().playerSpeed() * 100.0 * game.config().tickIntervalMs() / 1000.0);
        int mapLimit = game.config().mapSize() * 100 / 2;
        int x = clamp(player.x() + player.direction().xSign() * step, -mapLimit, mapLimit);
        int y = clamp(player.y() + player.direction().ySign() * step, -mapLimit, mapLimit);
        return player.withPosition(x, y);
    }

    private List<ProtocolMessage> resolveInteractions(Set<Integer> interactions) {
        List<ProtocolMessage> events = new ArrayList<>();
        List<Integer> orderedIds = interactions.stream().sorted().toList();
        Flag flag = game.flag();
        if (flag.status() == FlagStatus.AVAILABLE || flag.status() == FlagStatus.DROPPED) {
            for (int playerId : orderedIds) {
                Player player = game.player(playerId);
                if (player != null && distance(player.x(), player.y(), flag.x(), flag.y()) <= game.config().interactionRadius() * 100.0) {
                    Player carrier = player.withHasFlag(true);
                    game.updatePlayer(carrier);
                    game.setFlag(new Flag(carrier.x(), carrier.y(), FlagStatus.CARRIED, carrier.playerId()));
                    events.add(new FlagPickedUpMessage(game.tick(), carrier.playerId()));
                    return List.copyOf(events);
                }
            }
            return List.copyOf(events);
        }

        if (flag.status() == FlagStatus.CARRIED && flag.carrierId() > 0) {
            Player carrier = game.player(flag.carrierId());
            if (carrier == null) {
                return List.copyOf(events);
            }
            for (int playerId : orderedIds) {
                if (playerId == carrier.playerId()) {
                    continue;
                }
                Player attacker = game.player(playerId);
                if (attacker != null && distance(attacker.x(), attacker.y(), carrier.x(), carrier.y()) <= game.config().interactionRadius() * 100.0) {
                    game.updatePlayer(carrier.withHasFlag(false));
                    game.updatePlayer(attacker.withHasFlag(true));
                    game.setFlag(new Flag(attacker.x(), attacker.y(), FlagStatus.CARRIED, attacker.playerId()));
                    events.add(new FlagStolenMessage(game.tick(), carrier.playerId(), attacker.playerId()));
                    break;
                }
            }
        }
        return List.copyOf(events);
    }

    private void updateCarriedFlagPosition() {
        Flag flag = game.flag();
        if (flag.status() != FlagStatus.CARRIED || flag.carrierId() <= 0) {
            return;
        }
        Player carrier = game.player(flag.carrierId());
        if (carrier != null) {
            game.setFlag(new Flag(carrier.x(), carrier.y(), FlagStatus.CARRIED, carrier.playerId()));
        }
    }

    private GameOverMessage evaluateVictory() {
        for (Player player : orderedPlayers()) {
            if (!player.hasFlag()) {
                continue;
            }
            if (distance(player.x(), player.y(), 0, 0) - game.config().playerRadius() * 100.0 > game.config().circleRadius() * 100.0) {
                game.setStatus(GameStatus.FINISHED);
                game.setFlag(new Flag(player.x(), player.y(), FlagStatus.OUTSIDE, player.playerId()));
                return new GameOverMessage(player.playerId(), player.name(), GameOverReason.EXITED_CIRCLE_WITH_FLAG);
            }
        }
        return null;
    }

    private List<Player> orderedPlayers() {
        List<Player> players = new ArrayList<>(game.players());
        players.sort(Comparator.comparingInt(Player::playerId));
        return players;
    }

    private double distance(int x1, int y1, int x2, int y2) {
        return Math.hypot(x1 - x2, y1 - y2);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
