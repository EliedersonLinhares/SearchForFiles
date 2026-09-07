package com.esl.searchforfiles.actions.videoConverter;


import com.esl.searchforfiles.actions.compressFile.ByteFormatter;
import com.esl.searchforfiles.configuration.UIConfig;
import com.esl.searchforfiles.model.FileInfo;
import com.esl.searchforfiles.ui.ResultsPanel;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class VideoConverterManager extends JFrame {

    private final List<FileInfo> items;
    private final VideoTableModel tableModel;
    private final VideoConversionService conversionService;
    private final ResultsPanel resultsPanel;

    private JTable table;
    private JComboBox<ConversionProfile.FormatPreset> formatCombo;
    private JComboBox<ConversionProfile.ResolutionOption> resolutionCombo;
    private JComboBox<ConversionProfile.QualityOption> qualityCombo;
    private JLabel destinationLabel;

    private JProgressBar overallProgressBar;
    private JProgressBar fileProgressBar;
    private JLabel statusLabel;
    private JLabel currentFileLabel;
    private JLabel remainingLabel;
    private JLabel filesLabel;

    private JButton convertButton;
    private JButton cancelButton;
    private JButton closeButton;
    private JComboBox<ConversionProfile.CpuLimitOption> cpuLimitCombo;

    private File destinationDirectory;

    private volatile boolean operationRunning;

    public VideoConverterManager(Window owner, List<FileInfo> items, ResultsPanel resultsPanel) {
        super("Converter — " + (items != null ? items.size() : 0) + " item(s)");

        this.items = items != null ? List.copyOf(items) : List.of();
        this.resultsPanel = resultsPanel;
        this.tableModel = new VideoTableModel(this.items);
        this.conversionService = new VideoConversionService();

        if (owner != null) owner.setEnabled(false);
        configureWindow(owner);
        buildUI();
        setVisible(true);
    }

    private void configureWindow(Window owner) {
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setSize(1300, 800);
        setMinimumSize(new Dimension(1200, 750));
        setLocationRelativeTo(owner);

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                closeManager();
            }
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                if (owner != null) { owner.setEnabled(true); owner.toFront(); }
                if (resultsPanel != null) {
                    resultsPanel.getFileExplorerSwing().getController().resumeAfterEdit();
                    resultsPanel.exitVideoConverterMode(); // ou um exitConvertMode() equivalente
                }
            }
        });
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

        JPanel destPanel = new JPanel(new BorderLayout(5, 0));
        destinationLabel = new JLabel("Destino: (mesma pasta dos arquivos originais)");
        destinationLabel.setFont(UIConfig.FONT_DEFAULT);
        destPanel.add(destinationLabel, BorderLayout.CENTER);

        JButton destinationButton = new JButton();
        destinationButton.setFont(UIConfig.FONT_DEFAULT);
        buttonConfiguration(destinationButton,120,UIConfig.FONT_DEFAULT_BOLD, "Destino...");
        destinationButton.addActionListener(e -> chooseDestination());
        destPanel.add(destinationButton, BorderLayout.EAST);
        main.add(destPanel, BorderLayout.NORTH);

        JPanel options = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));



        options.add(new JLabel("Formato:"));
        formatCombo = new JComboBox<>(ConversionProfile.FormatPreset.values());
        formatCombo.setFont(UIConfig.FONT_DEFAULT);
        formatCombo.addActionListener(e -> updateQualityAvailability());
        options.add(formatCombo);

        options.add(new JLabel("Resolução:"));
        resolutionCombo = new JComboBox<>(ConversionProfile.ResolutionOption.values());
        resolutionCombo.setFont(UIConfig.FONT_DEFAULT);
        options.add(resolutionCombo);

        options.add(new JLabel("Qualidade:"));
        qualityCombo = new JComboBox<>(ConversionProfile.QualityOption.values());
        qualityCombo.setFont(UIConfig.FONT_DEFAULT);
        qualityCombo.setSelectedItem(ConversionProfile.QualityOption.NORMAL);
        options.add(qualityCombo);

        options.add(new JLabel("Uso de CPU:"));
        cpuLimitCombo = new JComboBox<>(ConversionProfile.CpuLimitOption.values());
        cpuLimitCombo.setFont(UIConfig.FONT_DEFAULT);
        cpuLimitCombo.setToolTipText("Limitar o uso de CPU deixa a conversão mais lenta, "
                + "mas mantém o computador responsivo para outras tarefas durante o processo.");
        cpuLimitCombo.setSelectedItem(ConversionProfile.CpuLimitOption.MEDIUM); // padrão razoável
        options.add(cpuLimitCombo);

        main.add(options, BorderLayout.CENTER);
        return main;
    }

    /** Ao escolher "Copiar sem recodificar", resolução/qualidade não fazem sentido. */
    private void updateQualityAvailability() {
        boolean isCopy = formatCombo.getSelectedItem() == ConversionProfile.FormatPreset.COPY_REMUX;
        resolutionCombo.setEnabled(!isCopy);
        qualityCombo.setEnabled(!isCopy);
        if (isCopy) {
            resolutionCombo.setSelectedItem(ConversionProfile.ResolutionOption.ORIGINAL);
        }
        buildTopPanel_addValidationResetListeners();
    }
    private void buildTopPanel_addValidationResetListeners() {
        formatCombo.addActionListener(e -> tableModel.clearValidation());
        resolutionCombo.addActionListener(e -> tableModel.clearValidation());
        qualityCombo.addActionListener(e -> tableModel.clearValidation());
    }

    private JPanel buildTablePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        table = new JTable(tableModel);
        table.setRowHeight(40);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        table.getColumnModel().getColumn(0).setPreferredWidth(260); // Nome
        table.getColumnModel().getColumn(1).setPreferredWidth(110); // Tamanho
        table.getColumnModel().getColumn(2).setPreferredWidth(460); // Caminho
        table.getColumnModel().getColumn(3).setPreferredWidth(120); // Status
        table.getColumnModel().getColumn(4).setPreferredWidth(36);  // Aviso (ícone)
        table.getColumnModel().getColumn(4).setMaxWidth(36);
        table.getColumnModel().getColumn(4).setResizable(false);

        table.getColumnModel().getColumn(1).setCellRenderer(new SizeCellRenderer());
        table.getColumnModel().getColumn(4).setCellRenderer(new WarningCellRenderer());

    /* Tooltip dinâmico por linha, mostrando o motivo do aviso/erro ao passar o mouse
       sobre a coluna de ícone. Sobrescrevemos JTable diretamente para ter acesso
       à linha/coluna exatas do evento de mouse. */
        table = new JTable(tableModel) {
            @Override
            public String getToolTipText(java.awt.event.MouseEvent e) {
                int row = rowAtPoint(e.getPoint());
                int col = columnAtPoint(e.getPoint());
                if (col == 4 && row >= 0) {
                    return tableModel.getWarningTooltip(row);
                }
                return super.getToolTipText(e);
            }
        };
        table.setModel(tableModel);
        table.setRowHeight(40);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getColumnModel().getColumn(0).setPreferredWidth(260);
        table.getColumnModel().getColumn(1).setPreferredWidth(110);
        table.getColumnModel().getColumn(2).setPreferredWidth(460);
        table.getColumnModel().getColumn(3).setPreferredWidth(120);
        table.getColumnModel().getColumn(4).setPreferredWidth(36);
        table.getColumnModel().getColumn(4).setMaxWidth(36);
        table.getColumnModel().getColumn(4).setResizable(false);
        table.getColumnModel().getColumn(1).setCellRenderer(new SizeCellRenderer());
        table.getColumnModel().getColumn(4).setCellRenderer(new WarningCellRenderer());

        ToolTipManager.sharedInstance().registerComponent(table);

        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    private class WarningCellRenderer extends DefaultTableCellRenderer {

        public WarningCellRenderer() {
            setHorizontalAlignment(SwingConstants.CENTER);
            setFont(getFont().deriveFont(16f));
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focused, int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, focused, row, column);

            setText(value != null ? value.toString() : "");

            if (!selected) {
                if (tableModel.rowHasError(row)) {
                    setForeground(new Color(0xD32F2F)); // vermelho
                } else if (tableModel.rowHasWarning(row)) {
                    setForeground(new Color(0xF9A825)); // âmbar
                } else {
                    setForeground(table.getForeground());
                }
            }

            return this;
        }
    }

    private JPanel buildBottomPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10));

        JPanel info = new JPanel(new GridLayout(3, 1, 0, 2));
        statusLabel = new JLabel("Pronto.");
        statusLabel.setFont(UIConfig.FONT_DEFAULT);
        currentFileLabel = new JLabel("Arquivo: —");
        currentFileLabel.setFont(UIConfig.FONT_DEFAULT);
        remainingLabel = new JLabel("Tempo restante: —");
        remainingLabel.setFont(UIConfig.FONT_DEFAULT);
        filesLabel = new JLabel("Arquivos: —");
        filesLabel.setFont(UIConfig.FONT_DEFAULT);
        info.add(statusLabel);
        info.add(currentFileLabel);
        info.add(remainingLabel);
        panel.add(info, BorderLayout.NORTH);

        JPanel bars = new JPanel(new GridLayout(2, 1, 0, 4));
        fileProgressBar = new JProgressBar(0, 100);
        fileProgressBar.setFont(UIConfig.FONT_DEFAULT);
        fileProgressBar.setStringPainted(true);
        fileProgressBar.setString("Arquivo atual: 0%");

        overallProgressBar = new JProgressBar(0, 100);
        overallProgressBar.setFont(UIConfig.FONT_DEFAULT);
        overallProgressBar.setStringPainted(true);
        overallProgressBar.setString("Total: 0%");

        bars.add(fileProgressBar);
        bars.add(overallProgressBar);
        panel.add(bars, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        convertButton = new JButton();
        buttonConfiguration(convertButton,120,UIConfig.FONT_DEFAULT_BOLD, "🎬 Converter");
        convertButton.addActionListener(e -> startConversion());

        cancelButton = new JButton("Cancelar");
        cancelButton.setFont(UIConfig.FONT_DEFAULT);
        cancelButton.setEnabled(false);
        cancelButton.addActionListener(e -> cancelConversion());

        closeButton = new JButton("Fechar");
        closeButton.setFont(UIConfig.FONT_DEFAULT);
        closeButton.addActionListener(e -> closeManager());

        buttons.add(convertButton);
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
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            destinationDirectory = chooser.getSelectedFile();
            destinationLabel.setText("Destino: " + destinationDirectory.getAbsolutePath());
        }
    }

    public static void buttonConfiguration(JButton button, int width, Font font, String title) {
        // 1. Define o tamanho preferido (respeitando o BorderLayout/FlowLayout)
        int w = (width > 0) ? width : button.getPreferredSize().width;
        button.setText(title);
        button.setPreferredSize(new Dimension(w, button.getPreferredSize().height));
        button.setFont(font);
    }

    // ------------------------------------------------------------------
    // Execução da conversão
    // ------------------------------------------------------------------

    private void startConversion() {
        if (operationRunning) return;

        ConversionProfile.FormatPreset format = (ConversionProfile.FormatPreset) formatCombo.getSelectedItem();
        ConversionProfile.ResolutionOption resolution = (ConversionProfile.ResolutionOption) resolutionCombo.getSelectedItem();
        ConversionProfile.QualityOption quality = (ConversionProfile.QualityOption) qualityCombo.getSelectedItem();
        ConversionProfile.CpuLimitOption cpuLimit = (ConversionProfile.CpuLimitOption) cpuLimitCombo.getSelectedItem();

        ConversionProfile profile = ConversionProfile.from(format, resolution, quality, cpuLimit);

        statusLabel.setText("Verificando compatibilidade...");
        convertButton.setEnabled(false);

        new SwingWorker<FormatCompatibility.BatchValidationResult, Void>() {
            @Override
            protected FormatCompatibility.BatchValidationResult doInBackground() {
                return FormatCompatibility.validateBatch(items, profile);
            }

            @Override
            protected void done() {
                convertButton.setEnabled(true);
                try {
                    FormatCompatibility.BatchValidationResult validation = get();

                    tableModel.applyValidation(validation); // NOVO: marca as linhas na tabela

                    if (validation.hasAnyError()) {
                        showValidationErrors(validation);
                        statusLabel.setText("Corrija as opções ou remova os arquivos com erro (ícone ⛔).");
                        return;
                    }

                    if (validation.hasAnyWarning() && !confirmValidationWarnings(validation)) {
                        statusLabel.setText("Pronto.");
                        return;
                    }

                    proceedToEstimate(profile, quality);

                } catch (Exception e) {
                    statusLabel.setText("Pronto.");
                    JOptionPane.showMessageDialog(VideoConverterManager.this,
                            "Erro ao validar arquivos: " + e.getMessage(),
                            "Erro", JOptionPane.WARNING_MESSAGE);
                }
            }
        }.execute();
    }

    private void showValidationErrors(FormatCompatibility.BatchValidationResult validation) {
        StringBuilder sb = new StringBuilder("Não é possível converter com as opções selecionadas:\n\n");

        for (var entry : validation.perFile().entrySet()) {
            FormatCompatibility.ValidationResult r = entry.getValue();
            if (!r.isValid()) {
                String fileName = new File(entry.getKey()).getName();
                sb.append("• ").append(fileName).append(":\n");
                for (String err : r.errors()) {
                    sb.append("   - ").append(err).append("\n");
                }
            }
        }

        JOptionPane.showMessageDialog(this, sb.toString(), "Combinação inválida", JOptionPane.ERROR_MESSAGE);
    }

    private boolean confirmValidationWarnings(FormatCompatibility.BatchValidationResult validation) {
        StringBuilder sb = new StringBuilder("Alguns avisos antes de continuar:\n\n");

        // Deduplica avisos identicos entre arquivos diferentes para nao poluir o dialogo
        Set<String> uniqueWarnings = new LinkedHashSet<>();
        for (var entry : validation.perFile().entrySet()) {
            uniqueWarnings.addAll(entry.getValue().warnings());
        }
        for (String w : uniqueWarnings) {
            sb.append("• ").append(w).append("\n\n");
        }
        sb.append("Deseja continuar mesmo assim?");

        int answer = JOptionPane.showConfirmDialog(this, sb.toString(), "Avisos de compatibilidade",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        return answer == JOptionPane.YES_OPTION;
    }

    /** Continuação do fluxo original (estimativa de tamanho + confirmação + conversão). */
    private void proceedToEstimate(ConversionProfile profile, ConversionProfile.QualityOption quality) {
        statusLabel.setText("Calculando estimativa...");
        convertButton.setEnabled(false);

        new SwingWorker<VideoConversionService.EstimateResult, Void>() {
            @Override
            protected VideoConversionService.EstimateResult doInBackground() {
                return conversionService.estimate(items, profile, quality);
            }

            @Override
            protected void done() {
                convertButton.setEnabled(true);
                try {
                    VideoConversionService.EstimateResult est = get();
                    if (!confirmEstimateAndSpace(est, profile)) {
                        statusLabel.setText("Pronto.");
                        return;
                    }
                    tableModel.resetStatuses();
                    fileProgressBar.setValue(0);
                    overallProgressBar.setValue(0);
                    statusLabel.setText("Preparando conversão...");
                    operationRunning = true;
                    setControls(true);
                    runConversion(profile);
                } catch (Exception e) {
                    statusLabel.setText("Pronto.");
                    JOptionPane.showMessageDialog(VideoConverterManager.this,
                            "Não foi possível estimar o tamanho: " + e.getMessage(),
                            "Erro", JOptionPane.WARNING_MESSAGE);
                }
            }
        }.execute();
    }

    private boolean confirmEstimateAndSpace(VideoConversionService.EstimateResult est, ConversionProfile profile) {

        File destDir = destinationDirectory != null ? destinationDirectory : determineDefaultDestination();

        try {
            conversionService.checkDiskSpace(destDir, est.estimatedBytes());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Espaço insuficiente", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        double savedPct = est.estimatedSavedRatio() * 100;
        boolean isGrowth = est.estimatedBytes() > est.originalBytes();

        String sizeChangeText = isGrowth
                ? String.format("aprox. %.0f%% maior", (est.estimatedBytes() / (double) est.originalBytes() - 1) * 100)
                : String.format("aprox. %.0f%% de redução", savedPct);

        StringBuilder message = new StringBuilder();
        message.append(String.format(
                "Tamanho original: %s%nTamanho estimado após conversão: %s (%s)%n%n",
                ByteFormatter.format(est.originalBytes()),
                ByteFormatter.format(est.estimatedBytes()),
                sizeChangeText));

        if (est.hasIncompleteData()) {
            message.append(String.format(
                    "Atenção: %d de %d arquivo(s) não puderam ser analisados; " +
                            "a estimativa para eles assume tamanho igual ao original.%n%n",
                    est.failedProbes(), est.fileCount()));
        }

        message.append("Esta é uma estimativa aproximada — o CRF se adapta ao conteúdo do vídeo, " +
                "então o resultado real pode variar bastante.%n%nContinuar?".formatted());

        int answer = JOptionPane.showConfirmDialog(this, message.toString(), "Estimativa de conversão",
                JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
        return answer == JOptionPane.YES_OPTION;
    }

    private File determineDefaultDestination() {
        if (items.isEmpty()) return null;
        return new File(items.get(0).getPath()).getParentFile();
    }

    private void runConversion(ConversionProfile profile) {
        new SwingWorker<BatchConversionResult, ConversionProgress>() {

            @Override
            protected BatchConversionResult doInBackground() {
                return conversionService.convert(items, destinationDirectory, profile, this::publish);
            }

            @Override
            protected void process(List<ConversionProgress> chunks) {
                if (chunks.isEmpty()) return;
                updateProgress(chunks.get(chunks.size() - 1));
            }

            @Override
            protected void done() {
                BatchConversionResult result;
                try {
                    result = get();
                } catch (Exception e) {
                    result = new BatchConversionResult(List.of(), 0);
                }
                finishConversion(result);
            }
        }.execute();
    }

    private void updateProgress(ConversionProgress progress) {
        fileProgressBar.setValue(progress.getFilePercent());
        fileProgressBar.setString("Arquivo atual: " + progress.getFilePercent() + "%");

        overallProgressBar.setValue(progress.getOverallPercent());
        overallProgressBar.setString("Total: " + progress.getOverallPercent() + "%");

        currentFileLabel.setText("Arquivo: " + progress.getCurrentFile());
        statusLabel.setText(progress.getCurrentTask());
        filesLabel.setText("Arquivos: " + progress.getProcessedFiles() + " / " + progress.getTotalFiles());

        long remaining = progress.getEstimatedRemainingMillis();
        remainingLabel.setText("Tempo restante: " + (remaining < 0 ? "calculando..." : formatDuration(remaining)));

        tableModel.markProcessing(progress.getCurrentFile());
    }

    private void cancelConversion() {
        if (!operationRunning) return;
        cancelButton.setEnabled(false);
        statusLabel.setText("Cancelando...");
        conversionService.cancel();
    }

    private void finishConversion(BatchConversionResult result) {
        operationRunning = false;
        setControls(false);

        tableModel.applyResults(result);

        if (result.wasFullyCancelled()) {
            statusLabel.setText("Conversão cancelada.");
            JOptionPane.showMessageDialog(this, "A conversão foi cancelada.", "Cancelado",
                    JOptionPane.INFORMATION_MESSAGE);
        } else if (result.hasErrors()) {
            statusLabel.setText("Conversão concluída com erros.");
            JOptionPane.showMessageDialog(this,
                    String.format("Concluído: %d sucesso(s), %d erro(s).",
                            result.successCount(), result.errorCount()),
                    "Concluído com erros", JOptionPane.WARNING_MESSAGE);
        } else {
            statusLabel.setText("Conversão concluída.");
            JOptionPane.showMessageDialog(this,
                    String.format("Todos os %d arquivo(s) foram convertidos com sucesso.",
                            result.successCount()),
                    "Concluído", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void setControls(boolean running) {
        convertButton.setEnabled(!running);
        cancelButton.setEnabled(running);
        closeButton.setEnabled(!running);
        formatCombo.setEnabled(!running);
        resolutionCombo.setEnabled(!running);
        qualityCombo.setEnabled(!running);
    }

    private void closeManager() {
        if (operationRunning) {
            int result = JOptionPane.showConfirmDialog(this,
                    "Existe uma conversão em andamento.\n\nDeseja cancelar?",
                    "Conversão em andamento", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (result == JOptionPane.YES_OPTION) {
                conversionService.cancel();
            }
            return;
        }
        dispose();
    }

    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        seconds %= 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private class SizeCellRenderer extends DefaultTableCellRenderer {
        public SizeCellRenderer() { setHorizontalAlignment(SwingConstants.RIGHT); }
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focused, int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, focused, row, column);
            if (value instanceof Long size) setText(ByteFormatter.format(size));
            return this;
        }
    }
}