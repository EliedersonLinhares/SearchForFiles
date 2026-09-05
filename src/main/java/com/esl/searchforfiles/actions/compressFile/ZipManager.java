package com.esl.searchforfiles.actions.compressFile;

import com.esl.searchforfiles.configuration.UIConfig;
import com.esl.searchforfiles.model.FileInfo;
import com.esl.searchforfiles.ui.ResultsPanel;
import net.lingala.zip4j.model.enums.CompressionLevel;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.io.File;
import java.util.List;
import java.util.Optional;

public class ZipManager extends JFrame {

    private final List<FileInfo> items;
    private final ZipTableModel tableModel;
    private final ZipCompressionService zipService;
    private final ResultsPanel resultsPanel;
    private JTable table;
    private JTextField zipNameField;
    private JComboBox<CompressionOption> compressionCombo;
    private JCheckBox smartStoreCheck;
    private JCheckBox preserveDirectoriesCheck;
    private JCheckBox passwordCheck;
    private JPasswordField passwordField;
    private JProgressBar progressBar;
    private JLabel statusLabel;
    private JLabel currentFileLabel;
    private JLabel speedLabel;
    private JLabel remainingLabel;
    private JLabel sizeLabel;
    private JLabel filesLabel;
    private JButton zipButton;
    private JButton cancelButton;
    private JButton closeButton;
    private File destinationDirectory;

    private Color normalColor;
    private Color hoverColor;
    private Color borderColor;

    private volatile boolean operationRunning;
    private volatile boolean cancelRequested;

    public ZipManager(Window owner, List<FileInfo> items, ResultsPanel resultsPanel) {

        super("Compactar — " + (items != null ? items.size() : 0) + " item(s)");

        this.items = items != null ? List.copyOf(items) : List.of();
        this.resultsPanel = resultsPanel;
        this.tableModel = new ZipTableModel(this.items);

        this.zipService = new ZipCompressionService();
        if (owner != null) owner.setEnabled(false);
        configureWindow(owner);

        buildUI();

        setVisible(true);
    }

