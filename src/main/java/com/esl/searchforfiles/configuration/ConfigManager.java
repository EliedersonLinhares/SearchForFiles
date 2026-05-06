package com.esl.searchforfiles.configuration;

import com.esl.searchforfiles.model.FileType;
import com.esl.searchforfiles.model.OrderBy;
import com.esl.searchforfiles.model.SortOption;
import com.esl.searchforfiles.others.ThumbnailSize;

import java.io.*;
import java.util.*;

/**
 * Gerencia as configurações do aplicativo.
 * Salva e carrega preferências do usuário em arquivo .properties
 */
public class ConfigManager {

    // Chaves de configuração disponíveis
    public static final String KEY_DEFAULT_FOLDER = "default_folder";
    public static final String KEY_THUMBNAILS_SIZE = "thumbnails_size";
    public static final String KEY_SORT_BY = "sort_by";
    public static final String KEY_ORDER_BY = "order_by";
    public static final String KEY_FILE_TYPE = "file_type";
    public static final String KEY_STAR_RATING = "star_rating";
    public static final String KEY_SUBFOLDER_ITEMS = "subFolder_items";
    public static final String KEY_SHOW_STAR_RATING = "showStarRating";
    public static final String KEY_SHOW_TYPE_FILE = "showTypeFile";
    public static final String KEY_SHOW_ANIMATED_GIF = "showAnimatedGif";
    public static final String KEY_SEARCH_HISTORY = "search_history";
    public static final String KEY_TAG_HISTORY = "tag_history";

    private static final String CONFIG_FILE = "app_config.properties";
    private static final String APP_DIR_NAME = ".advancedsearch";
    // Valores padrão para cada chave
    private static final Map<String, String> DEFAULTS = new LinkedHashMap<>();

    static {
        DEFAULTS.put(KEY_DEFAULT_FOLDER, "C:\\Users\\ESL\\Downloads");
        DEFAULTS.put(KEY_THUMBNAILS_SIZE, ThumbnailSize.MEDIO.toString());
        DEFAULTS.put(KEY_SORT_BY, SortOption.DATE.toString());
        DEFAULTS.put(KEY_ORDER_BY, OrderBy.DESC.toString());
        DEFAULTS.put(KEY_FILE_TYPE, FileType.ALL.toString());
        DEFAULTS.put(KEY_STAR_RATING, "0");
        DEFAULTS.put(KEY_SUBFOLDER_ITEMS, "false");
        DEFAULTS.put(KEY_SHOW_STAR_RATING, "true");
        DEFAULTS.put(KEY_SHOW_TYPE_FILE, "true");
        DEFAULTS.put(KEY_SHOW_ANIMATED_GIF, "true");

    }

    private final File configFile;
    private final Map<String, String> configs;

    // -------------------------------------------------------------------------
    // Construtor
    // -------------------------------------------------------------------------

    public ConfigManager() {
        String userHome = System.getProperty("user.home");
        String appDir = userHome + File.separator + APP_DIR_NAME;

        // Garante que o diretório da aplicação existe
        File appDirectory = new File(appDir);
        if (!appDirectory.exists()) {
            appDirectory.mkdirs();
        }

        this.configFile = new File(appDir, CONFIG_FILE);
        this.configs = new LinkedHashMap<>(DEFAULTS); // começa com os defaults

        System.out.println("Arquivo de configuração: " + configFile.getAbsolutePath());

        // Cria o arquivo vazio se ainda não existir
        if (!configFile.exists()) {
            try {
                configFile.createNewFile();
                System.out.println("Arquivo de configuração criado.");
            } catch (IOException e) {
                System.err.println("Erro ao criar arquivo de configuração: " + e.getMessage());
            }
        }

        loadConfigs();
    }

    // -------------------------------------------------------------------------
    // Leitura e escrita genéricas
    // -------------------------------------------------------------------------

    private static final String SEP = ";;"; // separador seguro

    public List<String> getList(String key) {
        String value = get(key);
        if (value == null || value.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(value.split(SEP)));
    }

