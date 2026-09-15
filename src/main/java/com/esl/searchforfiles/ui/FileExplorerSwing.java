package com.esl.searchforfiles.ui;


import com.esl.searchforfiles.Theme.ThemeManager;
import com.esl.searchforfiles.actions.compressFile.CompressModeManager;
import com.esl.searchforfiles.actions.fileTransfer.TransferService;
import com.esl.searchforfiles.actions.imageEditor.EditModeManager;
import com.esl.searchforfiles.actions.renameFile.RenameMode;
import com.esl.searchforfiles.actions.renameFile.RenameModeManager;
import com.esl.searchforfiles.actions.videoConverter.VideoConverterModeManager;
import com.esl.searchforfiles.configuration.ConfigManager;
import com.esl.searchforfiles.configuration.UIConfig;
import com.esl.searchforfiles.localization.I18N;
import com.esl.searchforfiles.model.FileInfo;
import com.esl.searchforfiles.model.PaginationInfo;
import com.esl.searchforfiles.others.ThumbnailSize;
import com.esl.searchforfiles.preview.imageViewer.ImageViewerFrame;
import com.esl.searchforfiles.preview.model3DViewer.Obj3DViewerFrame;
import com.esl.searchforfiles.preview.pdfViewer.PdfViewerFrame;
import com.esl.searchforfiles.preview.videoViewer.VideoPlayerFrame;
import com.esl.searchforfiles.service.FavoritesService;
import com.esl.searchforfiles.service.IndexFilterService;
import com.esl.searchforfiles.service.SyncService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

/**
 * Interface gráfica para o sistema de busca avançada
 * Adaptado do FileExplorer original para usar AdvancedFileSearch
 */
public class FileExplorerSwing extends JFrame implements I18N.LanguageChangeListener {

    private final SearchPanel searchPanel;
    private final ResultsPanel resultsPanel;
    private final SearchController controller;
    private final FavoritesService favoritesService; // NOVO
    private final PaginationPanel paginationPanel;
    private final NavigationHistory navigationHistory = new NavigationHistory();
    private final EditModeManager editModeManager = new EditModeManager();
    private final CompressModeManager compressModeManager = new CompressModeManager();
    private final VideoConverterModeManager videoConverterModeManager = new VideoConverterModeManager();
    private final RenameModeManager renameModeFiles =
            new RenameModeManager(RenameMode.FILES);
    private final RenameModeManager renameModeFolders =
            new RenameModeManager(RenameMode.FOLDERS);
    private FolderTreePanel treePanel;
    private FavoritesPanel favoritesPanel; // NOVO
    private ConfigManager configManager;
    private BottomIndicatorPanel bottomIndicatorPanel;
    private IndexFilterService indexFilterService;
    private TransferService transferService;
    private ThemeManager themeManager;
    private String selectedPath = "C:\\";
    private int currentPage = 1; // NOVO
    private String currentSortBy = "last_modified"; // NOVO
    private String currentSortOrder = "DESC"; // NOVO
    private SubFolderPanel subFolderPanel;           // NOVO
    private boolean showSubfolderContents = false; // NOVO — controlado pelo menu
    private boolean showGlobalSearch = false; // NOVO

    private boolean useDefaultWindowsProgramImage = false;
    private boolean useDefaultWindowsProgramVideo = false;
    private boolean useDefaultWindowsProgramPdf = false;
    private boolean useDefaultWindowsProgram3dModel = false;

    private JSplitPane rightSplit;
    private JSplitPane mainSplit;
    private JPanel leftPanel;

    private boolean programmaticDividerChange = false;
    private boolean programmaticMainDividerChange = false;
    private boolean showingWelcome = true;

    public FileExplorerSwing(ThemeManager themeManager) {
        super(I18N.get("fileExplorerSwing.frameTitle"));
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(new Dimension(1600, 800));
        // setExtendedState(Frame.MAXIMIZED_BOTH);
        setMinimumSize(new Dimension(1600, 800));
        setLocationRelativeTo(null);
        setIconImages(UIConfig.IconsConfig(this));

        setLayout(new BorderLayout(10, 10));

        favoritesService = new FavoritesService();
        configManager = new ConfigManager();

        I18N.setLocale(configManager.getSavedLocale());

        this.themeManager = themeManager;
        setSelectedPath(configManager.getSavedDefaultFolder());
        showGlobalSearch = configManager.getSavedShowGlobalItens();
        showSubfolderContents = configManager.getSavedSubfolderItems();

        bottomIndicatorPanel = new BottomIndicatorPanel(this);

        indexFilterService = new IndexFilterService();
        // Pastas que NUNCA devem ser indexadas
        indexFilterService.excludeFolder("C:\\Windows");
        indexFilterService.excludeFolder("C:\\Program Files");
        indexFilterService.excludeFolder("C:\\ProgramData");
        indexFilterService.excludeFolder("C:\\Program Files (x86)");

        // Dentro de C:\Projects, indexar SOMENTE as pastas src e docs
        indexFilterService.allowOnly("C:\\Projects\\MeuApp",
                "C:\\Projects\\MeuApp\\src",
                "C:\\Projects\\MeuApp\\docs"
        );

        try {
            controller = new SearchController(this, indexFilterService, this);
            controller.setFileSystemChangeListener(this::onFileSystemChanged);
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this,
                    "Erro ao inicializar: " + e.getMessage(),
                    "Erro", JOptionPane.ERROR_MESSAGE);
            throw new RuntimeException(e);
        }

