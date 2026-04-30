package com.esl.searchforfiles.ui;

import mslinks.ShellLink;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.*;

/**
 * Resolvedor de atalhos .lnk usando biblioteca mslinks
 * MUITO MAIS SIMPLES E CONFIÁVEL que parse manual
 */
public class SimpleLinkResolver {

    /**
     * Verifica se é atalho
     */
    public static boolean isShortcut(File file) {
        return file != null &&
                file.exists() &&
                file.getName().toLowerCase().endsWith(".lnk");
    }
    public static boolean isValid(File file) {
        return file != null &&
                !file.exists();
    }

    /**
     * Resolve atalho usando biblioteca mslinks com fallbacks
     */
    public static File resolveShortcut(File shortcutFile) {
        if (!isShortcut(shortcutFile)) {
            return null;
        }

        // MÉTODO 1: Tenta com mslinks
        try {
            ShellLink link = new ShellLink(shortcutFile);

            // Obtém o caminho do destino
            String targetPath = link.resolveTarget();

            if (targetPath != null && !targetPath.isEmpty()) {
                File targetFile = new File(targetPath);

                if (targetFile.exists()) {
                    return targetFile;
                }
            }

            // Fallback: tenta caminho relativo
            String relativePath = link.getRelativePath();
            if (relativePath != null && !relativePath.isEmpty()) {
                File parent = shortcutFile.getParentFile();
                if (parent != null) {
                    File relativeFile = new File(parent, relativePath);
                    if (relativeFile.exists()) {
                        return relativeFile;
                    }
                }
            }

        } catch (Exception e) {
            // Se mslinks falhar (unsupported ItemID type), tenta métodos alternativos
            System.err.println("⚠️ mslinks falhou para: " + shortcutFile.getName() +
                    " - Tentando método alternativo...");

            // MÉTODO 2: Tenta parse manual simples
            File manualResult = parseShortcutManually(shortcutFile);
            if (manualResult != null && manualResult.exists()) {
                System.out.println("✓ Resolvido com método alternativo: " + manualResult.getName());
                return manualResult;
            }

            // MÉTODO 3: Usa Windows Script (último recurso)
            File scriptResult = resolveWithWindowsScript(shortcutFile);
            if (scriptResult != null && scriptResult.exists()) {
                System.out.println("✓ Resolvido com script: " + scriptResult.getName());
                return scriptResult;
            }
        }

        return null;
    }

    /**
     * Obtém informações detalhadas do atalho (com tratamento de erro)
     */
    public static ShortcutInfo getShortcutInfo(File shortcutFile) {
        if (!isShortcut(shortcutFile)) {
            return null;
        }

        try {
            ShellLink link = new ShellLink(shortcutFile);

            return new ShortcutInfo(
                    link.resolveTarget(),
                    link.getRelativePath(),
                    link.getWorkingDir(),
                    link.getIconLocation(),
                    link.getCMDArgs()
            );

        } catch (Exception e) {
            // Se falhar, retorna info mínima
            System.err.println("⚠️ Não foi possível obter info detalhada de: " + shortcutFile.getName());
            return new ShortcutInfo(null, null, null, null, null);
        }
    }


    /**
     * Classe para armazenar informações do atalho
     */
    public static class ShortcutInfo {
        public final String targetPath;
        public final String relativePath;
        public final String workingDir;
        public final String iconLocation;
        public final String arguments;

        public ShortcutInfo(String targetPath, String relativePath,
                            String workingDir, String iconLocation, String arguments) {
            this.targetPath = targetPath;
            this.relativePath = relativePath;
            this.workingDir = workingDir;
            this.iconLocation = iconLocation;
            this.arguments = arguments;
        }

        @Override
        public String toString() {
            return String.format(
                    "ShortcutInfo{\n" +
                            "  Target: %s\n" +
                            "  Relative: %s\n" +
                            "  Working Dir: %s\n" +
                            "  Icon: %s\n" +
                            "  Args: %s\n" +
                            "}",
                    targetPath, relativePath, workingDir, iconLocation, arguments
            );
        }
    }

    /**
     * Teste
     */
    public static void main(String[] args) {
        File shortcut = new File("C:\\Users\\Public\\Desktop\\Google Chrome.lnk");

        if (shortcut.exists()) {
            System.out.println("=== Testando atalho ===");
            System.out.println("Arquivo: " + shortcut.getName());

            File target = resolveShortcut(shortcut);
            if (target != null) {
                System.out.println("✓ Destino: " + target.getAbsolutePath());
                System.out.println("✓ Existe: " + target.exists());
            } else {
                System.out.println("✗ Não foi possível resolver");
            }

            System.out.println("\n=== Informações detalhadas ===");
            ShortcutInfo info = getShortcutInfo(shortcut);
            if (info != null) {
                System.out.println(info);
            }
        }
    }

