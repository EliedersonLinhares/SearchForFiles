package com.esl.searchforfiles.configuration;

import javax.swing.*;
import java.awt.*;

public class UIConfig {
    private UIConfig() {
        /* This utility class should not be instantiated */
    }


    // Definição da sua fonte padrão
    public static final Font FONT_XS_SMALL = new Font("SansSerif", Font.PLAIN, 10);
    public static final Font FONT_SMALL = new Font("SansSerif", Font.PLAIN, 12);
    public static final Font FONT_DEFAULT = new Font("SansSerif", Font.PLAIN, 14);
    public static final Font FONT_DEFAULT_BOLD = new Font("SansSerif", Font.BOLD, 14);
    public static final Font FONT_DEFAULT_LARGE = new Font("SansSerif", Font.BOLD, 16);
    public static final Font FONT_TITLE = new Font("SansSerif", Font.BOLD, 18);
    public static final Font FONT_MESSAGE_ICON = new Font("SansSerif", Font.PLAIN, 48);
    public static final Font FONT_MESSAGE_TEXT = new Font("SansSerif", Font.BOLD, 18);

    //Definiçao de Cores
    public static final Color SELECTED_COLOR = new Color(33, 150, 243, 80);
    public static final Color SELECTED_BORDER = new Color(33, 150, 243);
    public static final Color DARK_NORMAL_COLOR = new Color(56, 56, 56);
    public static final Color DARK_HOVER_COLOR = new Color(70, 70, 70);
    public static final Color LIGHT_NORMAL_COLOR = new Color(200, 200, 200);
    public static final Color LIGHT_HOVER_COLOR =  new Color(180, 180, 190);
    public static final Color BLUE = new Color(50, 90, 150);
    public static final Color RED = new Color(140, 60, 60);
    public static final Color LIGHT_RED = new Color(255, 80, 80);

    // Estados e Validações
    public static Color accent() { return UIManager.getColor("Component.accentColor"); }
    public static Color focus() { return UIManager.getColor("Component.focusColor"); }
    public static Color success() { return UIManager.getColor("Component.success.borderColor"); }
    public static Color error() { return UIManager.getColor("Component.error.borderColor"); }
    public static Color warning() { return UIManager.getColor("Component.warning.borderColor"); }

    // Textos e Fundos Globais
    public static Color background() { return UIManager.getColor("Panel.background"); }
    public static Color foreground() { return UIManager.getColor("Label.foreground"); }
    public static Color inputBackground() { return UIManager.getColor("Component.background"); }
    public static Color sliderTrackColor() { return UIManager.getColor("Slider.trackColor"); }
    public static Color buttonBackgroundColor() { return UIManager.getColor("Button.background"); }

    // Seleções
    public static Color selectionBackground() { return UIManager.getColor("TextField.selectionBackground"); }
    public static Color selectionForeground() { return UIManager.getColor("TextField.selectionForeground"); }

}


