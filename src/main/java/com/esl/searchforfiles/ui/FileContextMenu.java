package com.esl.searchforfiles.ui;

import com.esl.searchforfiles.cache.thumbnail.ThumbnailCacheManager;
import com.esl.searchforfiles.compressedFiles.CompressedWorker;
import com.esl.searchforfiles.configuration.UIConfig;
import com.esl.searchforfiles.database.DatabaseManager;
import com.esl.searchforfiles.model.FileInfo;
import com.esl.searchforfiles.preview.imageViewer.ImageViewerFrame;
import com.esl.searchforfiles.service.FavoritesService;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

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
    private boolean currentIncludeSubfolders;
    private final FileExplorerSwing fileExplorerSwing;
    private final FavoritesService favoritesService;

    public FileContextMenu(File file, FileInfo fileInfo, Component parent, DatabaseManager dbManager,
                           FileItemPanel itemPanel, FolderNavigationListener folderNavListener
                          , FileExplorerSwing fileExplorerSwing, FavoritesService favoritesService
    ) {
        this.file = file;
        this.fileInfo = fileInfo;
        this.parent = parent;
        this.dbManager = dbManager;
        this.itemPanel = itemPanel;
        this.folderNavListener = folderNavListener;
        this.fileExplorerSwing = fileExplorerSwing;
        this.favoritesService = favoritesService;
        this.cacheManager = FileItemPanel.getThumbnailCacheManager();

        setFont(UIConfig.FONT_DEFAULT);
        createMenuItems();
    }

    private void createMenuItems() {

        if(fileInfo.getExtension().contains("zip") || fileInfo.getExtension().contains("rar") ) {
            JMenuItem rarItem = new JMenuItem("\uD83D\uDCE6\u200B Descompactar arquivo");
            rarItem.addActionListener(e -> {

                // 4. Dispara o Worker passando a nova pasta de destino configurada
                CompressedWorker worker = new CompressedWorker(fileExplorerSwing, file);
                worker.execute();
            });
            add(rarItem);
        }




        if (fileInfo.isDirectory()) {
            // Para PASTAS: "Entrar" como ação principal
            JMenuItem enterItem = new JMenuItem("📂 Entrar na pasta");
            enterItem.addActionListener(e -> {
                if (folderNavListener != null)
                    folderNavListener.onNavigateTo(file.getAbsolutePath());
            });
            add(enterItem);
        } else {
            // Para ARQUIVOS: "Abrir" como ação principal (comportamento original)
            JMenuItem openItem = new JMenuItem("\uD83D\uDCC3\u200B Abrir");
            openItem.addActionListener(e -> openFile());
            add(openItem);
        }

        // "Abrir no Explorer" sempre disponível
        JMenuItem openExplorerItem = new JMenuItem("🗂️  Abrir no Explorer");
        openExplorerItem.addActionListener(e -> openFolder());
        add(openExplorerItem);


        // Avaliação e Tags (apenas para arquivos, não faz sentido em pastas)
        if (!fileInfo.isDirectory()) {
            add(createRatingSubmenu());
            JMenuItem tagsItem = new JMenuItem("🏷️  Gerenciar Tags...");
            tagsItem.addActionListener(e -> openTagDialog());
            add(tagsItem);
            addSeparator();

        }

        addSeparator();

        add(makeItem("\uD83C\uDF9A\uFE0F\u200B Propriedades", null, e -> showProperties()));
        add(makeItem("\u200B\uD83D\uDCCB Copiar Nome", null, e -> copyFileName()));
        add(makeItem("\u200B\uD83D\uDCCB Copiar Caminho Completo", null, e -> copyFullPath()));

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

            itemPanel.updateRatingOverlay(stars);

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


    private void copyFileName() {
        // 1. Transforma a String em um objeto transferível
        StringSelection stringSelection = new StringSelection(fileInfo.getName());

        transferAction(stringSelection);
    }
    private void copyFullPath() {
        // 1. Transforma a String em um objeto transferível
        StringSelection stringSelection = new StringSelection(fileInfo.getPath());

        transferAction(stringSelection);
    }

    private void transferAction(StringSelection stringSelection) {
        // 2. Pega a área de transferência do sistema
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();

        // 3. Define o conteúdo da área de transferência
        clipboard.setContents(stringSelection, null);

        // Feedback visual para o usuário
        JOptionPane.showMessageDialog(this, "Texto copiado com sucesso!");
    }


    // ── helpers ─────────────────────────────────────────────────────
    private JMenuItem makeItem(String text, Icon icon, ActionListener al) {
        JMenuItem item = new JMenuItem(text);
        if (icon != null) item.setIcon(icon);
        item.addActionListener(al);
        return item;
    }


    private boolean isImageFile(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".jpg") || name.endsWith(".jpeg") ||
                name.endsWith(".bmp") || name.endsWith(".tiff") ||
                name.endsWith(".tif") || name.endsWith(".webp") ||
                name.endsWith(".gif") || name.endsWith(".tga") ||
                name.endsWith(".ppm") || name.endsWith(".pgm") ||
                name.endsWith(".pbm") || name.endsWith(".hdr") ||
                name.endsWith(".exr") || name.endsWith(".heic") ||
                name.endsWith(".heif") || name.endsWith(".avif") ||
                name.endsWith(".jxl") || name.endsWith(".dds") ||
                name.endsWith(".pcx") || name.endsWith(".sgi") ||
                name.endsWith(".jp2") || name.endsWith(".j2k") ||
                name.endsWith(".ico");
    }
    private boolean isVideoFile(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".mp4") || name.endsWith(".avi") ||
                name.endsWith(".mkv") || name.endsWith(".mov") ||
                name.endsWith(".wmv") || name.endsWith(".flv") ||
                name.endsWith(".webm") || name.endsWith(".m4v");
    }

    private void openFile() {
        try {
            if(isImageFile(file)) {
                new ImageViewerFrame(fileExplorerSwing, file);
            }else {
                Desktop.getDesktop().open(file);
            }
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

        LocalDateTime ldt = Instant.ofEpochMilli(fileInfo.getLastModified())
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();

        // Criando um formatador para a data ficar no padrão brasileiro (opcional, mas recomendado)
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

        String message = """
        Nome: %s
        Caminho: %s
        Tipo: %s
        Extensão: %s
        Tamanho: %.2f MB (%.2f KB)
        Última modificação: %s
        É diretório: %s"""
                .formatted(
                        fileInfo.getName(),
                        fileInfo.getPath(),
                        fileInfo.getFileType(),
                        fileInfo.getExtension(),
                        fileInfo.getSize() / (1024.0 * 1024.0),
                        fileInfo.getSize() / 1024.0,
                        ldt.format(formatter), // Se preferir o formato padrão do Java (ISO), use apenas 'ldt'
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