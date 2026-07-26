package model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class Game {
    private final int gameId;
    private final GameConfig config;
    private final Map<Integer, Player> players;
    private GameStatus status;
    private long tick;
    private Flag flag;

    public Game(int gameId, GameConfig config) {
        this.gameId = gameId;
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.players = new LinkedHashMap<>();
        this.status = GameStatus.WAITING;
        this.tick = 0L;
        this.flag = new Flag(0, 0, FlagStatus.AVAILABLE, 0);
    }

    public int gameId() {
        return gameId;
    }

    public GameConfig config() {
        return config;
    }

    public GameStatus status() {
        return status;
    }

    public long tick() {
        return tick;
    }

    public Flag flag() {
        return flag;
    }

    public List<Player> players() {
        return List.copyOf(players.values());
    }

    public Player player(int playerId) {
        return players.get(playerId);
    }

    public void setStatus(GameStatus status) {
        this.status = Objects.requireNonNull(status, "status must not be null");
    }

    public void incrementTick() {
        tick++;
    }

    public void resetForLobby() {
        tick = 0L;
        flag = new Flag(0, 0, FlagStatus.AVAILABLE, 0);
        for (Player player : List.copyOf(players.values())) {
            players.put(player.playerId(), new Player(
                    player.playerId(),
                    player.name(),
                    0,
                    0,
                    Direction.NONE,
                    player.connected(),
                    false
            ));
        }
    }

    public void setFlag(Flag flag) {
        this.flag = Objects.requireNonNull(flag, "flag must not be null");
    }

    public void addPlayer(Player player) {
        Player validatedPlayer = Objects.requireNonNull(player, "player must not be null");
        players.put(validatedPlayer.playerId(), validatedPlayer);
    }

    public void updatePlayer(Player player) {
        Player validatedPlayer = Objects.requireNonNull(player, "player must not be null");
        players.put(validatedPlayer.playerId(), validatedPlayer);
    }

    public Player removePlayer(int playerId) {
        return players.remove(playerId);
    }
}
