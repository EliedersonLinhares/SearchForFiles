package com.esl.searchforfiles.ui;

import com.esl.searchforfiles.actions.fileTransfer.TransferMode;
import com.esl.searchforfiles.actions.fileTransfer.TransferService;
import com.esl.searchforfiles.actions.imageEditor.EditModeManager;
import com.esl.searchforfiles.actions.imageEditor.ImageEditorFrame;
import com.esl.searchforfiles.actions.renameFile.RenameFrame;
import com.esl.searchforfiles.actions.renameFile.RenameModeManager;
import com.esl.searchforfiles.cache.thumbnail.ThumbnailCacheManager;
import com.esl.searchforfiles.configuration.UIConfig;
import com.esl.searchforfiles.model.FileInfo;
import com.esl.searchforfiles.others.ThumbnailSize;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.*;
import java.util.List;

/**
 * Painel para exibir resultados da busca em grid
 * ATUALIZADO: Inclui menu de contexto para gerenciar cache de thumbnails
 */
public class ResultsPanel extends JPanel {

    private final JPanel gridPanel;
    private final JScrollPane scrollPane;
    // NOVO: Gerenciador de cache de thumbnails
    private final ThumbnailCacheManager cacheManager;
    private final FileExplorerSwing fileExplorerSwing;
    private final Set<File> savedTransferSelected = new HashSet<>();
    private final Set<File> savedEditSelected = new HashSet<>();
    private final Set<File> savedRenameSelected = new HashSet<>();
    private FileItemClickListener clickListener;
    // Armazena últimos resultados para re-renderizar ao redimensionar
    private List<FileInfo> lastResults;
    // Cor de fundo customizável
//    private Color backgroundColor = new Color(245, 245, 250); // Cinza azulado claro
    private ThumbnailSize currentThumbSize = ThumbnailSize.MEDIO; // NOVO
    private int selectedIndex = -1;  // índice do item selecionado no grid
    private List<FileItemPanel> currentItems = new ArrayList<>(); // refs aos panels
    private JScrollBar vBar;
    private KeyEventDispatcher keyDispatcher;
    private TransferService transferService;
    private JToolBar transferToolBar;
    private EditModeManager editModeManager;
    private JToolBar editToolBar;
    private FileItemPanel item;
    private RenameModeManager renameModeManager;
    private JToolBar renameToolBar;

    public ResultsPanel(FileExplorerSwing fileExplorerSwing) {
        this.fileExplorerSwing = fileExplorerSwing;
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("\uD83D\uDDBC Resultados"));

        gridPanel = new JPanel(null) {
            @Override
            protected void paintComponent(Graphics g) {
                // Limpa toda a área antes de redesenhar os filhos
                g.setColor(getBackground());
                g.fillRect(0, 0, getWidth(), getHeight());
                super.paintComponent(g);
            }
        };
        gridPanel.setOpaque(true);           // IMPORTANTE: deve ser opaco
        gridPanel.setBackground(getBackgroundColor());
        gridPanel.setFocusable(true);

        // NOVO: Inicializa o gerenciador de cache
        this.cacheManager = FileItemPanel.getThumbnailCacheManager();

        scrollPane = new JScrollPane(gridPanel);
        scrollPane.getVerticalScrollBar().setUnitIncrement(20);
        vBar = scrollPane.getVerticalScrollBar();
        add(scrollPane, BorderLayout.CENTER);


        // Listener para redimensionamento
        // CORREÇÃO: Listener otimizado para redimensionamento fluido
        setupResizeListener();

        // NOVO: Configura menu de contexto do cache
        setupCacheContextMenu();

        setupKeyboardScroll();

        getFileExplorerSwing().getThemeManager().addThemeChangeListener(() ->
                SwingUtilities.invokeLater(() -> {
                    gridPanel.setBackground(getBackgroundColor());
                    // outros componentes com cor customizada...
                    revalidate();
                    repaint();
                })
        );

    }

    public FileExplorerSwing getFileExplorerSwing() {
        return fileExplorerSwing;
    }

    /**
     * Obtém cor de fundo atual
     * NOVO MÉTODO
     */
