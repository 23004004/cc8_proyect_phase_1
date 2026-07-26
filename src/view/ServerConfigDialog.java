package view;

import model.GameConfig;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

public final class ServerConfigDialog extends JDialog {
    private final JTextField serverNameField;
    private final JTextField tcpPortField;
    private final JTextField discoveryPortField;
    private final JCheckBox compatibilityPortCheck;
    private GameConfig selectedConfig;

    public ServerConfigDialog(Frame owner, GameConfig defaults) {
        super(owner, "Crear partida", true);
        this.serverNameField = new JTextField(defaults.serverName(), 20);
        this.tcpPortField = new JTextField(String.valueOf(defaults.serverPort()), 8);
        this.discoveryPortField = new JTextField(String.valueOf(defaults.discoveryPort()), 8);
        this.compatibilityPortCheck = new JCheckBox("Anunciar tambien en UDP 5000", defaults.extraDiscoveryPorts().contains("5000"));
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setContentPane(buildContent(defaults));
        pack();
        setLocationRelativeTo(owner);
    }

    public static GameConfig showDialog(GameConfig defaults) {
        final ServerConfigDialog[] dialogRef = new ServerConfigDialog[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                ServerConfigDialog dialog = new ServerConfigDialog(null, defaults);
                dialogRef[0] = dialog;
                dialog.setVisible(true);
            });
        } catch (Exception ex) {
            return defaults;
        }
        ServerConfigDialog dialog = dialogRef[0];
        return dialog == null ? null : dialog.selectedConfig;
    }

    private JPanel buildContent(GameConfig defaults) {
        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        JPanel fields = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 5, 5, 5);
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0;
        c.gridy = 0;
        fields.add(new JLabel("Nombre servidor"), c);
        c.gridx = 1;
        fields.add(serverNameField, c);
        c.gridx = 0;
        c.gridy = 1;
        fields.add(new JLabel("Puerto TCP"), c);
        c.gridx = 1;
        fields.add(tcpPortField, c);
        c.gridx = 0;
        c.gridy = 2;
        fields.add(new JLabel("Puerto discovery UDP"), c);
        c.gridx = 1;
        fields.add(discoveryPortField, c);
        c.gridx = 1;
        c.gridy = 3;
        fields.add(compatibilityPortCheck, c);
        root.add(fields, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        javax.swing.JButton cancel = new javax.swing.JButton("Cancelar");
        javax.swing.JButton start = new javax.swing.JButton("Levantar servidor");
        buttons.add(cancel);
        buttons.add(start);
        root.add(buttons, BorderLayout.SOUTH);

        cancel.addActionListener(event -> {
            selectedConfig = null;
            dispose();
        });
        start.addActionListener(event -> accept(defaults));
        return root;
    }

    private void accept(GameConfig defaults) {
        try {
            int tcpPort = parsePort(tcpPortField.getText());
            int discoveryPort = parsePort(discoveryPortField.getText());
            String extraPorts = compatibilityPortCheck.isSelected() && discoveryPort != 5000 ? "5000" : "";
            selectedConfig = defaults
                    .withServerName(serverNameField.getText())
                    .withServerPort(tcpPort)
                    .withDiscoveryPort(discoveryPort)
                    .withExtraDiscoveryPorts(extraPorts);
            dispose();
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Configuracion invalida", JOptionPane.ERROR_MESSAGE);
        }
    }

    private int parsePort(String raw) {
        int port;
        try {
            port = Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("El puerto debe ser numerico.");
        }
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("El puerto debe estar entre 1 y 65535.");
        }
        return port;
    }
}
