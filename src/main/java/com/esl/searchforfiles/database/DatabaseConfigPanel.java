package com.esl.searchforfiles.database;

import com.esl.searchforfiles.configuration.ConfigPanelBase;
import com.esl.searchforfiles.ui.FileExplorerSwing;

import javax.swing.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DatabaseConfigPanel extends ConfigPanelBase {

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

    public DatabaseConfigPanel(DatabaseManager db, FileExplorerSwing mainFrame) {
        this.db = db;
        this.mainFrame = mainFrame;



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

        JButton btnExcludeIndexedFolder = makeBtn("Excluir Pasta Indexada");
        btnExcludeIndexedFolder.setEnabled(db.isFolderIndexed(mainFrame.getSelectedPath()));
        btnExcludeIndexedFolder.addActionListener(e -> {

            int choice = JOptionPane.showConfirmDialog(
                    this,
                    "Deseja realmente excluir esta pasta indexada?\n" + mainFrame.getSelectedPath(),
                    "Confirmar",
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
}