//    public Color getBackgroundColor() {
//        // Tenta pegar a cor de painel do tema atual
//        Color themed = UIManager.getColor("Panel.background");
//        if (themed != null) {
//            if (fileExplorerSwing.getThemeManager().getCurrentTheme().contentEquals("FlatArcOrangeIJTheme")) {
//               return new Color(245, 245, 250);
//            } else if (fileExplorerSwing.getThemeManager().getCurrentTheme().contentEquals("FlatArcDarkOrangeIJTheme")) {
//                return  new Color(40, 40, 50);
//            } else if (fileExplorerSwing.getThemeManager().getCurrentTheme().contentEquals("FlatDraculaIJTheme")) {
//              return new  Color(40, 40, 50);
//            }
//        }
//        return  new  Color(40, 40, 50); // fallback para quando não há tema
//    }
    public Color getBackgroundColor() {
        Color bg = UIManager.getColor("Panel.background");
        if (bg == null) return new Color(40, 40, 50);

        double luminance = 0.299 * bg.getRed() + 0.587 * bg.getGreen() + 0.114 * bg.getBlue();
        boolean isDark = luminance < 128;

        return isDark
                ? new Color(40, 40, 50)
                : new Color(245, 245, 250);
    }

    public void setupKeyboardScroll() {
//        JScrollBar vBar = scrollPane.getVerticalScrollBar();
        int unit = 60;
        int block = 300;

        keyDispatcher = e -> {
            // Só processa KEY_PRESSED
            if (e.getID() != KeyEvent.KEY_PRESSED) return false;

            // Se o foco está num campo de texto, não intercepta nenhuma tecla
            Component focused = KeyboardFocusManager
                    .getCurrentKeyboardFocusManager().getFocusOwner();

            // ✅ GUARDA PRINCIPAL: só age se o foco estiver no ResultsPanel
            // ou em qualquer filho dele (gridPanel, scrollPane, etc.)
            boolean focusIsHere = focused != null &&
                    SwingUtilities.isDescendingFrom(focused, ResultsPanel.this);

            if (!focusIsHere) return false; // ← devolve o evento para quem tem o foco


            // Teclas de navegação de item só são bloqueadas por JTextField
            boolean isTextField = focused instanceof JTextField
                    || focused instanceof JTextArea;

            // ← → sempre requerem que o foco NÃO esteja num campo de texto
            if (isTextField) {
                // Deixa todas as teclas passarem quando digitando
                return false;
            }

            int key = e.getKeyCode();

            switch (key) {

                // ── Navegação entre itens ─────────────────────────────
                case KeyEvent.VK_LEFT -> {
                    if (currentItems.isEmpty()) return false;
                    moveSelection(selectedIndex < 0 ? 0 : -1);
                    return true;
                }
                case KeyEvent.VK_RIGHT -> {
                    if (currentItems.isEmpty()) return false;
                    moveSelection(selectedIndex < 0 ? 0 : +1);
                    return true;
                }

                // ── ↑ ↓: move item se há seleção, senão faz scroll ───
                case KeyEvent.VK_UP -> {
                    if (!currentItems.isEmpty() && selectedIndex >= 0) {
                        moveSelection(-getItemsPerRow());
                    } else {
                        vBar.setValue(Math.max(vBar.getMinimum(),
                                vBar.getValue() - unit));
                    }
                    return true;
                }
                case KeyEvent.VK_DOWN -> {
                    if (!currentItems.isEmpty() && selectedIndex >= 0) {
                        moveSelection(+getItemsPerRow());
                    } else {
                        vBar.setValue(Math.min(
                                vBar.getMaximum() - vBar.getVisibleAmount(),
                                vBar.getValue() + unit));
                    }
                    return true;
                }

                // ── Scroll de bloco ───────────────────────────────────
                case KeyEvent.VK_PAGE_UP -> {
                    vBar.setValue(Math.max(vBar.getMinimum(),
                            vBar.getValue() - block));
                    return true;
                }
                case KeyEvent.VK_PAGE_DOWN -> {
                    vBar.setValue(Math.min(
                            vBar.getMaximum() - vBar.getVisibleAmount(),
                            vBar.getValue() + block));
                    return true;
                }

                // ── Ir ao topo / final ────────────────────────────────
                case KeyEvent.VK_HOME -> {
                    topScroll();
                    return true;
                }
                case KeyEvent.VK_END -> {
                    vBar.setValue(vBar.getMaximum() - vBar.getVisibleAmount());
                    return true;
                }

                // ── Enter abre item selecionado ───────────────────────
                case KeyEvent.VK_ENTER -> {
                    if (selectedIndex >= 0 && selectedIndex < currentItems.size()) {
                        FileItemPanel item = currentItems.get(selectedIndex);
                        if (clickListener != null)
                            clickListener.onFileDoubleClick(item.getFile());
                        return true;
                    }
                    return false; // sem seleção: Enter chega ao searchField
                }

                // ── Escape limpa seleção ──────────────────────────────
                case KeyEvent.VK_ESCAPE -> {
                    if (selectedIndex >= 0 && selectedIndex < currentItems.size()) {
                        currentItems.get(selectedIndex).setSelected(false);
                        selectedIndex = -1;
                        return true;
                    }
                    return false;
                }
            }

            return false;
        };

        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(keyDispatcher);
    }

    public void topScroll() {
        vBar.setValue(vBar.getMinimum());
    }


    public void dispose() {
        if (keyDispatcher != null) {
            KeyboardFocusManager.getCurrentKeyboardFocusManager()
                    .removeKeyEventDispatcher(keyDispatcher);
            keyDispatcher = null;
        }
    }

    /**
     * Move a seleção delta posições, respeitando os limites.
     */
    private void moveSelection(int delta) {
        if (currentItems.isEmpty()) return;
        int next = selectedIndex < 0 ? 0
                : Math.max(0, Math.min(currentItems.size() - 1, selectedIndex + delta));
        selectItem(next);
    }

    /**
     * Calcula quantos itens cabem por linha com base no tamanho atual.
     */
    private int getItemsPerRow() {
        int panelWidth = scrollPane.getViewport().getWidth();
        int cardWidth = currentThumbSize.thumbPx + 20;
        return Math.max(1, (panelWidth - 12) / (cardWidth + 12));
    }

    /**
     * Configura listener para redimensionamento fluido
     * NOVO MÉTODO
     */
    private void setupResizeListener() {
        // Timer para debounce do redimensionamento
        Timer resizeTimer = new Timer(150, e -> {
            if (lastResults != null && !lastResults.isEmpty()) {
                renderGrid(lastResults);
            }
        });
        resizeTimer.setRepeats(false);

        // Listener de redimensionamento
        scrollPane.getViewport().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                // Debounce: só re-renderiza 150ms após parar de redimensionar
                resizeTimer.restart();
            }
        });
    }

    /**
     * NOVO: Configura menu de contexto para gerenciar cache
     */
    private void setupCacheContextMenu() {
        // Adiciona listener de mouse ao gridPanel
        gridPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showCacheMenu(e);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showCacheMenu(e);
                }
            }

            private void showCacheMenu(MouseEvent e) {
                // Só mostra o menu se não clicar em um FileItemPanel
                Component comp = gridPanel.getComponentAt(e.getPoint());
                if (comp == gridPanel || comp == null) {
                    CacheContextMenu.show(gridPanel, cacheManager, e.getX(), e.getY());
                }
            }
        });

        // OPCIONAL: Adiciona atalho de teclado (Ctrl+Shift+C)
        InputMap inputMap = getInputMap(WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = getActionMap();

        KeyStroke cacheKeyStroke = KeyStroke.getKeyStroke(
                KeyEvent.VK_C,
                InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK
        );

        inputMap.put(cacheKeyStroke, "showCacheMenu");
        actionMap.put("showCacheMenu", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // Mostra menu no centro do painel
                int x = gridPanel.getWidth() / 2;
                int y = gridPanel.getHeight() / 2;
                CacheContextMenu.show(gridPanel, cacheManager, x, y);
            }
        });
    }

    public void setThumbnailSize(ThumbnailSize size) {
        this.currentThumbSize = size;
        // Limpa cache de ícones para forçar re-render no novo tamanho
        FileItemPanel.ICON_CACHE.clear();
        if (lastResults != null && !lastResults.isEmpty()) {
            renderGrid(lastResults);
        }
    }


    //    /**