        // === PAINEL SUPERIOR ===
        searchPanel = new SearchPanel(this);
        resultsPanel = new ResultsPanel(this);
        FileItemPanel.ICON_CACHE.clear(); // força recarregamento sem distorção
        searchPanel.setSearchListener(this::onSearch);
        searchPanel.setIndexListener(this::onIndexRequest);
        searchPanel.setGlobalSearchListener(this::onGlobalSearchChanged); // NOVO
        searchPanel.setShowSubfolderContentListener(this::onSubFolderItensChanged);
        searchPanel.setNavigationListener(new SearchPanel.NavigationListener() {
            @Override
            public void onBack() {
                String prev = navigationHistory.back();
                if (prev != null) navigateTo(prev, false);
            }

            @Override
            public void onForward() {
                String next = navigationHistory.forward();
                if (next != null) navigateTo(next, false);
            }
        });
        searchPanel.setThumbnailSizeListener(resultsPanel::setThumbnailSize);


        // Envolve o searchPanel num wrapper com BorderLayout para que o
        // WrapLayout interno recalcule a altura e o NORTH se expanda ao fazer wrap.
        JPanel searchWrapper = new JPanel(new BorderLayout());
        searchWrapper.add(searchPanel, BorderLayout.CENTER);
        add(searchWrapper, BorderLayout.NORTH);  // ← wrapper no NORTH, não o searchPanel direto

        // === PAINEL ESQUERDO ===
        leftPanel = createLeftPanel();

        // === PAINEL CENTRAL (resultados + paginação) ===

        resultsPanel.setFileItemClickListener(new ResultsPanel.FileItemClickListener() {
            @Override
            public void onFileDoubleClick(File file) {
                if (file.isDirectory()) {
                    navigateTo(file.getAbsolutePath(), true);
                } else {
                    try {
                        if (isImageFile(file) && !useDefaultWindowsProgramImage) {
                            new ImageViewerFrame(FileExplorerSwing.this, file);
                        } else if (isVideoFile(file) && !useDefaultWindowsProgramVideo) {
                            new VideoPlayerFrame(FileExplorerSwing.this, file);
                        } else if (isPdfFile(file) && !useDefaultWindowsProgramPdf) {
                            new PdfViewerFrame(FileExplorerSwing.this, file);
                        } else if (isObject3dFile(file) && !useDefaultWindowsProgram3dModel) {
                            new Obj3DViewerFrame(FileExplorerSwing.this, file);
                        } else {
                            Desktop.getDesktop().open(file);
                        }

                    } catch (IOException | IllegalArgumentException e) {
                        JOptionPane.showMessageDialog(FileExplorerSwing.this,
                                "Erro ao abrir: " + e.getMessage());
                    }
                }
            }

            @Override
            public void onFileRightClick(File file, FileInfo fileInfo,
                                         Component source, int x, int y,
                                         FileItemPanel itemPanel) {

                FileContextMenu menu = new FileContextMenu(
                        file, fileInfo, source, controller.getDbManager(), itemPanel,
                        path -> navigateTo(path, true)
                        , FileExplorerSwing.this, favoritesService
                );
                if (!editModeManager.isEditModeActive() && !transferService.isTransferModeActive()
                        && !renameModeFiles.isActive() && !compressModeManager.isCompressModeActive()
                        && !videoConverterModeManager.isVideoConverterModeActive()) {
                    menu.show(source, x, y);
                }
            }
        });

        paginationPanel = new PaginationPanel(this);
        paginationPanel.setPaginationListener(new PaginationPanel.PaginationListener() {
            @Override
            public void onPageChanged(int newPage) {
                currentPage = newPage;
                performCurrentSearch();
            }

            @Override
            public void onPageSizeChanged(int newPageSize) {
                currentPage = 1;
                performCurrentSearch();
            }
        });

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(resultsPanel, BorderLayout.CENTER);


        JPanel paginationWrapper = new JPanel(new BorderLayout());
        paginationWrapper.add(paginationPanel, BorderLayout.CENTER);
        centerPanel.add(paginationWrapper, BorderLayout.SOUTH);  // ← wrapper no NORTH, não o searchPanel direto


        // === PAINEL DIREITO (subpastas) ===
        subFolderPanel = new SubFolderPanel(this, configManager);
        subFolderPanel.setVisible(configManager.getSavedShowRightPanel());
        resultsPanel.setSubFolderPanel(subFolderPanel);
        subFolderPanel.setFolderClickListener(folder ->
                navigateTo(folder.getAbsolutePath(), true));

