package model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class Game {
    private final String gameId;
    private final GameConfig config;
    private final Map<String, Player> players;
    private final List<Position> obstacles;
    private GameStatus status;
    private long tick;
    private Flag flag;

    public Game(String gameId, GameConfig config) {
        if (gameId == null || gameId.isBlank()) {
            throw new IllegalArgumentException("gameId must not be blank");
        }
        this.gameId = gameId;
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.players = new LinkedHashMap<>();
        this.obstacles = new ArrayList<>();
        this.status = GameStatus.WAITING;
        this.tick = 0L;
    }

    public String gameId() {
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

    public List<Position> obstacles() {
        return Collections.unmodifiableList(obstacles);
    }

    public List<Player> players() {
        return List.copyOf(players.values());
    }

    public Player player(String playerId) {
        return players.get(playerId);
    }

    public void setStatus(GameStatus status) {
        this.status = Objects.requireNonNull(status, "status must not be null");
    }

    public void incrementTick() {
        tick++;
    }

    public void setFlag(Flag flag) {
        this.flag = Objects.requireNonNull(flag, "flag must not be null");
    }

    public void addObstacle(Position position) {
        obstacles.add(Objects.requireNonNull(position, "position must not be null"));
    }

    public void addPlayer(Player player) {
        Player validatedPlayer = Objects.requireNonNull(player, "player must not be null");
        players.put(validatedPlayer.playerId(), validatedPlayer);
    }

    public void updatePlayer(Player player) {
        Player validatedPlayer = Objects.requireNonNull(player, "player must not be null");
        players.put(validatedPlayer.playerId(), validatedPlayer);
    }

    public Player removePlayer(String playerId) {
        return players.remove(playerId);
    }
}
