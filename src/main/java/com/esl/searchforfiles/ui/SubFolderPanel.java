package com.esl.searchforfiles.ui;


import com.esl.searchforfiles.model.FileInfo;
import com.esl.searchforfiles.model.FileType;
import com.esl.searchforfiles.model.SearchCriteria;
import com.esl.searchforfiles.service.IconService;

import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

public class SubFolderPanel extends JPanel {

    private static final Color BG_COLOR = new Color(45, 45, 45);
    private static final Color HOVER_COLOR = new Color(60, 60, 60);
    private static final Color HEADER_COLOR = new Color(33, 150, 243);
    private static final FileSystemView FSV = FileSystemView.getFileSystemView();

    private final JPanel listPanel;
    private final JScrollPane scrollPane;
    private final JLabel titleLabel;
    private FolderClickListener clickListener;

    public SubFolderPanel() {
        setLayout(new BorderLayout(0, 0));
        setBackground(BG_COLOR);
        setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, new Color(70, 70, 70)));

        // ── Cabeçalho ─────────────────────────────────────────────
        titleLabel = new JLabel("📁 Subpastas");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        titleLabel.setForeground(HEADER_COLOR);
        titleLabel.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 8));
        titleLabel.setBackground(new Color(38, 38, 38));
        titleLabel.setOpaque(true);
        add(titleLabel, BorderLayout.NORTH);

        // ── Lista de pastas ────────────────────────────────────────
        listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBackground(BG_COLOR);

        scrollPane = new JScrollPane(listPanel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(12);
        scrollPane.setBackground(BG_COLOR);
        scrollPane.getViewport().setBackground(BG_COLOR);
        add(scrollPane, BorderLayout.CENTER);

        setPreferredSize(new Dimension(200, 0));
        setVisible(false); // começa oculto
    }

    // ── API pública ───────────────────────────────────────────────

    /**
     * Atualiza o painel com as subpastas do caminho informado.
     * Se não houver subpastas, esconde o painel automaticamente.
     */
    public void loadSubfolders(String parentPath, SearchController controller) {
        listPanel.removeAll();

        SwingWorker<List<FileInfo>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<FileInfo> doInBackground() throws Exception {
                // Busca apenas pastas diretas (sem recursão) do diretório atual
                SearchCriteria criteria = new SearchCriteria()
                        .withFileType(FileType.FOLDER)
                        .inPath(parentPath, false) // false = só pasta atual, sem subpastas
                        .sortBy("name", "ASC")
                        .limit(500);
                return controller.searchDirect(criteria);
            }

            @Override
            protected void done() {
                try {
                    List<FileInfo> folders = get();

                    // Filtra a própria pasta pai
                    folders = folders.stream()
                            .filter(f -> !f.getPath().equalsIgnoreCase(parentPath))
                            .toList();

                    if (folders.isEmpty()) {
                        SwingUtilities.invokeLater(() -> {
                            setVisible(false);
                            Container p = getParent();
                            if (p != null) p.revalidate();
                        });
                    } else {
                        populateList(folders);
                        titleLabel.setText("📁 Subpastas (" + folders.size() + ")");
                        SwingUtilities.invokeLater(() -> {
                            setVisible(true);
                            Container p = getParent();
                            if (p != null) p.revalidate();
                        });
                    }

                    revalidate();
                    repaint();

                    // Notifica o pai para reorganizar o layout
                    Container parent = getParent();
                    if (parent != null) parent.revalidate();

                } catch (Exception ex) {
                    setVisible(false);
                }
            }
        };
        worker.execute();
    }

    /**
     * Esconde o painel (ex: ao fazer uma busca textual).
     */
    public void hide() {
        if (!isVisible()) return;
        SwingUtilities.invokeLater(() -> {
            setVisible(false);
            Container p = getParent();
            if (p != null) p.revalidate();
        });
    }

    public void setFolderClickListener(FolderClickListener l) {
        this.clickListener = l;
    }

    // ── Renderização ──────────────────────────────────────────────

    private void populateList(List<FileInfo> folders) {
        listPanel.removeAll();
        listPanel.add(Box.createVerticalStrut(4));

        for (FileInfo fi : folders) {
            listPanel.add(createFolderRow(fi));
            listPanel.add(Box.createVerticalStrut(1));
        }

        listPanel.add(Box.createVerticalGlue());
        listPanel.revalidate();
        listPanel.repaint();
    }

    private JPanel createFolderRow(FileInfo fi) {
        File file = new File(fi.getPath());
        System.out.println(file.getName());



        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setBackground(BG_COLOR);
        row.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel iconLabel = new JLabel();
       // iconLabel.setIcon(resizeIcon(FSV.getSystemIcon(file), 16));
        iconLabel.setIcon(IconService.getIcon(file,"",FileType.FOLDER,32));
        iconLabel.setOpaque(false);
        // NOVO: propaga cursor para filhos
        iconLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        String displayName = fi.getName().isEmpty() ? fi.getPath() : fi.getName();
        JLabel nameLabel = new JLabel(displayName);
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        nameLabel.setToolTipText(fi.getPath());
        // NOVO: propaga cursor para filhos
        nameLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        row.add(iconLabel, BorderLayout.WEST);
        row.add(nameLabel, BorderLayout.CENTER);

        // NOVO: listener extraído para ser reutilizado em todos os componentes
        MouseAdapter rowListener = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                row.setBackground(HOVER_COLOR);
                iconLabel.setBackground(HOVER_COLOR);
                nameLabel.setBackground(HOVER_COLOR);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                // CORREÇÃO: só remove hover se o cursor saiu do row inteiro,
                // não apenas de um filho para outro filho
                Component dest = SwingUtilities.getDeepestComponentAt(
                        row.getParent(),
                        e.getXOnScreen() - row.getParent().getLocationOnScreen().x,
                        e.getYOnScreen() - row.getParent().getLocationOnScreen().y
                );
                // Se o destino ainda é o row ou um filho dele, não remove o hover
                if (dest != null && (dest == row || SwingUtilities.isDescendingFrom(dest, row))) {
                    return;
                }
                row.setBackground(BG_COLOR);
                iconLabel.setBackground(BG_COLOR);
                nameLabel.setBackground(BG_COLOR);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 1 && clickListener != null)
                    clickListener.onFolderClicked(file);
            }
        };

        // NOVO: aplica o listener no row E em todos os filhos
        row.addMouseListener(rowListener);
        iconLabel.addMouseListener(rowListener);
        nameLabel.addMouseListener(rowListener);

        return row;
    }

    private ImageIcon resizeIcon(Icon icon, int size) {
        if (icon == null) return null;
        BufferedImage img = new BufferedImage(
                icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        icon.paintIcon(null, g, 0, 0);
        g.dispose();
        return new ImageIcon(img.getScaledInstance(size, size, Image.SCALE_SMOOTH));
    }

    // ── Interface ─────────────────────────────────────────────────
    public interface FolderClickListener {
        void onFolderClicked(File folder);
    }
}