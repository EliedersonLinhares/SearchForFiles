package com.esl.searchforfiles.ui;

import com.esl.searchforfiles.configuration.WrapLayout;
import com.esl.searchforfiles.service.SyncService;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

public class BottomIndicatorPanel {

    private JLabel autoRefreshIndicator;
    private JLabel syncIndicator; // NOVO
    private JLabel statusLabel;
    private final  FileExplorerSwing fileExplorerSwing;
    private boolean working = false;

    public JLabel getStatusLabel() {
        return statusLabel;
    }

    public void setStatusLabel(JLabel statusLabel) {
        this.statusLabel = statusLabel;
    }

    public BottomIndicatorPanel(FileExplorerSwing fileExplorerSwing){
        this.fileExplorerSwing = fileExplorerSwing;
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
        // Notifica quem estiver ouvindo se o valor mudou
        suporte.firePropertyChange("working", antigo, novoEstado);
    }

    /**
     * Cria painel de status com indicador de auto-refresh
     * NOVO MÉTODO
     */
    public JPanel createStatusPanel() {

        // Painel raiz com BorderLayout — garante esquerda/direita independente da largura
        JPanel statusPanel = new JPanel(new BorderLayout(8, 0));
        statusPanel.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));

        // ── Esquerda: label de status ─────────────────────────────────────────────
        statusLabel = new JLabel("📂 Local de busca: C:\\ | Sistema pronto");
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        statusPanel.add(statusLabel, BorderLayout.CENTER); // CENTER estica e ocupa o espaço livre

        // ── Direita: indicadores ──────────────────────────────────────────────────
        syncIndicator = new JLabel();
        syncIndicator.setFont(new Font("SansSerif", Font.BOLD, 14));
        syncIndicator.setForeground(new Color(33, 150, 243));
        syncIndicator.setVisible(false);

        autoRefreshIndicator = new JLabel();
        autoRefreshIndicator.setFont(new Font("SansSerif", Font.BOLD, 14));
        autoRefreshIndicator.setForeground(new Color(76, 175, 80));
        autoRefreshIndicator.setVisible(false);

        // Painel dos indicadores sempre colado à direita
        JPanel indicatorsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        indicatorsPanel.add(syncIndicator);
        indicatorsPanel.add(autoRefreshIndicator);

        statusPanel.add(indicatorsPanel, BorderLayout.EAST);

        return statusPanel;
    }

    /**
     * Mostra indicador de sincronização
     * NOVO MÉTODO
     */
    void showSyncIndicator(String message) {
        syncIndicator.setText(message);
        syncIndicator.setVisible(true);
    }

    /**
     * Esconde indicador com cor apropriada
     * MODIFICADO: Trata caso de pasta não indexada
     */
    public void hideSyncIndicator(boolean isWarning) {
        if (isWarning) {
            syncIndicator.setForeground(new Color(255, 152, 0)); // Laranja para aviso
        } else {
            syncIndicator.setForeground(new Color(33, 150, 243)); // Azul normal
        }

        Timer timer = new Timer(5000, e -> { // 5 segundos para avisos
            syncIndicator.setVisible(false);
        });
        timer.setRepeats(false);
        timer.start();
    }

    /**
     * Cria callback para sincronização
     * MODIFICADO: Trata caso de pasta não indexada
     */
    SearchController.SyncCallback createSyncCallback(String path) {
        return new SearchController.SyncCallback() {
            @Override
            public void onSyncCompleted(SyncService.SyncResult result) {
                SwingUtilities.invokeLater(() -> {

                    // CASO 1: Pasta não indexada
                    if (result.isNotIndexed()) {
                        showSyncIndicator("⚠️ Pasta não indexada - Use 'Indexar'");
                        hideSyncIndicator(true); // Aviso em laranja

                        String statusText = "📂 " + fileExplorerSwing.getSelectedPath() +
                                " | ⚠️ Pasta não indexada";
                        statusLabel.setText(statusText);

                        // Limpa resultados se há busca ativa
                        fileExplorerSwing.getResultsPanel().showMessage(
                                "⚠️ Pasta não está indexada\n\n" +
                                        "Clique em 'Indexar' para indexar esta pasta antes de buscar.",
                                ResultsPanel.MessageType.ERROR
                        );

                        return;
                    }

                    // CASO 2: Pasta indexada com mudanças
                    if (result.hasChanges()) {
                        String message = String.format(
                                "✅ Sincronizado: +%d | ↻%d | -%d",
                                result.getAdded(),
                                result.getUpdated(),
                                result.getDeleted()
                        );
                        showSyncIndicator(message);
                        setWorking(false);
                        String statusText = "📂 " + fileExplorerSwing.getSelectedPath() + " | " + result.getSummary();
                        String monitoringStatus = fileExplorerSwing.getController().getMonitoringStatus();
                        if (!monitoringStatus.isEmpty()) {
                            statusText += " | " + monitoringStatus;
                        }
                        statusLabel.setText(statusText);

                    } else {
                        // CASO 3: Pasta indexada sem mudanças
                        showSyncIndicator("✅ Sincronizado");
                        setWorking(false);
                        updateStatusLabel();
                    }

                    hideSyncIndicator(false); // Normal em azul
                });
            }

            @Override
            public void onSyncError(Exception e) {
                SwingUtilities.invokeLater(() -> {
                    showSyncIndicator("❌ Erro na sincronização");
                    setWorking(false);
                    hideSyncIndicator(true);
                });
            }
        };
    }

    /**
     * Mostra indicador visual de auto-refresh
     * NOVO MÉTODO
     */
    void showAutoRefreshIndicator() {
        autoRefreshIndicator.setText("🔄 Atualizando...");
        autoRefreshIndicator.setVisible(true);
        setWorking(true);

        // Esconde após 2 segundos
        Timer timer = new Timer(2000, e -> {
            autoRefreshIndicator.setVisible(false);
        });
        timer.setRepeats(false);
        timer.start();
    }
    /**
     * Atualiza label de status com informação de monitoramento
     * NOVO MÉTODO
     */
    private void updateStatusLabel() {
        String monitoringStatus = fileExplorerSwing.getController().getMonitoringStatus();
        String statusText = "📂 Pasta selecionada: " + fileExplorerSwing.getSelectedPath();

        if (!monitoringStatus.isEmpty()) {
            statusText += " | " + monitoringStatus;
        }

        statusLabel.setText(statusText);
    }

}
