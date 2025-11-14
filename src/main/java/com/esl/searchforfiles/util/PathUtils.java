package com.esl.searchforfiles.util;

public class PathUtils {
//    public static String getExtension(String filename) {
//        if (filename == null || filename.isEmpty()) return "";
//
//        int lastDot = filename.lastIndexOf('.');
//        if (lastDot > 0 && lastDot < filename.length() - 1) {
//            return filename.substring(lastDot + 1).toLowerCase();
//        }
//        return "";
//    }
    /**
     * Extrai extensão do arquivo
     * CORRIGIDO: Trata nomes nulos ou inválidos
     */
    public static String getExtension(String filename) {
        // VALIDAÇÃO: Nome não pode ser nulo ou vazio
        if (filename == null || filename.isEmpty()) {
            return "";
        }

        try {
            int lastDot = filename.lastIndexOf('.');

            // Valida posição do ponto
            if (lastDot > 0 && lastDot < filename.length() - 1) {
                return filename.substring(lastDot + 1).toLowerCase();
            }

            return "";

        } catch (Exception e) {
            // Qualquer erro, retorna vazio
            return "";
        }
    }
//    public static String normalizeDriveLetter(String driveLetter) {
//        if (driveLetter == null || driveLetter.isEmpty()) {
//            return "";
//        }
//        return driveLetter.toUpperCase().replaceAll("[:\\\\]", "") + ":\\";
//    }
    /**
     * Normaliza letra de drive
     */
    public static String normalizeDriveLetter(String driveLetter) {
        if (driveLetter == null || driveLetter.isEmpty()) {
            return "";
        }
        return driveLetter.toUpperCase().replaceAll("[:\\\\]", "") + ":\\";
    }
//    public static String getDriveFromPath(String path) {
//        if (path == null || path.length() < 2) return "";
//        return path.substring(0, 2);
//    }
    /**
     * Obtém drive de um caminho
     */
    public static String getDriveFromPath(String path) {
        if (path == null || path.length() < 2) return "";
        return path.substring(0, 2);
    }

    /**
     * Valida se um caminho é seguro
     * NOVO MÉTODO
     */
    public static boolean isValidPath(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }

        try {
            // Testa se o caminho tem estrutura válida
            return path.length() > 0 && !path.contains("\0");
        } catch (Exception e) {
            return false;
        }
    }
}
