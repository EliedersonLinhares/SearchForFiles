package com.esl.searchforfiles.configuration;


import com.sun.jna.Native;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

// 1. Kernel32Ex — interface JNA customizada que declara
//    GetFileInformationByHandle com a estrutura correta
// ════════════════════════════════════════════════════════════════
public interface Kernel32Ex extends StdCallLibrary {

    Kernel32Ex INSTANCE = Native.load("kernel32", Kernel32Ex.class,
            W32APIOptions.DEFAULT_OPTIONS);

    // ── Estrutura BY_HANDLE_FILE_INFORMATION declarada manualmente ─
    @Structure.FieldOrder({
            "dwFileAttributes",
            "ftCreationTime",
            "ftLastAccessTime",
            "ftLastWriteTime",
            "dwVolumeSerialNumber",
            "nFileSizeHigh",
            "nFileSizeLow",
            "nNumberOfLinks",
            "nFileIndexHigh",
            "nFileIndexLow"
    })
    class BY_HANDLE_FILE_INFORMATION extends Structure {
        public int      dwFileAttributes;
        public FILETIME ftCreationTime     = new FILETIME();
        public FILETIME ftLastAccessTime   = new FILETIME();
        public FILETIME ftLastWriteTime    = new FILETIME();
        public int      dwVolumeSerialNumber;
        public int      nFileSizeHigh;
        public int      nFileSizeLow;
        public int      nNumberOfLinks;
        public int      nFileIndexHigh;
        public int      nFileIndexLow;

        // Versão ByReference necessária para passar como ponteiro
        public static class ByReference
                extends BY_HANDLE_FILE_INFORMATION
                implements Structure.ByReference {}
    }

    // ── Estrutura FILETIME (usada dentro de BY_HANDLE_FILE_INFORMATION) ─
    @Structure.FieldOrder({"dwLowDateTime", "dwHighDateTime"})
    class FILETIME extends Structure {
        public int dwLowDateTime;
        public int dwHighDateTime;

        public static class ByReference
                extends FILETIME
                implements Structure.ByReference {}
    }

    // ── Declaração da função WinAPI ────────────────────────────────
    boolean GetFileInformationByHandle(
            WinNT.HANDLE hFile,
            BY_HANDLE_FILE_INFORMATION lpFileInformation
    );

    // ── CreateFile — necessário para abrir o handle ────────────────
    WinNT.HANDLE CreateFile(
            String lpFileName,
            int    dwDesiredAccess,
            int    dwShareMode,
            WinBase.SECURITY_ATTRIBUTES lpSecurityAttributes,
            int    dwCreationDisposition,
            int    dwFlagsAndAttributes,
            WinNT.HANDLE hTemplateFile
    );

    boolean CloseHandle(WinNT.HANDLE hObject);
}
