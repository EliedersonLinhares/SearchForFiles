package com.esl.searchforfiles.database;

import com.esl.searchforfiles.configuration.ConfigPanelBase;
import com.esl.searchforfiles.localization.I18N;
import com.esl.searchforfiles.ui.FileExplorerSwing;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class DatabaseConfigPanel extends ConfigPanelBase implements I18N.LanguageChangeListener {

    private final DatabaseManager db;
    private final FileExplorerSwing mainFrame;

    private final JLabel totalFilesLabel   = makeValueLabel("...");
    private final JLabel totalFoldersLabel = makeValueLabel("...");
    private final JLabel lastIndexedLabel  = makeValueLabel("...");
    private final JLabel lastModifiedLabel = makeValueLabel("...");
    private final JLabel totalSizeLabel    = makeValueLabel("...");
    private final JLabel dbSizeLabel       = makeValueLabel("...");
    private final JLabel tagsLabel         = makeValueLabel("...");
    private final JLabel extensionsLabel   = makeValueLabel("...");
    private TitledBorder statsSectionBorder;
    private TitledBorder tagSectionBorder;
    private TitledBorder extensionSectionBorder;
    private TitledBorder databaseSectionBorder;

    private JLabel totalRowsLabel;
    private JLabel totalFoldersRowsLabel;
    private JLabel totalSizeRowsLabel;
    private JLabel lastIndexedRowsLabel;
    private JLabel lastModifiedRowsLabel;
    private JLabel tagRowsLabel;
    private JLabel extensionRowsLabel;
    private JLabel databaseRowsLabel;
    private JLabel localizationRowsLabel;
    private JLabel localizationHintLabel;
    private JButton btnRefresh;
    private JButton btnExcludeIndexedFolder;

    private JLabel valueLabelText;
    private JLabel valueLabelThirdPart;

    public DatabaseConfigPanel(DatabaseManager db, FileExplorerSwing mainFrame) {
        this.db = db;
        this.mainFrame = mainFrame;

        JPanel stats = addSection(I18N.get("databaseConfigPanel.JPanel.stats"));
        statsSectionBorder = (TitledBorder) stats.getBorder();

        totalRowsLabel = addRowLabel(stats, I18N.get("databaseConfigPanel.rowTotalLabel.text"), totalFilesLabel);
        totalFoldersRowsLabel = addRowLabel(stats, I18N.get("databaseConfigPanel.totalFoldersRowsLabel"),   totalFoldersLabel);
        totalSizeRowsLabel = addRowLabel(stats, I18N.get("databaseConfigPanel.totalSizeRowsLabel"),      totalSizeLabel);
        lastIndexedRowsLabel = addRowLabel(stats, I18N.get("databaseConfigPanel.lastIndexedRowsLabel"),   lastIndexedLabel);
        lastModifiedRowsLabel = addRowLabel(stats, I18N.get("databaseConfigPanel.lastModifiedRowsLabel"),     lastModifiedLabel);

        JPanel tagsSection = addSection(I18N.get("databaseConfigPanel.JPanel.tagsSection"));
        tagSectionBorder = (TitledBorder) tagsSection.getBorder();
        tagRowsLabel = addRowLabel(tagsSection, I18N.get("databaseConfigPanel.tagRowsLabel"), tagsLabel);

        JPanel extSection = addSection(I18N.get("databaseConfigPanel.JPanel.extSection"));
        extensionSectionBorder = (TitledBorder) extSection.getBorder();
        extensionRowsLabel = addRowLabel(extSection, I18N.get("databaseConfigPanel.extensionRowsLabel"), extensionsLabel);

        JPanel dbSection = addSection(I18N.get("databaseConfigPanel.JPanel.dbSection"));
        databaseSectionBorder = (TitledBorder) dbSection.getBorder();
        databaseRowsLabel = addRowLabel(dbSection, I18N.get("databaseConfigPanel.databaseRowsLabel"), dbSizeLabel);
        localizationHintLabel = makeHint( DatabaseManager.DB_PATH.toAbsolutePath().toString());
        localizationRowsLabel = addRowLabel(dbSection, I18N.get("databaseConfigPanel.localizationRowsLabel") + " " ,localizationHintLabel);

        btnRefresh = makeBtn(I18N.get("databaseConfigPanel.btnRefresh"));
        btnRefresh.addActionListener(e -> refresh());
        addButtons(btnRefresh);

        btnExcludeIndexedFolder = makeBtn(I18N.get("databaseConfigPanel.btnExcludeIndexedFolder"));
        btnExcludeIndexedFolder.setEnabled(db.isFolderIndexed(mainFrame.getSelectedPath()));
        btnExcludeIndexedFolder.addActionListener(e -> {

            int choice = JOptionPane.showConfirmDialog(
                    this,
                     I18N.get("databaseConfigPanel.btnExcludeIndexedFolder.JOptionPane.showConfirmDialog.text") + "\n" + mainFrame.getSelectedPath(),
                    I18N.get("databaseConfigPanel.btnExcludeIndexedFolder.JOptionPane.showConfirmDialog.title"),
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );

            // Handle the user's selection
            if (choice == JOptionPane.YES_OPTION) {
                try {
                    removeFolder(mainFrame.getSelectedPath());
                } catch (SQLException ex) {
                    throw new RuntimeException(ex);
                }

                // Converte a String para um objeto Path
                Path path = Paths.get(mainFrame.getSelectedPath());
                // Pega o caminho do diretório pai
                Path pai = path.getParent();
                mainFrame.navigateTo(pai.toString(), true);

            } else if (choice == JOptionPane.NO_OPTION) {
                System.out.println("User chose NO. Action canceled.");
            } else if (choice == JOptionPane.CLOSED_OPTION) {
                System.out.println("User closed the dialog box without choosing.");
            }

        });
        addButtons(btnExcludeIndexedFolder);

        updateTexts();
        I18N.addLanguageChangeListener(this);

        refresh();

    }

    public void refresh() {
        new SwingWorker<Void, Void>() {
            long files, folders, totalBytes, dbBytes;
            LocalDateTime lastIndexed, lastModified;
            long[] tagStats;
            List<Map.Entry<String, Long>> exts;

            @Override
            protected Void doInBackground() throws Exception {
                files        = db.getDatabaseInformation().getTotalFiles();
                folders      = db.getDatabaseInformation().getTotalFolders();
                totalBytes   = db.getDatabaseInformation().getTotalIndexedSizeBytes();
                dbBytes      = db.getDatabaseInformation().getDatabaseFileSizeBytes();
                lastIndexed  = db.getDatabaseInformation().getLastIndexedAt();
                lastModified = db.getDatabaseInformation().getLastModifiedFile();
                tagStats     = db.getDatabaseInformation().getTagStats();
                exts         = db.getDatabaseInformation().getTopExtensions(5);
                return null;
            }

            @Override
            protected void done() {
                setValueLabelText(totalFilesLabel,   String.format("%,d", files));
                setValueLabelText(totalFoldersLabel, String.format("%,d", folders));
                setValueLabelText(totalSizeLabel,    DatabaseInformation.formatBytes(totalBytes));
                setValueLabelText(dbSizeLabel,       DatabaseInformation.formatBytes(dbBytes));
                setValueLabelText(lastIndexedLabel,  DatabaseInformation.formatDateTime(lastIndexed));
                setValueLabelText(lastModifiedLabel, DatabaseInformation.formatDateTime(lastModified));

                valueLabelThirdPart = new JLabel(I18N.get("databaseConfigPanel.valueLabelThirdPart")) ;
                valueLabelText =  setValueLabel(tagsLabel,
                        tagStats[0] + " tags · " + tagStats[1] + " " + valueLabelThirdPart.getText());

                String extText = exts.stream()
                        .map(e -> "." + e.getKey() + " (" + e.getValue() + ")")
                        .collect(Collectors.joining(" · "));
                setValueLabelText(extensionsLabel, extText.isEmpty() ? "—" : extText);
            }
        }.execute();
    }

    public void removeFolder(String folderPath) throws  SQLException {
        // 1. Descobre a política com que foi indexada (com fallback de inferência para dados legados)
        boolean includeSubfolders = db.getIncludeSubfoldersPolicy(folderPath);

        // 2. Para o monitoramento ANTES de remover do banco
        //    (evita que um evento de FS dispare reindexação durante a remoção)
        mainFrame.getController().getMonitoringService().unregisterDirectory(folderPath);

        // 3. Remove do banco (file_index, indexed_folders, tags, identidades órfãs)
        int removed = db.removeIndexedFolder(folderPath, includeSubfolders);

        // 4. Limpa cache de busca
        mainFrame.getController().getMonitoringService().getSearchService().clearCache();

        // 5. Notifica a UI para atualizar (reaproveitando o callback já existente)
        // Se você tiver acesso ao mesmo callback do MonitoringService, pode reusar:
        // fileChangeCallback.onFileChanged();

        System.out.println("✅ Pasta removida completamente: " + folderPath
                + " (" + removed + " registros no índice)");
    }

    protected void updateTexts() {

        refresh();

        if(statsSectionBorder != null) statsSectionBorder.setTitle(I18N.get("databaseConfigPanel.JPanel.stats"));
        if(tagSectionBorder != null) tagSectionBorder.setTitle(I18N.get("databaseConfigPanel.JPanel.tagsSection"));
        if(extensionSectionBorder != null) extensionSectionBorder.setTitle(I18N.get("databaseConfigPanel.JPanel.extSection"));
        if(databaseSectionBorder != null) databaseSectionBorder.setTitle(I18N.get("databaseConfigPanel.JPanel.dbSection"));

        if (totalRowsLabel != null)  totalRowsLabel.setText(I18N.get("databaseConfigPanel.rowTotalLabel.text"));
        if(totalFoldersRowsLabel != null) totalFoldersRowsLabel.setText(I18N.get("databaseConfigPanel.totalFoldersRowsLabel"));
        if(totalSizeRowsLabel != null) totalSizeRowsLabel.setText(I18N.get("databaseConfigPanel.totalSizeRowsLabel"));
        if(lastIndexedLabel != null) lastIndexedLabel.setText(I18N.get("databaseConfigPanel.lastIndexedRowsLabel"));
        if(lastModifiedLabel != null) lastModifiedLabel.setText(I18N.get("databaseConfigPanel.lastModifiedRowsLabel"));
        if(tagRowsLabel != null) tagRowsLabel.setText(I18N.get("databaseConfigPanel.tagRowsLabel"));
        if(extensionRowsLabel != null) extensionRowsLabel.setText(I18N.get("databaseConfigPanel.extensionRowsLabel"));
        if(databaseRowsLabel != null) databaseRowsLabel.setText(I18N.get("databaseConfigPanel.databaseRowsLabel"));
        if(localizationRowsLabel != null) localizationRowsLabel.setText(I18N.get("databaseConfigPanel.localizationRowsLabel"));
        if(btnRefresh != null) btnRefresh.setText(I18N.get("databaseConfigPanel.btnRefresh"));
        if(btnExcludeIndexedFolder != null) btnExcludeIndexedFolder.setText(I18N.get("databaseConfigPanel.btnExcludeIndexedFolder"));

        if(valueLabelThirdPart != null) valueLabelThirdPart.setText(I18N.get("databaseConfigPanel.valueLabelThirdPart"));

    }

    @Override
    public void onLanguageChanged(Locale newLocale) {
        updateTexts();
        repaint();
        revalidate();
    }
}