package com.esl.searchforfiles.ui;

import com.esl.searchforfiles.cache.thumbnail.ThumbnailCacheManager;
import com.esl.searchforfiles.model.FileInfo;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.Date;

/**
 * Menu de contexto (botão direito) para arquivos
 */
public class FileContextMenu extends JPopupMenu {

    private final File file;
    private final FileInfo fileInfo;
    private final Component parent;
    private final ThumbnailCacheManager cacheManager;

    public FileContextMenu(File file, FileInfo fileInfo, Component parent) {
        this.file = file;
        this.fileInfo = fileInfo;
        this.parent = parent;
        this.cacheManager = FileItemPanel.getThumbnailCacheManager();

        createMenuItems();
    }

    private void createMenuItems() {
        // Abrir
        JMenuItem openItem = new JMenuItem("Abrir");
        openItem.setIcon(UIManager.getIcon("FileView.fileIcon"));
        openItem.addActionListener(e -> openFile());
        add(openItem);

        // Abrir pasta
        JMenuItem openFolderItem = new JMenuItem("Abrir pasta");
        openFolderItem.setIcon(UIManager.getIcon("FileView.directoryIcon"));
        openFolderItem.addActionListener(e -> openFolder());
        add(openFolderItem);

        addSeparator();
        // NOVO: Se for vídeo, adiciona opção de cache
        if (isVideoFile(file)) {
            JMenu cacheMenu = createCacheSubmenu();
            add(cacheMenu);
            addSeparator();
        }

        // Propriedades
        JMenuItem propertiesItem = new JMenuItem("Propriedades");
        propertiesItem.addActionListener(e -> showProperties());
        add(propertiesItem);
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
}