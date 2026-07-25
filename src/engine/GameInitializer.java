package engine;

import model.Direction;
import model.Flag;
import model.FlagStatus;
import model.Game;
import model.Player;

import java.util.Random;

public final class GameInitializer {
    private final Random random = new Random();

    public void initialize(Game game) {
        game.setFlag(new Flag(0, 0, FlagStatus.AVAILABLE, 0));
        int spawnDistance = (game.config().circleRadius() + game.config().spawnMargin()) * 100;
        int mapLimit = game.config().mapSize() * 100 / 2;
        for (Player player : game.players()) {
            double angle = random.nextDouble(0.0, Math.PI * 2.0);
            int x = clamp((int) Math.round(Math.cos(angle) * spawnDistance), -mapLimit, mapLimit);
            int y = clamp((int) Math.round(Math.sin(angle) * spawnDistance), -mapLimit, mapLimit);
            game.updatePlayer(new Player(player.playerId(), player.name(), x, y, Direction.NONE, true, false));
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
