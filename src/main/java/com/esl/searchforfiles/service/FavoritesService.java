package com.esl.searchforfiles.service;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Gerencia pastas favoritas com persistência em arquivo
 */
public class FavoritesService {
    private static final String FAVORITES_FILE = "favorites.txt";
    private final Set<String> favorites;
    private final List<FavoritesChangeListener> listeners;

    public FavoritesService() {
        this.favorites = new LinkedHashSet<>();
        this.listeners = new ArrayList<>();
        loadFavorites();
    }

    /**
     * Adiciona pasta aos favoritos
     */
    public boolean addFavorite(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }

        // Normaliza o caminho
        File file = new File(path);
        if (!file.exists() || !file.isDirectory()) {
            return false;
        }

        String normalizedPath = file.getAbsolutePath();

        if (favorites.add(normalizedPath)) {
            saveFavorites();
            notifyListeners();
            System.out.println("⭐ Favorito adicionado: " + normalizedPath);
            return true;
        }

        return false; // Já existe
    }

    /**
     * Remove pasta dos favoritos
     */
    public boolean removeFavorite(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }

        if (favorites.remove(path)) {
            saveFavorites();
            notifyListeners();
            System.out.println("🗑️  Favorito removido: " + path);
            return true;
        }

        return false;
    }

    /**
     * Verifica se é favorito
     */
    public boolean isFavorite(String path) {
        return favorites.contains(path);
    }

    /**
     * Retorna lista de favoritos
     */
    public List<String> getFavorites() {
        return new ArrayList<>(favorites);
    }

    /**
     * Limpa todos os favoritos
     */
    public void clearFavorites() {
        favorites.clear();
        saveFavorites();
        notifyListeners();
    }

    /**
     * Carrega favoritos do arquivo
     */
    private void loadFavorites() {
        File file = new File(FAVORITES_FILE);
        if (!file.exists()) {
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    File favFile = new File(line);
                    if (favFile.exists() && favFile.isDirectory()) {
                        favorites.add(line);
                    }
                }
            }
            System.out.println("✓ Carregados " + favorites.size() + " favoritos");
        } catch (IOException e) {
            System.err.println("Erro ao carregar favoritos: " + e.getMessage());
        }
    }

    /**
     * Salva favoritos no arquivo
     */
    private void saveFavorites() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(FAVORITES_FILE))) {
            for (String favorite : favorites) {
                writer.write(favorite);
                writer.newLine();
            }
        } catch (IOException e) {
            System.err.println("Erro ao salvar favoritos: " + e.getMessage());
        }
    }

    /**
     * Adiciona listener para mudanças
     */
    public void addListener(FavoritesChangeListener listener) {
        listeners.add(listener);
    }

    /**
     * Remove listener
     */
    public void removeListener(FavoritesChangeListener listener) {
        listeners.remove(listener);
    }

    /**
     * Notifica listeners sobre mudanças
     */
    private void notifyListeners() {
        for (FavoritesChangeListener listener : listeners) {
            listener.onFavoritesChanged();
        }
    }

    public interface FavoritesChangeListener {
        void onFavoritesChanged();
    }
}