        // === SPLITS ===
        // 1. rightSplit: centerPanel (resultados) + subFolderPanel
        rightSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                centerPanel, subFolderPanel);
        rightSplit.setResizeWeight(1.0);      // resultados expande, subpastas fixo
        rightSplit.setDividerSize(4);
        rightSplit.setContinuousLayout(true);
        // Divider location em pixels após o frame estar visível (veja abaixo)
        rightSplit.setLastDividerLocation(configManager.getRightSplitPos());

        subFolderPanel.setParentSplit(rightSplit); // ← adicione esta linha


        // 2. mainSplit: leftPanel + rightSplit
        mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                leftPanel, rightSplit);
        mainSplit.setDividerLocation(300);
        mainSplit.setResizeWeight(0.0);

        add(mainSplit, BorderLayout.CENTER);  // ← único add ao CENTER

        // === PAINEL INFERIOR ===
        JPanel statusPanel = bottomIndicatorPanel.createStatusPanel();
        add(statusPanel, BorderLayout.SOUTH);
        resultsPanel.setThumbnailSize(ThumbnailSize.fromLabel(getConfigManager().getSavedThumbnailsSize()));

        showWelcomeMessage();
        setVisible(true);


        // Salva posição do divider sempre que o usuário o mover.
        // Salvamos a largura do subFolderPanel (rightSplit.getWidth() - dividerPos)
        // em vez da posição absoluta do divider, pois ela é invariante ao resize da janela.

        // Flag — só salva após a inicialização estar completa
        final boolean[] splitReady = {false};

        rightSplit.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, e -> {
            if (!splitReady[0]) return;
            if (programmaticDividerChange) return; // ← ignora mudanças feitas pelo próprio código

            int divPos = (int) e.getNewValue();
            int totalW = rightSplit.getWidth();
            if (divPos > 0 && totalW > 0) {
                int subFolderWidth = totalW - divPos - rightSplit.getDividerSize();
                if (subFolderWidth > 0)
                    configManager.saveRightSplitPos(subFolderWidth);
            }
        });

        final boolean[] mainSplitReady = {false}; // flag de "pronto" própria, separada da do rightSplit

        mainSplit.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, e -> {
            if (!mainSplitReady[0]) return;
            if (programmaticMainDividerChange) return; // ignora mudanças feitas pelo seu próprio código

            int divPos = (int) e.getNewValue();
            if (divPos > 0) {
                configManager.saveMainSplitPos(divPos); // aqui já é a largura do leftPanel, sem subtração
            }
        });
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                SwingUtilities.invokeLater(() -> SwingUtilities.invokeLater(() -> {

                    // --- restauração do rightSplit (já existente, sem mudanças) ---
                    int totalW = rightSplit.getWidth();
                    if (totalW > 0) {
                        int subFolderWidth = configManager.getRightSplitPos();
                        if (subFolderWidth <= 0) subFolderWidth = 200;
                        int divPos = totalW - subFolderWidth - rightSplit.getDividerSize();
                        rightSplit.setDividerLocation(Math.max(0, divPos));
                        SwingUtilities.invokeLater(() -> splitReady[0] = true);
                    }

                    // --- restauração do mainSplit (NOVO, independente) ---
                    programmaticMainDividerChange = true;
                    try {
                        int leftWidth = configManager.getMainSplitPos();
                        if (leftWidth <= 0) leftWidth = 300; // valor padrão inicial
                        mainSplit.setDividerLocation(leftWidth);
                    } finally {
                        SwingUtilities.invokeLater(() -> {
                            programmaticMainDividerChange = false;
                            mainSplitReady[0] = true; // libera o listener só depois de restaurar
                        });
                    }
                }));
            }
        });

        transferService = new TransferService();

        // MODIFICADO: em vez de performCurrentSearch() direto,
        // passa pelo navigateTo() para que a sincronização aconteça
        // antes de exibir os resultados
        navigateTo(selectedPath, true);  // selectedPath = "C:\" por padrão

        // Ouve a mudança em tempo real
        bottomIndicatorPanel.addPropertyChangeListener(evt -> {
            if (!"working".equals(evt.getPropertyName())) {
                boolean novoValor = (boolean) evt.getNewValue();

                // Garante a atualização na Thread correta do Swing
                SwingUtilities.invokeLater(() -> {
                    setEnabled(novoValor);
                });
            }
        });

        // Registra o listener global de mouse
        Toolkit.getDefaultToolkit().addAWTEventListener(new AWTEventListener() {
            @Override
            public void eventDispatched(AWTEvent event) {
                if (event.getID() == MouseEvent.MOUSE_CLICKED) {
                    Object origem = event.getSource();
                    if (!(origem instanceof Component)) return;

                    Component clicado = (Component) origem;

                    // Se o clique não veio de dentro da JList, limpa a seleção dela
                    if (!estaDentroDe(clicado, favoritesPanel.getFavoritesList())) {
                        favoritesPanel.getFavoritesList().clearSelection();
                    }

                    // Se o clique não veio de dentro da JTree, limpa a seleção dela
                    if (!estaDentroDe(clicado, treePanel.getFolderTree())) {
                        treePanel.getFolderTree().clearSelection();
                    }
                }
            }
        }, AWTEvent.MOUSE_EVENT_MASK);


        I18N.addLanguageChangeListener(this);

    }

    // Verifica se "clicado" é o próprio componente-alvo ou está dentro dele
    static boolean estaDentroDe(Component clicado, Component alvo) {
        Component c = clicado;
        while (c != null) {
            if (c == alvo) return true;
            c = c.getParent();
        }
        return false;
    }


    int getSafeRightSplitPos() {
        int v = configManager.getRightSplitPos();
        return v > 0 ? v : 200; // default
    }

    int getSafeLeftSplitPos() {
        int v = configManager.getMainSplitPos();
        return v > 0 ? v : 300; // default
    }

    /**
     * Chamado quando o usuário alterna a busca global.
     * NOVO MÉTODO
     */
    private void onGlobalSearchChanged(boolean globalSearch) {
        showGlobalSearch = globalSearch;
        currentPage = 1; // volta para primeira página, já que o conjunto de resultados muda
        performCurrentSearch();
    }

    private void onSubFolderItensChanged(boolean subFolderContents) {
        showSubfolderContents = subFolderContents;
        currentPage = 1; // volta para primeira página, já que o conjunto de resultados muda
        performCurrentSearch();
    }

    /**
     * Ativa/desativa o overlay de avaliação (estrelas) em todas as miniaturas.
     * Centraliza o que antes estava duplicado em FileContextMenu.
     */
    public void setShowRatingOverlay(boolean visible) {
        FileItemPanel.setShowRatingOverlay(visible);
        configManager.saveShowStarRating(visible);
        propagateToFileItemPanels(FileItemPanel::applyOverlayVisibility);
    }

    /**
     * Ativa/desativa o selo de tipo de arquivo em todas as miniaturas.
     */
    public void setShowExtensionTypeOverlay(boolean visible) {
        FileItemPanel.setShowExtensionFileOverlay(visible);
        configManager.saveShowTypeFile(visible);
        propagateToFileItemPanels(FileItemPanel::applyExtensionTypeVisibility);
    }

    /**
     * Ativa/desativa a reprodução de GIFs animados nas miniaturas.
     */
    public void setShowAnimatedGif(boolean visible) {
        AnimatedGifThumb.setShowAnimatedGif(visible);
        configManager.saveShowAnimatedGif(visible);
        propagateToFileItemPanels(FileItemPanel::applyAnimatedGifVisibility);
    }

    /**
     * Percorre toda a árvore de componentes a partir deste JFrame e aplica
     * a ação informada a cada FileItemPanel encontrado. Generaliza os três
     * métodos propagateXxx/applyToAllXxx que antes existiam separadamente
     * em FileContextMenu.
     */
    private void propagateToFileItemPanels(Consumer<FileItemPanel> action) {
        applyToAllFileItemPanels(this, action);
    }

    private void applyToAllFileItemPanels(Container container, Consumer<FileItemPanel> action) {
        for (Component c : container.getComponents()) {
            if (c instanceof FileItemPanel fip) {
                action.accept(fip);
            } else if (c instanceof Container inner) {
                applyToAllFileItemPanels(inner, action);
            }
        }
    }

    private boolean isObject3dFile(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".obj");
    }

    private boolean isPdfFile(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".pdf");
    }

    private boolean isVideoFile(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".mp4") || name.endsWith(".avi") ||
                name.endsWith(".mkv") || name.endsWith(".mov") ||
                name.endsWith(".wmv") || name.endsWith(".flv") ||
                name.endsWith(".webm") || name.endsWith(".m4v") ||
                name.endsWith(".mp3") || name.endsWith(".flac") ||
                name.endsWith(".ogg") || name.endsWith(".m4a") ||
                name.endsWith(".wav");
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
                name.endsWith(".ico") || name.endsWith(".png") ||
                name.endsWith(".psd");
    }

    public SearchController getController() {
        return controller;
    }

    public BottomIndicatorPanel getBottomIndicatorPanel() {
        return bottomIndicatorPanel;
    }

    public SearchPanel getSearchPanel() {
        return searchPanel;
    }

    public TransferService getTransferService() {
        return transferService;
    }

    public ThemeManager getThemeManager() {
        return themeManager;
    }

    public ResultsPanel getResultsPanel() {
        return resultsPanel;
    }

    public String getSelectedPath() {
        return selectedPath;
    }

    public void setSelectedPath(String selectedPath) {

        this.selectedPath = selectedPath;
    }

    public FavoritesPanel getFavoritesPanel() {
        return favoritesPanel;
    }

    public FolderTreePanel getTreePanel() {
        return treePanel;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }


    public void toggleTransferMode() {
        deactivateOtherModes(transferService);
        if (transferService.isTransferModeActive()) {
            resultsPanel.exitTransferMode();
            // Remove referência dos painéis laterais
            treePanel.setTransferManager(null);
            subFolderPanel.setTransferManager(null);
            favoritesPanel.setTransferManager(null);
        } else {
            resultsPanel.enterTransferMode(transferService);
            // Injeta TransferManager nos painéis que recebem drop
            treePanel.setTransferManager(transferService);
            subFolderPanel.setTransferManager(transferService);
            favoritesPanel.setTransferManager(transferService);
        }
    }

    /**
     * Navega para o caminho informado.
     *
     * @param path        Caminho de destino
     * @param pushHistory true  = navegação normal (empurra no histórico)
     *                    false = movimento pelo histórico (back/forward)
     */


    public void navigateTo(String path, boolean pushHistory) {
        selectedPath = path;
        if (pushHistory) navigationHistory.push(path);

        searchPanel.updateNavigationState(navigationHistory);
        searchPanel.clearSearchTerm();
        subFolderPanel.loadSubfolders(selectedPath, controller);
        currentPage = 1;

        bottomIndicatorPanel.showSyncIndicator(I18N.get("fileExplorerSwing.showSyncIndicator"));
        bottomIndicatorPanel.setWorking(true);

        // Cria o diálogo antecipadamente para poder referenciar dentro da tarefa
        BlockingDialog dlg = new BlockingDialog(
                this,
                I18N.get("fileExplorerSwing.BlockingDialog.message"),
                path);

        // NOVO: guarda o status descoberto durante a sincronização
        final boolean[] folderIndexed = {false};

        BlockingTaskRunner.run(dlg,
                // ── Tarefa em background (Runnable, não Consumer) ─────
                () -> {
                    try {
                        CountDownLatch latch = new CountDownLatch(1);

                        controller.syncFolderIfNeeded(path,
                                new SearchController.SyncCallback() {
                                    @Override
                                    public void onSyncCompleted(SyncService.SyncResult result) {
                                        folderIndexed[0] = !result.isNotIndexed(); // NOVO
                                        if (!result.isNotIndexed()) {
                                            // dlg é acessado diretamente — sem parâmetro
                                            dlg.setSubMessage(
                                                    result.hasChanges()
                                                            ? String.format(I18N.get("fileExplorerSwing.navigateTo.BlockingTaskRunner.run"),
                                                            result.getAdded(),
                                                            result.getUpdated(),
                                                            result.getDeleted())
                                                            : I18N.get("fileExplorerSwing.navigateTo.BlockingTaskRunner.result"));

                                            controller.startMonitoringAsync(path);
                                        } else {
                                            dlg.setSubMessage(I18N.get("fileExplorerSwing.navigateTo.BlockingTaskRunner.notIndexed"));
                                        }
                                        latch.countDown();
                                    }

                                    @Override
                                    public void onSyncError(Exception e) {
                                        dlg.setSubMessage(I18N.get("fileExplorerSwing.navigateTo.BlockingTaskRunner.error"));
                                        latch.countDown();
                                    }
                                });

                        latch.await();

                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                },

                // ── Callback na EDT após a tarefa ─────────────────────
                () -> {
                    bottomIndicatorPanel.hideSyncIndicator(false);
                    searchPanel.setIndexButtonEnabled(!folderIndexed[0]); // NOVO
                    bottomIndicatorPanel.setWorking(false);
                    searchPanel.setIndexButtonEnabled(!folderIndexed[0] && !getController().isDriveRoot(selectedPath)); // NOVO: bloqueia raiz de drive também
                    showingWelcome = false;
                    performCurrentSearch();
                    subFolderPanel.reload();
                }
        );
    }


    /**
     * Chamado quando o sistema de arquivos muda
     * NOVO MÉTODO
     */
    private void onFileSystemChanged() {
        // Mostra feedback visual temporário
        bottomIndicatorPanel.showAutoRefreshIndicator();

    }

    /**
     * NOVO: Cria painel esquerdo com Tree e Favoritos
     */
    private JPanel createLeftPanel() {
        JPanel leftPanel = new JPanel(new BorderLayout(0, 5));

        leftPanel.setVisible(configManager.getSavedShowLeftPanels());

        // Tree de pastas
        treePanel = new FolderTreePanel(favoritesService, this);
        treePanel.setSelectionListener(this::onFolderSelected);

        // Painel de favoritos
        favoritesPanel = new FavoritesPanel(favoritesService, this);
        favoritesPanel.setSelectionListener(this::onFavoriteSelected);

        // NOVO: Split pane vertical (Tree em cima, Favoritos embaixo)
        JSplitPane verticalSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                treePanel, favoritesPanel);
        verticalSplit.setDividerLocation(400); // Tree fica com 400px de altura
        verticalSplit.setResizeWeight(0.67); // 2/3 para tree, 1/3 para favoritos
        verticalSplit.setBorder(BorderFactory.createEmptyBorder());

        leftPanel.add(verticalSplit, BorderLayout.CENTER);
        leftPanel.setPreferredSize(new Dimension(300, 600));

        return leftPanel;
    }

    // Substitua onSearch() existente:
    private void onSearch(String searchTerm, String filter,
                          String sortBy, String sortOrder,
                          int minRating, String tag) {          // NOVO
        currentPage = 1;
        currentSortBy = sortBy;
        currentSortOrder = sortOrder;
        onSearchWithPagination(
                searchTerm,
                searchPanel.getSelectedFilter(),
                searchPanel.getSortBy(),
                searchPanel.getSortOrder(),
                searchPanel.getMinRating(),
                searchPanel.getTagFilter(),
                showSubfolderContents,   // NOVO — passa flag de subpastas
                showGlobalSearch,
                currentPage,
                paginationPanel.getPageSize()
        );
    }


    public void performCurrentSearch() {
        // Sem mais lógica de hide/show do subFolderPanel aqui
        String searchTerm = searchPanel.getSearchTerm();

        onSearchWithPagination(
                searchTerm,
                searchPanel.getSelectedFilter(),
                configManager.getSavedSortBy(),
                searchPanel.getSortOrder(),
                configManager.getSavedStarRating(),
                searchPanel.getTagFilter(),
                showSubfolderContents,
                showGlobalSearch,
                currentPage,
                paginationPanel.getPageSize()
        );
    }


