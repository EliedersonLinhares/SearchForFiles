package com.esl.searchforfiles.ui;

import com.esl.searchforfiles.service.FavoritesService;

import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.List;

/**
 * Painel com JList de pastas favoritas
 */
public class FavoritesPanel extends JPanel {
    private final JList<String> favoritesList;
    private final DefaultListModel<String> listModel;
    private final FavoritesService favoritesService;
    private FavoriteSelectionListener selectionListener;
    private FileExplorerSwing fileExplorerSwing;

    public FavoritesPanel(FavoritesService favoritesManager, FileExplorerSwing fileExplorerSwing) {
        this.favoritesService = favoritesManager;
        this.fileExplorerSwing = fileExplorerSwing;

        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("⭐ Favoritos"));
        setPreferredSize(new Dimension(300, 200));

        // Modelo da lista
        listModel = new DefaultListModel<>();

        // JList
        favoritesList = new JList<>(listModel);
        favoritesList.setFont(new Font("SansSerif", Font.PLAIN, 12));
        favoritesList.setCellRenderer(new FavoritesCellRenderer());
        favoritesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // CORREÇÃO: MouseListener em vez de ListSelectionListener
        // Isso garante que SEMPRE dispara ao clicar, mesmo no item já selecionado
        favoritesList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                // Ignora clique direito (será tratado pelo menu contexto)
                if (SwingUtilities.isRightMouseButton(e)) {
                    return;
                }

                // Obtém o índice clicado
                int index = favoritesList.locationToIndex(e.getPoint());

                if (index >= 0 && index < listModel.getSize()) {
                    String selected = listModel.getElementAt(index);

                    // Verifica se não é o item placeholder
                    if (selected != null &&
                            !selected.equals("(Nenhum favorito)") &&
                            selectionListener != null) {

                        // Força seleção visual
                        favoritesList.setSelectedIndex(index);

                        // Dispara evento
                        File folder = new File(selected);
                        if (folder.exists() && folder.isDirectory()) {
                            selectionListener.onFavoriteSelected(folder);
                            System.out.println("⭐ Favorito clicado: " + selected);
                            fileExplorerSwing.performCurrentSearch();
                        } else {
                            JOptionPane.showMessageDialog(FavoritesPanel.this,
                                    "Esta pasta não existe mais:\n" + selected,
                                    "Pasta Inválida", JOptionPane.WARNING_MESSAGE);
                        }
                    }
                }
            }
        });

        // Menu de contexto (botão direito)
        JPopupMenu contextMenu = createContextMenu();

        favoritesList.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showContextMenu(e);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showContextMenu(e);
                }
            }

            private void showContextMenu(MouseEvent e) {
                int index = favoritesList.locationToIndex(e.getPoint());
                if (index >= 0) {
                    favoritesList.setSelectedIndex(index);
                    String selected = listModel.getElementAt(index);

                    // Só mostra menu se não for o placeholder
                    if (!selected.equals("(Nenhum favorito)")) {
                        contextMenu.show(e.getComponent(), e.getX(), e.getY());
                    }
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(favoritesList);
        add(scrollPane, BorderLayout.CENTER);

        // Painel inferior com botão limpar
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 5));
        JButton clearButton = new JButton("Limpar Todos");
        clearButton.setFont(new Font("SansSerif", Font.PLAIN, 10));
        clearButton.addActionListener(e -> clearAllFavorites());
        bottomPanel.add(clearButton);
        add(bottomPanel, BorderLayout.SOUTH);

        // Carrega favoritos iniciais
        refreshFavorites();

        // Listener para mudanças nos favoritos
        favoritesService.addListener(this::refreshFavorites);
    }

    /**
     * Atualiza lista de favoritos
     */
    private void refreshFavorites() {
        listModel.clear();
        List<String> favorites = favoritesService.getFavorites();

        if (favorites.isEmpty()) {
            listModel.addElement("(Nenhum favorito)");
            favoritesList.setEnabled(false);
        } else {
            for (String favorite : favorites) {
                listModel.addElement(favorite);
            }
            favoritesList.setEnabled(true);
        }
    }

    /**
     * Cria menu de contexto
     */
    private JPopupMenu createContextMenu() {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem openItem = new JMenuItem("Abrir no Explorer");
        openItem.addActionListener(e -> {
            String selected = favoritesList.getSelectedValue();
            if (selected != null && !selected.equals("(Nenhum favorito)")) {
                try {
                    Desktop.getDesktop().open(new File(selected));
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this,
                            "Erro ao abrir: " + ex.getMessage(),
                            "Erro", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        JMenuItem removeItem = new JMenuItem("Remover dos Favoritos");
        removeItem.addActionListener(e -> {
            String selected = favoritesList.getSelectedValue();
            if (selected != null && !selected.equals("(Nenhum favorito)")) {
                int confirm = JOptionPane.showConfirmDialog(this,
                        "Remover dos favoritos?\n\n" + selected,
                        "Confirmar Remoção",
                        JOptionPane.YES_NO_OPTION);

                if (confirm == JOptionPane.YES_OPTION) {
                    favoritesService.removeFavorite(selected);
                }
            }
        });

        menu.add(openItem);
        menu.addSeparator();
        menu.add(removeItem);

        return menu;
    }

    /**
     * Limpa todos os favoritos
     */
    private void clearAllFavorites() {
        if (favoritesService.getFavorites().isEmpty()) {
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "Remover TODOS os favoritos?",
                "Confirmar",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            favoritesService.clearFavorites();
        }
    }

    public void setSelectionListener(FavoriteSelectionListener listener) {
        this.selectionListener = listener;
    }

    public interface FavoriteSelectionListener {
        void onFavoriteSelected(File folder);
    }

    /**
     * Renderizador customizado para células
     */
    private static class FavoritesCellRenderer extends DefaultListCellRenderer {
        private final FileSystemView fsv = FileSystemView.getFileSystemView();

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            String path = (String) value;

            if (path.equals("(Nenhum favorito)")) {
                setIcon(null);
                setForeground(Color.GRAY);
                setFont(getFont().deriveFont(Font.ITALIC));
            } else {
                File file = new File(path);
                setIcon(fsv.getSystemIcon(file));

                // Mostra apenas o nome da pasta
                String displayName = file.getName();
                if (displayName.isEmpty()) {
                    displayName = path; // Raiz de drive
                }
                setText(displayName);
                setToolTipText(path);
            }

            return this;
        }
    }
}
