package com.esl.searchforfiles.ui;


import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class UltraSimpleJNA {
    private static final Map<String, ImageIcon> ICON_CACHE = new ConcurrentHashMap<>();

    // Flags
    private static final int SHGFI_ICON = 0x000000100;
    private static final int SHGFI_LARGEICON = 0x0;
    private static final int SHGFI_SMALLICON = 0x1;

    /**
     * Interface Shell32 minimalista
     */
    public interface Shell32Min extends StdCallLibrary {
        Shell32Min INSTANCE = Native.load("shell32", Shell32Min.class, W32APIOptions.DEFAULT_OPTIONS);

        // Retorna long em vez de Pointer para evitar problemas
        long SHGetFileInfo(
                String pszPath,
                int dwFileAttributes,
                SHFILEINFO psfi,
                int cbFileInfo,
                int uFlags
        );
    }

    /**
     * Interface User32 minimalista
     */
    public interface User32Min extends StdCallLibrary {
        User32Min INSTANCE = Native.load("user32", User32Min.class, W32APIOptions.DEFAULT_OPTIONS);

        boolean DestroyIcon(Pointer hIcon);
        Pointer GetDC(Pointer hWnd);
        int ReleaseDC(Pointer hWnd, Pointer hDC);
        boolean DrawIcon(Pointer hDC, int X, int Y, Pointer hIcon);
    }

    /**
     * Interface GDI32 minimalista (usa Pointer em tudo)
     */
    public interface GDI32Min extends StdCallLibrary {
        GDI32Min INSTANCE = Native.load("gdi32", GDI32Min.class, W32APIOptions.DEFAULT_OPTIONS);

        Pointer CreateCompatibleDC(Pointer hdc);
        Pointer CreateCompatibleBitmap(Pointer hdc, int nWidth, int nHeight);
        Pointer SelectObject(Pointer hdc, Pointer hgdiobj);
        boolean DeleteDC(Pointer hdc);
        boolean DeleteObject(Pointer hObject);

        int GetBitmapBits(Pointer hbmp, int cbBuffer, Pointer lpvBits);
    }

    /**
     * Estrutura SHFILEINFO usando Pointer
     */
    @Structure.FieldOrder({"hIcon", "iIcon", "dwAttributes", "szDisplayName", "szTypeName"})
    public static class SHFILEINFO extends Structure {
        public Pointer hIcon;  // Usa Pointer em vez de HICON
        public int iIcon;
        public int dwAttributes;
        public char[] szDisplayName = new char[260];
        public char[] szTypeName = new char[80];
    }

    /**
     * Método principal - obtém ícone
     */
    public static ImageIcon getIcon(File file, int targetSize) {
        if (file == null || !file.exists()) {
            return createDefaultIcon(targetSize);
        }

        String cacheKey = file.getAbsolutePath() + "_" + targetSize;
        ImageIcon cached = ICON_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        try {
            BufferedImage image = extractAndRenderIcon(file, targetSize);

            if (image != null) {
                ImageIcon icon = new ImageIcon(image);
                ICON_CACHE.put(cacheKey, icon);
                return icon;
            }

        } catch (Exception e) {
            System.err.println("Erro ao extrair ícone: " + e.getMessage());
        }

        return createDefaultIcon(targetSize);
    }

    /**
     * Extrai e renderiza ícone (tudo com Pointer)
     */
    private static BufferedImage extractAndRenderIcon(File file, int targetSize) {
        SHFILEINFO fileInfo = new SHFILEINFO();

        // Escolhe flag baseado no tamanho
        int flag = targetSize <= 16 ? SHGFI_SMALLICON : SHGFI_LARGEICON;

        // Obtém ícone
        long result = Shell32Min.INSTANCE.SHGetFileInfo(
                file.getAbsolutePath(),
                0,
                fileInfo,
                fileInfo.size(),
                SHGFI_ICON | flag
        );

        if (result == 0 || fileInfo.hIcon == null || fileInfo.hIcon == Pointer.NULL) {
            return null;
        }

        try {
            // Renderiza ícone em bitmap
            BufferedImage image = renderIconToImage(fileInfo.hIcon, targetSize);
            return image;

        } finally {
            // Sempre libera o ícone
            User32Min.INSTANCE.DestroyIcon(fileInfo.hIcon);
        }
    }

    /**
     * Renderiza ícone usando AWT (mais simples e confiável)
     */
    private static BufferedImage renderIconToImage(Pointer hIcon, int targetSize) {
        try {
            // Cria imagem temporária para desenhar o ícone
            int nativeSize = 32; // Tamanho nativo do ícone large
            BufferedImage tempImage = new BufferedImage(nativeSize, nativeSize, BufferedImage.TYPE_INT_ARGB);

            // Obtém contexto de dispositivo
            Pointer hdcScreen = User32Min.INSTANCE.GetDC(null);
            Pointer hdcMem = GDI32Min.INSTANCE.CreateCompatibleDC(hdcScreen);

            // Cria bitmap
            Pointer hBitmap = GDI32Min.INSTANCE.CreateCompatibleBitmap(hdcScreen, nativeSize, nativeSize);
            Pointer hOldBitmap = GDI32Min.INSTANCE.SelectObject(hdcMem, hBitmap);

            // Desenha ícone
            User32Min.INSTANCE.DrawIcon(hdcMem, 0, 0, hIcon);

            // Lê pixels do bitmap
            int bufferSize = nativeSize * nativeSize * 4;
            Pointer buffer = new com.sun.jna.Memory(bufferSize);
            GDI32Min.INSTANCE.GetBitmapBits(hBitmap, bufferSize, buffer);

            // Converte para BufferedImage
            byte[] pixels = buffer.getByteArray(0, bufferSize);

            for (int y = 0; y < nativeSize; y++) {
                for (int x = 0; x < nativeSize; x++) {
                    int idx = (y * nativeSize + x) * 4;

                    int blue = pixels[idx] & 0xFF;
                    int green = pixels[idx + 1] & 0xFF;
                    int red = pixels[idx + 2] & 0xFF;
                    int alpha = pixels[idx + 3] & 0xFF;

                    if (alpha == 0) alpha = 255;

                    int argb = (alpha << 24) | (red << 16) | (green << 8) | blue;
                    tempImage.setRGB(x, y, argb);
                }
            }

            // Limpa recursos
            GDI32Min.INSTANCE.SelectObject(hdcMem, hOldBitmap);
            GDI32Min.INSTANCE.DeleteObject(hBitmap);
            GDI32Min.INSTANCE.DeleteDC(hdcMem);
            User32Min.INSTANCE.ReleaseDC(null, hdcScreen);

            // Redimensiona se necessário
            if (targetSize != nativeSize) {
                return resizeImage(tempImage, targetSize);
            }

            return tempImage;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Redimensiona com qualidade
     */
    private static BufferedImage resizeImage(BufferedImage source, int size) {
        BufferedImage resized = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = resized.createGraphics();

        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        g2d.drawImage(source, 0, 0, size, size, null);
        g2d.dispose();

        return resized;
    }

    /**
     * Cria ícone padrão
     */
    private static ImageIcon createDefaultIcon(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        g2d.setColor(new Color(240, 240, 245));
        g2d.fillRoundRect(size/8, size/8, size*3/4, size*3/4, size/8, size/8);

        g2d.setColor(new Color(180, 180, 190));
        g2d.setStroke(new BasicStroke(2));
        g2d.drawRoundRect(size/8, size/8, size*3/4, size*3/4, size/8, size/8);

        g2d.dispose();
        return new ImageIcon(image);
    }

    /**
     * Limpa cache
     */
    public static void clearCache() {
        ICON_CACHE.clear();
    }

    /**
     * Teste
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            File testFile = new File("C:\\Windows\\notepad.exe");

            JFrame frame = new JFrame("Ultra Simple JNA Test");
            frame.setLayout(new FlowLayout());

            frame.add(new JLabel("16:", getIcon(testFile, 16), JLabel.LEFT));
            frame.add(new JLabel("32:", getIcon(testFile, 32), JLabel.LEFT));
            frame.add(new JLabel("48:", getIcon(testFile, 48), JLabel.LEFT));
            frame.add(new JLabel("64:", getIcon(testFile, 64), JLabel.LEFT));
            frame.add(new JLabel("128:", getIcon(testFile, 128), JLabel.LEFT));

            frame.pack();
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setVisible(true);
        });
    }
}
