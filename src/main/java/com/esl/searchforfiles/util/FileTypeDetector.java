package com.esl.searchforfiles.util;

import com.esl.searchforfiles.model.FileType;

public class FileTypeDetector {

    public static FileType detect(String filename, boolean isDirectory) {
        // Diretórios sempre retornam FOLDER
        if (isDirectory) {
            return FileType.FOLDER;
        }

        // VALIDAÇÃO: Filename não pode ser nulo
        if (filename == null || filename.isEmpty()) {
            return FileType.ALL;
        }

        try {
            String extension = PathUtils.getExtension(filename);

            if (extension == null || extension.isEmpty()) {
                return FileType.ALL;
            }

            return FileType.fromExtension(extension);

        } catch (Exception e) {
            // Qualquer erro, retorna ALL
            return FileType.ALL;
        }
    }
}