//     * Exibe resultados no grid
//     * MODIFICADO: Armazena resultados para re-renderização
//     */
    public void showResults(List<FileInfo> results) {
        this.lastResults = results;
        renderGrid(results);
    }

    private void renderGrid(List<FileInfo> results) {

        // ── Salva seleções ativas antes de recriar os itens ──────────
        savedTransferSelected.clear();
        savedEditSelected.clear();
        savedRenameSelected.clear();

        for (FileItemPanel p : currentItems) {
            if (p.selectionCheckbox != null && p.selectionCheckbox.isSelected())
                savedTransferSelected.add(p.getDisplayFile());
            if (p.editSelectionCheckbox != null && p.editSelectionCheckbox.isSelected())
                savedEditSelected.add(p.getDisplayFile());
            if (p.renameSelectionCheckbox != null && p.renameSelectionCheckbox.isSelected())
                savedRenameSelected.add(p.getDisplayFile());
        }


        gridPanel.removeAll();
        gridPanel.setLayout(null);
        gridPanel.setBackground(getBackgroundColor());

        currentItems.clear();   // NOVO: limpa lista de itens
        selectedIndex = -1;     // NOVO: reseta seleção

        int panelWidth = scrollPane.getViewport().getWidth();
        if (panelWidth <= 0) panelWidth = getWidth();
        if (panelWidth <= 100) {
            SwingUtilities.invokeLater(() -> renderGrid(results));
            return;
        }

        int spacing = 12;
        int cardWidth = currentThumbSize.thumbPx + 20;
        int cardHeight = currentThumbSize.cardHeight;

        int itemsPerRow = Math.max(1, (panelWidth - spacing) / (cardWidth + spacing));
        int dynamicWidth = (panelWidth - (itemsPerRow + 1) * spacing) / itemsPerRow;

        int x = spacing, y = spacing, count = 0;

        for (FileInfo fileInfo : results) {
            File file = new File(fileInfo.getPath());
            if (!file.exists()) continue;

            item = new FileItemPanel(
                    file, fileInfo, dynamicWidth, cardHeight,
                    currentThumbSize.thumbPx, this);
            item.setBounds(x, y, dynamicWidth, cardHeight);

            if (clickListener != null) item.setClickListener(clickListener);

            // NOVO: clique simples seleciona o item
            final int idx = count;
            item.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (!e.isPopupTrigger())
                        selectItem(idx);
                }
            });

            gridPanel.add(item);
            currentItems.add(item);   // NOVO

            count++;
            if (count % itemsPerRow == 0) {
                x = spacing;
                y += cardHeight + spacing;
            } else {
                x += dynamicWidth + spacing;
            }
        }

        int totalRows = (int) Math.ceil((double) count / itemsPerRow);
        int totalHeight = spacing + totalRows * (cardHeight + spacing);
        gridPanel.setPreferredSize(new Dimension(panelWidth, totalHeight));
        gridPanel.revalidate();
        gridPanel.repaint();

        reapplyTransferModeIfActive();
        reapplyEditModeIfActive();
        reapplyRenameModeIfActive();

        // NOVO: requisita foco após renderizar
        // invokeLater garante que o layout já terminou antes de pedir foco
        SwingUtilities.invokeLater(gridPanel::requestFocusInWindow);
    }


    // ── Método de seleção ─────────────────────────────────────────────
    private void selectItem(int index) {
        if (index < 0 || index >= currentItems.size()) return;

        // Desmarca anterior
        if (selectedIndex >= 0 && selectedIndex < currentItems.size())
            currentItems.get(selectedIndex).setSelected(false);

        selectedIndex = index;
        currentItems.get(selectedIndex).setSelected(true);

        // Garante visibilidade do item selecionado no scroll
        scrollToItem(currentItems.get(selectedIndex));
    }

    private void scrollToItem(FileItemPanel item) {
        Rectangle bounds = item.getBounds();

        // Expande a região visível com uma margem ao redor do item
        // para garantir que a borda de seleção (2px) fique totalmente visível
        Rectangle expanded = new Rectangle(
                bounds.x - 4,
                bounds.y - 4,
                bounds.width + 8,
                bounds.height + 8
        );

        gridPanel.scrollRectToVisible(expanded);

        // NOVO: aguarda o scroll terminar antes de repintar
        // invokeLater coloca o repaint no fim da fila de eventos,
        // depois que o viewport já atualizou sua posição
        SwingUtilities.invokeLater(() -> {
            gridPanel.repaint();

            // Segunda passagem para garantir limpeza total
            // (necessário quando o scroll é grande)
            SwingUtilities.invokeLater(() -> gridPanel.repaint());
        });
    }

    /**
     * Exibe mensagem centralizada
     * MODIFICADO: Usa backgroundColor
     */
    public void showMessage(String message, MessageType type) {
        this.lastResults = null; // Limpa últimos resultados

        gridPanel.removeAll();
        gridPanel.setLayout(new BorderLayout());
        gridPanel.setBackground(getBackgroundColor());

        JPanel messagePanel = createMessagePanel(message, type);
        gridPanel.add(messagePanel, BorderLayout.CENTER);
        gridPanel.revalidate();
        gridPanel.repaint();
    }

    /**
     * Cria painel de mensagem
     * MODIFICADO: Usa backgroundColor
     */
    private JPanel createMessagePanel(String message, MessageType type) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(getBackgroundColor());

        String icon = switch (type) {
            case WELCOME -> "🔍";
            case LOADING -> "⏳";
            case NO_RESULTS -> "❌";
            case ERROR -> "⚠️";
        };

        JLabel iconLabel = new JLabel(icon);
        iconLabel.setFont(UIConfig.FONT_MESSAGE_ICON);
        iconLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel messageLabel = new JLabel(message);
        messageLabel.setFont(UIConfig.FONT_MESSAGE_TEXT);
        messageLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        panel.add(Box.createVerticalGlue());
        panel.add(iconLabel);
        panel.add(Box.createVerticalStrut(10));
        panel.add(messageLabel);
        panel.add(Box.createVerticalGlue());

        return panel;
    }

    /**
     * Força re-renderização dos resultados atuais
     * NOVO MÉTODO: Útil para atualizar após mudanças de tema
     */
    public void refresh() {
        if (lastResults != null && !lastResults.isEmpty()) {
            renderGrid(lastResults);
        }
    }


    public void setFileItemClickListener(FileItemClickListener listener) {
        this.clickListener = listener;
    }

    /**
     * Chamado por FileExplorerSwing quando o botão "Selecionar" é pressionado.
     */
    public void enterTransferMode(TransferService tm) {
        this.transferService = tm;
        tm.enterTransferMode();

        // Mostra barra de ferramentas de transferência
        if (transferToolBar == null) {
            transferToolBar = buildTransferToolBar(tm);
            add(transferToolBar, BorderLayout.NORTH);
            revalidate();
        }

        applyTransferModeToItems(tm);
    }

    public void exitTransferMode() {
        if (transferService != null) transferService.exitTransferMode();
        transferService = null;

        // Remove a toolbar
        if (transferToolBar != null) {
            remove(transferToolBar);
            transferToolBar = null;
            revalidate();
        }

        // Remove checkboxes de todos os cards
        for (FileItemPanel item : currentItems) item.disableTransferMode();
        repaint();
    }

    private void applyTransferModeToItems(TransferService tm) {
        for (FileItemPanel item : currentItems) item.enableTransferMode(tm);
    }

    /**
     * Chamado no final de renderGrid() para re-aplicar o modo
     * de transferência caso esteja ativo.
     */
