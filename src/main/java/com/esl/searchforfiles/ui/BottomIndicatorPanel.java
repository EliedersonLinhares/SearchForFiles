package com.esl.searchforfiles.ui;

import com.esl.searchforfiles.configuration.UIConfig;
import com.esl.searchforfiles.configuration.WrapLayout;
import com.esl.searchforfiles.localization.I18N;
import com.esl.searchforfiles.model.PaginationInfo;
import com.esl.searchforfiles.service.SyncService;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.Locale;

public class BottomIndicatorPanel implements I18N.LanguageChangeListener {

    private JLabel autoRefreshIndicator;
    private JLabel syncIndicator;
    private JLabel statusLabel;
    private final FileExplorerSwing fileExplorerSwing;
    private boolean working = false;

    // ── NOVO: estado necessário para reconstruir os textos ao trocar idioma ──
    private enum StatusKind { INITIAL, FOLDER_SELECTED, NOT_INDEXED, SYNC_CHANGES, SYNC_NO_CHANGES, SEARCH_RESULTS, INDEX_DONE, INDEX_ERROR }
    private StatusKind currentStatusKind = StatusKind.INITIAL;
    private SyncService.SyncResult lastSyncResult; // usado quando currentStatusKind == SYNC_CHANGES

    private enum IndicatorKind { NONE, SYNC_UP_TO_DATE, SYNC_ERROR, SYNC_CHANGES, SYNC_NOT_INDEXED, AUTO_REFRESHING }
    private IndicatorKind currentSyncIndicatorKind = IndicatorKind.NONE;

    private SearchStatusContext lastSearchStatus;
    private String lastIndexedPath;


    public JLabel getStatusLabel() {
        return statusLabel;
    }

    public void setStatusLabel(JLabel statusLabel) {
        this.statusLabel = statusLabel;
    }

    public BottomIndicatorPanel(FileExplorerSwing fileExplorerSwing) {
        this.fileExplorerSwing = fileExplorerSwing;
        I18N.addLanguageChangeListener(this);
    }

    public boolean isWorking() {
        return working;
    }