//    private void onSearchWithPagination(String searchTerm, String filter,
//                                        String sortBy, String sortOrder,
//                                        int minRating, String tag, boolean includeSubfolders, boolean globalSearch,
//                                        int page, int pageSize) {
//
//        String loadingMessage;
//        if (searchTerm == null || searchTerm.trim().isEmpty()) {
//            loadingMessage = "📂 Carregando todos os arquivos...";
//        } else {
//            loadingMessage = "🔍 Buscando: " + searchTerm + "...";
//        }
//
//        resultsPanel.showMessage(loadingMessage, ResultsPanel.MessageType.LOADING);
//        paginationPanel.setEnabled(false);
//
//        controller.performSearchWithPagination(
//                searchTerm, filter, selectedPath,
//                sortBy, sortOrder,
//                minRating, tag,
//                includeSubfolders,   // NOVO
//                globalSearch,
//                page, pageSize,
//                new SearchController.PaginatedSearchCallback() {
//
//                    @Override
//                    public void onSearchStarted() {
//
//                    }
//
//                    @Override
//                    public void onSearchCompleted(List<FileInfo> results, PaginationInfo pagination) {
//
//                        // NOVO: remove a própria pasta atual dos resultados
//                        List<FileInfo> filtered = results.stream()
//                                .filter(f -> !f.getPath().equalsIgnoreCase(selectedPath))
//                                .filter(f -> !f.getExtension().equalsIgnoreCase("ini"))
//                                .filter(f -> !f.getExtension().equalsIgnoreCase("db"))
//                                .toList();
//                        resultsPanel.atualizarBordaCaminho(selectedPath);
//                        if (filtered.isEmpty()) {
//                            String emptyMessage;
//                            if (searchTerm == null || searchTerm.trim().isEmpty()) {
//                                emptyMessage = "📂 Nenhum arquivo corresponde aos critérios de busca";
//                            } else {
//                                // NOVO: menciona os filtros ativos na mensagem de vazio
//                                StringBuilder sb = new StringBuilder("Nenhum arquivo encontrado");
//                                sb.append(" para: ").append(searchTerm);
//                                if (minRating > 0)
//                                    sb.append(" | Rating ≥ ").append(minRating).append("★");
//                                if (tag != null && !tag.isEmpty())
//                                    sb.append(" | Tag: \"").append(tag).append("\"");
//                                emptyMessage = sb.toString();
//                            }
//                            resultsPanel.showMessage(emptyMessage, ResultsPanel.MessageType.NO_RESULTS);
//                            paginationPanel.setEnabled(false);
//                        } else {
//                            resultsPanel.showResults(filtered);
//                            paginationPanel.updatePagination(pagination);
//
//
//
//                            // NOVO: garante foco no painel de resultados
//                            // para que as teclas funcionem imediatamente
//                            SwingUtilities.invokeLater(resultsPanel::requestFocusInWindow);
//                        }
//
//                        // NOVO: inclui rating e tag no texto de status
//                        String sortInfo = getSortDisplayName(sortBy) +
//                                " " + (sortOrder.equals("ASC") ? "↑" : "↓");
//
//                        StringBuilder filters = new StringBuilder();
//                        if (minRating > 0)
//                            filters.append(" | ").append("★".repeat(minRating)).append("+");
//                        if (tag != null && !tag.isEmpty())
//                            filters.append(" | 🏷️ ").append(tag);
//
//                        String monitoringStatus = controller.getMonitoringStatus();
//
//                        String statusText;
//                        if (searchTerm == null || searchTerm.trim().isEmpty()) {
//                            statusText = String.format("📂 Listando: %s | Ordem: %s%s | Local: %s",
//                                    pagination, sortInfo, filters, selectedPath);
//                        } else {
//                            statusText = String.format("✅ %s | Ordem: %s%s | Local: %s",
//                                    pagination, sortInfo, filters, selectedPath);
//                        }
//
//                        if (!monitoringStatus.isEmpty())
//                            statusText += " | " + monitoringStatus + " 🔄";
//
//                        bottomIndicatorPanel.getStatusLabel().setText(statusText);
//                    }
//
//                    @Override
//                    public void onSearchError(Exception e) {
//                        resultsPanel.showMessage("Erro: " + e.getMessage(),
//                                ResultsPanel.MessageType.ERROR);
//                        paginationPanel.setEnabled(false);
//                    }
//                });
//    }

    private void onSearchWithPagination(String searchTerm, String filter,
                                        String sortBy, String sortOrder,
                                        int minRating, String tag, boolean includeSubfolders, boolean globalSearch,
                                        int page, int pageSize) {

        String loadingMessage = (searchTerm == null || searchTerm.trim().isEmpty())
                ? I18N.get("fileExplorerSwing.loading.allFiles")
                : I18N.get("fileExplorerSwing.loading.searching").replace("{0}", searchTerm);

        resultsPanel.showMessage(loadingMessage, ResultsPanel.MessageType.LOADING);
        paginationPanel.setEnabled(false);

        controller.performSearchWithPagination(
                searchTerm, filter, selectedPath,
                sortBy, sortOrder,
                minRating, tag,
                includeSubfolders,
                globalSearch,
                page, pageSize,
                new SearchController.PaginatedSearchCallback() {

                    @Override
                    public void onSearchStarted() {
                    }

                    @Override
                    public void onSearchCompleted(List<FileInfo> results, PaginationInfo pagination) {

                        List<FileInfo> filtered = results.stream()
                                .filter(f -> !f.getPath().equalsIgnoreCase(selectedPath))
                                .filter(f -> !f.getExtension().equalsIgnoreCase("ini"))
                                .filter(f -> !f.getExtension().equalsIgnoreCase("db"))
                                .toList();
                        resultsPanel.atualizarBordaCaminho(selectedPath);

                        if (filtered.isEmpty()) {
                            String emptyMessage;
                            if (searchTerm == null || searchTerm.trim().isEmpty()) {
                                emptyMessage = I18N.get("fileExplorerSwing.empty.noMatch");
                            } else {
                                StringBuilder sb = new StringBuilder(I18N.get("fileExplorerSwing.empty.noneFoundFor"))
                                        .append(" ").append(searchTerm);
                                if (minRating > 0)
                                    sb.append(" | ").append(I18N.get("fileExplorerSwing.empty.ratingSuffix"))
                                            .append(" ≥ ").append(minRating).append("★");
                                if (tag != null && !tag.isEmpty())
                                    sb.append(" | ").append(I18N.get("fileExplorerSwing.empty.tagSuffix"))
                                            .append(": \"").append(tag).append("\"");
                                emptyMessage = sb.toString();
                            }
                            resultsPanel.showMessage(emptyMessage, ResultsPanel.MessageType.NO_RESULTS);
                            paginationPanel.setEnabled(false);
                        } else {
                            resultsPanel.showResults(filtered);
                            paginationPanel.updatePagination(pagination);
                            SwingUtilities.invokeLater(resultsPanel::requestFocusInWindow);
                        }

                        // ── AQUI está a mudança principal: nunca mais setText direto ──
                        String sortFieldKey = getSortDisplayNameKey(sortBy);
                        boolean hasSearchTerm = searchTerm != null && !searchTerm.trim().isEmpty();
                        bottomIndicatorPanel.showSearchStatus(
                                hasSearchTerm,
                                pagination,
                                sortFieldKey != null ? sortFieldKey : sortBy, // fallback bruto se chave não mapeada
                                sortOrder,
                                minRating,
                                tag,
                                controller.getMonitoringStatus(),
                                selectedPath
                        );
                    }

                    @Override
                    public void onSearchError(Exception e) {
                        resultsPanel.showMessage(
                                I18N.get("fileExplorerSwing.error.search").replace("{0}", e.getMessage()),
                                ResultsPanel.MessageType.ERROR);
                        paginationPanel.setEnabled(false);
                    }
                });
    }


    /**
     * Converte nome do campo para exibição
     * NOVO MÉTODO
     */
