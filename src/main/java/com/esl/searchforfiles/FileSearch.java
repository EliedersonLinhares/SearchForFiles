package com.esl.searchforfiles;

import com.esl.searchforfiles.ui.FileExplorerSwing;
import com.formdev.flatlaf.intellijthemes.FlatDarkFlatIJTheme;

import javax.swing.*;

public class FileSearch {
    static void main() {
        SwingUtilities.invokeLater(() -> {
            System.out.println("╔════════════════════════════════════════════════════════════════╗");
            System.out.println("║  INTERFACE GRÁFICA - Advanced File Search                     ║");
            System.out.println("╚════════════════════════════════════════════════════════════════╝");
            FlatDarkFlatIJTheme.setup();
            new FileExplorerSwing();
        });
    }
}
