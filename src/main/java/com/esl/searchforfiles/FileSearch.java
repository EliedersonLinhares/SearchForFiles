package com.esl.searchforfiles;

import com.esl.searchforfiles.Theme.ThemeManager;
import com.esl.searchforfiles.ui.FileExplorerSwing;
import com.esl.searchforfiles.ui.FileItemPanel;
import com.formdev.flatlaf.intellijthemes.FlatArcDarkOrangeIJTheme;
import com.formdev.flatlaf.intellijthemes.FlatArcOrangeIJTheme;
import com.formdev.flatlaf.intellijthemes.FlatDarkFlatIJTheme;
import com.formdev.flatlaf.intellijthemes.FlatDraculaIJTheme;

import javax.swing.*;

public class FileSearch {
    static void main() {

        SwingUtilities.invokeLater(() -> {
            System.out.println("╔════════════════════════════════════════════════════════════════╗");
            System.out.println("║  INTERFACE GRÁFICA - Advanced File Search                     ║");
            System.out.println("╚════════════════════════════════════════════════════════════════╝");

           // ADICIONA SHUTDOWN HOOK para encerrar executors
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Encerrando executores...");

                // Encerra executor de thumbnails
                FileItemPanel.shutdown();

                System.out.println("Executores encerrados.");
            }));
            ThemeManager themeManager = new ThemeManager();
            themeManager.setupCurrentTheme();

            UIManager.put("Button.arc", 999);
            new FileExplorerSwing(themeManager);
        });
    }
}