    private void configureWindow(Window owner) {

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setSize(1200, 780);
        setMinimumSize(new Dimension(900, 650));
        setLocationRelativeTo(owner);


        addWindowListener(new java.awt.event.WindowAdapter() {
                        @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                closeManager();
            }
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                if (owner != null) owner.setEnabled(true);
                owner.toFront();

                // Libera flag E dispara refresh em um único ponto controlado
                resultsPanel.getFileExplorerSwing()
                        .getController()
                        .resumeAfterEdit();

                resultsPanel.exitCompressMode();
            }
        });

        resultsPanel.getFileExplorerSwing().getThemeManager().addThemeChangeListener(() ->
                SwingUtilities.invokeLater(this::refreshColors)
        );
    }

    private void refreshColors() {

        if(UIManager.getBoolean("laf.dark")){
            normalColor = UIConfig.DARK_NORMAL_COLOR;
            hoverColor  = UIConfig.DARK_HOVER_COLOR;
        }else {
            normalColor = UIConfig.LIGHT_NORMAL_COLOR;
            hoverColor  = UIConfig.LIGHT_HOVER_COLOR;
        }

        // borderColor geralmente pode vir direto do tema
        borderColor = Optional.ofNullable(UIConfig.accent())
                .orElse(UIConfig.SELECTED_BORDER);
    }

    private void buildUI() {
        setLayout(new BorderLayout());
        add(buildTopPanel(), BorderLayout.NORTH);
        add(buildTablePanel(), BorderLayout.CENTER);
        add(buildBottomPanel(), BorderLayout.SOUTH);
    }

    private JPanel buildTopPanel() {
        JPanel main = new JPanel(new BorderLayout(8, 8));

        main.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
        JPanel namePanel = new JPanel(new BorderLayout(5, 0));
        namePanel.add(new JLabel("Nome do ZIP:"), BorderLayout.WEST);
        zipNameField = new JTextField("arquivo.zip");

        namePanel.add(zipNameField, BorderLayout.CENTER);

        JButton destinationButton = new JButton("Destino...");
        destinationButton.addActionListener(e -> chooseDestination());

        namePanel.add(destinationButton, BorderLayout.EAST);
        main.add(namePanel, BorderLayout.NORTH);

        JPanel options = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        options.add(new JLabel("Compressão:"));

        compressionCombo = new JComboBox<>(CompressionOption.values());
        compressionCombo.setSelectedItem(CompressionOption.NORMAL);
        options.add(compressionCombo);

        smartStoreCheck = new JCheckBox("Otimizar arquivos já comprimidos");
        smartStoreCheck.setSelected(true);
        options.add(smartStoreCheck);

        preserveDirectoriesCheck = new JCheckBox("Preservar estrutura de pastas");
        preserveDirectoriesCheck.setSelected(true);
        options.add(preserveDirectoriesCheck);

        passwordCheck = new JCheckBox("Senha AES-256");
        options.add(passwordCheck);

        passwordField = new JPasswordField(12);
        passwordField.setEnabled(false);

        passwordCheck.addActionListener(e -> passwordField.setEnabled(passwordCheck.isSelected()));
        options.add(passwordField);

        main.add(options, BorderLayout.CENTER);

        return main;
    }

    private JPanel buildTablePanel() {

        JPanel panel = new JPanel(new BorderLayout());

        table = new JTable(tableModel);
        table.setRowHeight(44);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getColumnModel().getColumn(0).setPreferredWidth(50);
        table.getColumnModel().getColumn(1).setPreferredWidth(240);
        table.getColumnModel().getColumn(2).setPreferredWidth(120);
        table.getColumnModel().getColumn(3).setPreferredWidth(500);
        table.getColumnModel().getColumn(4).setPreferredWidth(130);
        table.getColumnModel().getColumn(2).setCellRenderer(new SizeCellRenderer());
        table.getColumnModel().getColumn(4).setCellRenderer(new StatusCellRenderer());
        panel.add(new JScrollPane(table), BorderLayout.CENTER);

        return panel;
    }

    private JPanel buildBottomPanel() {

        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10));

        JPanel info = new JPanel(new GridLayout(5, 1, 0, 2));

        statusLabel = new JLabel("Pronto.");
        currentFileLabel = new JLabel("Arquivo: —");
        speedLabel = new JLabel("Velocidade: —");
        remainingLabel = new JLabel("Tempo restante: —");
        sizeLabel = new JLabel("Tamanho: —");
        filesLabel = new JLabel("Arquivos: —");

        info.add(statusLabel);
        info.add(currentFileLabel);
        info.add(speedLabel);
        info.add(remainingLabel);
        info.add(sizeLabel);
        info.add(filesLabel);

        panel.add(info, BorderLayout.NORTH);

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);

        panel.add(progressBar, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        zipButton = new JButton("Compactar");

        zipButton.addActionListener(e -> startCompression());
        cancelButton = new JButton("Cancelar");
        cancelButton.setEnabled(false);
        cancelButton.addActionListener(e -> cancelCompression());

        closeButton = new JButton("Fechar");
        closeButton.addActionListener(e -> closeManager());

        buttons.add(zipButton);
        buttons.add(cancelButton);
        buttons.add(closeButton);

        panel.add(buttons, BorderLayout.SOUTH);

        return panel;
    }

    private void chooseDestination() {

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Escolher pasta de destino");
        chooser.setCurrentDirectory(destinationDirectory);
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

        int result = chooser.showOpenDialog(this);

        if (result == JFileChooser.APPROVE_OPTION) {
            destinationDirectory = chooser.getSelectedFile();
            statusLabel.setText("Destino: " + destinationDirectory.getAbsolutePath());
        }
    }

    private void startCompression() {
        if (operationRunning) {
            return;
        }

        String zipName = zipNameField.getText().trim();

        if (zipName.isBlank()) {
            showWarning("Informe o nome do arquivo ZIP.");
            return;
        }
        if (!zipName.toLowerCase().endsWith(".zip")) {
            zipName += ".zip";
        }
        if (destinationDirectory == null) {
            destinationDirectory = determineDefaultDestination();
        }
        if (destinationDirectory == null) {
            chooseDestination();
            if (destinationDirectory == null) {
                return;
            }
        }

        File destination = new File(destinationDirectory, zipName);

        if (destination.exists()) {
            int answer = JOptionPane.showConfirmDialog(this, "O arquivo já existe:\n\n" +
                    destination.getAbsolutePath() + "\n\n" + "Deseja substituir?", "Arquivo existente",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (answer != JOptionPane.YES_OPTION) {

                return;
            }
        }

        char[] password = passwordCheck.isSelected() ? passwordField.getPassword() : null;
        if (passwordCheck.isSelected() && (password == null || password.length == 0)) {
            showWarning("Informe uma senha.");
            return;
        }

        CompressionOption option = (CompressionOption) compressionCombo.getSelectedItem();
        CompressionLevel level = option != null ? option.level : CompressionLevel.NORMAL;
        CompressionProfile profile = new CompressionProfile(level, smartStoreCheck.isSelected(), passwordCheck.isSelected(), password);

        tableModel.resetStatuses();
        progressBar.setValue(0);
        progressBar.setString("0%");
        statusLabel.setText("Preparando compactação...");

        operationRunning = true;
        setControls(true);
        executeCompression(destination, profile, preserveDirectoriesCheck.isSelected());
    }

    private void executeCompression(File destination, CompressionProfile profile, boolean preserveDirectories) {

        new SwingWorker<ZipResult, ZipProgress>() {

            @Override
            protected ZipResult doInBackground() {

                return zipService.compress(items, destination, profile, preserveDirectories, this::publish);
            }
            @Override
            protected void process(List<ZipProgress> chunks) {

                if (chunks.isEmpty()) {
                    return;
                }
                ZipProgress progress = chunks.get(chunks.size() - 1);
                updateProgress(progress);
            }
            @Override
            protected void done() {

                ZipResult result;
                try {
                    result = get();
                } catch (Exception e) {

                    result = ZipResult.error(destination, 0, 0, 0, 0, 0, e);
                }
                finishCompression(result);
            }
        }.execute();
    }

    private void updateProgress(ZipProgress progress) {

        progressBar.setValue(progress.getPercent());
        progressBar.setString(progress.getPercent() + "%");
        currentFileLabel.setText("Arquivo: " + progress.getCurrentFile());
        speedLabel.setText("Velocidade: " + formatBytes(progress.getBytesPerSecond()) + "/s");
        remainingLabel.setText("Tempo restante: " + formatDuration(progress.getEstimatedRemainingMillis()));
        sizeLabel.setText("Tamanho: " + formatBytes(progress.getProcessedBytes()) + " / " + formatBytes(progress.getTotalBytes()));
        filesLabel.setText("Arquivos: " + progress.getProcessedFiles() + " / " + progress.getTotalFiles());
        statusLabel.setText(progress.getCurrentTask());
    }

    private void cancelCompression() {

        if (!operationRunning) {
            return;
        }

        cancelButton.setEnabled(false);
        statusLabel.setText("Cancelando...");
        zipService.cancel();
    }

    private void finishCompression(ZipResult result) {

        operationRunning = false;
        setControls(false);

        if (result.isSuccess()) {
            progressBar.setValue(100);
            progressBar.setString("100%");
            statusLabel.setText("Compactação concluída.");

            JOptionPane.showMessageDialog(this, "Compactação concluída.\n\n" + "Arquivo:\n" + result.getZipFile().getAbsolutePath() + "\n\n" + "Tamanho:\n" + formatBytes(result.getZipSize()) + "\n\n" + "Arquivos:\n" + result.getProcessedFiles() + "\n\n" + "Tempo:\n" + formatDuration(result.getElapsedMillis()), "Concluído", JOptionPane.INFORMATION_MESSAGE);

        } else if (result.isCancelled()) {
            statusLabel.setText("Compactação cancelada.");
            JOptionPane.showMessageDialog(this, "A compactação foi cancelada.\n\n" + "O arquivo ZIP incompleto " + "foi removido.", "Cancelado", JOptionPane.INFORMATION_MESSAGE);

        } else {
            statusLabel.setText("Erro na compactação.");
            Throwable error = result.getError();
            String message = error != null ? error.getMessage() : "Erro desconhecido.";
            JOptionPane.showMessageDialog(this, "Não foi possível criar " + "o arquivo ZIP.\n\n" + message, "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void setControls(boolean running) {

        zipButton.setEnabled(!running);
        cancelButton.setEnabled(running);
        closeButton.setEnabled(!running);
        zipNameField.setEnabled(!running);
        compressionCombo.setEnabled(!running);
        smartStoreCheck.setEnabled(!running);
        preserveDirectoriesCheck.setEnabled(!running);
        passwordCheck.setEnabled(!running);
        passwordField.setEnabled(!running && passwordCheck.isSelected());
    }

    private File determineDefaultDestination() {
        if (items.isEmpty()) {
            return null;
        }
        File first = new File(items.get(0).getPath());
        if (first.isDirectory()) {
            return first.getParentFile();
        }
        return first.getParentFile();
    }

    private void closeManager() {
        if (operationRunning) {
            int result = JOptionPane.showConfirmDialog(this,
                    "Existe uma compactação " + "em andamento.\n\n" + "Deseja cancelar?",
                    "Compactação em andamento", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

            if (result == JOptionPane.YES_OPTION) {
                zipService.cancel();
            }
            return;
        }
        dispose();
    }

    private void showWarning(String message) {
        JOptionPane.showMessageDialog(this, message, "Aviso", JOptionPane.WARNING_MESSAGE);
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double value = bytes;
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unit = 0;
        while (value >= 1024 && unit < units.length - 1) {
            value /= 1024.0;
            unit++;
        }
        return String.format("%.2f %s", value, units[unit]);
    }

    private String formatDuration(long millis) {
        if (millis < 0) {
            return "calculando...";
        }

        long seconds = millis / 1000;
        long hours = seconds / 3600;
        seconds %= 3600;
        long minutes = seconds / 60;
        seconds %= 60;

        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format("%02d:%02d", minutes, seconds);
    }

    public enum CompressionOption {
        FAST("Rápida", CompressionLevel.FAST),
        NORMAL("Normal", CompressionLevel.NORMAL),
        MAXIMUM("Máxima", CompressionLevel.MAXIMUM);
        private final String label;
        private final CompressionLevel level;
        CompressionOption(String label, CompressionLevel level) {
            this.label = label;
            this.level = level;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private class SizeCellRenderer extends DefaultTableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focused, int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, focused, row, column);
            if (value instanceof Long size) {
                setText(formatBytes(size));
            }
            return this;
        }
    }

    private class StatusCellRenderer extends DefaultTableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focused, int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, focused, row, column);

            if (!selected) {
                ZipTableModel.Status status = tableModel.getStatus(row);
                setText(switch (status) {
                    case WAITING -> "Aguardando";
                    case PROCESSING -> "Compactando...";
                    case SUCCESS -> "✓ Concluído";
                    case ERROR -> "✗ Erro";
                    case SKIPPED -> "Ignorado";
                });
            }

            return this;
        }
    }

}