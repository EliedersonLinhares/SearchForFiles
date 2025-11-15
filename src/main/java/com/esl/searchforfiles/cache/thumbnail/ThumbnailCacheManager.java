package com.esl.searchforfiles.cache.thumbnail;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gerenciador de cache persistente para thumbnails de vídeos
 */
public class ThumbnailCacheManager {
    private static final String CACHE_DIR_NAME = ".thumbnail_cache";
    private static final String THUMBNAIL_FORMAT = "jpg";
    private static final float JPEG_QUALITY = 0.85f;

    private final Path cacheDirectory;
    private final ConcurrentHashMap<String, Boolean> processingFiles = new ConcurrentHashMap<>();

    public ThumbnailCacheManager() {
        // Cria diretório de cache no diretório do usuário
        String userHome = System.getProperty("user.home");
        this.cacheDirectory = Paths.get(userHome, CACHE_DIR_NAME, "videos");
        createCacheDirectory();
    }

    /**
     * Construtor alternativo com diretório customizado
     */
    public ThumbnailCacheManager(String customCacheDir) {
        this.cacheDirectory = Paths.get(customCacheDir, "video_thumbnails");
        createCacheDirectory();
    }

    private void createCacheDirectory() {
        try {
            Files.createDirectories(cacheDirectory);
            System.out.println("Diretório de cache criado/verificado: " + cacheDirectory);
        } catch (IOException e) {
            System.err.println("Erro ao criar diretório de cache: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Gera um hash único baseado no caminho do arquivo, tamanho e data de modificação
     */
    private String generateCacheKey(File videoFile, int thumbnailSize) {
        try {
            String key = videoFile.getAbsolutePath() +
                    "_" + videoFile.lastModified() +
                    "_" + videoFile.length() +
                    "_size" + thumbnailSize;

            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(key.getBytes("UTF-8"));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }

            return hexString.toString();
        } catch (Exception e) {
            // Fallback: usa apenas o nome do arquivo
            return videoFile.getName().replaceAll("[^a-zA-Z0-9]", "_") + "_" + thumbnailSize;
        }
    }

    /**
     * Retorna o caminho do arquivo de cache para um vídeo
     */
    private Path getCachePath(File videoFile, int thumbnailSize) {
        String cacheKey = generateCacheKey(videoFile, thumbnailSize);
        return cacheDirectory.resolve(cacheKey + "." + THUMBNAIL_FORMAT);
    }

    /**
     * Verifica se existe thumbnail em cache para o vídeo
     */
    public boolean hasCachedThumbnail(File videoFile, int thumbnailSize) {
        Path cachePath = getCachePath(videoFile, thumbnailSize);
        return Files.exists(cachePath) && Files.isReadable(cachePath);
    }

    /**
     * Carrega thumbnail do cache
     */
    public BufferedImage loadCachedThumbnail(File videoFile, int thumbnailSize) {
        Path cachePath = getCachePath(videoFile, thumbnailSize);

        try {
            if (Files.exists(cachePath)) {
                BufferedImage img = ImageIO.read(cachePath.toFile());
                if (img != null) {
                    System.out.println("Thumbnail carregado do cache: " + videoFile.getName());
                    return img;
                }
            }
        } catch (IOException e) {
            System.err.println("Erro ao carregar thumbnail do cache: " + e.getMessage());
            // Se houver erro, tenta deletar o arquivo corrompido
            try {
                Files.deleteIfExists(cachePath);
            } catch (IOException ex) {
                // Ignora erro ao deletar
            }
        }

        return null;
    }

    /**
     * Salva thumbnail no cache
     */
    public boolean saveThumbnailToCache(File videoFile, int thumbnailSize, BufferedImage thumbnail) {
        if (thumbnail == null) {
            return false;
        }

        Path cachePath = getCachePath(videoFile, thumbnailSize);

        try {
            // Salva como JPEG para economizar espaço
            ImageIO.write(thumbnail, THUMBNAIL_FORMAT, cachePath.toFile());
            System.out.println("Thumbnail salvo no cache: " + videoFile.getName() +
                    " -> " + cachePath.getFileName());
            return true;

        } catch (IOException e) {
            System.err.println("Erro ao salvar thumbnail no cache: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Verifica se o arquivo está sendo processado no momento
     */
    public boolean isProcessing(File videoFile, int thumbnailSize) {
        String key = videoFile.getAbsolutePath() + "_" + thumbnailSize;
        return processingFiles.getOrDefault(key, false);
    }

    /**
     * Marca arquivo como em processamento
     */
    public void markAsProcessing(File videoFile, int thumbnailSize) {
        String key = videoFile.getAbsolutePath() + "_" + thumbnailSize;
        processingFiles.put(key, true);
    }

    /**
     * Remove marca de processamento
     */
    public void unmarkAsProcessing(File videoFile, int thumbnailSize) {
        String key = videoFile.getAbsolutePath() + "_" + thumbnailSize;
        processingFiles.remove(key);
    }

    /**
     * Remove thumbnail específico do cache
     */
    public boolean removeCachedThumbnail(File videoFile, int thumbnailSize) {
        Path cachePath = getCachePath(videoFile, thumbnailSize);
        try {
            return Files.deleteIfExists(cachePath);
        } catch (IOException e) {
            System.err.println("Erro ao remover thumbnail do cache: " + e.getMessage());
            return false;
        }
    }

    /**
     * Limpa todo o cache de thumbnails
     */
    public void clearCache() {
        try {
            Files.walk(cacheDirectory)
                    .filter(Files::isRegularFile)
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            System.err.println("Erro ao deletar: " + path);
                        }
                    });
            System.out.println("Cache de thumbnails limpo.");
        } catch (IOException e) {
            System.err.println("Erro ao limpar cache: " + e.getMessage());
        }
    }

    /**
     * Retorna o tamanho total do cache em bytes
     */
    public long getCacheSize() {
        try {
            return Files.walk(cacheDirectory)
                    .filter(Files::isRegularFile)
                    .mapToLong(path -> {
                        try {
                            return Files.size(path);
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .sum();
        } catch (IOException e) {
            return 0;
        }
    }

    /**
     * Retorna o tamanho do cache formatado
     */
    public String getCacheSizeFormatted() {
        long bytes = getCacheSize();
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    /**
     * Retorna quantidade de thumbnails no cache
     */
    public long getThumbnailCount() {
        try {
            return Files.walk(cacheDirectory)
                    .filter(Files::isRegularFile)
                    .count();
        } catch (IOException e) {
            return 0;
        }
    }

    /**
     * Limpa thumbnails antigos (mais de X dias)
     */
    public void clearOldThumbnails(int daysOld) {
        long cutoffTime = System.currentTimeMillis() - (daysOld * 24L * 60 * 60 * 1000);

        try {
            Files.walk(cacheDirectory)
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        try {
                            return Files.getLastModifiedTime(path).toMillis() < cutoffTime;
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                            System.out.println("Thumbnail antigo removido: " + path.getFileName());
                        } catch (IOException e) {
                            System.err.println("Erro ao deletar thumbnail antigo: " + path);
                        }
                    });
        } catch (IOException e) {
            System.err.println("Erro ao limpar thumbnails antigos: " + e.getMessage());
        }
    }

    /**
     * Retorna o diretório de cache
     */
    public Path getCacheDirectory() {
        return cacheDirectory;
    }
}
