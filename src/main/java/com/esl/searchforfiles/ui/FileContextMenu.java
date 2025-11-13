package com.esl.searchforfiles.ui;

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

    public FileContextMenu(File file, FileInfo fileInfo, Component parent) {
        this.file = file;
        this.fileInfo = fileInfo;
        this.parent = parent;

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

        // Propriedades
        JMenuItem propertiesItem = new JMenuItem("Propriedades");
        propertiesItem.addActionListener(e -> showProperties());
        add(propertiesItem);
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