package engine;

import model.Direction;
import model.Flag;
import model.FlagStatus;
import model.Game;
import model.Player;
import model.Position;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

public final class GameInitializer {
    private final Random random;

    public GameInitializer() {
        this(new Random());
    }

    public GameInitializer(Random random) {
        this.random = Objects.requireNonNull(random, "random must not be null");
    }

    public void initialize(Game game) {
        Objects.requireNonNull(game, "game must not be null");

        List<Player> orderedPlayers = new ArrayList<>(game.players());
        orderedPlayers.sort(Comparator.comparing(Player::playerId));

        int rows = game.config().rows();
        int columns = game.config().columns();
        int obstacleCount = (rows * columns * game.config().obstaclePercentage()) / 100;
        List<SpawnPoint> spawnPoints = createSpawnPoints(rows, columns);
        if (orderedPlayers.size() > spawnPoints.size()) {
            throw new IllegalStateException("Too many players for the available spawn points");
        }

        List<SpawnPoint> assignedSpawnPoints = new ArrayList<>();
        for (int index = 0; index < orderedPlayers.size(); index++) {
            assignedSpawnPoints.add(removeRandomSpawnPoint(spawnPoints));
        }

        Generation generation = generateValidBoard(game, obstacleCount, entryPositions(assignedSpawnPoints));

        game.clearObstacles();
        for (Position obstacle : generation.obstacles()) {
            game.addObstacle(obstacle);
        }
        game.setFlag(new Flag(generation.flagPosition(), FlagStatus.AVAILABLE, null));

        for (int index = 0; index < orderedPlayers.size(); index++) {
            Player player = orderedPlayers.get(index);
            SpawnPoint spawnPoint = assignedSpawnPoints.get(index);
            game.updatePlayer(player
                    .withPosition(spawnPoint.position())
                    .withDirection(spawnPoint.direction())
                    .withInsideBoard(false)
                    .withHasFlag(false)
                    .withProtectionUntil(0L)
                    .withConnected(true));
        }

    }

    private Generation generateValidBoard(Game game, int obstacleCount, Set<Position> requiredFreePositions) {
        int availableObstacleCells = game.config().rows() * game.config().columns() - requiredFreePositions.size() - 1;
        int effectiveObstacleCount = Math.min(obstacleCount, Math.max(0, availableObstacleCells));
        int attempts = 0;
        while (attempts++ < 500) {
            List<Position> obstacles = generateObstacles(
                    game.config().rows(),
                    game.config().columns(),
                    effectiveObstacleCount,
                    requiredFreePositions
            );
            Position flagPosition = generateFlagPosition(game, obstacles);
            if (hasPathToBorder(game.config().rows(), game.config().columns(), flagPosition, obstacles)
                    && allPositionsReachFlag(game.config().rows(), game.config().columns(), flagPosition, obstacles, requiredFreePositions)) {
                return new Generation(obstacles, flagPosition);
            }
        }
        throw new IllegalStateException("Unable to generate a valid board");
    }

    private List<Position> generateObstacles(int rows, int columns, int obstacleCount, Set<Position> requiredFreePositions) {
        Set<Position> obstacles = new HashSet<>();
        while (obstacles.size() < obstacleCount) {
            int row = random.nextInt(rows);
            int column = random.nextInt(columns);
            Position candidate = new Position(row, column);
            if (!requiredFreePositions.contains(candidate)) {
                obstacles.add(candidate);
            }
        }
        return new ArrayList<>(obstacles);
    }

    private Position generateFlagPosition(Game game, List<Position> obstacles) {
        int rows = game.config().rows();
        int columns = game.config().columns();
        int rowSpan = Math.max(1, rows * game.config().centralFlagAreaPercentage() / 100);
        int columnSpan = Math.max(1, columns * game.config().centralFlagAreaPercentage() / 100);
        int rowStart = Math.max(0, (rows - rowSpan) / 2);
        int columnStart = Math.max(0, (columns - columnSpan) / 2);
        int rowEnd = Math.min(rows - 1, rowStart + rowSpan - 1);
        int columnEnd = Math.min(columns - 1, columnStart + columnSpan - 1);

        Set<Position> obstacleSet = new HashSet<>(obstacles);
        for (int attempt = 0; attempt < 200; attempt++) {
            int row = rowStart + random.nextInt(rowEnd - rowStart + 1);
            int column = columnStart + random.nextInt(columnEnd - columnStart + 1);
            Position candidate = new Position(row, column);
            if (!obstacleSet.contains(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to place flag");
    }

    private boolean hasPathToBorder(int rows, int columns, Position origin, List<Position> obstacles) {
        Set<Position> blocked = new HashSet<>(obstacles);
        Set<Position> visited = new HashSet<>();
        ArrayDeque<Position> queue = new ArrayDeque<>();
        queue.add(origin);
        visited.add(origin);

        while (!queue.isEmpty()) {
            Position current = queue.removeFirst();
            if (current.isOnBorder(rows, columns)) {
                return true;
            }
            for (Direction direction : Direction.values()) {
                Position next = current.move(direction);
                if (next.isInside(rows, columns) && !blocked.contains(next) && visited.add(next)) {
                    queue.addLast(next);
                }
            }
        }
        return false;
    }

    private boolean allPositionsReachFlag(
            int rows,
            int columns,
            Position flagPosition,
            List<Position> obstacles,
            Set<Position> requiredFreePositions
    ) {
        for (Position position : requiredFreePositions) {
            if (!hasPath(rows, columns, position, flagPosition, obstacles)) {
                return false;
            }
        }
        return true;
    }

    private boolean hasPath(int rows, int columns, Position origin, Position target, List<Position> obstacles) {
        Set<Position> blocked = new HashSet<>(obstacles);
        Set<Position> visited = new HashSet<>();
        ArrayDeque<Position> queue = new ArrayDeque<>();
        queue.add(origin);
        visited.add(origin);

        while (!queue.isEmpty()) {
            Position current = queue.removeFirst();
            if (current.equals(target)) {
                return true;
            }
            for (Direction direction : Direction.values()) {
                Position next = current.move(direction);
                if (next.isInside(rows, columns) && !blocked.contains(next) && visited.add(next)) {
                    queue.addLast(next);
                }
            }
        }
        return false;
    }

    private List<SpawnPoint> createSpawnPoints(int rows, int columns) {
        List<SpawnPoint> spawnPoints = new ArrayList<>((rows + columns) * 2);
        for (int column = 0; column < columns; column++) {
            spawnPoints.add(new SpawnPoint(new Position(-1, column), Direction.DOWN));
            spawnPoints.add(new SpawnPoint(new Position(rows, column), Direction.UP));
        }
        for (int row = 0; row < rows; row++) {
            spawnPoints.add(new SpawnPoint(new Position(row, -1), Direction.RIGHT));
            spawnPoints.add(new SpawnPoint(new Position(row, columns), Direction.LEFT));
        }
        return spawnPoints;
    }

    private SpawnPoint removeRandomSpawnPoint(List<SpawnPoint> spawnPoints) {
        return spawnPoints.remove(random.nextInt(spawnPoints.size()));
    }

    private Set<Position> entryPositions(List<SpawnPoint> spawnPoints) {
        Set<Position> positions = new HashSet<>();
        for (SpawnPoint spawnPoint : spawnPoints) {
            positions.add(spawnPoint.entryPosition());
        }
        return positions;
    }

    private record Generation(List<Position> obstacles, Position flagPosition) {
    }

    private record SpawnPoint(Position position, Direction direction) {
        private Position entryPosition() {
            return position.move(direction);
        }
    }
}
