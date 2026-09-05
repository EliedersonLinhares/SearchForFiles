package com.esl.searchforfiles.actions.compressFile;

import com.esl.searchforfiles.configuration.UIConfig;
import com.esl.searchforfiles.model.FileInfo;
import com.esl.searchforfiles.ui.ResultsPanel;
import net.lingala.zip4j.model.enums.CompressionLevel;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
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
    private JCheckBox splitCheck;
    private JComboBox<SplitSizeOption> splitSizeCombo;
    private JCheckBox perTypeOptimizationCheck;
    private JTextField commentField;
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

        if (UIManager.getBoolean("laf.dark")) {
            normalColor = UIConfig.DARK_NORMAL_COLOR;
            hoverColor = UIConfig.DARK_HOVER_COLOR;
        } else {
            normalColor = UIConfig.LIGHT_NORMAL_COLOR;
            hoverColor = UIConfig.LIGHT_HOVER_COLOR;
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

    private String nomeDoArquivo(){
        String nomeDoArquivo = "arquivo.zip";

        // 1. Obtém a data e hora atual do sistema
        LocalDateTime agora = LocalDateTime.now();

        // 2. Define o formato: dd-MM-yyyy para data e HH:mm para hora (24h)
        DateTimeFormatter formatador = DateTimeFormatter.ofPattern("dd-MM-yyyy - HH:mm");

        // 3. Formata a data atual e junta com o nome
        String dataHoraFormatada = agora.format(formatador);
        return nomeDoArquivo + " - " + dataHoraFormatada + ".zip";
    }

    private JPanel buildTopPanel() {
        JPanel main = new JPanel(new BorderLayout(8, 8));

        main.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
        JPanel namePanel = new JPanel(new BorderLayout(5, 0));
        String name = nomeDoArquivo();
        JLabel nameLabel = new JLabel("Nome do ZIP:");
        nameLabel.setFont(UIConfig.FONT_DEFAULT);
        namePanel.add(nameLabel, BorderLayout.WEST);
        zipNameField = new JTextField("arquivo.zip");
        zipNameField.setFont(UIConfig.FONT_DEFAULT);

        namePanel.add(zipNameField, BorderLayout.CENTER);

        JButton destinationButton = new JButton();
        buttonConfiguration(destinationButton,120,UIConfig.FONT_DEFAULT_BOLD, "Destino...");
        destinationButton.addActionListener(e -> chooseDestination());

        namePanel.add(destinationButton, BorderLayout.EAST);
        main.add(namePanel, BorderLayout.NORTH);

        JPanel options = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        JLabel compressionLabel = new JLabel("Compressão");
        compressionLabel.setFont(UIConfig.FONT_DEFAULT);
        options.add(compressionLabel);

        compressionCombo = new JComboBox<>(CompressionOption.values());
        compressionCombo.setFont(UIConfig.FONT_DEFAULT);
        compressionCombo.setSelectedItem(CompressionOption.NORMAL);
        options.add(compressionCombo);

        smartStoreCheck = new JCheckBox("Otimizar arquivos já comprimidos");
        smartStoreCheck.setFont(UIConfig.FONT_DEFAULT);
        smartStoreCheck.setSelected(true);
        options.add(smartStoreCheck);

        preserveDirectoriesCheck = new JCheckBox("Preservar estrutura de pastas");
        preserveDirectoriesCheck.setFont(UIConfig.FONT_DEFAULT);
        preserveDirectoriesCheck.setSelected(true);
        options.add(preserveDirectoriesCheck);

        passwordCheck = new JCheckBox("Senha AES-256");
        passwordCheck.setFont(UIConfig.FONT_DEFAULT);
        options.add(passwordCheck);

        passwordField = new JPasswordField(12);
        passwordField.setFont(UIConfig.FONT_DEFAULT);
        passwordField.setEnabled(false);

        passwordCheck.addActionListener(e -> passwordField.setEnabled(passwordCheck.isSelected()));
        options.add(passwordField);

        splitCheck = new JCheckBox("Dividir em partes");
        splitCheck.setFont(UIConfig.FONT_DEFAULT);

        splitSizeCombo = new JComboBox<>(SplitSizeOption.values());
        splitSizeCombo.setFont(UIConfig.FONT_DEFAULT);
        splitSizeCombo.setSelectedItem(SplitSizeOption.MB_200);
        splitSizeCombo.setEnabled(false);

        perTypeOptimizationCheck = new JCheckBox("Otimizar nível por tipo de arquivo");
        perTypeOptimizationCheck.setFont(UIConfig.FONT_DEFAULT);

        commentField = new JTextField(16);
        commentField.setFont(UIConfig.FONT_DEFAULT);
        commentField.setToolTipText("Comentário salvo dentro do arquivo ZIP (opcional)");

        splitCheck.addActionListener(e -> {
            boolean split = splitCheck.isSelected();
            splitSizeCombo.setEnabled(split);

            // Limitações da API de split do Zip4j: sem otimização por arquivo nesse modo,
            // e a estrutura de pastas é sempre preservada.
            smartStoreCheck.setEnabled(!split);
            perTypeOptimizationCheck.setEnabled(!split);
            preserveDirectoriesCheck.setEnabled(!split);
            if (split) {
                preserveDirectoriesCheck.setSelected(true);
                String tip = "Não configurável em modo dividido (limitação da API de split do Zip4j).";
                preserveDirectoriesCheck.setToolTipText(tip);
                smartStoreCheck.setToolTipText(tip);
                perTypeOptimizationCheck.setToolTipText(tip);
            } else {
                preserveDirectoriesCheck.setToolTipText(null);
                smartStoreCheck.setToolTipText(null);
                perTypeOptimizationCheck.setToolTipText(null);
            }
        });

        options.add(splitCheck);
        options.add(splitSizeCombo);
        options.add(perTypeOptimizationCheck);

        JLabel commentLabel = new JLabel("Comentário:");
        commentLabel.setFont(UIConfig.FONT_DEFAULT);
        options.add(commentLabel);
        options.add(commentField);

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

        alignHeaders();

        panel.add(new JScrollPane(table), BorderLayout.CENTER);

        return panel;
    }

    private void alignHeaders() {
        table.getColumnModel().getColumn(2).setHeaderRenderer(rightAlignedHeader()); // Tamanho
        table.getColumnModel().getColumn(4).setHeaderRenderer(centerAlignedHeader()); // Status
    }

    private TableCellRenderer rightAlignedHeader() {
        JTableHeader header = table.getTableHeader();
        DefaultTableCellRenderer base = (DefaultTableCellRenderer) header.getDefaultRenderer();
        return new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int column) {
                Component c = base.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (c instanceof JLabel label) {
                    label.setHorizontalAlignment(SwingConstants.RIGHT);
                }
                return c;
            }
        };
    }

    private TableCellRenderer centerAlignedHeader() {
        JTableHeader header = table.getTableHeader();
        DefaultTableCellRenderer base = (DefaultTableCellRenderer) header.getDefaultRenderer();
        return new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int column) {
                Component c = base.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (c instanceof JLabel label) {
                    label.setHorizontalAlignment(SwingConstants.CENTER);
                }
                return c;
            }
        };
    }

    private JPanel buildBottomPanel() {

        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10));

        JPanel info = new JPanel(new GridLayout(5, 1, 0, 2));

        statusLabel = new JLabel("Pronto.");
        statusLabel.setFont(UIConfig.FONT_DEFAULT);
        currentFileLabel = new JLabel("Arquivo: —");
        currentFileLabel.setFont(UIConfig.FONT_DEFAULT);
        speedLabel = new JLabel("Velocidade: —");
        speedLabel.setFont(UIConfig.FONT_DEFAULT);
        remainingLabel = new JLabel("Tempo restante: —");
        remainingLabel.setFont(UIConfig.FONT_DEFAULT);
        sizeLabel = new JLabel("Tamanho: —");
        sizeLabel.setFont(UIConfig.FONT_DEFAULT);
        filesLabel = new JLabel("Arquivos: —");
        filesLabel.setFont(UIConfig.FONT_DEFAULT);

        info.add(statusLabel);
        info.add(currentFileLabel);
        info.add(speedLabel);
        info.add(remainingLabel);
        info.add(sizeLabel);
        info.add(filesLabel);

        panel.add(info, BorderLayout.NORTH);

        progressBar = new JProgressBar(0, 100);
        progressBar.setFont(UIConfig.FONT_DEFAULT);
        progressBar.setStringPainted(true);

        panel.add(progressBar, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        zipButton = new JButton();
        buttonConfiguration(zipButton,150,UIConfig.FONT_DEFAULT_BOLD, "✅ Compactar");

        zipButton.addActionListener(e -> startCompression());
        cancelButton = new JButton();
        buttonConfiguration(cancelButton,100,UIConfig.FONT_DEFAULT, "Cancelar");
        cancelButton.setEnabled(false);
        cancelButton.addActionListener(e -> cancelCompression());

        closeButton = new JButton();
        buttonConfiguration(closeButton,100,UIConfig.FONT_DEFAULT, "Fechar");
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


    public static void buttonConfiguration(JButton button, int width, Font font, String title) {
        // 1. Define o tamanho preferido (respeitando o BorderLayout/FlowLayout)
        int w = (width > 0) ? width : button.getPreferredSize().width;
        button.setText(title);
        button.setPreferredSize(new Dimension(w, button.getPreferredSize().height));
        button.setFont(font);
    }
    public static void labelConfiguration(JLabel label, Font font, String title) {
        label.setText(title);
        label.setFont(font);
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

        Map<String, CompressionLevel> overrides = perTypeOptimizationCheck.isSelected()
                ? CompressionProfile.defaultLevelOverrides() : Map.of();

        boolean split = splitCheck.isSelected();
        long splitSize = split ? ((SplitSizeOption) splitSizeCombo.getSelectedItem()).bytes : 0;
        String comment = commentField.getText();

        CompressionProfile profile = new CompressionProfile(level, smartStoreCheck.isSelected(),
                passwordCheck.isSelected(), password, split, splitSize, comment, overrides);

        boolean preserveDirectories = preserveDirectoriesCheck.isSelected();

        statusLabel.setText("Calculando estimativa...");
        zipButton.setEnabled(false);

        new SwingWorker<ZipCompressionService.EstimateResult, Void>() {
            @Override
            protected ZipCompressionService.EstimateResult doInBackground() throws Exception {
                return zipService.estimate(items, destination, profile, preserveDirectories);
            }
            @Override
            protected void done() {
                zipButton.setEnabled(true);
                try {
                    ZipCompressionService.EstimateResult est = get();
                    if (!confirmEstimateAndSpace(est, destination)) {
                        statusLabel.setText("Pronto.");
                        return;
                    }
                    tableModel.resetStatuses();
                    progressBar.setValue(0);
                    progressBar.setString("0%");
                    statusLabel.setText("Preparando compactação...");
                    operationRunning = true;
                    setControls(true);
                    executeCompression(destination, profile, preserveDirectories);
                } catch (Exception e) {
                    statusLabel.setText("Pronto.");
                    showWarning("Não foi possível estimar o tamanho: " + e.getMessage());
                }
            }
        }.execute();
    }

    private boolean confirmEstimateAndSpace(ZipCompressionService.EstimateResult est, File destination) {
        try {
            zipService.checkDiskSpace(destination.getAbsoluteFile().getParentFile(), est.estimatedCompressedBytes());
        } catch (IOException e) {
            showWarning(e.getMessage());
            return false;
        }

        double savedPct = est.estimatedSavedRatio() * 100;
        String message = String.format(
                "Tamanho original: %s%nTamanho estimado após compactação: %s (aprox. %.0f%% de redução)%n%n" +
                        "Esta é uma estimativa aproximada; o resultado real pode variar conforme o conteúdo dos arquivos.%n%n" +
                        "Continuar?",
                ByteFormatter.format(est.originalBytes()),
                ByteFormatter.format(est.estimatedCompressedBytes()),
                savedPct);

        int answer = JOptionPane.showConfirmDialog(this, message, "Estimativa de compactação",
                JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
        return answer == JOptionPane.YES_OPTION;
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
        speedLabel.setText("Velocidade: " + ByteFormatter.format(progress.getBytesPerSecond()) + "/s");
        remainingLabel.setText("Tempo restante: " + formatDuration(progress.getEstimatedRemainingMillis()));
        sizeLabel.setText("Tamanho: " + ByteFormatter.format(progress.getProcessedBytes()) + " / " + ByteFormatter.format(progress.getTotalBytes()));
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

            String message = "Compactação concluída.\n\n" +
                    "Arquivo:\n" + result.getZipFile().getAbsolutePath() + "\n\n" +
                    "Tamanho:\n" + ByteFormatter.format(result.getZipSize()) + "\n\n" +
                    "Arquivos:\n" + result.getProcessedFiles() + "\n\n" +
                    "Tempo:\n" + formatDuration(result.getElapsedMillis());

            Object[] optionsArr = {"OK", "Abrir pasta de destino"};
            int choice = JOptionPane.showOptionDialog(this, message, "Concluído",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, optionsArr, optionsArr[0]);

            if (choice == 1) {
                openDestinationFolder(result.getZipFile());
            }

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

    private void openDestinationFolder(File zipFile) {
        File folder = zipFile.getParentFile();
        if (folder == null || !folder.exists()) {
            showWarning("Não foi possível localizar a pasta de destino.");
            return;
        }
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(folder);
            } else {
                showWarning("Abrir pastas não é suportado neste sistema.");
            }
        } catch (IOException e) {
            showWarning("Não foi possível abrir a pasta: " + e.getMessage());
        }
    }

    private void setControls(boolean running) {

        zipButton.setEnabled(!running);
        cancelButton.setEnabled(running);
        closeButton.setEnabled(!running);
        zipNameField.setEnabled(!running);
        compressionCombo.setEnabled(!running);
        smartStoreCheck.setEnabled(!running && !splitCheck.isSelected());
        preserveDirectoriesCheck.setEnabled(!running && !splitCheck.isSelected());
        passwordCheck.setEnabled(!running);
        passwordField.setEnabled(!running && passwordCheck.isSelected());
        splitCheck.setEnabled(!running);
        splitSizeCombo.setEnabled(!running && splitCheck.isSelected());
        perTypeOptimizationCheck.setEnabled(!running && !splitCheck.isSelected());
        commentField.setEnabled(!running);
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

    public enum SplitSizeOption {
        MB_100("100 MB", 100L * 1024 * 1024),
        MB_200("200 MB", 200L * 1024 * 1024),
        MB_500("500 MB", 500L * 1024 * 1024),
        GB_1("1 GB", 1024L * 1024 * 1024);

        final String label;
        final long bytes;
        SplitSizeOption(String label, long bytes) {
            this.label = label;
            this.bytes = bytes;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private class SizeCellRenderer extends DefaultTableCellRenderer {

        public SizeCellRenderer() {
            setHorizontalAlignment(SwingConstants.RIGHT);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focused, int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, focused, row, column);
            if (value instanceof Long size) {
                setText(ByteFormatter.format(size));
            }
            return this;
        }
    }

    private class StatusCellRenderer extends DefaultTableCellRenderer {

        public StatusCellRenderer() {
            setHorizontalAlignment(SwingConstants.CENTER);
        }

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