package view;

import protocol.GameOverMessage;
import protocol.GameStartedMessage;
import protocol.GameStateMessage;
import protocol.dto.FlagDto;
import protocol.dto.PlayerDto;
import protocol.dto.PositionDto;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class PanelGame extends JPanel {
    private final Object lock = new Object();

    private int rows = 20;
    private int columns = 20;
    private long tick;
    private String gameId = "-";
    private String statusText = "Esperando partida";
    private FlagDto flag;
    private List<PositionDto> obstacles = List.of();
    private List<PlayerDto> players = List.of();

    public PanelGame() {
        setBackground(new Color(0x111418));
        setForeground(new Color(0xE5E7EB));
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(900, 760));
    }

    public void applyGameStarted(GameStartedMessage message) {
        Objects.requireNonNull(message, "message must not be null");
        runOnEdt(() -> {
            synchronized (lock) {
                this.rows = message.rows();
                this.columns = message.columns();
                this.gameId = message.gameId();
                this.tick = 0L;
                this.flag = message.flag();
                this.obstacles = List.copyOf(message.obstacles());
                this.players = List.copyOf(message.players());
                this.statusText = "Partida iniciada";
            }
            revalidate();
            repaint();
        });
    }

    public void applyGameState(GameStateMessage message) {
        Objects.requireNonNull(message, "message must not be null");
        runOnEdt(() -> {
            synchronized (lock) {
                if (!Objects.equals(this.gameId, message.gameId())) {
                    return;
                }
                this.tick = message.tick();
                this.flag = message.flag();
                this.players = List.copyOf(message.players());
                this.statusText = "En juego";
            }
            repaint();
        });
    }

    public void applyGameOver(GameOverMessage message) {
        Objects.requireNonNull(message, "message must not be null");
        runOnEdt(() -> {
            synchronized (lock) {
                if (!Objects.equals(this.gameId, message.gameId())) {
                    return;
                }
                this.statusText = "Ganador: " + message.winnerName() + " (" + message.winnerId() + ")";
            }
            repaint();
        });
    }

    public void applyStatusText(String text) {
        runOnEdt(() -> {
            synchronized (lock) {
                this.statusText = text == null ? "" : text;
            }
            repaint();
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            paintBoard(g2);
            paintFooter(g2);
        } finally {
            g2.dispose();
        }
    }

    private void paintBoard(Graphics2D g2) {
        int top = 24;
        int left = 24;
        int bottom = 96;
        int boardWidth = Math.max(10, getWidth() - left * 2);
        int boardHeight = Math.max(10, getHeight() - top - bottom);
        int cellSize = Math.max(18, Math.min(boardWidth / columns, boardHeight / rows));
        int boardPixelWidth = cellSize * columns;
        int boardPixelHeight = cellSize * rows;
        int boardX = left + Math.max(0, (boardWidth - boardPixelWidth) / 2);
        int boardY = top + Math.max(0, (boardHeight - boardPixelHeight) / 2);

        g2.setColor(new Color(0x0F172A));
        g2.fillRoundRect(boardX - 10, boardY - 10, boardPixelWidth + 20, boardPixelHeight + 20, 12, 12);

        g2.setColor(new Color(0x1F2937));
        g2.fillRect(boardX, boardY, boardPixelWidth, boardPixelHeight);

        drawObstacles(g2, boardX, boardY, cellSize);
        drawFlag(g2, boardX, boardY, cellSize);
        drawPlayers(g2, boardX, boardY, cellSize);
        drawGrid(g2, boardX, boardY, cellSize);
    }

    private void drawGrid(Graphics2D g2, int boardX, int boardY, int cellSize) {
        g2.setColor(new Color(0x334155));
        for (int row = 0; row <= rows; row++) {
            int y = boardY + row * cellSize;
            g2.drawLine(boardX, y, boardX + columns * cellSize, y);
        }
        for (int column = 0; column <= columns; column++) {
            int x = boardX + column * cellSize;
            g2.drawLine(x, boardY, x, boardY + rows * cellSize);
        }
    }

    private void drawObstacles(Graphics2D g2, int boardX, int boardY, int cellSize) {
        g2.setColor(new Color(0x475569));
        synchronized (lock) {
            for (PositionDto obstacle : obstacles) {
                int x = boardX + obstacle.column() * cellSize;
                int y = boardY + obstacle.row() * cellSize;
                g2.fillRect(x + 2, y + 2, cellSize - 4, cellSize - 4);
            }
        }
    }

    private void drawFlag(Graphics2D g2, int boardX, int boardY, int cellSize) {
        FlagDto currentFlag;
        synchronized (lock) {
            currentFlag = flag;
        }
        if (currentFlag == null) {
            return;
        }

        if (currentFlag.row() < 0 || currentFlag.column() < 0 || currentFlag.row() >= rows || currentFlag.column() >= columns) {
            return;
        }

        int x = boardX + currentFlag.column() * cellSize;
        int y = boardY + currentFlag.row() * cellSize;
        g2.setColor(new Color(0xFACC15));
        g2.fillOval(x + 4, y + 4, cellSize - 8, cellSize - 8);
        g2.setColor(new Color(0x78350F));
        g2.drawOval(x + 4, y + 4, cellSize - 8, cellSize - 8);
    }

    private void drawPlayers(Graphics2D g2, int boardX, int boardY, int cellSize) {
        List<PlayerDto> snapshot;
        synchronized (lock) {
            snapshot = new ArrayList<>(players);
        }
        snapshot.sort(Comparator.comparing(PlayerDto::playerId));

        for (PlayerDto player : snapshot) {
            if (player.row() < 0 || player.column() < 0 || player.row() >= rows || player.column() >= columns) {
                continue;
            }
            int x = boardX + player.column() * cellSize;
            int y = boardY + player.row() * cellSize;

            Color fill = player.hasFlag() ? new Color(0xEF4444) : new Color(0x38BDF8);
            if (player.protectedPlayer()) {
                g2.setColor(new Color(0x60A5FA));
                g2.setStroke(new BasicStroke(3f));
                g2.drawOval(x + 3, y + 3, cellSize - 6, cellSize - 6);
            }
            g2.setColor(fill);
            g2.fillOval(x + 5, y + 5, cellSize - 10, cellSize - 10);
            g2.setColor(Color.WHITE);
            g2.setFont(getFont().deriveFont(Font.BOLD, Math.max(11f, cellSize / 3f)));
            String label = player.playerId();
            int textWidth = g2.getFontMetrics().stringWidth(label);
            int textX = x + (cellSize - textWidth) / 2;
            int textY = y + (cellSize + g2.getFontMetrics().getAscent()) / 2 - 2;
            g2.drawString(label, textX, textY);
        }
    }

    private void paintFooter(Graphics2D g2) {
        synchronized (lock) {
            g2.setColor(getForeground());
            g2.setFont(getFont().deriveFont(Font.BOLD, 15f));
            g2.drawString("Game: " + gameId, 24, getHeight() - 54);
            g2.drawString("Tick: " + tick, 24, getHeight() - 32);
            g2.drawString(statusText, 24, getHeight() - 10);
        }
    }

    private void runOnEdt(Runnable runnable) {
        if (SwingUtilities.isEventDispatchThread()) {
            runnable.run();
        } else {
            SwingUtilities.invokeLater(runnable);
        }
    }
}