//    private String getSortDisplayName(String fieldName) {
//        return switch (fieldName) {
//            case "name" -> "Nome";
//            case "last_modified" -> "Data";
//            case "size" -> "Tamanho";
//            case "file_type" -> "Tipo";
//            case "path" -> "Caminho";
//            default -> fieldName;
//        };
//    }
    private String getSortDisplayNameKey(String fieldName) {
        return switch (fieldName) {
            case "name" -> "sortField.name";
            case "last_modified" -> "sortField.date";
            case "size" -> "sortField.size";
            case "file_type" -> "sortField.type";
            case "path" -> "sortField.path";
            default -> null; // sem tradução — usa o próprio fieldName como fallback
        };
    }

//    private void onIndexRequest() {
//        String message = String.format(
//                "Deseja indexar a pasta selecionada?\n" +
//                        "Pasta: %s\n\n" +
//                        "===================Atenção=============================\n" +
//                        "A velocidade da indexação vai depender da velocide do disco onde\n" +
//                        "os arquivos estão armazenados (Nvme > SSD > HD). Mutos arquivos (< 1000)\n" +
//                        "podem resultar em uma demora no processo\n\n" +
//                        "Incluir subpastas?",
//                selectedPath
//        );
//
//
//        int option = JOptionPane.showConfirmDialog(this,
//                message,
//                "Indexar Pasta",
//                JOptionPane.YES_NO_CANCEL_OPTION,
//                JOptionPane.QUESTION_MESSAGE);
//
//        if (option == JOptionPane.YES_OPTION) {
//            indexFolder(true);
//        } else if (option == JOptionPane.NO_OPTION) {
//            indexFolder(false);
//        }
//    }
private void onIndexRequest() {
    String message = String.format(
            I18N.get("fileExplorerSwing.indexRequest.message"),
            selectedPath
    );

    int option = JOptionPane.showConfirmDialog(this,
            message,
            I18N.get("fileExplorerSwing.indexRequest.title"),
            JOptionPane.YES_NO_CANCEL_OPTION,
            JOptionPane.QUESTION_MESSAGE);

    if (option == JOptionPane.YES_OPTION) {
        indexFolder(true);
    } else if (option == JOptionPane.NO_OPTION) {
        indexFolder(false);
    }
}

    //    private void indexFolder(boolean includeSubfolders) {
