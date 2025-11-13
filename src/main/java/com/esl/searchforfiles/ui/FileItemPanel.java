package com.esl.searchforfiles.ui;

import com.esl.searchforfiles.model.FileInfo;
import com.esl.searchforfiles.model.FileType;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Painel visual para representar um arquivo/pasta
 */
public class FileItemPanel extends JPanel {

    private final File file;
    private final FileInfo fileInfo;
    private ResultsPanel.FileItemClickListener clickListener;

    public FileItemPanel(File file, FileInfo fileInfo, int width, int height) {
        this.file = file;
        this.fileInfo = fileInfo;

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createLineBorder(new Color(230, 230, 230)));
        setAlignmentX(Component.CENTER_ALIGNMENT);

        // Ícone / Miniatura
        JLabel iconLabel = createIconLabel(width);

        // Nome do arquivo
        JLabel nameLabel = new JLabel(shortName(file.getName(), 14));
        nameLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        nameLabel.setHorizontalAlignment(SwingConstants.CENTER);
        nameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Tooltip
        setToolTipText(createTooltip());

        // Layout
        add(Box.createVerticalGlue());
        add(iconLabel);
        add(Box.createVerticalStrut(5));
        add(nameLabel);
        add(Box.createVerticalGlue());

        // Mouse listeners
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && clickListener != null) {
                    clickListener.onFileDoubleClick(file);
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger() && clickListener != null) {
                    clickListener.onFileRightClick(file, fileInfo, FileItemPanel.this, e.getX(), e.getY());
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger() && clickListener != null) {
                    clickListener.onFileRightClick(file, fileInfo, FileItemPanel.this, e.getX(), e.getY());
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                setBackground(new Color(240, 240, 255));
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                setBackground(Color.WHITE);
                setCursor(Cursor.getDefaultCursor());
            }
        });
    }

    private JLabel createIconLabel(int width) {
        JLabel iconLabel = new JLabel();
        iconLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);

        if (fileInfo.getFileType() == FileType.IMAGE && file.exists()) {
            try {
                BufferedImage img = ImageIO.read(file);
                if (img != null) {
                    int size = Math.min(width - 40, 100);
                    Image scaled = img.getScaledInstance(size, size, Image.SCALE_SMOOTH);
                    iconLabel.setIcon(new ImageIcon(scaled));
                } else {
                    iconLabel.setIcon(getSystemIcon(file));
                }
            } catch (IOException e) {
                iconLabel.setIcon(getSystemIcon(file));
            }
        } else {

            iconLabel.setIcon(getSystemIcon(file));
        }

        return iconLabel;
    }

    private String createTooltip() {
        return String.format(
                "<html><b>%s</b><br>Tamanho: %.2f MB<br>Tipo: %s<br>Caminho: %s</html>",
                file.getName(),
                fileInfo.getSize() / (1024.0 * 1024.0),
                fileInfo.getFileType(),
                file.getParent()
        );
    }

    private Icon getSystemIcon(File file) {
        FileSystemView fsv = FileSystemView.getFileSystemView();
        return fsv.getSystemIcon(file);
    }

    private String shortName(String name, int maxLen) {
        if (name.length() <= maxLen) return name;
        return name.substring(0, maxLen - 3) + "...";
    }

    public void setClickListener(ResultsPanel.FileItemClickListener listener) {
        this.clickListener = listener;
    }

    public File getFile() {
        return file;
    }

    public FileInfo getFileInfo() {
        return fileInfo;
    }
}