//    private void reapplyTransferModeIfActive() {
//        if (transferService != null && transferService.isTransferModeActive()) {
//            applyTransferModeToItems(transferService);
//        }
//    }
    private void reapplyTransferModeIfActive() {
        if (transferService != null && transferService.isTransferModeActive()) {
            for (FileItemPanel item : currentItems) {
                item.enableTransferMode(transferService);
                if (savedTransferSelected.contains(item.getDisplayFile())) {
                    transferService.selectFile(item.getDisplayFile()); // seleciona no service
                    if (item.selectionCheckbox != null)
                        item.selectionCheckbox.setSelected(true);
                    item.updateModeBackground(); // ← aplica a cor
                }
            }
        }
    }

    /**
     * Barra de ferramentas exibida no topo do ResultsPanel durante o modo
     * de transferência. Fornece "Selecionar todos", "Limpar" e "Sair".
     */
    private JToolBar buildTransferToolBar(TransferService tm) {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        // bar.setBackground(new Color(33, 33, 60));

        JLabel lbl = new JLabel(" ✂️  Modo de Transferência ");
        lbl.setFont(UIConfig.FONT_TITLE);
        bar.add(lbl);

        bar.addSeparator();

        JButton selectAll = new JButton(" Selecionar todos ");
        selectAll.setFont(UIConfig.FONT_DEFAULT);
        selectAll.addActionListener(e -> {
            List<File> files = currentItems.stream()
                    .map(FileItemPanel::getDisplayFile)
                    .toList();
            tm.selectAll(files);
            currentItems.forEach(item -> {
                if (item.selectionCheckbox != null) {
                    item.selectionCheckbox.setSelected(true);
                    item.updateModeBackground();
                    clearItemBackground();
                }
            });
        });
        bar.add(selectAll);

        JButton clearSel = new JButton(" Limpar seleção ");
        clearSel.setFont(UIConfig.FONT_DEFAULT);
        clearSel.addActionListener(e -> {
            tm.clearSelection();
            currentItems.forEach(item -> {
                if (item.selectionCheckbox != null){
                    item.selectionCheckbox.setSelected(false);
                    item.updateModeBackground();
                    clearItemBackground();
                }
            });
        });
        bar.add(clearSel);
        bar.addSeparator();
        JButton copy = new JButton(" Copiar ");
        copy.setFont(UIConfig.FONT_DEFAULT);
        copy.setForeground(UIConfig.SELECTED_BORDER);
        copy.addActionListener(e -> item.requestTransfer(TransferMode.COPY));

        bar.add(copy);
        JButton move = new JButton(" Mover ");
        move.setFont(UIConfig.FONT_DEFAULT);
        move.setForeground(UIConfig.SELECTED_BORDER);
        move.addActionListener(e -> item.requestTransfer(TransferMode.MOVE));
        bar.add(move);

        JButton delete = new JButton(" Apagar ");
        delete.setFont(UIConfig.FONT_DEFAULT);
        delete.setForeground(UIConfig.SELECTED_BORDER);
        delete.addActionListener(e -> item.requestDelete());
        bar.add(delete);

        bar.addSeparator();

        JButton exitBtn = new JButton(" ✕ Sair ");
        exitBtn.setFont(UIConfig.FONT_DEFAULT);
        exitBtn.setForeground(UIConfig.LIGHT_RED);
        exitBtn.addActionListener(e -> exitTransferMode());
        bar.add(exitBtn);

        return bar;
    }

    public void enterEditMode(EditModeManager em) {
        this.editModeManager = em;
        em.enterEditMode();

        if (editToolBar == null) {
            editToolBar = buildEditToolBar(em);
            add(editToolBar, BorderLayout.NORTH);
            revalidate();
        }
        applyEditModeToItems(em);
    }

    public void exitEditMode() {
        if (editModeManager != null) editModeManager.exitEditMode();
        editModeManager = null;

        if (editToolBar != null) {
            remove(editToolBar);
            editToolBar = null;
            revalidate();
        }
        for (FileItemPanel item : currentItems) item.disableEditMode();
        repaint();
    }

    private void applyEditModeToItems(EditModeManager em) {
        for (FileItemPanel item : currentItems) item.enableEditMode(em);
    }

    /**
     * Reaplica o modo de edição após re-render do grid.
     */
    private void reapplyEditModeIfActive() {
        if (editModeManager != null && editModeManager.isEditModeActive()) {
            for (FileItemPanel item : currentItems) {
                item.enableEditMode(editModeManager);
                if (savedEditSelected.contains(item.getDisplayFile())) {
                    editModeManager.selectFile(item.getDisplayFile());
                    if (item.editSelectionCheckbox != null)
                        item.editSelectionCheckbox.setSelected(true);
                    item.updateModeBackground();
                }
            }
        }
    }

    public void openConfiguration() {
        new ConfigurationFrame(SwingUtilities.getWindowAncestor(this), this, getFileExplorerSwing().getThemeManager(),
                getFileExplorerSwing());
    }

    private JToolBar buildEditToolBar(EditModeManager em) {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        //   bar.setBackground(new Color(20, 80, 60));

        JLabel lbl = new JLabel("  🖼  Modo de Edição ");
        lbl.setFont(UIConfig.FONT_TITLE);
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD));
        bar.add(lbl);

        bar.addSeparator();

        JButton selectAll = new JButton(" Selecionar todos ");
        selectAll.setFont(UIConfig.FONT_DEFAULT);
        selectAll.addActionListener(e -> {
            List<File> files = currentItems.stream()
                    .map(FileItemPanel::getDisplayFile)
                    .toList();
            em.selectAll(files);
            currentItems.forEach(item -> {
                if (EditModeManager.isAccepted(item.getDisplayFile())
                        && item.editSelectionCheckbox != null) {
                    item.editSelectionCheckbox.setSelected(true);
                    item.updateModeBackground();
                    clearItemBackground();
                }
            });
        });
        bar.add(selectAll);

        JButton clearSel = new JButton(" Limpar seleção ");
        clearSel.setFont(UIConfig.FONT_DEFAULT);
        clearSel.addActionListener(e -> {
            em.clearSelection();
            currentItems.forEach(item -> {
                if (item.editSelectionCheckbox != null) {
                    item.editSelectionCheckbox.setSelected(false);
                    item.updateModeBackground();
                    clearItemBackground();
                }
            });
        });
        bar.add(clearSel);

        bar.addSeparator();

        JButton openEditor = new JButton(" 🖊 Abrir com o editor ");
        openEditor.setFont(UIConfig.FONT_DEFAULT);
        openEditor.setForeground(UIConfig.SELECTED_BORDER);
        openEditor.setFocusPainted(false);
        openEditor.addActionListener(e -> {
            if (em.getSelectedCount() == 0) {
                JOptionPane.showMessageDialog(
                        SwingUtilities.getWindowAncestor(this),
                        "Selecione ao menos uma imagem.",
                        "Aviso", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            new ImageEditorFrame(
                    SwingUtilities.getWindowAncestor(this),
                    this,
                    new ArrayList<>(em.getSelectedFiles()));
        });
        bar.add(openEditor);

        bar.addSeparator();

        JButton exitBtn = new JButton(" ✕ Sair ");
        exitBtn.setFont(UIConfig.FONT_DEFAULT);
        exitBtn.setForeground(UIConfig.LIGHT_RED);
        exitBtn.addActionListener(e -> exitEditMode());
        bar.add(exitBtn);

        return bar;
    }

    private void clearItemBackground() {
        Container parent = getParent();
        if (parent != null) {
            // Invalida a região do pai que cobre este componente
            // incluindo 2px extras para cobrir a borda anterior
            parent.repaint(
                    getX() - 2,
                    getY() - 2,
                    getWidth() + 4,
                    getHeight() + 4
            );
        }
    }

    public void enterRenameMode(RenameModeManager rm) {
        this.renameModeManager = rm;
        rm.enterMode();

        if (renameToolBar == null) {
            renameToolBar = buildRenameToolBar(rm);
            add(renameToolBar, BorderLayout.NORTH);
            revalidate();
        }
        applyRenameModeToItems(rm);
    }

    public void exitRenameMode() {
        if (renameModeManager != null) renameModeManager.exitMode();
        renameModeManager = null;

        if (renameToolBar != null) {
            remove(renameToolBar);
            renameToolBar = null;
            revalidate();
        }
        for (FileItemPanel item : currentItems) item.disableRenameMode();
        repaint();
    }

    private void applyRenameModeToItems(RenameModeManager rm) {
        for (FileItemPanel item : currentItems) item.enableRenameMode(rm);
    }

    // Adicione ao final de renderGrid(), após reapplyEditModeIfActive():
//    private void reapplyRenameModeIfActive() {
//        if (renameModeManager != null && renameModeManager.isActive())
//            applyRenameModeToItems(renameModeManager);
//    }
    private void reapplyRenameModeIfActive() {
        if (renameModeManager != null && renameModeManager.isActive()) {
            for (FileItemPanel item : currentItems) {
                item.enableRenameMode(renameModeManager);
                if (savedRenameSelected.contains(item.getDisplayFile())) {
                    renameModeManager.selectFile(item.getDisplayFile());
                    if (item.renameSelectionCheckbox != null)
                        item.renameSelectionCheckbox.setSelected(true);
                    item.updateModeBackground();
                }
            }
        }
    }

    private JToolBar buildRenameToolBar(RenameModeManager rm) {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);

        JLabel lbl = new JLabel("  " + rm.getMode().toolbarLabel + "  ");
        lbl.setFont(UIConfig.FONT_TITLE);
        bar.add(lbl);
        bar.addSeparator();

        JButton selectAll = new JButton(" Selecionar todos ");
        selectAll.setFont(UIConfig.FONT_DEFAULT);
        selectAll.addActionListener(e -> {
            List<File> files = currentItems.stream()
                    .map(FileItemPanel::getDisplayFile).toList();
            rm.selectAll(files);
            currentItems.forEach(item -> {
                if (rm.accepts(item.getDisplayFile())
                        && item.renameSelectionCheckbox != null) {
                    item.renameSelectionCheckbox.setSelected(true);
                    item.updateModeBackground();
                    clearItemBackground();
                }
            });
        });
        bar.add(selectAll);

        JButton clearSel = new JButton(" Limpar seleção ");
        clearSel.setFont(UIConfig.FONT_DEFAULT);
        clearSel.addActionListener(e -> {
            rm.clearSelection();
            currentItems.forEach(item -> {
                if (item.renameSelectionCheckbox != null){
                    item.renameSelectionCheckbox.setSelected(false);
                    item.updateModeBackground();
                    clearItemBackground();
                }
            });

        });
        bar.add(clearSel);
        bar.addSeparator();

        JButton renameBtn = new JButton(" ✏ Renomear ");
        renameBtn.setFont(UIConfig.FONT_DEFAULT_BOLD);
        renameBtn.setForeground(UIConfig.SELECTED_BORDER);
        renameBtn.setToolTipText("Clique para Renomear");
        renameBtn.addActionListener(e -> {
            if (rm.getSelectedCount() == 0) {
                JOptionPane.showMessageDialog(
                        SwingUtilities.getWindowAncestor(this),
                        "Selecione ao menos um item.", "Aviso",
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            // Converte File → FileInfo para o RenameFrame
            List<FileInfo> infos = rm.getSelectedFiles().stream()
                    .map(f -> {
                        // Busca o FileInfo correspondente nos items renderizados
                        return currentItems.stream()
                                .filter(p -> p.getDisplayFile().equals(f))
                                .map(FileItemPanel::getFileInfo)
                                .findFirst().orElse(null);
                    })
                    .filter(Objects::nonNull)
                    .toList();

            new RenameFrame(SwingUtilities.getWindowAncestor(this),
                    rm.getMode(), infos, this);
        });
        bar.add(renameBtn);
        bar.addSeparator();

        JButton exitBtn = new JButton(" ✕ Sair ");
        exitBtn.setFont(UIConfig.FONT_DEFAULT);
        exitBtn.setForeground(UIConfig.LIGHT_RED);

        exitBtn.addActionListener(e -> exitRenameMode());
        bar.add(exitBtn);

        return bar;
    }


    public enum MessageType {
        WELCOME, LOADING, NO_RESULTS, ERROR
    }

    public interface FileItemClickListener {
        void onFileDoubleClick(File file);

        void onFileRightClick(File file, FileInfo fileInfo,
                              Component source, int x, int y,
                              FileItemPanel itemPanel); // NOVO
    }
}