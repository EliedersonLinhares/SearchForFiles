package com.esl.searchforfiles.configuration;


import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

public class WindowsUserFolderFilter {
    // Pastas dentro de C:\Users que são SEMPRE bloqueadas
    private static final Set<String> BLOCKED_USER_ROOT_FOLDERS = Set.of(
            "public",
            "default",
            "default user",
            "all users"
    );

    // Dentro de cada pasta de usuário, SOMENTE estas são permitidas
    private static final Set<String> ALLOWED_USER_SUBFOLDERS = Set.of(
            "desktop",
            "documents",
            "downloads",
            "pictures",
            "music",
            "videos"
            // adicione outras conforme necessário
    );

    private final Path usersRoot;

    public WindowsUserFolderFilter() {
        // C:\Users — usa a variável de ambiente do próprio Windows
        String userProfile = System.getenv("USERPROFILE"); // ex: C:\Users\joao
        this.usersRoot = Paths.get(userProfile).getParent() // sobe para C:\Users
                .toAbsolutePath().normalize();
    }

    /**
     * Retorna true se o caminho pode ser indexado
     */
    public boolean shouldIndex(Path path) {
        Path normalized = path.toAbsolutePath().normalize();

        // Só aplica lógica especial se estiver dentro de C:\Users
        if (!normalized.startsWith(usersRoot)) {
            return true; // fora de C:\Users, não interfere
        }

        // Quantos níveis abaixo de C:\Users está este path?
        Path relative = usersRoot.relativize(normalized);
        int depth = relative.getNameCount();

        // Nível 1: C:\Users\<algo>  → pasta de usuário ou Public
        if (depth == 1) {
            String folderName = relative.getName(0).toString().toLowerCase();
            if (BLOCKED_USER_ROOT_FOLDERS.contains(folderName)) {
                return false; // bloqueia Public, Default, etc.
            }
            return true; // permite C:\Users\joao, C:\Users\maria, etc.
        }

        // Nível 2: C:\Users\<usuario>\<subpasta>
        if (depth >= 2) {
            String userFolder = relative.getName(0).toString().toLowerCase();

            // Se o próprio usuário já é bloqueado, bloqueia tudo dentro
            if (BLOCKED_USER_ROOT_FOLDERS.contains(userFolder)) {
                return false;
            }

            // Verifica se a subpasta imediata está na lista de permitidas
            String subFolder = relative.getName(1).toString().toLowerCase();
            if (!ALLOWED_USER_SUBFOLDERS.contains(subFolder)) {
                return false; // bloqueia AppData, Searches, Links, etc.
            }
        }

        return true;
    }

    /** Expõe o caminho raiz detectado (útil para log) */
    public Path getUsersRoot() {
        return usersRoot;
    }
}
