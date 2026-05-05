package com.esl.searchforfiles.ui;

import com.esl.searchforfiles.cache.thumbnail.ThumbnailCacheManager;
import com.esl.searchforfiles.configuration.ConfigManager;
import com.esl.searchforfiles.configuration.FileTransferHandler;
import com.esl.searchforfiles.model.FileInfo;
import com.esl.searchforfiles.model.FileType;
import com.esl.searchforfiles.service.IconService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static com.esl.searchforfiles.ui.AnimatedGifThumb.isAnimatedGif;

/**
 * Menu de contexto (botão direito) para arquivos
 * ATUALIZADO: Inclui opção para gerenciar cache se for vídeo
 */
public class FileItemPanel extends JPanel {

    static final Map<String, ImageIcon> ICON_CACHE = new ConcurrentHashMap<>();
    // No topo da classe
    private static final ThumbnailCacheManager THUMBNAIL_CACHE = new ThumbnailCacheManager();
    private static final ExecutorService THUMBNAIL_EXECUTOR = Executors.newFixedThreadPool(2);
    private static final Color SELECTED_COLOR = new Color(33, 150, 243, 80); // azul semitransparente
    private static final Color SELECTED_BORDER = new Color(33, 150, 243);
    // Campo estático — compartilhado entre todas as instâncias
    private static final Map<String, List<WeakReference<JLabel>>> PENDING_TARGETS
            = new ConcurrentHashMap<>();
    // --- um ícone provisório "loading" gerado dinamicamente ---
// cria um ImageIcon simples (quadrado) que você pode usar enquanto a thumbnail carrega.
// você pode substituir por um GIF animado se preferir.
    private static final ImageIcon LOADING_ICON = createLoadingIcon(128, 128);
    private static final Set<String> IMAGE_EXTS = new HashSet<>(Arrays.asList(
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "tif", "tiff",
            "heic", "heif", "svg", "raw", "arw", "cr2", "nef"
    ));
    private static final Set<String> VIDEO_EXTS = new HashSet<>(Arrays.asList(
            "mp4", "m4v", "mov", "avi", "mkv", "webm", "flv", "wmv", "mpeg", "mpg"
    ));
    private static boolean showRatingOverlay = true; // NOVO — controlado pelo menu de contexto
    private static boolean showExtensionFileOverlay = true; // NOVO — controlado pelo menu de contexto
    private static boolean showAnimatedGif = true; // NOVO — controlado pelo menu de contexto
    private final File originalFile;  // Arquivo original (.lnk ou não)
    private final FileInfo fileInfo;
    private final ThumbnailCacheManager cacheManager;
    private final ResultsPanel resultsPanel;
    // Cores para estados
    private final Color normalColor = new Color(56, 56, 56);
    private final Color hoverColor = new Color(70, 70, 70);
    private final int thumbSize;
    private File displayFile;
    private ResultsPanel.FileItemClickListener clickListener;
    private RatingOverlay ratingOverlay; // NOVO
    private boolean selected = false;
    private TypeOverlay typeOverlay; // NOVO
    private DragAction dragAction;
    private AnimatedGifThumb animatedGifThumb;

    private JComponent iconSlot;

    public Color getNormalColor() {
        return normalColor;
    }

