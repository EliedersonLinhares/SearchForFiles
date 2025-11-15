package com.esl.searchforfiles;

import com.esl.searchforfiles.ui.FileExplorerSwing;
import com.esl.searchforfiles.ui.FileItemPanel;
import com.formdev.flatlaf.intellijthemes.FlatDarkFlatIJTheme;

import javax.swing.*;

public class FileSearch {
    static void main() {
        SwingUtilities.invokeLater(() -> {
            System.out.println("╔════════════════════════════════════════════════════════════════╗");
            System.out.println("║  INTERFACE GRÁFICA - Advanced File Search                     ║");
            System.out.println("╚════════════════════════════════════════════════════════════════╝");
            // Adiciona shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(FileItemPanel::shutdown));

            FlatDarkFlatIJTheme.setup();
            new FileExplorerSwing();
        });
    }
}
