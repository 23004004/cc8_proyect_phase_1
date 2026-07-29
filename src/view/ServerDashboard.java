package view;

import model.GameStatus;
import model.Player;
import protocol.messages.GameOverMessage;
import protocol.messages.GameStartedMessage;
import protocol.messages.GameStateMessage;
import protocol.messages.LobbyStateMessage;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.Comparator;
import java.util.List;

public final class ServerDashboard extends JFrame {
    private final JLabel statusLabel = new JLabel("Estado: WAITING");
    private final JLabel countLabel = new JLabel("Jugadores: 0");
    private final JLabel playersTitleLabel = new JLabel("Lobby de jugadores");
    private final DefaultListModel<String> playersModel = new DefaultListModel<>();
    private final PanelGame gamePanel = new PanelGame();
    private final JButton startButton = new JButton("Iniciar partida");
    private final JButton stopButton = new JButton("Cerrar servidor");

    public ServerDashboard(String serverName, int tcpPort, int discoveryPort) {
        super("Servidor - " + serverName);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setContentPane(buildContent(serverName, tcpPort, discoveryPort));
        setPreferredSize(new Dimension(1180, 820));
        pack();
        setLocationRelativeTo(null);
    }

    public void setStartAction(Runnable action) {
        startButton.addActionListener(event -> action.run());
    }

    public void setStopAction(Runnable action) {
        stopButton.addActionListener(event -> action.run());
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent event) {
                action.run();
            }
        });
    }

    public void update(GameStatus status, List<Player> players) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("Estado: " + status);
            countLabel.setText("Jugadores: " + players.size());
            playersTitleLabel.setText(status == GameStatus.WAITING ? "Lobby de jugadores" : "Jugadores conectados");
            playersModel.clear();
            players.stream()
                    .sorted(Comparator.comparingInt(Player::playerId))
                    .map(player -> "#" + player.playerId() + "  " + player.name())
                    .forEach(playersModel::addElement);
            startButton.setText(status == GameStatus.FINISHED ? "Reiniciar partida" : "Iniciar partida");
            startButton.setEnabled((status == GameStatus.WAITING || status == GameStatus.FINISHED) && !players.isEmpty());
        });
    }

    public void showLobby(LobbyStateMessage message) {
        gamePanel.applyLobbyState(message);
    }

    public void showGameStarted(GameStartedMessage message) {
        gamePanel.applyGameStarted(message);
    }

    public void showGameState(GameStateMessage message) {
        gamePanel.applyGameState(message);
    }

    public void showGameOver(GameOverMessage message) {
        gamePanel.applyGameOver(message);
    }

    private JPanel buildContent(String serverName, int tcpPort, int discoveryPort) {
        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        JPanel header = new JPanel(new java.awt.GridLayout(0, 1, 4, 4));
        header.add(new JLabel("Servidor: " + serverName));
        header.add(new JLabel("TCP: " + tcpPort + " | Discovery UDP: " + discoveryPort));
        header.add(statusLabel);
        header.add(countLabel);
        root.add(header, BorderLayout.NORTH);

        JList<String> playersList = new JList<>(playersModel);
        JPanel playersPanel = new JPanel(new BorderLayout(6, 6));
        playersPanel.add(playersTitleLabel, BorderLayout.NORTH);
        playersPanel.add(new JScrollPane(playersList), BorderLayout.CENTER);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, playersPanel, gamePanel);
        splitPane.setResizeWeight(0.22);
        splitPane.setDividerLocation(260);
        root.add(splitPane, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(startButton);
        buttons.add(stopButton);
        root.add(buttons, BorderLayout.SOUTH);
        startButton.setEnabled(false);
        return root;
    }
}
