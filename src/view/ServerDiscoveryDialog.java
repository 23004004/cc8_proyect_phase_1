package view;

import connect.ServerChoice;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class ServerDiscoveryDialog extends JDialog {
    private final Map<String, ServerChoice> serversByKey = new LinkedHashMap<>();
    private final javax.swing.DefaultListModel<ServerChoice> serverModel = new javax.swing.DefaultListModel<>();
    private final JList<ServerChoice> serverList = new JList<>(serverModel);
    private final JTextField playerNameField = new JTextField("Jugador", 24);
    private final JTextField hostField = new JTextField("127.0.0.1", 24);
    private final JTextField portField = new JTextField("5000", 10);
    private final JTextField discoveryPortField = new JTextField("5000", 10);
    private final javax.swing.JCheckBox compatibilityPortCheck = new javax.swing.JCheckBox("Buscar tambien UDP 5001");
    private final javax.swing.JCheckBox radminScanCheck = new javax.swing.JCheckBox("Radmin/VPN", true);
    private final JLabel statusLabel = new JLabel("Buscando servidores...");
    private ServerChoice selectedServer;

    public ServerDiscoveryDialog(Frame owner, Runnable refreshAction, Consumer<Boolean> scanningCallback) {
        super(owner, "Servidores disponibles", true);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setContentPane(buildContent(refreshAction, scanningCallback));
        setPreferredSize(new Dimension(820, 520));
        pack();
        setLocationRelativeTo(owner);
    }

    public ServerChoice selectedServer() {
        return selectedServer;
    }

    public String playerName() {
        String value = playerNameField.getText().trim();
        return value.isBlank() ? "Jugador" : value;
    }

    public int discoveryPort() {
        return parsePort(discoveryPortField.getText(), 5000);
    }

    public String extraDiscoveryPorts() {
        return compatibilityPortCheck.isSelected() && discoveryPort() != 5001 ? "5001" : "";
    }

    public boolean radminScanEnabled() {
        return radminScanCheck.isSelected();
    }

    public void updateServers(List<ServerChoice> servers) {
        runOnEdt(() -> {
            boolean changed = false;
            for (ServerChoice server : servers) {
                ServerChoice previous = serversByKey.put(server.key(), server);
                changed = changed || !server.equals(previous);
            }
            if (changed) {
                ServerChoice selected = serverList.getSelectedValue();
                serverModel.clear();
                for (ServerChoice server : serversByKey.values()) {
                    serverModel.addElement(server);
                }
                if (selected != null) {
                    serverList.setSelectedValue(serversByKey.get(selected.key()), true);
                } else if (!serverModel.isEmpty()) {
                    serverList.setSelectedIndex(0);
                }
            }
            statusLabel.setText(serverModel.isEmpty() ? "Buscando servidores..." : "Servidores encontrados: " + serverModel.size());
        });
    }

    public void setScanning(boolean scanning) {
        runOnEdt(() -> statusLabel.setText(scanning ? "Buscando servidores..." : statusLabel.getText()));
    }

    private JPanel buildContent(Runnable refreshAction, Consumer<Boolean> scanningCallback) {
        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        serverList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        root.add(new JScrollPane(serverList), BorderLayout.CENTER);

        JPanel manualPanel = new JPanel(new GridBagLayout());
        manualPanel.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 0;
        manualPanel.add(new JLabel("Nombre"), c);
        c.gridx = 1;
        c.weightx = 1;
        c.gridwidth = 3;
        manualPanel.add(playerNameField, c);
        c.gridx = 0;
        c.gridy = 1;
        c.weightx = 0;
        c.gridwidth = 1;
        manualPanel.add(new JLabel("Host"), c);
        c.gridx = 1;
        c.weightx = 1;
        manualPanel.add(hostField, c);
        c.gridx = 2;
        c.weightx = 0;
        manualPanel.add(new JLabel("Puerto"), c);
        c.gridx = 3;
        c.weightx = 0.35;
        manualPanel.add(portField, c);
        c.gridx = 0;
        c.gridy = 2;
        c.weightx = 0;
        manualPanel.add(new JLabel("Discovery UDP"), c);
        c.gridx = 1;
        c.weightx = 0.35;
        manualPanel.add(discoveryPortField, c);
        c.gridx = 2;
        c.weightx = 0;
        manualPanel.add(compatibilityPortCheck, c);
        c.gridx = 3;
        manualPanel.add(radminScanCheck, c);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton refreshButton = new JButton("Actualizar");
        JButton connectButton = new JButton("Unirme");
        JButton manualButton = new JButton("Conectar manual");
        JButton cancelButton = new JButton("Cancelar");
        buttons.add(refreshButton);
        buttons.add(manualButton);
        buttons.add(connectButton);
        buttons.add(cancelButton);

        refreshButton.addActionListener(event -> {
            scanningCallback.accept(true);
            refreshAction.run();
        });
        connectButton.addActionListener(event -> {
            ServerChoice server = serverList.getSelectedValue();
            if (server != null) {
                selectedServer = server;
                dispose();
            }
        });
        manualButton.addActionListener(event -> selectManual());
        cancelButton.addActionListener(event -> {
            selectedServer = null;
            dispose();
        });
        serverList.addListSelectionListener(event -> {
            ServerChoice server = serverList.getSelectedValue();
            if (server != null) {
                hostField.setText(server.host());
                portField.setText(String.valueOf(server.port()));
            }
        });

        JPanel south = new JPanel(new BorderLayout(8, 8));
        south.add(manualPanel, BorderLayout.NORTH);
        south.add(statusLabel, BorderLayout.CENTER);
        south.add(buttons, BorderLayout.SOUTH);
        root.add(south, BorderLayout.SOUTH);
        return root;
    }

    private void selectManual() {
        String host = hostField.getText().trim();
        int port = parsePort(portField.getText(), -1);
        if (host.isBlank() || port <= 0 || port > 65535) {
            statusLabel.setText("Host o puerto manual inválido.");
            return;
        }
        selectedServer = new ServerChoice(host, port, "Manual", 0, "MANUAL", 0, 0);
        dispose();
    }

    private int parsePort(String raw, int fallback) {
        try {
            int port = Integer.parseInt(raw.trim());
            return port > 0 && port <= 65535 ? port : fallback;
        } catch (NumberFormatException ex) {
            return fallback;
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
