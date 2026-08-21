package com.esl.searchforfiles.configuration;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

public class VersionChecker {

    /**
     * Tenta encontrar a versão por múltiplas estratégias, em ordem:
     * 1. pom.properties (Maven padrão)
     * 2. MANIFEST.MF (Implementation-Version ou Bundle-Version)
     * 3. Busca no classpath por JARs com o nome do artefato
     */
    public static String getLibraryVersion(String groupId, String artifactId) {

        // 1. pom.properties — caminho Maven padrão
        String v = fromPomProperties(groupId, artifactId);
        if (isValid(v)) return v;

        // 2. MANIFEST.MF do JAR que contém a classe principal do artefato
        v = fromManifest(groupId, artifactId);
        if (isValid(v)) return v;

        // 3. Nome do JAR no classpath (ex: flatlaf-3.6.2.jar)
        v = fromJarName(artifactId);
        if (isValid(v)) return v;

        return "não encontrada";
    }

    // ── Estratégia 1: pom.properties ────────────────────────────

    private static String fromPomProperties(String groupId, String artifactId) {
        String path = String.format(
                "/META-INF/maven/%s/%s/pom.properties", groupId, artifactId);
        try (InputStream in = VersionChecker.class.getResourceAsStream(path)) {
            if (in == null) return null;
            Properties props = new Properties();
            props.load(in);
            return props.getProperty("version");
        } catch (IOException e) {
            return null;
        }
    }

    // ── Estratégia 2: MANIFEST.MF ────────────────────────────────
    // Lê o manifesto do JAR que está no classpath e procura por
    // Implementation-Version ou Bundle-Version (padrão OSGi, usado pelo FlatLaf).

    private static String fromManifest(String groupId, String artifactId) {
        // Monta um nome de pacote esperado a partir do groupId
        // ex: "com.formdev" → tenta carregar com.formdev.flatlaf.FlatLaf
        String[] candidates = buildClassCandidates(groupId, artifactId);

        for (String candidate : candidates) {
            try {
                Class<?> cls = Class.forName(candidate);
                String v = readManifestVersion(cls);
                if (isValid(v)) return v;
            } catch (ClassNotFoundException ignored) {
                // Tenta o próximo candidato
            }
        }
        return null;
    }

    private static String readManifestVersion(Class<?> cls) {
        // Package.getImplementationVersion() já lê o MANIFEST.MF automaticamente
        Package pkg = cls.getPackage();
        if (pkg != null) {
            String v = pkg.getImplementationVersion();
            if (isValid(v)) return v;
            v = pkg.getSpecificationVersion();
            if (isValid(v)) return v;
        }

        // Fallback: lê o MANIFEST.MF do JAR diretamente
        try {
            String jarPath = cls.getProtectionDomain()
                    .getCodeSource().getLocation().toURI().toString();
            if (!jarPath.endsWith(".jar")) return null;

            URL manifestUrl = new URL("jar:" + jarPath + "!/META-INF/MANIFEST.MF");
            try (InputStream in = manifestUrl.openStream()) {
                java.util.jar.Manifest mf = new java.util.jar.Manifest(in);
                java.util.jar.Attributes attrs = mf.getMainAttributes();

                // Tenta vários atributos comuns
                for (String key : List.of(
                        "Implementation-Version",
                        "Bundle-Version",          // OSGi / FlatLaf
                        "Specification-Version",
                        "X-Compile-Source-JDK")) {
                    String v = attrs.getValue(key);
                    if (isValid(v)) return v;
                }
            }
        } catch (Exception ignored) {}

        return null;
    }

    // ── Estratégia 3: nome do JAR no classpath ───────────────────
    // Procura por um JAR cujo nome começa com o artifactId e extrai
    // a versão do nome do arquivo (ex: flatlaf-3.6.2.jar → 3.6.2).

    private static String fromJarName(String artifactId) {
        String cp = System.getProperty("java.class.path", "");
        String sep = System.getProperty("path.separator", ";");

        for (String entry : cp.split(sep)) {
            String name = Paths.get(entry).getFileName().toString().toLowerCase();
            String prefix = artifactId.toLowerCase();

            if (name.startsWith(prefix) && name.endsWith(".jar")) {
                // Remove prefixo e extensão: "flatlaf-3.6.2.jar" → "3.6.2"
                String rest = name.substring(prefix.length(), name.length() - 4);
                if (rest.startsWith("-")) rest = rest.substring(1);
                if (isValid(rest)) return rest;
            }
        }
        return null;
    }

    // ── Candidatos de classe por groupId/artifactId ──────────────
    // Heurística: monta nomes de classe prováveis para localizar o JAR.

    private static String[] buildClassCandidates(String groupId, String artifactId) {
        // Converte artifactId com hifens em camelCase para nomes de classe
        // ex: flatlaf-intellij-themes → FlatLafIntelliJThemes
        String simple = Arrays.stream(artifactId.split("-"))
                .map(w -> w.isEmpty() ? "" :
                        Character.toUpperCase(w.charAt(0)) + w.substring(1))
                .collect(Collectors.joining());

        String pkg = groupId.replace("-", ".");

        return new String[]{
                pkg + "." + artifactId.replace("-", ".") + "." + simple,
                pkg + "." + simple,
                pkg + "." + artifactId.replace("-", "") + "." + simple,
                // Casos conhecidos explícitos
                "com.formdev.flatlaf.FlatLaf",
                "com.formdev.flatlaf.intellijthemes.FlatAllIJThemes",
                "org.bytedeco.javacv.FrameGrabber",
                "org.apache.pdfbox.Loader",
                "com.github.vatbub.mslinks.ShellLink",
        };
    }

    private static boolean isValid(String v) {
        return v != null && !v.isBlank() && !v.equals("Versão não encontrada");
    }
}