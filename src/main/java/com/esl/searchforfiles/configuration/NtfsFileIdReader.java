package com.esl.searchforfiles.configuration;

import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinNT;

import java.nio.file.Files;
import java.nio.file.Path;

public class NtfsFileIdReader {

    // Constantes WinAPI
    private static final int GENERIC_READ            = 0x80000000;
    private static final int FILE_SHARE_READ         = 0x00000001;
    private static final int FILE_SHARE_WRITE        = 0x00000002;
    private static final int FILE_SHARE_DELETE       = 0x00000004;
    private static final int OPEN_EXISTING           = 3;
    private static final int FILE_FLAG_BACKUP_SEMANTICS = 0x02000000; // para pastas

    /**
     * Lê o NTFS File ID do arquivo ou pasta.
     * Retorna "volumeSerial-fileIndex" ou null em caso de erro.
     */
    public static String readFileId(Path path) {
        if (path == null) return null;

        String absPath = path.toAbsolutePath().toString();

        WinNT.HANDLE handle = Kernel32Ex.INSTANCE.CreateFile(
                absPath,
                GENERIC_READ,
                FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE,
                null,
                OPEN_EXISTING,
                FILE_FLAG_BACKUP_SEMANTICS,
                null
        );

        // Handle inválido — sem permissão ou arquivo não existe
        if (handle == null || WinBase.INVALID_HANDLE_VALUE.equals(handle)) {
            return null;
        }

        try {
            Kernel32Ex.BY_HANDLE_FILE_INFORMATION info =
                    new Kernel32Ex.BY_HANDLE_FILE_INFORMATION();

            boolean ok = Kernel32Ex.INSTANCE
                    .GetFileInformationByHandle(handle, info);
            if (!ok) return null;

            // Integer.toUnsignedLong evita valores negativos ao converter int→long
            long volumeSerial  = Integer.toUnsignedLong(info.dwVolumeSerialNumber);
            long fileIndexHigh = Integer.toUnsignedLong(info.nFileIndexHigh);
            long fileIndexLow  = Integer.toUnsignedLong(info.nFileIndexLow);
            long fileIndex     = (fileIndexHigh << 32) | fileIndexLow;

            return volumeSerial + "-" + fileIndex;

        } catch (Exception e) {
            System.err.println("⚠️ NtfsFileIdReader erro: " + e.getMessage());
            return null;
        } finally {
            Kernel32Ex.INSTANCE.CloseHandle(handle);
        }
    }
}