    /**
     * MÉTODO ALTERNATIVO 1: Parse manual simplificado do .lnk
     */
    private static File parseShortcutManually(File shortcutFile) {
        try (FileInputStream fis = new FileInputStream(shortcutFile)) {
            byte[] header = new byte[76];

            if (fis.read(header) != 76) {
                return null;
            }

            // Verifica magic number
            ByteBuffer buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
            int magic = buffer.getInt(0);

            if (magic != 0x0000004C) {  // 'L' magic number
                return null;
            }

            // Lê flags
            int flags = buffer.getInt(20);

            // Pula LinkTargetIDList se presente
            if ((flags & 0x01) != 0) {  // HAS_LINK_TARGET_ID_LIST
                byte[] sizeBytes = new byte[2];
                if (fis.read(sizeBytes) != 2) return null;

                int idListSize = ByteBuffer.wrap(sizeBytes).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
                fis.skip(idListSize);
            }

            // Lê LinkInfo se presente
            if ((flags & 0x02) != 0) {  // HAS_LINK_INFO
                byte[] sizeBytes = new byte[4];
                if (fis.read(sizeBytes) != 4) return null;

                int linkInfoSize = ByteBuffer.wrap(sizeBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();

                if (linkInfoSize > 28 && linkInfoSize < 1024 * 1024) {  // Sanity check
                    byte[] linkInfo = new byte[linkInfoSize - 4];
                    if (fis.read(linkInfo) == linkInfo.length) {
                        String path = extractPathFromLinkInfo(linkInfo);
                        if (path != null && !path.isEmpty()) {
                            return new File(path);
                        }
                    }
                }
            }

        } catch (Exception e) {
            // Ignora erros silenciosamente
        }

        return null;
    }

    /**
     * Extrai caminho do LinkInfo
     */
    private static String extractPathFromLinkInfo(byte[] linkInfo) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(linkInfo).order(ByteOrder.LITTLE_ENDIAN);

            if (linkInfo.length < 16) return null;

            int localBasePathOffset = buffer.getInt(12);

            if (localBasePathOffset > 0 && localBasePathOffset < linkInfo.length) {
                StringBuilder path = new StringBuilder();

                for (int i = localBasePathOffset; i < linkInfo.length; i++) {
                    byte b = linkInfo[i];
                    if (b == 0) break;
                    path.append((char) (b & 0xFF));
                }

                String result = path.toString().trim();
                if (!result.isEmpty()) {
                    return result;
                }
            }
        } catch (Exception e) {
            // Ignora
        }

        return null;
    }

    /**
     * MÉTODO ALTERNATIVO 2: Usa Windows Script para resolver atalho
     */
    private static File resolveWithWindowsScript(File shortcutFile) {
        // Este método usa VBScript como último recurso
        // Só funciona no Windows

        if (!System.getProperty("os.name").toLowerCase().contains("windows")) {
            return null;
        }

        try {
            // Cria script temporário
            File tempScript = File.createTempFile("resolve_lnk_", ".vbs");
            tempScript.deleteOnExit();

            String script = String.format(
                    "Set WshShell = CreateObject(\"WScript.Shell\")\n" +
                            "Set lnk = WshShell.CreateShortcut(\"%s\")\n" +
                            "WScript.Echo lnk.TargetPath\n",
                    shortcutFile.getAbsolutePath().replace("\\", "\\\\")
            );

            Files.write(tempScript.toPath(), script.getBytes(StandardCharsets.UTF_8));

            // Executa script
            ProcessBuilder pb = new ProcessBuilder(
                    "cscript", "//NoLogo", tempScript.getAbsolutePath()
            );
            pb.redirectErrorStream(true);

            Process process = pb.start();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream())
            );

            String targetPath = reader.readLine();
            reader.close();

            boolean finished = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
            }

            tempScript.delete();

            if (targetPath != null && !targetPath.trim().isEmpty()) {
                return new File(targetPath.trim());
            }

        } catch (Exception e) {
            // Ignora erros do script
        }

        return null;
    }
}
// ===================================================================
// INTEGRAÇÃO NO FileItemPanel (VERSÃO SIMPLIFICADA):
// ===================================================================