//        controller.indexFolder(selectedPath, includeSubfolders,
//                new SearchController.IndexCallback() {
//                    @Override
//                    public void onIndexCompleted() {
//                        bottomIndicatorPanel.getStatusLabel().setText("✓ Indexação concluída: " + selectedPath);
//                    }
//
//                    @Override
//                    public void onIndexError(Exception e) {
//                        bottomIndicatorPanel.getStatusLabel().setText("❌ Erro na indexação");
//                    }
//                });
//    }
    private void indexFolder(boolean includeSubfolders) {
        controller.indexFolder(selectedPath, includeSubfolders,
                new SearchController.IndexCallback() {
                    @Override
                    public void onIndexCompleted() {
                        bottomIndicatorPanel.showIndexDone(selectedPath); // era setText direto
                    }

                    @Override
                    public void onIndexError(Exception e) {
                        bottomIndicatorPanel.showIndexError(); // era setText direto
                    }
                });
    }

    private void onFolderSelected(File folder) {
        navigateTo(folder.getAbsolutePath(), true);
    }

    private void onFavoriteSelected(File folder) {
        if (folder != null && folder.exists() && folder.isDirectory()) {
            navigateTo(folder.getAbsolutePath(), true);
        }
    }


    /**
     * Mensagem de boas-vindas
     * MODIFICADO: Menciona busca vazia
     */
    /// /////////
