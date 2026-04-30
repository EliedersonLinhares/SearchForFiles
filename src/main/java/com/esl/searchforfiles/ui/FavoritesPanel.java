package com.esl.searchforfiles.ui;

import com.esl.searchforfiles.configuration.FileTransferHandler;
import com.esl.searchforfiles.model.FileType;
import com.esl.searchforfiles.service.FavoritesService;
import com.esl.searchforfiles.service.IconService;

import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import java.awt.*;
import java.awt.dnd.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
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
        clearButton.setFont(new Font("SansSerif", Font.PLAIN, 14));
        clearButton.addActionListener(e -> clearAllFavorites());
        bottomPanel.add(clearButton);
        add(bottomPanel, BorderLayout.SOUTH);

        // Carrega favoritos iniciais
        refreshFavorites();

        // Listener para mudanças nos favoritos
        favoritesService.addListener(this::refreshFavorites);

        setupDropTarget();
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


        JMenuItem defaultFolderItem = new JMenuItem("Definir como pasta padrão");
        defaultFolderItem.addActionListener(e -> {
            String selected = favoritesList.getSelectedValue();
            if (selected != null && !selected.equals("(Nenhum favorito)")) {
                fileExplorerSwing.getConfigManager().saveDefaulFolder(selected);
                System.out.println("Diretorio padrão salvo: " + selected);
            }
        });

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

        menu.add(defaultFolderItem);
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

    // Adicione este método e chame-o no construtor, após o setup da favoritesList
    private void setupDropTarget() {
        DropTargetListener dtl = new DropTargetAdapter() {

            @Override
            public void dragEnter(DropTargetDragEvent dtde) {
                if (dtde.isDataFlavorSupported(FileTransferHandler.FILE_FLAVOR)) {
                    dtde.acceptDrag(DnDConstants.ACTION_COPY);
                    // Feedback visual: borda destacada no painel
                    setBorder(BorderFactory.createTitledBorder(
                            BorderFactory.createLineBorder(new Color(33, 150, 243), 2),
                            "⭐ Favoritos"
                    ));
                } else {
                    dtde.rejectDrag();
                }
            }

            @Override
            public void dragExit(DropTargetEvent dte) {
                // Restaura borda original
                setBorder(BorderFactory.createTitledBorder("⭐ Favoritos"));
            }

            @Override
            public void drop(DropTargetDropEvent dtde) {
                // Restaura borda original
                setBorder(BorderFactory.createTitledBorder("⭐ Favoritos"));

                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);

                    @SuppressWarnings("unchecked")
                    List<File> files = (List<File>)
                            dtde.getTransferable().getTransferData(FileTransferHandler.FILE_FLAVOR);

                    if (files == null || files.isEmpty()) {
                        dtde.dropComplete(false);
                        return;
                    }

                    File dropped = files.get(0);

                    //veto a drives como C: ou D:
                    if (fileExplorerSwing.isDriveRoot(dropped.getAbsolutePath())) {
                        JOptionPane.showMessageDialog(FavoritesPanel.this,
                                "Drive raiz não pode ser adicionado aos favoritos!\n" + "Somente pastas.",
                                "Aviso", JOptionPane.WARNING_MESSAGE);
                        return;
                    }

                    // Só aceita pastas
                    if (!dropped.isDirectory()) {
                        JOptionPane.showMessageDialog(FavoritesPanel.this,
                                "Apenas pastas podem ser adicionadas aos favoritos.\n\n" +
                                        dropped.getName(),
                                "Tipo inválido", JOptionPane.WARNING_MESSAGE);
                        dtde.dropComplete(false);
                        return;
                    }

                    // Já é favorito?
                    if (favoritesService.isFavorite(dropped.getAbsolutePath())) {
                        JOptionPane.showMessageDialog(FavoritesPanel.this,
                                "Esta pasta já está nos favoritos!\n\n" +
                                        dropped.getName(),
                                "Aviso", JOptionPane.INFORMATION_MESSAGE);
                        dtde.dropComplete(false);
                        return;
                    }

                    // Adiciona
                    if (favoritesService.addFavorite(dropped.getAbsolutePath())) {
                        dtde.dropComplete(true);
                        System.out.println("⭐ Adicionado via drag: " + dropped.getAbsolutePath());
                    } else {
                        dtde.dropComplete(false);
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    dtde.dropComplete(false);
                }
            }
        };

        // Aplica o drop tanto na lista quanto no painel inteiro
        new DropTarget(favoritesList, dtl);
        new DropTarget(this, dtl);
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

                List<String> exclusion = List.of("Desktop","Downloads","Pictures","3D Objects","Documents"
                , "Favorites", "Meus Documentos", "Music", "OneDrive", "Recent" ,"Saved Games", "Search", "Videos");

                if(exclusion.stream().anyMatch( e -> e.equalsIgnoreCase(file.getName()))){
                    setIcon(fsv.getSystemIcon(file,32,32));
                }else {
                    setIcon(IconService.getIcon(file, "", FileType.FOLDER, 32));
                }

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
