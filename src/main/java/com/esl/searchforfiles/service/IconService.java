package com.esl.searchforfiles.service;

import com.esl.searchforfiles.model.FileType;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class IconService {
    // ── Nível 1: extensão específica ──────────────────────────────
    // Chave: extensão em minúsculo sem ponto (ex: "docx", "pdf")
    private static final Map<String, String> EXTENSION_ICONS = new HashMap<>();
    // ── Nível 2: FileType genérico ────────────────────────────────
    private static final Map<FileType, String> TYPE_ICONS = new EnumMap<>(FileType.class);
    // ── Cache unificado: chave = caminho do recurso ───────────────
    private static final Map<String, BufferedImage> IMAGE_CACHE
            = new ConcurrentHashMap<>();
    // No topo da classe, junto aos outros mapas
    private static final Map<String, String> NAMED_ICONS = new HashMap<>();

    static {
// "odt", "xls", "xlsx", "ppt", "pptx",
//                "csv", "xml", "json", "md", "log", "srt" , "ass", "sub" , "vtt"
//        EXTENSION_ICONS.put("exe", "/icons/ext/executable/exe.png");
        EXTENSION_ICONS.put("doc", "/icons/types/document.png");
        EXTENSION_ICONS.put("docx", "/icons/types/document.png");
        EXTENSION_ICONS.put("txt", "/icons/types/document.png");
        EXTENSION_ICONS.put("rtf", "/icons/types/document.png");
        EXTENSION_ICONS.put("odt", "/icons/types/document.png");
        EXTENSION_ICONS.put("xls", "/icons/types/document.png");
        EXTENSION_ICONS.put("xlsx", "/icons/types/document.png");
        EXTENSION_ICONS.put("ppt", "/icons/types/document.png");
        EXTENSION_ICONS.put("pptx", "/icons/types/document.png");
        EXTENSION_ICONS.put("csv", "/icons/types/document.png");
        EXTENSION_ICONS.put("json", "/icons/types/document.png");
        EXTENSION_ICONS.put("md", "/icons/types/document.png");
        EXTENSION_ICONS.put("log", "/icons/types/document.png");
        EXTENSION_ICONS.put("srt", "/icons/types/document.png");
        EXTENSION_ICONS.put("ass", "/icons/types/document.png");
        EXTENSION_ICONS.put("sub", "/icons/types/document.png");
        EXTENSION_ICONS.put("vtt", "/icons/types/document.png");


        EXTENSION_ICONS.put("m3u", "/icons/types/audio.png");
        // Adicione quantas extensões quiser aqui
    }

    static {
        //EXTENSION_ICONS.put("folder", "/icons/types/folder/folder.png"); // pasta via extensão fake
        TYPE_ICONS.put(FileType.FOLDER, "/icons/types/folder/folder.png");
        TYPE_ICONS.put(FileType.AUDIO, "/icons/types/audio.png");
//        TYPE_ICONS.put(FileType.DOCUMENT, "/icons/types/document.png");
        TYPE_ICONS.put(FileType.COMPRESSED, "/icons/types/compressed.png");
        TYPE_ICONS.put(FileType.EXECUTABLE, "/icons/types/executable.png");


    }

    static {
        // Registre aqui seus ícones padrão que não dependem de extensão
        NAMED_ICONS.put("file_not_found", "/icons/ext/unknown2.png");
    }


    // ─────────────────────────────────────────────────────────────
    // API pública
    // ─────────────────────────────────────────────────────────────
    public static BufferedImage resolveNamed(String key) {
        String path = NAMED_ICONS.get(key);
        return (path != null) ? load(path) : null;
    }

    /**
     * Retorna um Icon para uso direto em JLabel, no tamanho solicitado.
     * Hierarquia de resolução:
     * 1. Extensão específica  (EXTENSION_ICONS)
     * 2. FileType genérico    (TYPE_ICONS)
     * 3. Ícone nativo do SO   (FileSystemView — fallback sempre disponível)
     * <p>
     * Uso:
     * iconLabel.setIcon(IconService.getIcon(file, ext, fileType, 16));
     *
     * @param file      arquivo original — usado apenas no fallback do sistema
     * @param extension extensão em minúsculo sem ponto (ex: "docx"). Pode ser null.
     * @param fileType  tipo genérico do arquivo. Pode ser null.
     * @param size      lado do quadrado em pixels para o ícone final
     */
    public static Icon getIcon(File file, String extension, FileType fileType, int size) {
        String cacheKey = "icon_"
                + (extension != null ? extension : "")
                + "_" + (fileType != null ? fileType.name() : "")
                + "_" + size;

        // Cache em memória — evita reescalar sempre
        BufferedImage cached = IMAGE_CACHE.get(cacheKey);
        if (cached != null) return new ImageIcon(cached);

        // Níveis 1 e 2 — customizado
        BufferedImage src = resolve(extension, fileType);
        if (src != null) {
            BufferedImage scaled = scaleToSquare(src, size);
            IMAGE_CACHE.put(cacheKey, scaled);
            return new ImageIcon(scaled);
        }

        // Nível 3 — ícone nativo do SO
        if (file != null) {
            return resizeSystemIcon(
                    FileSystemView.getFileSystemView().getSystemIcon(file), size);
        }

        return null; // sem arquivo e sem customização
    }

    /**
     * Retorna um ícone baseado em uma chave interna (ex: "default_file"),
     * ignorando a extensão real do arquivo.
     */
    public static Icon getIconDefault(String key, int size) {
        String path = NAMED_ICONS.get(key);
        if (path == null) return null;

        String cacheKey = "named_" + key + "_" + size;

        // Tenta pegar do cache de imagens redimensionadas
        BufferedImage cached = IMAGE_CACHE.get(cacheKey);
        if (cached != null) return new ImageIcon(cached);

        // Carrega a imagem original e escala
        BufferedImage src = load(path);
        if (src != null) {
            BufferedImage scaled = scaleToSquare(src, size);
            IMAGE_CACHE.put(cacheKey, scaled);
            return new ImageIcon(scaled);
        }

        return null;
    }


    /**
     * Resolve e retorna a imagem para o arquivo informado.
     * Tenta extensão primeiro; se não houver, tenta FileType.
     * Retorna null se não houver customização — o chamador usa
     * o ícone padrão do sistema nesse caso.
     *
     * @param extension extensão do arquivo em minúsculo, sem ponto
     *                  (ex: "docx"). Pode ser null ou vazio.
     * @param fileType  tipo genérico do arquivo
     */
    public static BufferedImage resolve(String extension, FileType fileType) {
        // Nível 1 — extensão específica
        if (extension != null && !extension.isEmpty()) {
            String extPath = EXTENSION_ICONS.get(extension.toLowerCase());
            if (extPath != null) {
                BufferedImage img = load(extPath);
                if (img != null) return img;
            }
        }

        // Nível 2 — FileType genérico
        if (fileType != null) {
            String typePath = TYPE_ICONS.get(fileType);
            if (typePath != null) {
                return load(typePath);
            }
        }

        return null; // sem customização → ícone do sistema
    }

    /**
     * Verifica se existe alguma customização para o arquivo,
     * sem carregar a imagem. Útil para decidir se usa o pipeline
     * customizado ou o padrão.
     */
    public static boolean hasCustomIcon(String extension, FileType fileType) {
        if (extension != null && !extension.isEmpty()
                && EXTENSION_ICONS.containsKey(extension.toLowerCase())) {
            return true;
        }
        return fileType != null && TYPE_ICONS.containsKey(fileType);
    }

    // ─────────────────────────────────────────────────────────────
    // Interno
    // ─────────────────────────────────────────────────────────────

    /**
     * Carrega e cacheia uma imagem pelo caminho do recurso.
     */
    private static BufferedImage load(String resourcePath) {
        return IMAGE_CACHE.computeIfAbsent(resourcePath, path -> {
            try (InputStream is = IconService.class.getResourceAsStream(path)) {
                if (is == null) {
                    System.err.println("⚠️ Ícone não encontrado: " + path);
                    return null;
                }
                BufferedImage img = ImageIO.read(is);
                System.out.println("✓ Ícone carregado: " + path);
                return img;
            } catch (IOException e) {
                System.err.println("⚠️ Erro ao carregar ícone: " + path
                        + " — " + e.getMessage());
                return null;
            }
        });
    }

    /**
     * Escala um BufferedImage para um quadrado de `size` × `size`.
     */
    private static BufferedImage scaleToSquare(BufferedImage src, int size) {
        BufferedImage dst = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = dst.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        double scale = (double) size / Math.max(src.getWidth(), src.getHeight());
        int dw = (int) (src.getWidth() * scale);
        int dh = (int) (src.getHeight() * scale);
        g2.drawImage(src, (size - dw) / 2, (size - dh) / 2, dw, dh, null);
        g2.dispose();
        return dst;
    }

    /**
     * Redimensiona um Icon do sistema para `size` × `size`.
     */
    private static ImageIcon resizeSystemIcon(Icon icon, int size) {
        if (icon == null) return null;
        BufferedImage img = new BufferedImage(
                icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        icon.paintIcon(null, g2, 0, 0);
        g2.dispose();
        return new ImageIcon(scaleToSquare(img, size));
    }
}