    public FileItemPanel(File file, FileInfo fileInfo, int width, int height, int thumbSize, ResultsPanel resultsPanel) {

        this.originalFile = file;
        this.resultsPanel = resultsPanel;
        this.thumbSize = thumbSize;
        // VALIDAÇÃO CRÍTICA: Verifica se file não é null
        if (file == null) {
            throw new IllegalArgumentException("File não pode ser null!");
        }
        // Resolve atalho automaticamente
        if (SimpleLinkResolver.isShortcut(file)) {
            File target = SimpleLinkResolver.resolveShortcut(file);
            this.displayFile = (target != null && target.exists()) ? target : file;

            if (target != null) {
                System.out.println("📎 " + file.getName() + " ➜ " + target.getName());
            }
        } else {
            this.displayFile = file;
        }
        this.fileInfo = fileInfo;
        this.cacheManager = FileItemPanel.getThumbnailCacheManager();

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(normalColor);
        setAlignmentX(Component.CENTER_ALIGNMENT);
        setShowRatingOverlay(resultsPanel.getFileExplorerSwing().getConfigManager().getSavedShowStarRating());
        setShowExtensionFileOverlay(resultsPanel.getFileExplorerSwing().getConfigManager().getSavedShowTypeFile());

        animatedGifThumb = new AnimatedGifThumb(this);
        AnimatedGifThumb.setShowAnimatedGif(resultsPanel.getFileExplorerSwing().getConfigManager().getSavedShowAnimatedGif());

        // Ícone / Miniatura
        JComponent iconLabel = createIconLabel();  // JComponent em vez de JLabel
        this.iconSlot = iconLabel;
        iconLabel.setAlignmentX(Component.CENTER_ALIGNMENT);  // mantém — é método de JComponent

        // setHorizontalAlignment não existe em JComponent — trate por tipo:
        if (iconLabel instanceof JLabel lbl) {
            lbl.setHorizontalAlignment(SwingConstants.CENTER);
        } else {
            // JLayeredPane: centraliza o conteúdo via alinhamento do próprio label interno,
            // que já foi definido com CENTER dentro de createIconLabel()
            iconLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        }

        // Nome do arquivo
        JLabel nameLabel = new JLabel(shortName(file.getName(), 30));
        nameLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
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
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger() && clickListener != null) {
                    clickListener.onFileRightClick(
                            file, fileInfo,
                            FileItemPanel.this, e.getX(), e.getY(),
                            FileItemPanel.this); // NOVO — passa a si mesmo
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger() && clickListener != null) {
                    clickListener.onFileRightClick(
                            file, fileInfo,
                            FileItemPanel.this, e.getX(), e.getY(),
                            FileItemPanel.this); // NOVO
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                if (!selected) {
                    setBackground(hoverColor);
                    if (fileInfo.isDirectory()) {
                        setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                        setBorder(BorderFactory.createLineBorder(
                                new Color(33, 150, 243), 1));
                    } else {
                        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    }
                    // NOVO: repinta área do pai para limpar borda anterior
                    repaint();
                    Container parent = getParent();
                    if (parent != null)
                        parent.repaint(getX() - 2, getY() - 2, getWidth() + 4, getHeight() + 4);
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (!selected) {
                    setBackground(normalColor);
                    setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
                    // NOVO: repinta área do pai para limpar borda anterior
                    repaint();
                    Container parent = getParent();
                    if (parent != null)
                        parent.repaint(getX() - 2, getY() - 2, getWidth() + 4, getHeight() + 4);
                }
                setCursor(Cursor.getDefaultCursor());
            }

        });
        // Drag & Drop — só pastas podem ser arrastadas para os favoritos
        // arquivos comuns também podem ser arrastados (para uso futuro)
        setTransferHandler(new FileTransferHandler());

        dragAction = new DragAction(() -> displayFile, this);

    }

    public static boolean isShowRatingOverlay() {
        return showRatingOverlay;
    }

    public static void setShowRatingOverlay(boolean v) {
        showRatingOverlay = v;
    }

    public static boolean isShowExtensionFileOverlay() {
        return showExtensionFileOverlay;
    }

    public static void setShowExtensionFileOverlay(boolean showExtensionFileOverlay) {
        FileItemPanel.showExtensionFileOverlay = showExtensionFileOverlay;
    }


    /**
     * Registra um JLabel para receber o ícone quando o worker terminar.
     * Usa WeakReference para não impedir o GC de coletar labels descartados.
     */
    private static void registerPendingTarget(String key, JLabel target) {
        PENDING_TARGETS
                .computeIfAbsent(key, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(new WeakReference<>(target));
    }

    /**
     * Entrega o ícone a todos os JLabels pendentes para a key informada
     * e limpa a lista. Deve ser chamado na EDT.
     */
    private static void flushPendingTargets(String key, ImageIcon icon) {
        List<WeakReference<JLabel>> refs = PENDING_TARGETS.remove(key);
        if (refs == null) return;

        for (WeakReference<JLabel> ref : refs) {
            JLabel lbl = ref.get();
            if (lbl != null) {           // null = label já foi coletado pelo GC
                lbl.setIcon(icon);
                lbl.revalidate();
                lbl.repaint();
            }
        }
    }

    private static ImageIcon createLoadingIcon(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        // fundo semitransparente
        g.setComposite(AlphaComposite.SrcOver);
        g.setColor(new Color(240, 240, 240, 230));
        g.fillRect(0, 0, w, h);

        // borda leve
        g.setColor(new Color(200, 200, 200));
        g.drawRect(0, 0, w - 1, h - 1);

        // desenha três pontos centrais como 'loading'
        g.setFont(g.getFont().deriveFont(Font.BOLD, Math.max(10, w / 6f)));
        FontMetrics fm = g.getFontMetrics();
        String dots = "...";
        int tw = fm.stringWidth(dots);
        int tx = (w - tw) / 2;
        int ty = (h + fm.getAscent()) / 2 - 2;

        g.setColor(new Color(120, 120, 120));
        g.drawString(dots, tx, ty);

        g.dispose();
        return new ImageIcon(img);
    }

// ==============================================================
// NOVO MÉTODO: Atualiza ícone do label
// ==============================================================

    private static Icon getLoadingIcon() {
        return LOADING_ICON;
    }

    // Utilitário auxiliar (se não existir em outro lugar)
    public static String getExtension(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return (dot >= 0 && dot < name.length() - 1)
                ? name.substring(dot + 1).toLowerCase()
                : "";
    }

    private static String getMimeType(File file) {
        try {
            String type = java.nio.file.Files.probeContentType(file.toPath());
            if (type != null) {
                return type.toLowerCase(Locale.ROOT);
            }
        } catch (Exception ignored) {
        }

        // fallback para caso MIME retorne null
        String ext = getExtension(file);
        switch (ext) {
            case "jpg":
            case "jpeg":
            case "png":
            case "gif":
            case "bmp":
            case "tif":
            case "tiff":
            case "webp":
            case "heic":
            case "heif":
            case "svg":
                return "image/" + ext;

            case "mp4":
            case "m4v":
            case "mov":
            case "avi":
            case "mkv":
            case "flv":
            case "wmv":
            case "webm":
                return "video/" + ext;

            case "pdf":
                return "application/pdf";
        }

        return "application/octet-stream"; // genérico
    }

    private static boolean isImage(File file) {
        String mime = getMimeType(file);
        if (mime.startsWith("image/")) return true;

        String ext = getExtension(file);
        return IMAGE_EXTS.contains(ext);
    }

    private static boolean isVideo(File file) {
        String mime = getMimeType(file);
        if (mime.startsWith("video/")) return true;

        String ext = getExtension(file);
        return VIDEO_EXTS.contains(ext);
    }

    private static boolean isPdf(File file) {
        String mime = getMimeType(file);
        if (mime.equals("application/pdf")) return true;

        return getExtension(file).equals("pdf");
    }

    /**
     * Limpa thumbnails mais antigos que X dias
     */
    public static void clearOldThumbnails(int daysOld) {
        THUMBNAIL_CACHE.clearOldThumbnails(daysOld);
    }

    /**
     * Método para ser chamado ao fechar a aplicação
     */
    public static void shutdown() {
        THUMBNAIL_EXECUTOR.shutdown();
        try {
            if (!THUMBNAIL_EXECUTOR.awaitTermination(5, TimeUnit.SECONDS)) {
                THUMBNAIL_EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException e) {
            THUMBNAIL_EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
        }

    }

    /**
     * Retorna o gerenciador de cache (para uso externo)
     */
    public static ThumbnailCacheManager getThumbnailCacheManager() {
        return THUMBNAIL_CACHE;
    }

    public File getDisplayFile() {
        return displayFile;
    }

    public boolean isSelected() {
        return selected;
    }

    // ── Métodos de seleção ────────────────────────────────────────────
    public void setSelected(boolean selected) {
        this.selected = selected;
        updateVisual();
    }

    private void updateVisual() {
        if (selected) {
            setBackground(SELECTED_COLOR);
            setBorder(BorderFactory.createLineBorder(SELECTED_BORDER, 2));
        } else {
            setBackground(normalColor);
            setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        }

        // NOVO: repinta o próprio componente e pede ao pai
        // que limpe a área ao redor (onde a borda antiga ficava)
        repaint();
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

    /**
     * fitInsideSquare() — sem alteração, agora recebe imagem já proporcional
     * e só centraliza no canvas quadrado do card.
     */
    private BufferedImage fitInsideSquare(BufferedImage src, int boxSize) {
        if (src == null) return null;

        int srcW = src.getWidth();
        int srcH = src.getHeight();

        // A imagem já vem proporcional — só recalcula caso o boxSize seja diferente
        double scale = Math.min((double) boxSize / srcW, (double) boxSize / srcH);
        int dstW = Math.max(1, (int) (srcW * scale));
        int dstH = Math.max(1, (int) (srcH * scale));

        // Canvas quadrado transparente
        BufferedImage canvas = new BufferedImage(boxSize, boxSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = canvas.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        // Centraliza
        int offsetX = (boxSize - dstW) / 2;
        int offsetY = (boxSize - dstH) / 2;
        g2.drawImage(src, offsetX, offsetY, dstW, dstH, null);
        g2.dispose();

        return canvas;
    }

    /**
     * Versão para ícone do sistema (já é Icon, não BufferedImage).
     * Centraliza no espaço sem esticar.
     */
    private ImageIcon fitIconInsideSquare(Icon icon, int boxSize) {
        if (icon == null) return null;

        int iw = icon.getIconWidth();
        int ih = icon.getIconHeight();

        BufferedImage canvas = new BufferedImage(boxSize, boxSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = canvas.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Escala mantendo proporção
        double scale = Math.min((double) boxSize / iw, (double) boxSize / ih);
        int dstW = Math.max(1, (int) (iw * scale));
        int dstH = Math.max(1, (int) (ih * scale));
        int ox = (boxSize - dstW) / 2;
        int oy = (boxSize - dstH) / 2;

        // Desenha o ícone escalado e centralizado
        Image scaled = ((ImageIcon) resizeIcon(icon, Math.max(dstW, dstH))).getImage();
        g2.drawImage(scaled, ox, oy, dstW, dstH, null);
        g2.dispose();

        return new ImageIcon(canvas);
    }

    private JComponent createIconLabel() {
        int boxSize = this.thumbSize;
        File fileToDisplay = this.displayFile;   // já resolvido no construtor (destino real)

        // ── Detecta atalho pelo arquivo ORIGINAL, não pelo displayFile ──
        // originalFile é o .lnk; displayFile já é o destino
        boolean isShortcut = SimpleLinkResolver.isShortcut(this.originalFile);

        // ── VERIFICAÇÃO DE EXISTÊNCIA ──
        boolean exists = SimpleLinkResolver.isValid(this.originalFile);

        // Extensão e tipo vêm do DESTINO (displayFile)
        String ext;
        FileType fileType;
        if (isShortcut) {
            if (fileToDisplay.isDirectory()) {
                ext = "folder";
                fileType = FileType.FOLDER;
            } else {
                ext = getExtension(fileToDisplay);
                fileType = FileType.fromExtension(ext);
            }
        } else {
            ext = fileInfo.getExtension();
            fileType = fileInfo.getFileType();
        }

        // ── Ícone principal (do destino) ──────────────────────────────
        JLabel iconLabel = new JLabel();
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
        iconLabel.setVerticalAlignment(SwingConstants.CENTER);
        iconLabel.setPreferredSize(new Dimension(boxSize, boxSize));


        boolean hasTypeOvl = TypeOverlay.hasOverlay(ext, fileType);


        if (IconService.hasCustomIcon(ext, fileType)) {
            loadCustomIcon(ext, fileType, boxSize, iconLabel);
        } else if (AnimatedGifThumb.isShowAnimatedGif() && isAnimatedGif(fileToDisplay)) {
            JLabel gifLabel = animatedGifThumb.createGifLabel(boxSize);
            animatedGifThumb.loadAnimatedGif(fileToDisplay, boxSize, gifLabel);

            // Monta JLayeredPane opaco para isolar o GIF do RepaintManager
            JLayeredPane gifPane = animatedGifThumb.createGifLayeredPane(boxSize);
            gifLabel.setBounds(0, 0, boxSize, boxSize);
            gifPane.add(gifLabel, JLayeredPane.DEFAULT_LAYER);

            // Adiciona overlays se necessário
            if (isShortcut) {
                ShortcutOverlay so = new ShortcutOverlay();
                so.setBounds(0, 0, boxSize, boxSize);
                gifPane.add(so, JLayeredPane.MODAL_LAYER);
            }
            if (hasTypeOvl) {
                TypeOverlay so =new TypeOverlay(ext, fileType);
                so.setBounds(0, 0, boxSize, boxSize);
                gifPane.add(so, JLayeredPane.MODAL_LAYER);
            }
            ratingOverlay = new RatingOverlay(fileInfo.getRating()); // rating 0 = não desenha nada
            ratingOverlay.setBounds(0, 0, boxSize, boxSize);
            ratingOverlay.setVisible(showRatingOverlay && fileInfo.getRating() > 0);
            gifPane.add(ratingOverlay, JLayeredPane.MODAL_LAYER);

            return gifPane; // retorna direto — não precisa do fluxo abaixo
        } else if (isImage(fileToDisplay)) {
            loadThumbnailFit(fileToDisplay, boxSize, iconLabel);
        } else if (isVideo(fileToDisplay)) {
            loadVideoThumbnailFit(fileToDisplay, boxSize, iconLabel);
        } else if (isPdf(fileToDisplay)) {
            loadPdfThumbnailFit(fileToDisplay, boxSize, iconLabel);
        } else {

            Icon sys;
            if (isShortcut && !exists) {
                sys = IconService.getIconDefault("file_not_found", 512);
            } else {
                sys = FileSystemView.getFileSystemView().getSystemIcon(fileToDisplay);
            }

            iconLabel.setIcon(fitIconInsideSquare(sys, boxSize));
        }
        if (!hasTypeOvl && !isShortcut) return iconLabel;


        // ── Monta JLayeredPane ────────────────────────────────────────
        JLayeredPane layered = new JLayeredPane();
        layered.setPreferredSize(new Dimension(boxSize, boxSize));
        layered.setMaximumSize(new Dimension(boxSize, boxSize));   // impede que BoxLayout estique
        layered.setAlignmentX(Component.CENTER_ALIGNMENT);        // centraliza no BoxLayout
        layered.setOpaque(false);

        iconLabel.setBounds(0, 0, boxSize, boxSize);
        layered.add(iconLabel, JLayeredPane.DEFAULT_LAYER);

        ratingOverlay = new RatingOverlay(fileInfo.getRating()); // rating 0 = não desenha nada
        ratingOverlay.setBounds(0, 0, boxSize, boxSize);
        ratingOverlay.setVisible(showRatingOverlay && fileInfo.getRating() > 0);
        layered.add(ratingOverlay, JLayeredPane.PALETTE_LAYER);

        // Em createIconLabel() — remova o if interno de showExtensionFileOverlay:
        if (hasTypeOvl) {
            typeOverlay = new TypeOverlay(ext, fileType);
            typeOverlay.setBounds(0, 0, boxSize, boxSize);
            typeOverlay.setVisible(showExtensionFileOverlay); // ← visibilidade inicial
            layered.add(typeOverlay, JLayeredPane.PALETTE_LAYER);
        }

        // Seta de atalho — canto inferior esquerdo, sempre acima dos demais
        if (isShortcut) {
            ShortcutOverlay shortcutOverlay = new ShortcutOverlay();
            shortcutOverlay.setBounds(0, 0, boxSize, boxSize);
            layered.add(shortcutOverlay, JLayeredPane.MODAL_LAYER);
        }

        return layered;
    }


    private void loadCustomIcon(String extension, FileType fileType,
                                int boxSize, JLabel target) {
        // Chave de cache inclui extensão para diferenciar doc de docx
        String cacheKey = "custom_"
                + (extension != null ? extension.toLowerCase() : "")
                + "_" + (fileType != null ? fileType.name() : "")
                + "_" + boxSize;

        ImageIcon cached = ICON_CACHE.get(cacheKey);
        if (cached != null) {
            target.setIcon(cached);
            return;
        }

        new SwingWorker<ImageIcon, Void>() {
            @Override
            protected ImageIcon doInBackground() {
                // MODIFICADO: resolve passando extensão + tipo
                BufferedImage src = IconService.resolve(extension, fileType);
                if (src == null) return null;

                BufferedImage fitted = fitInsideSquare(src, boxSize);
                ImageIcon icon = new ImageIcon(fitted);
                ICON_CACHE.put(cacheKey, icon);
                return icon;
            }

            @Override
            protected void done() {
                try {
                    ImageIcon icon = get();
                    if (icon != null) {
                        SwingUtilities.invokeLater(() -> target.setIcon(icon));
                    } else {
                        Icon sys = FileSystemView.getFileSystemView()
                                .getSystemIcon(new File(fileInfo.getPath()));
                        SwingUtilities.invokeLater(() ->
                                target.setIcon(fitIconInsideSquare(sys, boxSize)));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }.execute();
    }

    /**
     * Carrega imagem e aplica fitInsideSquare em vez de scaleWithPreset.
     */
    void loadThumbnailFit(File file, int boxSize, JLabel target) {
        String key = "fit_" + file.getAbsolutePath() + "_" + boxSize;
        ImageIcon cached = ICON_CACHE.get(key);
        if (cached != null) {
            target.setIcon(cached);
            return;
        }

        new SwingWorker<ImageIcon, Void>() {
            @Override
            protected ImageIcon doInBackground() throws Exception {
                BufferedImage raw = loadThumbnail(file, boxSize, Preset.MEDIO);
                if (raw == null) return null;
                BufferedImage fitted = fitInsideSquare(raw, boxSize);
                ImageIcon icon = new ImageIcon(fitted);
                ICON_CACHE.put(key, icon);
                return icon;
            }

            @Override
            protected void done() {
                try {
                    ImageIcon ic = get();
                    SwingUtilities.invokeLater(() ->
                            target.setIcon(ic != null ? ic
                                    : fitIconInsideSquare(
                                    FileSystemView.getFileSystemView().getSystemIcon(file), boxSize)));
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() ->
                            target.setIcon(fitIconInsideSquare(
                                    FileSystemView.getFileSystemView().getSystemIcon(file), boxSize)));
                }
            }
        }.execute();
    }

    private void loadVideoThumbnailFit(File file, int boxSize, JLabel target) {
        String key = "vidfit_" + file.getAbsolutePath() + "_" + boxSize;

        // 1. Cache em memória — retorno imediato
        ImageIcon cached = ICON_CACHE.get(key);
        if (cached != null) {
            target.setIcon(cached);
            return;
        }

        // 2. Cache em disco
        BufferedImage disk = THUMBNAIL_CACHE.loadCachedThumbnail(file, boxSize);
        if (disk != null) {
            ImageIcon ic = new ImageIcon(fitInsideSquare(disk, boxSize));
            ICON_CACHE.put(key, ic);
            target.setIcon(ic);
            return;
        }

        // 3. Já está sendo processado — registra o target para
        //    receber o ícone quando o worker terminar
        if (THUMBNAIL_CACHE.isProcessing(file, boxSize)) {
            registerPendingTarget(key, target);
            return;
        }

        // 4. Processa em background
        THUMBNAIL_CACHE.markAsProcessing(file, boxSize);
        registerPendingTarget(key, target);

        THUMBNAIL_EXECUTOR.submit(() -> {
            try {
                BufferedImage raw = extractVideoThumbnail(file, boxSize);
                ImageIcon ic;

                if (raw != null) {
                    BufferedImage fitted = fitInsideSquare(raw, boxSize);
                    THUMBNAIL_CACHE.saveThumbnailToCache(file, boxSize, fitted);
                    ic = new ImageIcon(fitted);
                } else {
                    // Fallback: ícone do sistema centralizado
                    Icon sys = FileSystemView.getFileSystemView().getSystemIcon(file);
                    ic = fitIconInsideSquare(sys, boxSize);
                }

                // Salva no cache em memória
                ICON_CACHE.put(key, ic);

                // Notifica TODOS os targets pendentes para esta key
                // (podem existir múltiplos cards do mesmo vídeo na tela)
                final ImageIcon finalIc = ic;
                SwingUtilities.invokeLater(() -> flushPendingTargets(key, finalIc));

            } catch (Exception e) {
                System.err.println("Erro ao processar thumbnail: " + file.getName());
                Icon sys = FileSystemView.getFileSystemView().getSystemIcon(file);
                ImageIcon fallback = fitIconInsideSquare(sys, boxSize);
                ICON_CACHE.put(key, fallback);
                SwingUtilities.invokeLater(() -> flushPendingTargets(key, fallback));
            } finally {
                THUMBNAIL_CACHE.unmarkAsProcessing(file, boxSize);
            }
        });
    }

    /**
     * PDF: aplica fitInsideSquare após renderizar a página.
     */
    private void loadPdfThumbnailFit(File file, int boxSize, JLabel target) {
        String key = "pdffit_" + file.getAbsolutePath() + "_" + boxSize;
        ImageIcon cached = ICON_CACHE.get(key);
        if (cached != null) {
            target.setIcon(cached);
            return;
        }

        new SwingWorker<ImageIcon, Void>() {
            @Override
            protected ImageIcon doInBackground() {
                BufferedImage raw = extractPdfThumbnail(file, boxSize);
                if (raw == null) return null;
                BufferedImage fitted = fitInsideSquare(raw, boxSize);
                ImageIcon ic = new ImageIcon(fitted);
                ICON_CACHE.put(key, ic);
                return ic;
            }

            @Override
            protected void done() {
                try {
                    ImageIcon ic = get();
                    SwingUtilities.invokeLater(() ->
                            target.setIcon(ic != null ? ic
                                    : fitIconInsideSquare(
                                    FileSystemView.getFileSystemView().getSystemIcon(file), boxSize)));
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() ->
                            target.setIcon(fitIconInsideSquare(
                                    FileSystemView.getFileSystemView().getSystemIcon(file), boxSize)));
                }
            }
        }.execute();
    }

    /**
     * Atualiza o overlay de estrelas sem recriar o painel inteiro.
     */
    public void updateRatingOverlay(int newRating) {
        fileInfo.setRating(newRating);
        if (ratingOverlay != null) {
            ratingOverlay.setRating(newRating);
            ratingOverlay.setVisible(showRatingOverlay && newRating > 0);
            ratingOverlay.repaint(); // ← garante repaint do overlay

            // Força o pai (JLayeredPane) a se repintar também
            Container parent = ratingOverlay.getParent();
            if (parent != null) parent.repaint();
        }
    }

    /**
     * Aplica a visibilidade global a este painel.
     * Chamado quando o usuário liga/desliga pelo menu de contexto.
     */
    public void applyOverlayVisibility() {
        if (ratingOverlay != null) {
            ratingOverlay.setVisible(showRatingOverlay && fileInfo.getRating() > 0);
        }
    }

    public void applyExtensionTypeVisibility() {
        if (typeOverlay != null) {
            typeOverlay.setVisible(showExtensionFileOverlay);
        }
    }




    public void applyAnimatedGifVisibility() {
        // Só precisa recriar se o arquivo for GIF animado
        String ext = fileInfo.getExtension();
        if (!ext.equalsIgnoreCase("gif")) return;
        if (!AnimatedGifThumb.isAnimatedGif(displayFile)) return;

        // Invalida cache para forçar recriação pelo caminho correto
        String cacheKey = "gif_" + displayFile.getAbsolutePath() + "_" + thumbSize;
        ICON_CACHE.remove(cacheKey);

        SwingUtilities.invokeLater(() -> {
            JComponent newIcon = createIconLabel(); // lerá AnimatedGifThumb.isShowAnimatedGif()

            int idx = -1;
            for (int i = 0; i < getComponentCount(); i++) {
                if (getComponent(i) == iconSlot) { idx = i; break; }
            }
            if (idx < 0) return;

            remove(iconSlot);
            add(newIcon, idx);
            newIcon.setAlignmentX(Component.CENTER_ALIGNMENT);
            iconSlot = newIcon;

            revalidate();
            repaint();
        });
    }
    /**
     * Carrega a imagem em baixa resolução e retorna com proporção original.
     * NÃO força quadrado — deixa fitInsideSquare() fazer o encaixe.
     * Substitui o loadThumbnail() anterior.
     */
    private BufferedImage loadThumbnail(File file, int targetSize, Preset preset)
            throws IOException {

        try (ImageInputStream in = ImageIO.createImageInputStream(file)) {
            if (in == null) return null;

            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) return null;

            ImageReader reader = readers.next();
            reader.setInput(in);

            int origW = reader.getWidth(0);
            int origH = reader.getHeight(0);

            // Subsampling proporcional ao preset (igual ao original)
            int subsample = switch (preset) {
                case RAPIDO -> Math.max(2, Math.min(origW, origH) / (targetSize * 2));
                case MEDIO -> Math.max(1, Math.min(origW, origH) / targetSize);
                case ALTA_QUALIDADE -> 1;
            };

            ImageReadParam param = reader.getDefaultReadParam();
            param.setSourceSubsampling(subsample, subsample, 0, 0);

            BufferedImage lowRes = reader.read(0, param);
            reader.dispose();

            // MODIFICADO: escala mantendo proporção em vez de forçar quadrado
            return scaleProportional(lowRes, targetSize);
        }
    }

    /**
     * Escala a imagem para que o lado maior caiba em maxSize,
     * mantendo a proporção original. Substitui scaleWithPreset().
     */
    private BufferedImage scaleProportional(BufferedImage src, int maxSize) {
        if (src == null) return null;

        int srcW = src.getWidth();
        int srcH = src.getHeight();

        // Escala pelo lado maior
        double scale = (double) maxSize / Math.max(srcW, srcH);
        int dstW = Math.max(1, (int) (srcW * scale));
        int dstH = Math.max(1, (int) (srcH * scale));

        BufferedImage dst = new BufferedImage(dstW, dstH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = dst.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g2.drawImage(src, 0, 0, dstW, dstH, null);
        g2.dispose();
        return dst;
    }

    private BufferedImage scaleWithPreset(BufferedImage src, int size, Preset preset) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();

        switch (preset) {

            case RAPIDO:
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING,
                        RenderingHints.VALUE_RENDER_SPEED);
                break;

            case MEDIO:
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING,
                        RenderingHints.VALUE_RENDER_QUALITY);
                break;

            case ALTA_QUALIDADE:
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING,
                        RenderingHints.VALUE_RENDER_QUALITY);
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                break;
        }

        g2.drawImage(src, 0, 0, size, size, null);
        g2.dispose();

        return img;
    }

    public Icon resizeIcon(Icon icon, int size) {
        if (icon == null) return null;

        BufferedImage img = new BufferedImage(
                icon.getIconWidth(),
                icon.getIconHeight(),
                BufferedImage.TYPE_INT_ARGB
        );

        Graphics2D g2 = img.createGraphics();
        icon.paintIcon(null, g2, 0, 0);
        g2.dispose();

        // Agora redimensiona a imagem
        Image scaled = img.getScaledInstance(size, size, Image.SCALE_SMOOTH);
        return new ImageIcon(scaled);
    }

    private ImageIcon getOrCache(String key, Supplier<ImageIcon> generator) {
        return ICON_CACHE.computeIfAbsent(key, k -> generator.get());
    }

    // Atualize o método loadVideoThumbnailAsync:
    private void loadVideoThumbnailAsync(File file, int size, JLabel target) {
        String key = "vid_" + file.getAbsolutePath() + "_" + size;

        // 1. Verifica cache em memória primeiro
        ImageIcon cached = ICON_CACHE.get(key);
        if (cached != null) {
            target.setIcon(cached);
            return;
        }

        // 2. Verifica se já está processando este arquivo
        if (THUMBNAIL_CACHE.isProcessing(file, size)) {
            return; // Evita processar o mesmo arquivo múltiplas vezes
        }

        // 3. Tenta carregar do cache em disco
        BufferedImage cachedImage = THUMBNAIL_CACHE.loadCachedThumbnail(file, size);
        if (cachedImage != null) {
            ImageIcon ic = new ImageIcon(cachedImage);
            ICON_CACHE.put(key, ic); // Adiciona também ao cache em memória
            target.setIcon(ic);
            return;
        }

        // 4. Marca como processando
        THUMBNAIL_CACHE.markAsProcessing(file, size);

        // 5. Processa o vídeo em background
        THUMBNAIL_EXECUTOR.submit(() -> {
            try {
                BufferedImage img = extractVideoThumbnail(file, size);

                if (img != null) {
                    // Salva no cache em disco
                    THUMBNAIL_CACHE.saveThumbnailToCache(file, size, img);

                    // Salva no cache em memória
                    ImageIcon ic = new ImageIcon(img);
                    ICON_CACHE.put(key, ic);

                    // Atualiza a UI na thread do Swing
                    SwingUtilities.invokeLater(() -> target.setIcon(ic));
                } else {
                    // Se falhar, usa ícone do sistema
                    SwingUtilities.invokeLater(() ->
                            target.setIcon(getSystemIconCached(file, size)));
                }

            } catch (Exception e) {
                System.err.println("Erro ao processar thumbnail de vídeo: " + file.getName());
                e.printStackTrace();
                SwingUtilities.invokeLater(() ->
                        target.setIcon(getSystemIconCached(file, size)));

            } finally {
                // Remove marca de processamento
                THUMBNAIL_CACHE.unmarkAsProcessing(file, size);
            }
        });
    }

    // Atualizado  com melhor tratamento de erros:
    private BufferedImage extractVideoThumbnail(File file, int size) {
        FFmpegFrameGrabber grabber = null;
        Java2DFrameConverter converter = null;

        try {
            grabber = new FFmpegFrameGrabber(file);
            grabber.setFormat(null);
            grabber.setImageMode(FFmpegFrameGrabber.ImageMode.COLOR);

            // Timeout para evitar travamentos
            grabber.setOption("timeout", "5000000"); // 5 segundos em microsegundos

            try {
                grabber.start();
            } catch (FFmpegFrameGrabber.Exception e) {
                throw new RuntimeException(e);
            }

            // Tenta pegar um frame do meio do vídeo
            int totalFrames = grabber.getLengthInFrames();
            if (totalFrames > 10) {
                grabber.setFrameNumber(Math.min(totalFrames / 4, 50));
            }

            Frame frame = null;
            int attempts = 0;
            int maxAttempts = 10;

            // Tenta pegar um frame válido
            while (frame == null && attempts < maxAttempts) {
                frame = grabber.grabImage();
                attempts++;

                if (frame == null && attempts < maxAttempts) {
                    Thread.sleep(50); // Pequena pausa entre tentativas
                }
            }

            if (frame == null || frame.image == null) {
                System.err.println("Nenhum frame válido encontrado para: " + file.getName());
                return null;
            }

            converter = new Java2DFrameConverter();
            BufferedImage img = converter.convert(frame);

            if (img == null) {
                System.err.println("Falha ao converter frame para BufferedImage: " + file.getName());
                return null;
            }

            return scaleWithPreset(img, size, Preset.MEDIO);

        } catch (Exception e) {
            System.err.println("Erro ao extrair thumbnail do vídeo: " + file.getName());
            e.printStackTrace();
            return null;

        } finally {
            // Libera recursos na ordem correta
            if (converter != null) {
                try {
                    converter.close();
                } catch (Exception e) {
                    System.err.println("Erro ao fechar converter: " + e.getMessage());
                }
            }

            if (grabber != null) {
                try {
                    grabber.stop();
                    grabber.release();
                } catch (Exception e) {
                    System.err.println("Erro ao fechar grabber: " + e.getMessage());
                }
            }
        }
    }

    private void loadPdfThumbnailAsync(File file, int size, JLabel target) {
        String key = "pdf_" + file.getAbsolutePath() + "_" + size;

        ImageIcon cached = ICON_CACHE.get(key);
        if (cached != null) {
            target.setIcon(cached);
            return;
        }

        new SwingWorker<ImageIcon, Void>() {
            @Override
            protected ImageIcon doInBackground() throws Exception {
                BufferedImage img = extractPdfThumbnail(file, size);
                if (img == null) return null;

                ImageIcon ic = new ImageIcon(img);
                ICON_CACHE.put(key, ic);
                return ic;
            }

            @Override
            protected void done() {
                try {
                    ImageIcon ic = get();
                    if (ic != null) target.setIcon(ic);
                    else target.setIcon(getSystemIconCached(file, size));
                } catch (Exception e) {
                    target.setIcon(getSystemIconCached(file, size));
                }
            }
        }.execute();
    }

    private BufferedImage extractPdfThumbnail(File file, int size) {
        PDDocument document;
        try {
            document = Loader.loadPDF(file);
            PDFRenderer renderer = new PDFRenderer(document);

            // renderiza página 0
            BufferedImage page = renderer.renderImageWithDPI(0, 64); // bem rápido
            return scaleWithPreset(page, size, Preset.MEDIO);

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private String createTooltip() {
        return String.format(
                "<html><b>%s</b><br>Tamanho: %.2f MB<br>Tipo: %s<br>Caminho: %s</html>",
                displayFile.getName(),
                fileInfo.getSize() / (1024.0 * 1024.0),
                fileInfo.getFileType(),
                displayFile.getParent()
        );
    }

    private Icon getSystemIconCached(File file, int size) {
        String key = "sys_" + file.getAbsolutePath() + "_" + size;

        return getOrCache(key, () -> {
            Icon sysIcon = FileSystemView.getFileSystemView().getSystemIcon(file);
            return (ImageIcon) resizeIcon(sysIcon, size);
        });

    }

    /**
     * Carrega thumbnail de IMAGEM de forma assíncrona
     * USA APENAS CACHE EM MEMÓRIA (não salva em disco)
     */
    private void loadThumbnailAsyncCached(File file, int size, Preset preset, JLabel target) {
        String key = "thumb_" + file.getAbsolutePath() + "_" + size + "_" + preset;

        // Verifica APENAS cache em memória
        ImageIcon cached = ICON_CACHE.get(key);
        if (cached != null) {
            target.setIcon(cached);
            return;
        }

        // Gera thumbnail em background (SEM salvar em disco)
        new SwingWorker<ImageIcon, Void>() {
            @Override
            protected ImageIcon doInBackground() throws Exception {
                BufferedImage img = loadThumbnail(file, size, preset);
                if (img == null) return null;

                // Salva APENAS no cache em memória
                ImageIcon icon = new ImageIcon(img);
                ICON_CACHE.put(key, icon);
                return icon;
            }

            @Override
            protected void done() {
                try {
                    ImageIcon icon = get();
                    if (icon != null) {
                        SwingUtilities.invokeLater(() -> target.setIcon(icon));
                    } else {
                        SwingUtilities.invokeLater(() ->
                                target.setIcon(getSystemIconCached(file, size)));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    SwingUtilities.invokeLater(() ->
                            target.setIcon(getSystemIconCached(file, size)));
                }
            }
        }.execute();
    }

    private String shortName(String name, int maxLen) {
        if (name.length() <= maxLen) return name;
        return name.substring(0, maxLen - 3) + "...";
    }

    public void setClickListener(ResultsPanel.FileItemClickListener listener) {
        this.clickListener = listener;
    }

    public File getFile() {
        return displayFile;
    }

    public FileInfo getFileInfo() {
        return fileInfo;
    }

    public enum Preset {
        RAPIDO,
        MEDIO,
        ALTA_QUALIDADE
    }

}