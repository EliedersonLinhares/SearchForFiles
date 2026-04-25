package com.esl.searchforfiles.ui;

import com.esl.searchforfiles.cache.thumbnail.ThumbnailCacheManager;
import com.esl.searchforfiles.database.DatabaseManager;
import com.esl.searchforfiles.model.FileInfo;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Date;

/**
 * Menu de contexto (botão direito) para arquivos
 */
public class FileContextMenu extends JPopupMenu {

    private final File file;
    private final FileInfo fileInfo;
    private final Component parent;
    private final ThumbnailCacheManager cacheManager;
    private final DatabaseManager dbManager;          // NOVO
    private final FileItemPanel itemPanel; // NOVO
    private final FolderNavigationListener folderNavListener;

    public FileContextMenu(File file, FileInfo fileInfo, Component parent, DatabaseManager dbManager,
                           FileItemPanel itemPanel, FolderNavigationListener folderNavListener) {
        this.file = file;
        this.fileInfo = fileInfo;
        this.parent = parent;
        this.dbManager = dbManager;
        this.itemPanel = itemPanel;
        this.folderNavListener = folderNavListener;
        this.cacheManager = FileItemPanel.getThumbnailCacheManager();

        createMenuItems();
    }

    private void createMenuItems() {
        if (fileInfo.isDirectory()) {
            // Para PASTAS: "Entrar" como ação principal
            JMenuItem enterItem = new JMenuItem("📂 Entrar na pasta");
            enterItem.setFont(enterItem.getFont().deriveFont(Font.BOLD)); // destaque
            enterItem.addActionListener(e -> {
                if (folderNavListener != null)
                    folderNavListener.onNavigateTo(file.getAbsolutePath());
            });
            add(enterItem);
        } else {
            // Para ARQUIVOS: "Abrir" como ação principal (comportamento original)
            JMenuItem openItem = new JMenuItem("Abrir");
            openItem.setIcon(UIManager.getIcon("FileView.fileIcon"));
            openItem.addActionListener(e -> openFile());
            add(openItem);
        }

        // "Abrir no Explorer" sempre disponível
        JMenuItem openExplorerItem = new JMenuItem("🗂️  Abrir no Explorer");
        openExplorerItem.setIcon(UIManager.getIcon("FileView.directoryIcon"));
        openExplorerItem.addActionListener(e -> openFolder());
        add(openExplorerItem);

        addSeparator();

        // Avaliação e Tags (apenas para arquivos, não faz sentido em pastas)
        if (!fileInfo.isDirectory()) {
            add(createRatingSubmenu());
            JMenuItem tagsItem = new JMenuItem("🏷️  Gerenciar Tags...");
            tagsItem.addActionListener(e -> openTagDialog());
            add(tagsItem);
            addSeparator();

            if (isVideoFile(file)) {
                add(createCacheSubmenu());
                addSeparator();
            }
        }

        add(makeItem("Propriedades", null, e -> showProperties()));

        addSeparator();
        JCheckBoxMenuItem toggleOverlay = new JCheckBoxMenuItem("⭐ Mostrar avaliação nos ícones");
        toggleOverlay.setSelected(FileItemPanel.isShowRatingOverlay());
        toggleOverlay.addActionListener(e -> toggleRatingOverlay(toggleOverlay.isSelected()));
        add(toggleOverlay);
    }

    // ── Rating submenu ──────────────────────────────────────────────
    private JMenu createRatingSubmenu() {
        JMenu menu = new JMenu("⭐ Avaliação");

        // Marca a nota atual
        int current = fileInfo.getRating();

        String[] labels = {"Sem avaliação", "★", "★★", "★★★", "★★★★", "★★★★★"};
        for (int stars = 0; stars <= 5; stars++) {
            final int value = stars;
            JMenuItem item = new JMenuItem(labels[stars]);
            if (value == current) {
                item.setFont(item.getFont().deriveFont(Font.BOLD));
                item.setText("✓ " + labels[stars]);
            }
            item.addActionListener(e -> setRating(value));
            menu.add(item);
        }
        return menu;
    }

