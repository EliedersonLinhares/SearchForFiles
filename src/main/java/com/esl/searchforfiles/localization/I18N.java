package com.esl.searchforfiles.localization;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

public class I18N {
    private static ResourceBundle bundle;
    private static Locale currentLocale;
    private static List<LanguageChangeListener> listeners = new ArrayList<>();

    static {
        setLocale(Locale.getDefault());
    }
    public static void setLocale(Locale locale){
        currentLocale = locale;
        bundle = ResourceBundle.getBundle("messages", locale);
        Locale.setDefault(locale);
        notifyListeners();
    }

    public static String get(String key){
        return bundle.getString(key);
    }

    public static Locale getCurrentLocale(){
        return currentLocale;
    }

    // Adicionar listener
    public static void addLanguageChangeListener(LanguageChangeListener listener) {
        listeners.add(listener);
    }

    // Remover listener
    public static void removeLanguageChangeListener(LanguageChangeListener listener) {
        listeners.remove(listener);
    }

    // Notificar todos os listeners
    private static void notifyListeners() {
        for (LanguageChangeListener listener : listeners) {
            listener.onLanguageChanged(currentLocale);
        }
    }

    public interface LanguageChangeListener{
        void onLanguageChanged(Locale newLocale);
    }
}