//    private void showWelcomeMessage() {
//        resultsPanel.showMessage(
//                "Sistema de Busca Avançada\n" +
//                        "Selecione uma pasta e clique em 'Indexar'\n\n" +
//                        "💡 Recursos Ativos:\n" +
//                        "   🔄 Sincronização automática (apenas pastas indexadas)\n" +
//                        "   📡 Monitoramento em tempo real\n" +
//                        "   ⚡ Auto-refresh de resultados\n" +
//                        "   📂 Busca vazia = Lista TODOS os arquivos\n" +
//                        "   ✅ Índice sempre atualizado",
//                ResultsPanel.MessageType.WELCOME
//        );
//    }
    private void showWelcomeMessage() {
        resultsPanel.showMessage(I18N.get("fileExplorerSwing.welcome.message"),
                ResultsPanel.MessageType.WELCOME);
    }

    /**
     * Verifica se o caminho é uma raiz de drive
     */
    public boolean isDriveRoot(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }

        String normalized = path.trim().toUpperCase();

        // Padrões de drive raiz: C:\, C:/, C:
        return normalized.matches("^[A-Z]:\\\\?$") ||
                normalized.matches("^[A-Z]:/?$");
    }

    @Override
    public void dispose() {
        try {
            controller.close();
        } catch (SQLException e) {
            System.err.println("Erro ao fechar: " + e.getMessage());
        }
        resultsPanel.dispose();
        super.dispose();
    }


    /**
     * Alterna o modo de edição de imagens.
     * Conecte ao botão "Editar imagens" na SearchPanel.
     */
    public void toggleEditMode() {
        deactivateOtherModes(editModeManager);
        if (editModeManager.isEditModeActive()) {
            resultsPanel.exitEditMode();
        } else {
            // Garante que o modo de transferência não fique ativo ao mesmo tempo
            if (transferService.isTransferModeActive()) {
                resultsPanel.exitTransferMode();
            }
            resultsPanel.enterEditMode(editModeManager);
        }
    }

    /**
     * Botão para comprimir Arquivos
     */
    public void toggleCompressMode() {
        deactivateOtherModes(compressModeManager);
        if (compressModeManager.isCompressModeActive()) {
            resultsPanel.exitCompressMode();
        } else {
            // Garante que o modo de transferência não fique ativo ao mesmo tempo
            if (transferService.isTransferModeActive()) {
                resultsPanel.exitTransferMode();
            }
            resultsPanel.enterCompressMode(compressModeManager);

        }
    }

    /**
     * Botão para comprimir Arquivos
     */
    public void toggleVideoConverterMode() {
        deactivateOtherModes(videoConverterModeManager);
        if (videoConverterModeManager.isVideoConverterModeActive()) {
            resultsPanel.exitVideoConverterMode();
        } else {
            // Garante que o modo de transferência não fique ativo ao mesmo tempo
            if (transferService.isTransferModeActive()) {
                resultsPanel.exitTransferMode();
            }

            resultsPanel.enterVideoConverterMode(videoConverterModeManager);
        }
    }

    /**
     * Botão "Renomear arquivos"
     */
    public void toggleRenameModeFiles() {
        deactivateOtherModes(renameModeFiles);
        if (renameModeFiles.isActive()) {
            resultsPanel.exitRenameMode();
        } else {
            resultsPanel.enterRenameMode(renameModeFiles);
        }
    }

    /**
     * Botão "Renomear pastas"
     */
    public void toggleRenameModeFolders() {
        deactivateOtherModes(renameModeFolders);
        if (renameModeFolders.isActive()) {
            resultsPanel.exitRenameMode();
        } else {
            resultsPanel.enterRenameMode(renameModeFolders);
        }
    }

    public void toggleConfigurationMode() {
        if (renameModeFolders.isActive() || renameModeFiles.isActive()) {
            resultsPanel.exitRenameMode();
        }
        if (editModeManager.isEditModeActive()) {
            resultsPanel.exitEditMode();
        }
        if (transferService.isTransferModeActive()) {
            resultsPanel.exitTransferMode();
        }
        if (compressModeManager.isCompressModeActive()) {
            resultsPanel.exitCompressMode();
        }
        if (videoConverterModeManager.isVideoConverterModeActive()) {
            resultsPanel.exitVideoConverterMode();
        }
        resultsPanel.openConfiguration();
    }

    /**
     * Garante que apenas um modo fica ativo por vez.
     * Chame antes de ativar qualquer modo.
     */
    private void deactivateOtherModes(Object keepActive) {
        if (keepActive != transferService && transferService.isTransferModeActive())
            resultsPanel.exitTransferMode();
        if (keepActive != editModeManager && editModeManager.isEditModeActive())
            resultsPanel.exitEditMode();
        if (keepActive != renameModeFiles && renameModeFiles.isActive())
            resultsPanel.exitRenameMode();
        if (keepActive != renameModeFolders && renameModeFolders.isActive())
            resultsPanel.exitRenameMode();
        if (keepActive != compressModeManager && compressModeManager.isCompressModeActive())
            resultsPanel.exitCompressMode();
        if (keepActive != videoConverterModeManager && videoConverterModeManager.isVideoConverterModeActive())
            resultsPanel.exitVideoConverterMode();
    }