    private final PropertyChangeSupport suporte = new PropertyChangeSupport(this);
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        suporte.addPropertyChangeListener(listener);
    }

    public void setWorking(boolean novoEstado) {
        boolean antigo = this.working;
        this.working = novoEstado;
        suporte.firePropertyChange("working", antigo, novoEstado);
    }

    public JPanel createStatusPanel() {

        JPanel statusPanel = new JPanel(new BorderLayout(8, 0));
        statusPanel.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));

        // ── Esquerda: label de status ─────────────────────────────────────
        statusLabel = new JLabel(); // texto definido em updateTexts()/rebuildStatusLabel()
        statusLabel.setFont(UIConfig.FONT_DEFAULT);
        statusPanel.add(statusLabel, BorderLayout.CENTER);

        // ── Direita: indicadores ────────────────────────────────────────
        syncIndicator = new JLabel();
        syncIndicator.setFont(UIConfig.FONT_DEFAULT_BOLD);
        syncIndicator.setForeground(new Color(33, 150, 243));
        syncIndicator.setVisible(false);

        autoRefreshIndicator = new JLabel();
        autoRefreshIndicator.setFont(UIConfig.FONT_DEFAULT);
        autoRefreshIndicator.setForeground(new Color(76, 175, 80));
        autoRefreshIndicator.setVisible(false);

        JPanel indicatorsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        indicatorsPanel.add(syncIndicator);
        indicatorsPanel.add(autoRefreshIndicator);

        statusPanel.add(indicatorsPanel, BorderLayout.EAST);

        currentStatusKind = StatusKind.INITIAL;
        rebuildStatusLabel(); // aplica o texto inicial traduzido

        return statusPanel;
    }

    // ── Indicador de sincronização ──────────────────────────────────────

    void showSyncIndicator(String message) {
        syncIndicator.setText(message);
        syncIndicator.setVisible(true);
    }

    public void hideSyncIndicator(boolean isWarning) {
        if (isWarning) {
            syncIndicator.setForeground(new Color(255, 152, 0));
        } else {
            syncIndicator.setForeground(new Color(33, 150, 243));
        }

        Timer timer = new Timer(5000, e -> {
            syncIndicator.setVisible(false);
        });
        timer.setRepeats(false);
        timer.start();
    }

    SearchController.SyncCallback createSyncCallback(String path) {
        return new SearchController.SyncCallback() {
            @Override
            public void onSyncCompleted(SyncService.SyncResult result) {
                SwingUtilities.invokeLater(() -> {

                    // CASO 1: Pasta não indexada
                    if (result.isNotIndexed()) {
                        currentSyncIndicatorKind = IndicatorKind.SYNC_NOT_INDEXED;
                        showSyncIndicator(I18N.get("bottomIndicator.sync.notIndexed"));
                        hideSyncIndicator(true);

                        currentStatusKind = StatusKind.NOT_INDEXED;
                        rebuildStatusLabel();

                        fileExplorerSwing.getResultsPanel().showMessage(
                                I18N.get("bottomIndicator.results.notIndexedMessage"),
                                ResultsPanel.MessageType.ERROR
                        );

                        return;
                    }

                    // CASO 2: Pasta indexada com mudanças
                    if (result.hasChanges()) {
                        currentSyncIndicatorKind = IndicatorKind.SYNC_CHANGES;
                        lastSyncResult = result;

                        showSyncIndicator(I18N.get("bottomIndicator.sync.changes")
                                .replace("{0}", String.valueOf(result.getAdded()))
                                .replace("{1}", String.valueOf(result.getUpdated()))
                                .replace("{2}", String.valueOf(result.getDeleted())));
                        setWorking(false);

                        currentStatusKind = StatusKind.SYNC_CHANGES;
                        rebuildStatusLabel();

                    } else {
                        // CASO 3: Pasta indexada sem mudanças
                        currentSyncIndicatorKind = IndicatorKind.SYNC_UP_TO_DATE;
                        showSyncIndicator(I18N.get("bottomIndicator.sync.upToDate"));
                        setWorking(false);

                        currentStatusKind = StatusKind.SYNC_NO_CHANGES;
                        rebuildStatusLabel(); // já chama updateStatusLabel() internamente
                    }

                    hideSyncIndicator(false);
                });
            }

            @Override
            public void onSyncError(Exception e) {
                SwingUtilities.invokeLater(() -> {
                    currentSyncIndicatorKind = IndicatorKind.SYNC_ERROR;
                    showSyncIndicator(I18N.get("bottomIndicator.sync.error"));
                    setWorking(false);
                    hideSyncIndicator(true);
                });
            }
        };
    }

    void showAutoRefreshIndicator() {
        currentSyncIndicatorKind = IndicatorKind.AUTO_REFRESHING;
        autoRefreshIndicator.setText(I18N.get("bottomIndicator.autoRefresh.updating"));
        autoRefreshIndicator.setVisible(true);
        setWorking(true);

        Timer timer = new Timer(2000, e -> {
            autoRefreshIndicator.setVisible(false);
            if (currentSyncIndicatorKind == IndicatorKind.AUTO_REFRESHING) {
                currentSyncIndicatorKind = IndicatorKind.NONE;
            }
        });
        timer.setRepeats(false);
        timer.start();
    }

    /**
     * MODIFICADO: agora atualiza também currentStatusKind, para que o texto
     * seja reconstruído corretamente ao trocar o idioma.
     */
    private void updateStatusLabel() {
        currentStatusKind = StatusKind.FOLDER_SELECTED;
        rebuildStatusLabel();
    }

    /**
     * NOVO: única fonte de verdade para o texto de statusLabel.
     * Reconstrói o texto a partir de currentStatusKind e do estado salvo,
     * usando sempre I18N.get() no idioma atual. Chamado tanto pelos fluxos
     * normais (sync, seleção de pasta) quanto por updateTexts() (troca de idioma).
     */
    private void rebuildStatusLabel() {
        if (statusLabel == null) return;

        String monitoringStatus = fileExplorerSwing.getController() != null
                ? fileExplorerSwing.getController().getMonitoringStatus()
                : "";

        String text = switch (currentStatusKind) {
            case INITIAL -> I18N.get("bottomIndicator.status.initial");

            case NOT_INDEXED -> I18N.get("bottomIndicator.status.notIndexed")
                    .replace("{0}", fileExplorerSwing.getSelectedPath());

            case SYNC_CHANGES -> {
                String base = I18N.get("bottomIndicator.status.pathPrefix")
                        .replace("{0}", fileExplorerSwing.getSelectedPath())
                        + " | " + (lastSyncResult != null ? lastSyncResult.getSummary() : "");
                yield !monitoringStatus.isEmpty() ? base + " | " + monitoringStatus : base;
            }

            case SYNC_NO_CHANGES, FOLDER_SELECTED -> {
                String base = I18N.get("bottomIndicator.status.folderSelected")
                        .replace("{0}", fileExplorerSwing.getSelectedPath());
                yield !monitoringStatus.isEmpty() ? base + " | " + monitoringStatus : base;
            }

            // NOVO
            case SEARCH_RESULTS -> buildSearchStatusText(lastSearchStatus);

            case INDEX_DONE -> I18N.get("bottomIndicator.status.indexDone")
                    .replace("{0}", lastIndexedPath != null ? lastIndexedPath : "");

            case INDEX_ERROR -> I18N.get("bottomIndicator.status.indexError");
        };

        statusLabel.setText(text);
    }
    private String buildSearchStatusText(SearchStatusContext ctx) {
        if (ctx == null) return "";

        String sortFieldLabel = I18N.get(ctx.sortFieldKey);
        String arrow = "ASC".equals(ctx.sortOrder) ? "↑" : "↓";
        String sortInfo = sortFieldLabel + " " + arrow;

        StringBuilder filters = new StringBuilder();
        if (ctx.minRating > 0)
            filters.append(" | ").append("★".repeat(ctx.minRating)).append("+");
        if (ctx.tag != null && !ctx.tag.isEmpty())
            filters.append(" | 🏷️ ").append(ctx.tag);

        String key = ctx.hasSearchTerm
                ? "bottomIndicator.status.searchResults"
                : "bottomIndicator.status.listingResults";

        String text = I18N.get(key)
                .replace("{0}", String.valueOf(ctx.pagination))
                .replace("{1}", sortInfo)
                .replace("{2}", filters.toString())
                .replace("{3}", ctx.selectedPath);

        if (!ctx.monitoringStatus.isEmpty())
            text += " | " + ctx.monitoringStatus + " 🔄";

        return text;
    }

    // ── i18n ──────────────────────────────────────────────────────────

    /**
     * NOVO: reconstrói statusLabel a partir do estado atual, e retraduz
     * o indicador de sync/auto-refresh se estiver visível no momento.
     */
    private void updateTexts() {
        rebuildStatusLabel();

        if (syncIndicator.isVisible()) {
            String text = switch (currentSyncIndicatorKind) {
                case SYNC_UP_TO_DATE   -> I18N.get("bottomIndicator.sync.upToDate");
                case SYNC_ERROR        -> I18N.get("bottomIndicator.sync.error");
                case SYNC_NOT_INDEXED  -> I18N.get("bottomIndicator.sync.notIndexed");
                case SYNC_CHANGES      -> lastSyncResult != null
                        ? I18N.get("bottomIndicator.sync.changes")
                        .replace("{0}", String.valueOf(lastSyncResult.getAdded()))
                        .replace("{1}", String.valueOf(lastSyncResult.getUpdated()))
                        .replace("{2}", String.valueOf(lastSyncResult.getDeleted()))
                        : syncIndicator.getText();
                default -> syncIndicator.getText();
            };
            syncIndicator.setText(text);
        }

        if (autoRefreshIndicator.isVisible()) {
            autoRefreshIndicator.setText(I18N.get("bottomIndicator.autoRefresh.updating"));
        }
    }

    private static class SearchStatusContext {
        boolean hasSearchTerm;
        PaginationInfo pagination;
        String sortFieldKey;   // chave i18n do campo de ordenação, não o texto traduzido
        String sortOrder;      // "ASC" / "DESC"
        int minRating;
        String tag;
        String monitoringStatus;
        String selectedPath;
    }

    /**
     * NOVO: usado pelo FileExplorerSwing para reportar o status de uma busca concluída.
     * Substitui qualquer chamada direta a getStatusLabel().setText(...).
     */
    public void showSearchStatus(boolean hasSearchTerm, PaginationInfo pagination,
                                 String sortFieldKey, String sortOrder,
                                 int minRating, String tag,
                                 String monitoringStatus, String selectedPath) {
        SearchStatusContext ctx = new SearchStatusContext();
        ctx.hasSearchTerm = hasSearchTerm;
        ctx.pagination = pagination;
        ctx.sortFieldKey = sortFieldKey;
        ctx.sortOrder = sortOrder;
        ctx.minRating = minRating;
        ctx.tag = tag;
        ctx.monitoringStatus = monitoringStatus;
        ctx.selectedPath = selectedPath;

        this.lastSearchStatus = ctx;
        this.currentStatusKind = StatusKind.SEARCH_RESULTS;
        rebuildStatusLabel();
    }

    /** NOVO: para "Indexação concluída" / "Erro na indexação". */
    public void showIndexDone(String path) {
        this.lastIndexedPath = path;
        this.currentStatusKind = StatusKind.INDEX_DONE;
        rebuildStatusLabel();
    }

    public void showIndexError() {
        this.currentStatusKind = StatusKind.INDEX_ERROR;
        rebuildStatusLabel();
    }


    @Override
    public void onLanguageChanged(Locale newLocale) {
        updateTexts();
        if (statusLabel != null) {
            statusLabel.revalidate();
            statusLabel.repaint();
        }
    }
}