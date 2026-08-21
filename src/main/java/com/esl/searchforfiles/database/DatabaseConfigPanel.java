package com.esl.searchforfiles.database;

import com.esl.searchforfiles.configuration.ConfigPanelBase;

import javax.swing.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DatabaseConfigPanel extends ConfigPanelBase {

    private final DatabaseManager db;

    private final JLabel totalFilesLabel   = makeValueLabel("...");
    private final JLabel totalFoldersLabel = makeValueLabel("...");
    private final JLabel lastIndexedLabel  = makeValueLabel("...");
    private final JLabel lastModifiedLabel = makeValueLabel("...");
    private final JLabel totalSizeLabel    = makeValueLabel("...");
    private final JLabel dbSizeLabel       = makeValueLabel("...");
    private final JLabel tagsLabel         = makeValueLabel("...");
    private final JLabel extensionsLabel   = makeValueLabel("...");

    public DatabaseConfigPanel(DatabaseManager db) {
        this.db = db;

        JPanel stats = addSection("Estatísticas do índice");
        addRow(stats, "Arquivos indexados", totalFilesLabel);
        addRow(stats, "Pastas indexadas",   totalFoldersLabel);
        addRow(stats, "Tamanho total",      totalSizeLabel);
        addRow(stats, "Última indexação",   lastIndexedLabel);
        addRow(stats, "Último arquivo",     lastModifiedLabel);

        JPanel tagsSection = addSection("Tags e avaliações");
        addRow(tagsSection, "Tags / arquivos", tagsLabel);

        JPanel extSection = addSection("Extensões mais comuns");
        addRow(extSection, "Top 5", extensionsLabel);

        JPanel dbSection = addSection("Banco de dados");
        addRow(dbSection, "Tamanho do DB", dbSizeLabel);
        addRow(dbSection, makeHint("Localizado em: " +
                DatabaseManager.DB_PATH.toAbsolutePath()));

        JButton btnRefresh = makeBtn("Atualizar");
        btnRefresh.addActionListener(e -> refresh());
        addButtons(btnRefresh);

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
                setValueLabelText(tagsLabel,
                        tagStats[0] + " tags · " + tagStats[1] + " arquivo(s) com tag(s)");

                String extText = exts.stream()
                        .map(e -> "." + e.getKey() + " (" + e.getValue() + ")")
                        .collect(Collectors.joining(" · "));
                setValueLabelText(extensionsLabel, extText.isEmpty() ? "—" : extText);
            }
        }.execute();
    }
}