    private void setRating(int stars) {
        try {
            dbManager.setRating(fileInfo.getPath(), stars);
            fileInfo.setRating(stars);

            // NOVO: atualiza o overlay visualmente sem reload
            if (itemPanel != null) {
                itemPanel.updateRatingOverlay(stars);
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(parent,
                    "Erro ao salvar avaliação: " + ex.getMessage(),
                    "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ── Tag dialog ──────────────────────────────────────────────────
    private void openTagDialog() {
        TagManagerDialog dialog =
                new TagManagerDialog((Frame) SwingUtilities.getWindowAncestor(parent),
                        fileInfo, dbManager);
        dialog.setVisible(true);
    }

    // ── helpers ─────────────────────────────────────────────────────
    private JMenuItem makeItem(String text, Icon icon, ActionListener al) {
        JMenuItem item = new JMenuItem(text);
        if (icon != null) item.setIcon(icon);
        item.addActionListener(al);
        return item;
    }

    private void toggleRatingOverlay(boolean visible) {
        FileItemPanel.setShowRatingOverlay(visible);

        // Propaga para todos os FileItemPanels visíveis na tela
        propagateOverlayVisibility(parent);
    }

    /**
     * Percorre a hierarquia de componentes a partir do ResultsPanel
     * e atualiza todos os FileItemPanels encontrados.
     */
    private void propagateOverlayVisibility(Component origin) {
        // Sobe até o JFrame para depois descer até o gridPanel
        Container root = SwingUtilities.getAncestorOfClass(JFrame.class, origin);
        if (root == null) return;
        applyToAll(root);
    }

    private void applyToAll(Container container) {
        for (Component c : container.getComponents()) {
            if (c instanceof FileItemPanel fip) {
                fip.applyOverlayVisibility();
            } else if (c instanceof Container inner) {
                applyToAll(inner);
            }
        }
    }
    /**
     * NOVO: Cria submenu para opções de cache (apenas para vídeos)
     */
    private JMenu createCacheSubmenu() {
        JMenu cacheMenu = new JMenu("🎬 Cache do Thumbnail");

        // Verifica se tem thumbnail em cache
        boolean hasCached = cacheManager.hasCachedThumbnail(file, 100);

        if (hasCached) {
            JMenuItem cachedInfoItem = new JMenuItem("✓ Thumbnail em cache");
            cachedInfoItem.setEnabled(false);
            cachedInfoItem.setFont(new Font("SansSerif", Font.ITALIC, 11));
            cacheMenu.add(cachedInfoItem);
            cacheMenu.addSeparator();

            // Opção para remover do cache
            JMenuItem removeCacheItem = new JMenuItem("🗑️ Remover do Cache");
            removeCacheItem.addActionListener(e -> removeThumbnailCache());
            cacheMenu.add(removeCacheItem);

            // Opção para regenerar
            JMenuItem regenerateItem = new JMenuItem("🔄 Regenerar Thumbnail");
            regenerateItem.addActionListener(e -> regenerateThumbnail());
            cacheMenu.add(regenerateItem);
        } else {
            JMenuItem noCacheItem = new JMenuItem("Sem thumbnail em cache");
            noCacheItem.setEnabled(false);
            noCacheItem.setFont(new Font("SansSerif", Font.ITALIC, 11));
            cacheMenu.add(noCacheItem);
            cacheMenu.addSeparator();

            // Opção para gerar thumbnail
            JMenuItem generateItem = new JMenuItem("📸 Gerar Thumbnail Agora");
            generateItem.addActionListener(e -> generateThumbnail());
            cacheMenu.add(generateItem);
        }

        return cacheMenu;
    }
    /**
     * NOVO: Remove thumbnail do cache
     */
    private void removeThumbnailCache() {
        int result = JOptionPane.showConfirmDialog(
                parent,
                "Remover thumbnail deste vídeo do cache?\n\n" +
                        "O thumbnail será gerado novamente quando necessário.",
                "Confirmar Remoção",
                JOptionPane.YES_NO_OPTION
        );

        if (result == JOptionPane.YES_OPTION) {
            boolean removed = cacheManager.removeCachedThumbnail(file, 100);

            // Remove também do cache em memória
            String memKey = "vid_" + file.getAbsolutePath() + "_100";
            FileItemPanel.ICON_CACHE.remove(memKey);

            if (removed) {
                JOptionPane.showMessageDialog(
                        parent,
                        "Thumbnail removido do cache com sucesso!",
                        "Sucesso",
                        JOptionPane.INFORMATION_MESSAGE
                );
            } else {
                JOptionPane.showMessageDialog(
                        parent,
                        "Não foi possível remover o thumbnail.",
                        "Erro",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        }
    }

    /**
     * NOVO: Regenera thumbnail
     */
    private void regenerateThumbnail() {
        int result = JOptionPane.showConfirmDialog(
                parent,
                "Regenerar thumbnail deste vídeo?\n\n" +
                        "O thumbnail atual será substituído por um novo.",
                "Confirmar Regeneração",
                JOptionPane.YES_NO_OPTION
        );

        if (result == JOptionPane.YES_OPTION) {
            // Remove do cache
            cacheManager.removeCachedThumbnail(file, 100);
            String memKey = "vid_" + file.getAbsolutePath() + "_100";
            FileItemPanel.ICON_CACHE.remove(memKey);

            // Força regeneração (isso será feito automaticamente
            // quando o FileItemPanel for recriado)
            JOptionPane.showMessageDialog(
                    parent,
                    "Thumbnail será regenerado na próxima visualização.",
                    "Informação",
                    JOptionPane.INFORMATION_MESSAGE
            );
        }
    }

    /**
     * NOVO: Gera thumbnail imediatamente
     */
    private void generateThumbnail() {
        JOptionPane.showMessageDialog(
                parent,
                "O thumbnail será gerado automaticamente\n" +
                        "quando você visualizar este vídeo nos resultados.",
                "Informação",
                JOptionPane.INFORMATION_MESSAGE
        );
    }

    /**
     * NOVO: Verifica se o arquivo é um vídeo
     */
    private boolean isVideoFile(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".mp4") || name.endsWith(".avi") ||
                name.endsWith(".mkv") || name.endsWith(".mov") ||
                name.endsWith(".wmv") || name.endsWith(".flv") ||
                name.endsWith(".webm") || name.endsWith(".m4v");
    }
    private void openFile() {
        try {
            Desktop.getDesktop().open(file);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(parent,
                    "Erro ao abrir: " + e.getMessage(),
                    "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openFolder() {
        try {
            Desktop.getDesktop().open(file.getParentFile());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(parent,
                    "Erro ao abrir pasta: " + e.getMessage(),
                    "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showProperties() {
        String message = String.format(
                "Nome: %s\n" +
                        "Caminho: %s\n" +
                        "Tipo: %s\n" +
                        "Extensão: %s\n" +
                        "Tamanho: %.2f MB (%.2f KB)\n" +
                        "Última modificação: %s\n" +
                        "É diretório: %s",
                fileInfo.getName(),
                fileInfo.getPath(),
                fileInfo.getFileType(),
                fileInfo.getExtension(),
                fileInfo.getSize() / (1024.0 * 1024.0),
                fileInfo.getSize() / 1024.0,
                new Date(fileInfo.getLastModified()),
                fileInfo.isDirectory() ? "Sim" : "Não"
        );

        JOptionPane.showMessageDialog(parent, message,
                "Propriedades - " + fileInfo.getName(),
                JOptionPane.INFORMATION_MESSAGE);
    }

    public interface FolderNavigationListener {
        void onNavigateTo(String path);
    }

}