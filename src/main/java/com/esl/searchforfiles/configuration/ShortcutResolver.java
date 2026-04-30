package com.esl.searchforfiles.configuration;

import java.io.*;

/**
 * Resolve atalhos do Windows (.lnk) para o arquivo de destino.
 *
 * A API pública é propositalmente simples:
 *   ShortcutResolver.Result r = ShortcutResolver.resolve(file);
 *   r.target()     → File a ser exibido (destino ou o próprio arquivo)
 *   r.isShortcut() → true se o arquivo original era um .lnk
 */
public final class ShortcutResolver {

    private ShortcutResolver() {}

    // ── Resultado imutável ────────────────────────────────────────
    public record Result(File target, boolean isShortcut) {}

    /**
     * Se o arquivo for um .lnk válido, devolve o destino.
     * Caso contrário (não é atalho, destino não existe, erro de leitura)
     * devolve o próprio arquivo sem marcação de atalho.
     */
    public static Result resolve(File file) {
        if (file == null) return new Result(file, false);

        String name = file.getName().toLowerCase();
        if (!name.endsWith(".lnk")) return new Result(file, false);

        try {
            File target = parseLnk(file);
            if (target != null && target.exists()) {
                return new Result(target, true);
            }
        } catch (Exception e) {
            System.err.println("⚠️ ShortcutResolver: erro ao ler atalho "
                    + file.getName() + " — " + e.getMessage());
        }

        // Falhou ou destino não existe → trata como arquivo comum
        return new Result(file, false);
    }

    // ─────────────────────────────────────────────────────────────
    // Parsing nativo via Shell COM (ShellLink) — sem dependências
    // Funciona em qualquer JVM no Windows
    // ─────────────────────────────────────────────────────────────
    private static File parseLnk(File lnk) throws IOException {
        // Usa a classe interna sun.awt.shell.ShellFolder quando disponível
        // (presente no JDK para Windows desde o Java 6)
        try {
            Class<?> sfClass = Class.forName("sun.awt.shell.ShellFolder");
            Object sf = sfClass.getMethod("getShellFolder", File.class)
                    .invoke(null, lnk);
            // getLinkLocation() retorna o caminho do destino
            Object link = sfClass.getMethod("getLinkLocation").invoke(sf);
            if (link instanceof String path) return new File(path);
        } catch (ReflectiveOperationException ignored) {
            // API interna indisponível → cai no parser manual
        }

        // ── Parser manual do formato binário .lnk (Shell Link) ───
        // Especificação: MS-SHLLINK §2.1 (ShellLinkHeader) + §2.3 (LinkInfo)
        return parseLnkBinary(lnk);
    }

    /**
     * Lê o caminho local a partir do bloco LinkInfo do arquivo .lnk.
     * Cobre a grande maioria dos atalhos gerados pelo Windows Explorer.
     */
    private static File parseLnkBinary(File lnk) throws IOException {
        try (FileInputStream fis = new FileInputStream(lnk);
             DataInputStream dis = new DataInputStream(
                     new BufferedInputStream(fis))) {

            // ── Header (76 bytes) ─────────────────────────────────
            byte[] magic = new byte[4];
            dis.readFully(magic);
            // Magic: 4C 00 00 00
            if (magic[0] != 0x4C || magic[1] != 0x00
                    || magic[2] != 0x00 || magic[3] != 0x00) {
                return null; // não é um .lnk válido
            }

            // CLSID (16 bytes) + flags (4) + fileAttr (4) + timestamps (24)
            // + fileSize (4) + iconIndex (4) + showCmd (4) + hotKey (2)
            // + reserved (6) = 68 bytes a pular após o magic
            dis.skipBytes(68);

            // ── LinkFlags (lido acima junto com o header) ─────────
            // Precisamos saber se HasLinkTargetIDList e HasLinkInfo estão ativos.
            // Relemos o arquivo para pegar os flags corretamente.
            return parseLnkBinaryFull(lnk);
        }
    }

    private static File parseLnkBinaryFull(File lnk) throws IOException {
        byte[] data = java.nio.file.Files.readAllBytes(lnk.toPath());

        if (data.length < 76) return null;

        // LinkFlags está nos bytes 20–23 (little-endian)
        int flags = readInt(data, 20);
        boolean hasIDList   = (flags & 0x01) != 0;
        boolean hasLinkInfo = (flags & 0x02) != 0;

        int offset = 76; // fim do ShellLinkHeader

        // Pula IDList se presente
        if (hasIDList) {
            if (offset + 2 > data.length) return null;
            int idListSize = readShort(data, offset);
            offset += 2 + idListSize;
        }

        if (!hasLinkInfo || offset + 4 > data.length) return null;

        // ── LinkInfo ──────────────────────────────────────────────
        int linkInfoSize   = readInt(data, offset);
        int linkInfoOffset = readInt(data, offset + 8);  // LocalBasePathOffset
        boolean hasLocalPath = (readInt(data, offset + 4) & 0x01) != 0;

        if (hasLocalPath) {
            int pathStart = offset + linkInfoOffset;
            String path = readNullTerminatedString(data, pathStart);
            if (path != null) return new File(path);
        }

        // Tenta CommonPathSuffixOffset como fallback (offset 24 no LinkInfo)
        int commonOffset = readInt(data, offset + 24);
        if (commonOffset > 0) {
            String path = readNullTerminatedString(data, offset + commonOffset);
            if (path != null) return new File(path);
        }

        return null;
    }

    // ── Helpers de leitura little-endian ──────────────────────────

    private static int readInt(byte[] data, int pos) {
        return (data[pos] & 0xFF)
                | ((data[pos+1] & 0xFF) <<  8)
                | ((data[pos+2] & 0xFF) << 16)
                | ((data[pos+3] & 0xFF) << 24);
    }

    private static int readShort(byte[] data, int pos) {
        return (data[pos] & 0xFF) | ((data[pos+1] & 0xFF) << 8);
    }

    private static String readNullTerminatedString(byte[] data, int start) {
        if (start < 0 || start >= data.length) return null;
        int end = start;
        while (end < data.length && data[end] != 0) end++;
        return new String(data, start, end - start, java.nio.charset.StandardCharsets.ISO_8859_1);
    }
}