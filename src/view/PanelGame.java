package view;

import model.Direction;
import model.GameStatus;
import protocol.GameOverMessage;
import protocol.GameStartedMessage;
import protocol.GameStateMessage;
import protocol.LobbyStateMessage;
import protocol.dto.FlagDto;
import protocol.dto.PlayerDto;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class PanelGame extends JPanel {
    private final Object lock = new Object();
    private final Map<Integer, String> namesById = new HashMap<>();

    private int mapSize = 200000;
    private int circleRadius = 50000;
    private int playerRadius = 1500;
    private int interactionRadius = 6000;
    private long tick;
    private int localPlayerId;
    private Direction localDirection = Direction.NONE;
    private GameStatus gameStatus = GameStatus.WAITING;
    private String statusText = "Esperando partida";
    private FlagDto flag = new FlagDto(0, 0, model.FlagStatus.AVAILABLE, 0);
    private List<PlayerDto> players = List.of();

    public PanelGame() {
        setBackground(new Color(0x101820));
        setForeground(new Color(0xE8EEF2));
        setPreferredSize(new Dimension(920, 760));
    }

    public void applyLobbyState(LobbyStateMessage message) {
        Objects.requireNonNull(message, "message must not be null");
        runOnEdt(() -> {
            synchronized (lock) {
                namesById.clear();
                for (PlayerDto player : message.players()) {
                    namesById.put(player.playerId(), player.name());
                }
                players = List.copyOf(message.players());
                gameStatus = message.state();
                statusText = "Lobby: " + message.players().size() + " jugador(es)";
            }
            repaint();
        });
    }

    public void applyGameStarted(GameStartedMessage message) {
        Objects.requireNonNull(message, "message must not be null");
        runOnEdt(() -> {
            synchronized (lock) {
                mapSize = message.mapSize();
                circleRadius = message.circleRadius();
                playerRadius = message.playerRadius();
                interactionRadius = message.interactionRadius();
                flag = message.flag();
                players = List.copyOf(message.players());
                for (PlayerDto player : players) {
                    namesById.put(player.playerId(), player.name());
                }
                gameStatus = GameStatus.RUNNING;
                tick = 0L;
                statusText = "Partida iniciada";
            }
            repaint();
        });
    }

    public void applyGameState(GameStateMessage message) {
        Objects.requireNonNull(message, "message must not be null");
        runOnEdt(() -> {
            synchronized (lock) {
                if (message.tick() < tick) {
                    return;
                }
                tick = message.tick();
                flag = message.flag();
                players = mergeNames(message.players());
                gameStatus = GameStatus.RUNNING;
                statusText = "En juego";
            }
            repaint();
        });
    }

    public void applyGameOver(GameOverMessage message) {
        Objects.requireNonNull(message, "message must not be null");
        runOnEdt(() -> {
            synchronized (lock) {
                gameStatus = GameStatus.FINISHED;
                statusText = "Ganador: " + message.winnerName() + " (" + message.winnerId() + ")";
            }
            repaint();
        });
    }

    public void applyStatusText(String text) {
        runOnEdt(() -> {
            synchronized (lock) {
                statusText = text == null ? "" : text;
            }
            repaint();
        });
    }

    public void setLocalPlayerId(int playerId) {
        runOnEdt(() -> {
            synchronized (lock) {
                localPlayerId = playerId;
            }
            repaint();
        });
    }

    public void applyLocalDirection(Direction direction) {
        runOnEdt(() -> {
            synchronized (lock) {
                localDirection = direction == null ? Direction.NONE : direction;
            }
            repaint();
        });
    }

    public boolean lobbyActive() {
        synchronized (lock) {
            return gameStatus == GameStatus.WAITING || gameStatus == GameStatus.STARTING;
        }
    }

    public boolean canReturnToServerList() {
        synchronized (lock) {
            return gameStatus == GameStatus.WAITING || gameStatus == GameStatus.STARTING || gameStatus == GameStatus.FINISHED;
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (isLobbyVisible()) {
                paintLobby(g2);
            } else {
                Viewport viewport = viewport();
                paintMap(g2, viewport);
            }
            paintFooter(g2);
        } finally {
            g2.dispose();
        }
    }

    private boolean isLobbyVisible() {
        synchronized (lock) {
            return gameStatus == GameStatus.WAITING || gameStatus == GameStatus.STARTING;
        }
    }

    private void paintLobby(Graphics2D g2) {
        List<PlayerDto> snapshot;
        GameStatus status;
        String text;
        synchronized (lock) {
            snapshot = new ArrayList<>(players);
            status = gameStatus;
            text = statusText;
        }
        snapshot.sort(Comparator.comparingInt(PlayerDto::playerId));

        int margin = 48;
        int width = Math.max(280, getWidth() - margin * 2);
        int x = margin;
        int y = 56;

        g2.setColor(new Color(0x17212B));
        g2.fillRoundRect(x, y, width, Math.max(280, getHeight() - 152), 12, 12);
        g2.setColor(new Color(0x314252));
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawRoundRect(x, y, width, Math.max(280, getHeight() - 152), 12, 12);

        g2.setColor(getForeground());
        g2.setFont(getFont().deriveFont(Font.BOLD, 24f));
        g2.drawString("Lobby de jugadores", x + 24, y + 42);

        g2.setFont(getFont().deriveFont(Font.PLAIN, 15f));
        g2.setColor(new Color(0xB7C7D3));
        g2.drawString(text + " | Estado: " + statusTextFor(status), x + 24, y + 70);

        int rowY = y + 112;
        g2.setFont(getFont().deriveFont(Font.BOLD, 14f));
        g2.setColor(new Color(0x8BD3DD));
        g2.drawString("Jugador", x + 24, rowY);
        g2.drawString("Estatus", x + width - 180, rowY);

        g2.setFont(getFont().deriveFont(Font.PLAIN, 15f));
        if (snapshot.isEmpty()) {
            g2.setColor(new Color(0xB7C7D3));
            g2.drawString("Esperando jugadores conectados...", x + 24, rowY + 36);
            return;
        }

        int currentY = rowY + 32;
        for (PlayerDto player : snapshot) {
            boolean local = player.playerId() == localPlayerId;
            g2.setColor(local ? new Color(0x203948) : new Color(0x1C2A35));
            g2.fillRoundRect(x + 18, currentY - 22, width - 36, 34, 8, 8);
            g2.setColor(local ? Color.WHITE : getForeground());
            String name = labelFor(player) + (local ? "  (Tu)" : "");
            g2.drawString(name, x + 32, currentY);
            g2.setColor(status == GameStatus.STARTING ? new Color(0xF2C14E) : new Color(0x06D6A0));
            g2.drawString(status == GameStatus.STARTING ? "Iniciando" : "En lobby", x + width - 180, currentY);
            currentY += 42;
        }
    }

    private String statusTextFor(GameStatus status) {
        return switch (status) {
            case WAITING -> "Esperando";
            case STARTING -> "Iniciando";
            case RUNNING -> "En juego";
            case FINISHED -> "Finalizado";
            case CANCELLED -> "Cancelado";
        };
    }

    private void paintMap(Graphics2D g2, Viewport viewport) {
        g2.setColor(new Color(0x17212B));
        g2.fillRect(viewport.x(), viewport.y(), viewport.size(), viewport.size());

        int circleDiameter = (int) Math.round(circleRadius * 2.0 * viewport.size() / mapSize);
        int cx = worldToScreenX(0, viewport);
        int cy = worldToScreenY(0, viewport);
        g2.setColor(new Color(0x203948));
        g2.fillOval(cx - circleDiameter / 2, cy - circleDiameter / 2, circleDiameter, circleDiameter);
        g2.setColor(new Color(0x5BA6C9));
        g2.setStroke(new BasicStroke(2f));
        g2.drawOval(cx - circleDiameter / 2, cy - circleDiameter / 2, circleDiameter, circleDiameter);

        drawFlag(g2, viewport);
        drawPlayers(g2, viewport);

        g2.setColor(new Color(0x314252));
        g2.drawRect(viewport.x(), viewport.y(), viewport.size(), viewport.size());
    }

    private void drawFlag(Graphics2D g2, Viewport viewport) {
        FlagDto snapshot;
        synchronized (lock) {
            snapshot = flag;
        }
        int x = worldToScreenX(snapshot.x(), viewport);
        int y = worldToScreenY(snapshot.y(), viewport);
        g2.setColor(new Color(0xF2C14E));
        g2.fillOval(x - 8, y - 8, 16, 16);
        g2.setColor(new Color(0x6B3E08));
        g2.drawOval(x - 8, y - 8, 16, 16);
    }

    private void drawPlayers(Graphics2D g2, Viewport viewport) {
        List<PlayerDto> snapshot;
        synchronized (lock) {
            snapshot = new ArrayList<>(players);
        }
        snapshot.sort(Comparator.comparingInt(PlayerDto::playerId));
        int radiusPx = Math.max(7, (int) Math.round(playerRadius * viewport.size() / (double) mapSize));
        int interactPx = Math.max(radiusPx + 3, (int) Math.round(interactionRadius * viewport.size() / (double) mapSize));
        for (PlayerDto player : snapshot) {
            int x = worldToScreenX(player.x(), viewport);
            int y = worldToScreenY(player.y(), viewport);
            if (player.playerId() == localPlayerId) {
                g2.setColor(new Color(0x8BD3DD));
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawOval(x - interactPx, y - interactPx, interactPx * 2, interactPx * 2);
            }
            g2.setColor(player.hasFlag() ? new Color(0xEF476F) : new Color(0x06D6A0));
            g2.fillOval(x - radiusPx, y - radiusPx, radiusPx * 2, radiusPx * 2);
            if (player.playerId() == localPlayerId) {
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(3f));
                g2.drawOval(x - radiusPx - 3, y - radiusPx - 3, radiusPx * 2 + 6, radiusPx * 2 + 6);
            }
            g2.setColor(Color.WHITE);
            g2.setFont(getFont().deriveFont(Font.BOLD, 12f));
            String label = labelFor(player);
            int textWidth = g2.getFontMetrics().stringWidth(label);
            g2.drawString(label, x - textWidth / 2, y - radiusPx - 6);
        }
    }

    private void paintFooter(Graphics2D g2) {
        synchronized (lock) {
            g2.setColor(getForeground());
            g2.setFont(getFont().deriveFont(Font.BOLD, 15f));
            g2.drawString("Jugador: " + (localPlayerId == 0 ? "-" : localPlayerId) + " | Dirección: " + localDirection + " | Tick: " + tick, 24, getHeight() - 40);
            g2.drawString(statusText, 24, getHeight() - 16);
        }
    }

    private List<PlayerDto> mergeNames(List<PlayerDto> incoming) {
        List<PlayerDto> merged = new ArrayList<>(incoming.size());
        for (PlayerDto player : incoming) {
            String name = namesById.getOrDefault(player.playerId(), player.name());
            merged.add(new PlayerDto(player.playerId(), name, player.x(), player.y(), player.direction(), player.hasFlag()));
        }
        return merged;
    }

    private String labelFor(PlayerDto player) {
        String name;
        synchronized (lock) {
            name = namesById.getOrDefault(player.playerId(), player.name());
        }
        if (name == null || name.isBlank()) {
            return String.valueOf(player.playerId());
        }
        return player.playerId() + " " + name;
    }

    private Viewport viewport() {
        int size = Math.max(200, Math.min(getWidth() - 48, getHeight() - 112));
        int x = Math.max(24, (getWidth() - size) / 2);
        return new Viewport(x, 24, size);
    }

    private int worldToScreenX(int worldX, Viewport viewport) {
        return viewport.x() + (int) Math.round((worldX + mapSize / 2.0) * viewport.size() / mapSize);
    }

    private int worldToScreenY(int worldY, Viewport viewport) {
        return viewport.y() + (int) Math.round((worldY + mapSize / 2.0) * viewport.size() / mapSize);
    }

    private void runOnEdt(Runnable runnable) {
        if (SwingUtilities.isEventDispatchThread()) {
            runnable.run();
        } else {
            SwingUtilities.invokeLater(runnable);
        }
    }

    private record Viewport(int x, int y, int size) {
    }
}
