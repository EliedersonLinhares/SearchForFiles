package com.esl.searchforfiles.configuration;


import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

// ════════════════════════════════════════════════════════════════
// 3. FingerprintCalculator — calcula o fingerprint sem ler conteúdo
// ════════════════════════════════════════════════════════════════
public class FingerprintCalculator {

    /**
     * Fingerprint baseado em: nome + tamanho + lastModified.
     *
     * Removemos creationTime porque o Windows não a preserva ao
     * copiar entre volumes diferentes (pendrive → HD, rede, etc).
     * lastModified é preservado pelo Windows ao mover/copiar.
     *
     * Formato: "nome:tamanho:lastModified"
     */
    public static String calculate(Path path, BasicFileAttributes attrs) {
        if (path == null || attrs == null) return null;

        Path fileNamePath = path.getFileName();
        String name      = fileNamePath != null
                ? fileNamePath.toString() : path.toString();
        long size         = attrs.size();
        long lastModified = attrs.lastModifiedTime().toMillis();  // ← mudança

        return name + ":" + size + ":" + lastModified;
    }
}