    public void setList(String key, List<String> list) {
        String joined = String.join(SEP, list);
        set(key, joined);
    }
    public void addToHistory(String key, String value) {
        if (value == null || value.trim().isEmpty()) return;

        List<String> list = getList(key);

        // remove duplicado
        list.remove(value);

        // adiciona no topo
        list.add(0, value);

        // limita a 5
        if (list.size() > 5) {
            list = list.subList(0, 5);
        }

        setList(key, list);
    }


    /**
     * Retorna o valor de uma configuração como String.
     * Se a chave não existir, retorna o valor padrão definido em DEFAULTS,
     * ou null caso não haja padrão.
     */
    public String get(String key) {
        return configs.getOrDefault(key, DEFAULTS.get(key));
    }

    /**
     * Define e persiste um valor de configuração.
     */
    public void set(String key, String value) {
        configs.put(key, value);
        saveConfigs();
        System.out.println("✓ Config salva: " + key + " = " + value);
    }

    // -------------------------------------------------------------------------
    // Helpers tipados — evitam conversões repetidas no código cliente
    // -------------------------------------------------------------------------

    /**
     * Retorna configuração como int; usa defaultValue se conversão falhar.
     */
    public int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(get(key));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Define configuração a partir de um int.
     */
    public void setInt(String key, int value) {
        set(key, String.valueOf(value));
    }

