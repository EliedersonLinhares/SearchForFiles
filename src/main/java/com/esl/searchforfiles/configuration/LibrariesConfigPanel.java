package com.esl.searchforfiles.configuration;

import javax.swing.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class LibrariesConfigPanel extends ConfigPanelBase {

    // ── Descriptor de cada biblioteca ───────────────────────────
    private record Lib(String display, String groupId, String artifactId) {}

    private static final List<Lib> LIBS = List.of(
            // Banco de dados
            new Lib("SQLite JDBC",              "org.xerial",               "sqlite-jdbc"),

            // UI
            new Lib("FlatLaf",                  "com.formdev",              "flatlaf"),
            new Lib("FlatLaf IntelliJ Themes",  "com.formdev",              "flatlaf-intellij-themes"),

            // Vídeo / imagem
            new Lib("JavaCV Platform",          "org.bytedeco",             "javacv-platform"),
            new Lib("PDFBox",                   "org.apache.pdfbox",        "pdfbox"),
            new Lib("TwelveMonkeys JPEG",       "com.twelvemonkeys.imageio","imageio-jpeg"),
            new Lib("TwelveMonkeys TIFF",       "com.twelvemonkeys.imageio","imageio-tiff"),
            new Lib("TwelveMonkeys WebP",       "com.twelvemonkeys.imageio","imageio-webp"),
            new Lib("TwelveMonkeys BMP",        "com.twelvemonkeys.imageio","imageio-bmp"),
            new Lib("TwelveMonkeys PSD",        "com.twelvemonkeys.imageio","imageio-psd"),

            // Sistema / JNA
            new Lib("JNA",                      "net.java.dev.jna",         "jna"),
            new Lib("JNA Platform",             "net.java.dev.jna",         "jna-platform"),

            // Utilitários
            new Lib("mslinks (atalhos .lnk)",   "com.github.vatbub",        "mslinks"),
            new Lib("JNAFileChooser",           "com.github.steos",         "jnafilechooser"),

            // Logging
            new Lib("SLF4J API",                "org.slf4j",                "slf4j-api"),
            new Lib("Logback Classic",          "ch.qos.logback",           "logback-classic")
    );

    // ── Labels de versão (um por biblioteca) ────────────────────
    private final Map<String, JLabel> versionLabels = new LinkedHashMap<>();

    public LibrariesConfigPanel() {

        // ── Seção: runtime Java ──────────────────────────────────
        JPanel runtime = addSection("Ambiente de execução");
        addRow(runtime, "Java",    makeValueLabel(System.getProperty("java.version")
                + " (" + System.getProperty("java.vendor") + ")"));
        addRow(runtime, "JVM",     makeValueLabel(System.getProperty("java.vm.name")));
        addRow(runtime, "SO",      makeValueLabel(System.getProperty("os.name")
                + " " + System.getProperty("os.arch")));

        // ── Seção: bibliotecas (agrupadas por categoria) ─────────
        addLibSection("Banco de dados",   LIBS.subList(0,  1));
        addLibSection("Interface (UI)",   LIBS.subList(1,  3));
        addLibSection("Vídeo / Imagem",   LIBS.subList(3,  10));
        addLibSection("Sistema / JNA",    LIBS.subList(10, 12));
        addLibSection("Utilitários",      LIBS.subList(12, 14));
        addLibSection("Logging",          LIBS.subList(14, 16));

        // ── Botão atualizar ──────────────────────────────────────
//        JButton btnRefresh = makeBtn("Atualizar versões");
//        btnRefresh.addActionListener(e -> loadVersions());
//        addButtons(btnRefresh);

        // Carrega versões em background ao abrir
        loadVersions();
    }

    // ── Helpers de construção ────────────────────────────────────

    private void addLibSection(String title, List<Lib> libs) {
        JPanel section = addSection(title);
        for (Lib lib : libs) {
            JLabel lbl = makeValueLabel("carregando...");
            versionLabels.put(lib.artifactId(), lbl);
            addRow(section, lib.display(), lbl);
        }
    }

    // ── Carregamento assíncrono ──────────────────────────────────

    private void loadVersions() {
        // Reseta para "carregando..."
        versionLabels.values().forEach(l -> setValueLabelText(l, "carregando..."));

        new SwingWorker<Map<String, String>, Void>() {
            @Override
            protected Map<String, String> doInBackground() {
                Map<String, String> result = new LinkedHashMap<>();
                for (Lib lib : LIBS) {
                    String v = VersionChecker.getLibraryVersion(
                            lib.groupId(), lib.artifactId());
                    result.put(lib.artifactId(), v != null ? v : "não encontrada");
                }
                return result;
            }

            @Override
            protected void done() {
                try {
                    Map<String, String> versions = get();
                    versions.forEach((artifactId, version) -> {
                        JLabel lbl = versionLabels.get(artifactId);
                        if (lbl != null) setValueLabelText(lbl, version);
                    });
                } catch (Exception ex) {
                    versionLabels.values().forEach(l ->
                            setValueLabelText(l, "erro ao carregar"));
                }
            }
        }.execute();
    }

}
