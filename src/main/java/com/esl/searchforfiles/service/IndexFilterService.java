package com.esl.searchforfiles.service;

import com.esl.searchforfiles.configuration.WindowsUserFolderFilter;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class IndexFilterService {
    private final Set<Path> excludedPaths = new HashSet<>();
    private final Map<Path, Set<Path>> allowedSubfolders = new HashMap<>();
    private WindowsUserFolderFilter windowsFilter; // <-- adicionar

    /**
     * Nunca indexar esta pasta (nem subpastas)
     */
    public void excludeFolder(String path) {
        this.windowsFilter = new WindowsUserFolderFilter();
        excludedPaths.add(Paths.get(path).toAbsolutePath().normalize());
    }

    /**
     * Dentro de 'parentPath', indexar SOMENTE as pastas listadas
     */
    public void allowOnly(String parentPath, String... allowedPaths) {
        Path parent = Paths.get(parentPath).toAbsolutePath().normalize();
        Set<Path> allowed = Arrays.stream(allowedPaths)
                .map(p -> Paths.get(p).toAbsolutePath().normalize())
                .collect(Collectors.toSet());
        allowedSubfolders.put(parent, allowed);
    }

    public boolean shouldIndex(Path path) {
        Path normalized = path.toAbsolutePath().normalize();

        // 1. Filtro Windows (Users)
        if (!windowsFilter.shouldIndex(normalized)) return false;

        // 2. Exclusões manuais
        for (Path excluded : excludedPaths) {
            if (normalized.startsWith(excluded)) return false;
        }

        // 3. Restrições de subpastas manuais
        for (Map.Entry<Path, Set<Path>> entry : allowedSubfolders.entrySet()) {
            Path parent = entry.getKey();
            if (normalized.startsWith(parent) && !normalized.equals(parent)) {
                boolean insideAllowed = entry.getValue().stream()
                        .anyMatch(normalized::startsWith);
                if (!insideAllowed) return false;
            }
        }

        return true;
    }

}
