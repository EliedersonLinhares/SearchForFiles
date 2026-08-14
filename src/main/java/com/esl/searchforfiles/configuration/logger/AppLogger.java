package com.esl.searchforfiles.configuration.logger;

import javax.swing.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Logger centralizado da aplicação.
 * Singleton — obtenha a instância via AppLogger.get()
 *
 * Uso:
 *   AppLogger.get().info("Arquivo indexado: foto.jpg");
 *   AppLogger.get().warn("Diretório sem permissão: /tmp/x");
 *   AppLogger.get().error("Falha ao conectar ao banco: " + e.getMessage());
 *   AppLogger.get().critical("Índice corrompido — reiniciando");
 */
public class AppLogger {

    // ── Níveis ────────────────────────────────────────────────────────────────

    public enum Level {
        INFO    ("INFO",     "ℹ️"),
        WARN    ("AVISO",    "⚠️"),
        ERROR   ("ERRO",     "❌"),
        CRITICAL("CRÍTICO",  "🔴");

        public final String label;
        public final String icon;

        Level(String label, String icon) {
            this.label = label;
            this.icon  = icon;
        }
    }

    // ── Entrada de log ────────────────────────────────────────────────────────

    public record LogEntry(
            LocalDateTime timestamp,
            Level level,
            String source,
            String message
    ) {
        private static final DateTimeFormatter FMT =
                DateTimeFormatter.ofPattern("HH:mm:ss");

        public String formattedTime() {
            return timestamp.format(FMT);
        }

        @Override
        public String toString() {
            return "[%s] %s [%s] %s".formatted(
                    formattedTime(), level.icon, source, message);
        }
    }

    // ── Listener (para atualizar o dialog em tempo real) ──────────────────────

    public interface LogListener {
        void onLog(LogEntry entry);
    }

    // ── Singleton ─────────────────────────────────────────────────────────────

    private static volatile AppLogger instance;

    public static AppLogger get() {
        if (instance == null) {
            synchronized (AppLogger.class) {
                if (instance == null) instance = new AppLogger();
            }
        }
        return instance;
    }

    // ── Estado interno ────────────────────────────────────────────────────────

    private final List<LogEntry>    entries   = new ArrayList<>();
    private final List<LogListener> listeners = new CopyOnWriteArrayList<>();

    /** Nível mínimo a ser registrado/emitido (padrão: INFO = tudo). */
    private Level minLevel = Level.INFO;

    /** Espelha no console além do dialog? */
    private boolean consoleEcho = true;

    /** Limite de entradas mantidas em memória. */
    private static final int MAX_ENTRIES = 2_000;

    private AppLogger() {}

    // ── Configuração ──────────────────────────────────────────────────────────

    public synchronized void setMinLevel(Level level)   { this.minLevel     = level; }
    public synchronized void setConsoleEcho(boolean on) { this.consoleEcho  = on;    }

    // ── API pública ───────────────────────────────────────────────────────────

    /** Registra uma entrada de nível INFO. */
    public void info(String message) {
        log(Level.INFO, inferSource(), message);
    }

    /** Registra uma entrada de nível WARN. */
    public void warn(String message) {
        log(Level.WARN, inferSource(), message);
    }

    /** Registra uma entrada de nível ERROR. */
    public void error(String message) {
        log(Level.ERROR, inferSource(), message);
    }

    /** Registra uma entrada de nível CRITICAL. */
    public void critical(String message) {
        log(Level.CRITICAL, inferSource(), message);
    }

    /** Registra com source explícito (útil quando o inferido não é suficiente). */
    public void log(Level level, String source, String message) {
        if (level.ordinal() < minLevel.ordinal()) return;

        LogEntry entry = new LogEntry(LocalDateTime.now(), level, source, message);

        synchronized (this) {
            if (entries.size() >= MAX_ENTRIES) entries.remove(0);
            entries.add(entry);
        }

        if (consoleEcho) System.out.println(entry);

        // Notifica listeners na EDT para que o dialog atualize com segurança
        SwingUtilities.invokeLater(() -> listeners.forEach(l -> l.onLog(entry)));
    }

    // ── Acesso ao histórico ───────────────────────────────────────────────────

    public synchronized List<LogEntry> getEntries() {
        return List.copyOf(entries);
    }

    public synchronized List<LogEntry> getEntries(Level minLevel) {
        return entries.stream()
                .filter(e -> e.level().ordinal() >= minLevel.ordinal())
                .toList();
    }

    public synchronized void clear() {
        entries.clear();
    }

    // ── Listeners ─────────────────────────────────────────────────────────────

    public void addListener(LogListener listener) {
        listeners.add(listener);
    }

    public void removeListener(LogListener listener) {
        listeners.remove(listener);
    }

    // ── Utilitário interno ────────────────────────────────────────────────────

    /**
     * Infere o nome da classe que chamou o logger percorrendo a stack.
     * Ignora as frames do próprio AppLogger.
     */
    private String inferSource() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (StackTraceElement frame : stack) {
            String cls = frame.getClassName();
            if (!cls.equals(AppLogger.class.getName())
                    && !cls.equals(Thread.class.getName())) {
                // Retorna só o simpleClassName
                int dot = cls.lastIndexOf('.');
                return dot >= 0 ? cls.substring(dot + 1) : cls;
            }
        }
        return "App";
    }
}