//    public void createNewFolder() {
//        if (getSelectedPath() == null) {
//            JOptionPane.showMessageDialog(this,
//                    "Selecione uma pasta pai antes de criar uma nova pasta.",
//                    "Aviso", JOptionPane.WARNING_MESSAGE);
//            return;
//        }
//        String name = JOptionPane.showInputDialog(this,
//                "Nome da nova pasta:", "Nova pasta");
//        if (name == null || name.isBlank()) return;
//
//        File newDir = new File(getSelectedPath(), name.trim());
//        if (newDir.mkdirs()) {
//            JOptionPane.showMessageDialog(this,
//                    "Pasta criada: " + newDir.getAbsolutePath());
//            subFolderPanel.reload();
//        } else {
//            JOptionPane.showMessageDialog(this,
//                    "Não foi possível criar a pasta.", "Erro",
//                    JOptionPane.ERROR_MESSAGE);
//        }
//    }
public void createNewFolder() {
    if (getSelectedPath() == null) {
        JOptionPane.showMessageDialog(this,
                I18N.get("fileExplorerSwing.newFolder.selectParentWarning"),
                I18N.get("fileExplorerSwing.newFolder.warningTitle"),
                JOptionPane.WARNING_MESSAGE);
        return;
    }
    String name = JOptionPane.showInputDialog(this,
            I18N.get("fileExplorerSwing.newFolder.namePrompt"),
            I18N.get("fileExplorerSwing.newFolder.defaultName"));
    if (name == null || name.isBlank()) return;

    File newDir = new File(getSelectedPath(), name.trim());
    if (newDir.mkdirs()) {
        JOptionPane.showMessageDialog(this,
                I18N.get("fileExplorerSwing.newFolder.created").replace("{0}", newDir.getAbsolutePath()));
        subFolderPanel.reload();
    } else {
        JOptionPane.showMessageDialog(this,
                I18N.get("fileExplorerSwing.newFolder.failed"),
                I18N.get("fileExplorerSwing.newFolder.errorTitle"),
                JOptionPane.ERROR_MESSAGE);
    }
}

    public boolean isUseDefaultWindowsProgramVideo() {
        return useDefaultWindowsProgramVideo;
    }

    public void setUseDefaultWindowsProgramVideo(boolean useDefaultWindowsProgramVideo) {
        this.useDefaultWindowsProgramVideo = useDefaultWindowsProgramVideo;
    }

    public boolean isUseDefaultWindowsProgramImage() {
        return useDefaultWindowsProgramImage;
    }

    public void setUseDefaultWindowsProgramImage(boolean useDefaultWindowsProgramImage) {
        this.useDefaultWindowsProgramImage = useDefaultWindowsProgramImage;
    }

    public boolean isUseDefaultWindowsProgramPdf() {
        return useDefaultWindowsProgramPdf;
    }

    public void setUseDefaultWindowsProgramPdf(boolean useDefaultWindowsProgramPdf) {
        this.useDefaultWindowsProgramPdf = useDefaultWindowsProgramPdf;
    }

    public boolean isUseDefaultWindowsProgram3dModel() {
        return useDefaultWindowsProgram3dModel;
    }

    public void setUseDefaultWindowsProgram3dModel(boolean useDefaultWindowsProgram3dModel) {
        this.useDefaultWindowsProgram3dModel = useDefaultWindowsProgram3dModel;
    }

    public SubFolderPanel getSubFolderPanel() {
        return subFolderPanel;
    }

    public JSplitPane getRightSplit() {
        return rightSplit;
    }

    public JSplitPane getMainSplit() {
        return mainSplit;
    }

    public JPanel getLeftPanel() {
        return leftPanel;
    }

    public void setProgrammaticDividerChange(boolean programmaticDividerChange) {
        this.programmaticDividerChange = programmaticDividerChange;
    }

    public void setProgrammaticMainDividerChange(boolean programmaticMainDividerChange) {
        this.programmaticMainDividerChange = programmaticMainDividerChange;
    }


    @Override
    public void onLanguageChanged(Locale newLocale) {
        setTitle(I18N.get("fileExplorerSwing.frameTitle")); // se quiser traduzir o título da janela

        if (showingWelcome) {
            showWelcomeMessage(); // reconstrói a mensagem de boas-vindas no novo idioma
        } else {
            performCurrentSearch(); // reconstrói o texto de status + resultsPanel com o idioma atual
        }
    }
}