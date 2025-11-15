package com.esl.searchforfiles.cache.thumbnail;

import com.esl.searchforfiles.ui.FileItemPanel;

import java.util.prefs.Preferences;

public class CacheSettings {
    private static final String PREFS_NODE = "thumbnail_cache";
    private static Preferences prefs = Preferences.userRoot().node(PREFS_NODE);

    // Configurações
    public static boolean isAutoClearEnabled() {
        return prefs.getBoolean("auto_clear_enabled", false);
    }

    public static void setAutoClearEnabled(boolean enabled) {
        prefs.putBoolean("auto_clear_enabled", enabled);
    }

    public static int getAutoClearDays() {
        return prefs.getInt("auto_clear_days", 30);
    }

    public static void setAutoClearDays(int days) {
        prefs.putInt("auto_clear_days", days);
    }

    public static String getCacheLocation() {
        return prefs.get("cache_location", "");
    }

    public static void setCacheLocation(String location) {
        prefs.put("cache_location", location);
    }

    // Limpa automaticamente ao iniciar a aplicação se configurado
    public static void performStartupCleanup() {
        if (isAutoClearEnabled()) {
            int days = getAutoClearDays();
            FileItemPanel.clearOldThumbnails(days);
            System.out.println("Limpeza automática executada: " + days + " dias");
        }
    }
}