    /**
     * Retorna configuração como float; usa defaultValue se conversão falhar.
     */
    public float getFloat(String key, float defaultValue) {
        try {
            return Float.parseFloat(get(key));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Define configuração a partir de um float.
     */
    public void setFloat(String key, float value) {
        set(key, String.valueOf(value));
    }

    /**
     * Retorna configuração como boolean.
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        String val = get(key);
        if (val == null) return defaultValue;
        return Boolean.parseBoolean(val);
    }

    /**
     * Define configuração a partir de um boolean.
     */
    public void setBoolean(String key, boolean value) {
        set(key, String.valueOf(value));
    }

    // -------------------------------------------------------------------------
    // Atalhos específicos para a pasta padrão na inicialização do programa
    // -------------------------------------------------------------------------


    public String getSavedDefaultFolder() {
        return get(KEY_DEFAULT_FOLDER);
    }

    public void saveDefaulFolder(String defaultFolder) {
        set(KEY_DEFAULT_FOLDER, defaultFolder);
    }

    // -------------------------------------------------------------------------
    // Atalhos específicos para definição do tamanho dos thumbnails
    // -------------------------------------------------------------------------


    public String getSavedThumbnailsSize() {
        return get(KEY_THUMBNAILS_SIZE);
    }

    public void saveThumbnailsSize(String thumbnailsSize) {
        set(KEY_THUMBNAILS_SIZE, thumbnailsSize);
    }

    // -------------------------------------------------------------------------
    // Atalhos específicos para definição do ordenamento da busca
    // -------------------------------------------------------------------------

    public String getSavedSortBy() {
        return get(KEY_SORT_BY);
    }

    public void saveSortBy(String sortBy) {
        set(KEY_SORT_BY, sortBy);
    }

    // -------------------------------------------------------------------------
    // Atalhos específicos para definição do ordenamento desc ou asc
    // -------------------------------------------------------------------------

    public String getSavedOrderBy() {
        return get(KEY_ORDER_BY);
    }

    public void saveOrderBy(String orderby) {
        set(KEY_ORDER_BY, orderby);
    }

    // -------------------------------------------------------------------------
    // Atalhos específicos para definição de tipo de arquivo
    // -------------------------------------------------------------------------

    public String getSavedFileType() {
        return get(KEY_FILE_TYPE);
    }

    public void saveFiletype(String fileType) {
        set(KEY_FILE_TYPE, fileType);
    }

    // -------------------------------------------------------------------------
    // Atalhos específicos para definição das estrelas
    // -------------------------------------------------------------------------
    public int getSavedStarRating() {
        return getInt(KEY_STAR_RATING, 0);
    }

    public void saveStarRating(int starRating) {
        setInt(KEY_STAR_RATING, starRating);
    }

    // -------------------------------------------------------------------------
    // Atalhos específicos para definição se mostra conteudo das subpastas
    // -------------------------------------------------------------------------
    public boolean getSavedSubfolderItems() {
        return getBoolean(KEY_SUBFOLDER_ITEMS, false);
    }

    public void saveSubfolderItems(boolean subFolderItems) {
        setBoolean(KEY_SUBFOLDER_ITEMS, subFolderItems);
    }

    // -------------------------------------------------------------------------
    // Atalhos específicos para definição se mostra estrelas sobre o thumbnail
    // -------------------------------------------------------------------------
    public boolean getSavedShowStarRating() {
        return getBoolean(KEY_SHOW_STAR_RATING, true);
    }
    public void saveShowStarRating(boolean showStarRating) {
        setBoolean(KEY_SHOW_STAR_RATING, showStarRating);
    }

    // -------------------------------------------------------------------------
    // Atalhos específicos para definição se mostra icone do tipo de arquivo sobre o thumbnail
    // -------------------------------------------------------------------------
    public boolean getSavedShowTypeFile() {
        return getBoolean(KEY_SHOW_TYPE_FILE, true);
    }
    public void saveShowTypeFile(boolean showTypeFile) {setBoolean(KEY_SHOW_TYPE_FILE, showTypeFile);}

    // -------------------------------------------------------------------------
    // Atalhos específicos para definição se mostra o thumbnail de gif que seja animado
    // -------------------------------------------------------------------------
    public boolean getSavedShowAnimatedGif() {
        return getBoolean(KEY_SHOW_ANIMATED_GIF, true);
    }
    public void saveShowAnimatedGif(boolean showAnimatedGif) {setBoolean(KEY_SHOW_ANIMATED_GIF, showAnimatedGif);}



    // -------------------------------------------------------------------------
    // Persistência interna — formato "chave=valor", uma por linha
    // -------------------------------------------------------------------------

    /**
     * Carrega todas as configurações do arquivo.
     * Linhas em branco e comentários (iniciados com '#') são ignorados.
     * Chaves ausentes no arquivo mantêm o valor padrão de DEFAULTS.
     */
    private void loadConfigs() {
        if (!configFile.exists() || configFile.length() == 0) {
            System.out.println("Nenhuma configuração salva. Usando valores padrão.");
            saveConfigs(); // persiste os defaults
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                int separatorIdx = line.indexOf('=');
                if (separatorIdx < 1) continue; // linha malformada

                String key = line.substring(0, separatorIdx).trim();
                String value = line.substring(separatorIdx + 1).trim();
                configs.put(key, value);
            }
            System.out.println("✓ Configurações carregadas: " + configs.size() + " chave(s).");
        } catch (IOException e) {
            System.err.println("Erro ao carregar configurações: " + e.getMessage());
        }
    }

    /**
     * Salva todas as configurações no arquivo.
     */
    private void saveConfigs() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(configFile))) {
            writer.write("# Configurações do AdvancedSearch");
            writer.newLine();
            for (Map.Entry<String, String> entry : configs.entrySet()) {
                writer.write(entry.getKey() + "=" + entry.getValue());
                writer.newLine();
            }
            writer.flush();
        } catch (IOException e) {
            System.err.println("Erro ao salvar configurações: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Debug
    // -------------------------------------------------------------------------

    /**
     * Imprime todas as configurações atuais no console.
     */
    public void printDebugInfo() {
        System.out.println("========== DEBUG CONFIG MANAGER ==========");
        System.out.println("Arquivo : " + configFile.getAbsolutePath());
        System.out.println("Existe  : " + configFile.exists());
        System.out.println("Entradas: " + configs.size());
        for (Map.Entry<String, String> entry : configs.entrySet()) {
            System.out.println("  " + entry.getKey() + " = " + entry.getValue());
        }
        System.out.println("==========================================");
    }
}