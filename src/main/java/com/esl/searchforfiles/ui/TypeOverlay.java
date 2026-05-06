package com.esl.searchforfiles.ui;


import com.esl.searchforfiles.model.FileType;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TypeOverlay extends JComponent {

    // ── Nível 1: extensão específica → ícone de overlay ──────────
    // Mesma lógica do IconService, mas voltada para o badge de tipo.
    // Útil quando uma extensão merece um badge diferente do seu FileType.
    // Ex: "iso" (COMPRESSED) pode ter badge próprio distinto de "zip".
    private static final Map<String, String> EXTENSION_ICONS = new HashMap<>();
    // ── Nível 2: FileType genérico → ícone de overlay ────────────
    private static final Map<FileType, String> TYPE_ICONS = new EnumMap<>(FileType.class);
    // ── Cache unificado: chave = caminho do recurso ───────────────
    private static final Map<String, BufferedImage> IMAGE_CACHE = new ConcurrentHashMap<>();
    private static final Color BG_COLOR = new Color(0, 0, 0, 90);

    static {
        //AUDIO
        EXTENSION_ICONS.put("aac", "/icons/ext/audio/aac.png");
        EXTENSION_ICONS.put("alac", "/icons/ext/audio/alac.png");
        EXTENSION_ICONS.put("ape", "/icons/ext/audio/ape.png");
        EXTENSION_ICONS.put("flac", "/icons/ext/audio/flac.png");
        EXTENSION_ICONS.put("m3u", "/icons/ext/audio/m3u.png");
        EXTENSION_ICONS.put("m4a", "/icons/ext/audio/m4a.png");
        EXTENSION_ICONS.put("mp3", "/icons/ext/audio/mp3.png");
        EXTENSION_ICONS.put("ogg", "/icons/ext/audio/ogg.png");
        EXTENSION_ICONS.put("opus", "/icons/ext/audio/opus.png");
        EXTENSION_ICONS.put("wav", "/icons/ext/audio/wav.png");
        EXTENSION_ICONS.put("wma", "/icons/ext/audio/wma.png");
        //COMPRESSED
        EXTENSION_ICONS.put("7z", "/icons/ext/compressed/7z.png");
        EXTENSION_ICONS.put("bz2", "/icons/ext/compressed/bz2.png");
        EXTENSION_ICONS.put("dmg", "/icons/ext/compressed/dmg.png");
        EXTENSION_ICONS.put("gz", "/icons/ext/compressed/gz.png");
        EXTENSION_ICONS.put("iso", "/icons/ext/compressed/iso.png");
        EXTENSION_ICONS.put("pkg", "/icons/ext/compressed/pkg.png");
        EXTENSION_ICONS.put("rar", "/icons/ext/compressed/rar.png");
        EXTENSION_ICONS.put("tar", "/icons/ext/compressed/tar.png");
        EXTENSION_ICONS.put("xz", "/icons/ext/compressed/xz.png");
        EXTENSION_ICONS.put("zip", "/icons/ext/compressed/zip.png");
        //EXECUTABLE
        EXTENSION_ICONS.put("app", "/icons/ext/executable/app.png");
        EXTENSION_ICONS.put("bat", "/icons/ext/executable/bat.png");
        EXTENSION_ICONS.put("cmd", "/icons/ext/executable/cmd.png");
        EXTENSION_ICONS.put("com", "/icons/ext/executable/com.png");
        EXTENSION_ICONS.put("exe", "/icons/ext/executable/exe.png");
        EXTENSION_ICONS.put("ini", "/icons/ext/executable/ini.png");
        EXTENSION_ICONS.put("jar", "/icons/ext/executable/jar.png");
        EXTENSION_ICONS.put("msi", "/icons/ext/executable/msi.png");
        EXTENSION_ICONS.put("sh", "/icons/ext/executable/sh.png");

        //IMAGES
        EXTENSION_ICONS.put("jpg", "/icons/ext/image/jpg.png");
        EXTENSION_ICONS.put("jpeg", "/icons/ext/image/jpg.png");
        EXTENSION_ICONS.put("gif", "/icons/ext/image/gif.png");
        EXTENSION_ICONS.put("png", "/icons/ext/image/png.png");
        EXTENSION_ICONS.put("bmp", "/icons/ext/image/bmp.png");
        EXTENSION_ICONS.put("heic", "/icons/ext/image/heic.png");
        EXTENSION_ICONS.put("raw", "/icons/ext/image/raw.png");
        EXTENSION_ICONS.put("svg", "/icons/ext/image/svg.png");
        EXTENSION_ICONS.put("tif", "/icons/ext/image/tif.png");
        EXTENSION_ICONS.put("tiff", "/icons/ext/image/tiff.png");
        EXTENSION_ICONS.put("webp", "/icons/ext/image/webp.png");
        //VIDEOS
        EXTENSION_ICONS.put("avi", "/icons/ext/video/avi.png");
        EXTENSION_ICONS.put("flv", "/icons/ext/video/flv.png");
        EXTENSION_ICONS.put("m4v", "/icons/ext/video/m4v.png");
        EXTENSION_ICONS.put("mkv", "/icons/ext/video/mkv.png");
        EXTENSION_ICONS.put("mov", "/icons/ext/video/mov.png");
        EXTENSION_ICONS.put("mp4", "/icons/ext/video/mp4.png");
        EXTENSION_ICONS.put("webm", "/icons/ext/video/webm.png");
        EXTENSION_ICONS.put("wmv", "/icons/ext/video/wmv.png");
        //DOCUMENTS
        EXTENSION_ICONS.put("csv", "/icons/ext/document/csv.png");
        EXTENSION_ICONS.put("doc", "/icons/ext/document/doc.png");
        EXTENSION_ICONS.put("docx", "/icons/ext/document/docx.png");
        EXTENSION_ICONS.put("json", "/icons/ext/document/json.png");
        EXTENSION_ICONS.put("log", "/icons/ext/document/log.png");
        EXTENSION_ICONS.put("md", "/icons/ext/document/md.png");
        EXTENSION_ICONS.put("odt", "/icons/ext/document/odt.png");
        EXTENSION_ICONS.put("pdf", "/icons/ext/document/pdf.png");
        EXTENSION_ICONS.put("ppt", "/icons/ext/document/ppt.png");
        EXTENSION_ICONS.put("pptx", "/icons/ext/document/pptx.png");
        EXTENSION_ICONS.put("rtf", "/icons/ext/document/rtf.png");
        EXTENSION_ICONS.put("txt", "/icons/ext/document/txt.png");
        EXTENSION_ICONS.put("xls", "/icons/ext/document/xls.png");
        EXTENSION_ICONS.put("xlsx", "/icons/ext/document/xlsx.png");
        EXTENSION_ICONS.put("xml", "/icons/ext/document/xml.png");

    }

    static {
//        TYPE_ICONS.put(FileType.VIDEO, "/icons/overlay/video.png");
//        // Exemplos:
//        // TYPE_ICONS.put(FileType.AUDIO,    "/icons/overlay/audio.png");
//        // TYPE_ICONS.put(FileType.DOCUMENT, "/icons/overlay/document.png");
    }

    // ── Estado da instância ───────────────────────────────────────
    private final String extension; // pode ser null
    private final FileType fileType;
    private BufferedImage typeImage;

    // ── Construtor atualizado: recebe extensão + tipo ─────────────
    public TypeOverlay(String extension, FileType fileType) {
        this.extension = (extension != null) ? extension.toLowerCase() : null;
        this.fileType = fileType;
        setOpaque(false);
        loadImage();
    }

    // ── Verifica se existe overlay para extensão ou tipo ─────────
    public static boolean hasOverlay(String extension, FileType fileType) {
        if (extension != null && !extension.isEmpty()
                && EXTENSION_ICONS.containsKey(extension.toLowerCase())) {
            return true;
        }
        return fileType != null && TYPE_ICONS.containsKey(fileType);
    }

    // ── Resolve o caminho do recurso (extensão tem prioridade) ────
    private String resolvePath() {
        if (extension != null && !extension.isEmpty()) {
            String extPath = EXTENSION_ICONS.get(extension);
            if (extPath != null) return extPath;
        }
        if (fileType != null) {
            return TYPE_ICONS.get(fileType); // pode ser null → sem overlay
        }
        return null;
    }

    // ── Carregamento lazy com cache compartilhado ─────────────────
    private void loadImage() {
        String path = resolvePath();
        if (path == null) return;

        // Já em cache
        if (IMAGE_CACHE.containsKey(path)) {
            typeImage = IMAGE_CACHE.get(path);
            return;
        }

        // Carrega em background para não travar a EDT
        new SwingWorker<BufferedImage, Void>() {
            @Override
            protected BufferedImage doInBackground() {
                try (InputStream is = TypeOverlay.class.getResourceAsStream(path)) {
                    if (is == null) {
                        System.err.println("⚠️ TypeOverlay: arquivo não encontrado: " + path);
                        return null;
                    }
                    return ImageIO.read(is);
                } catch (IOException e) {
                    System.err.println("⚠️ TypeOverlay: erro ao carregar " + path);
                    return null;
                }
            }

            @Override
            protected void done() {
                try {
                    BufferedImage img = get();
                    if (img != null) {
                        IMAGE_CACHE.put(path, img);
                        typeImage = img;
                        repaint();
                    }
                } catch (Exception ignored) {
                }
            }
        }.execute();
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (typeImage == null) return;

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        int iconSize = Math.min(40, Math.max(14, (int) (getWidth() * 0.22)));
        int pad = 4;

        int bgX = getWidth() - iconSize - pad * 2 - 2;
        int bgY = getHeight() - iconSize - pad * 2 - 2;
        int bgW = iconSize + pad * 2;
        int bgH = iconSize + pad * 2;

        g2.setColor(BG_COLOR);
        g2.fillRoundRect(bgX, bgY, bgW, bgH, 6, 6);

        g2.drawImage(typeImage, bgX + pad, bgY + pad, iconSize, iconSize, null);
        g2.dispose();
    }

}
