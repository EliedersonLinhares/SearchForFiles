package com.esl.searchforfiles.configuration;

import javax.swing.*;
import java.awt.event.ActionEvent;

public class KeyboardManager {

    public static void Action(JComponent component, String actioName, int keyCode, int modifiers, Runnable action) {
        InputMap inputMap = component.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = component.getActionMap();

        // Cria o KeyStroke usando o código da tecla e os modificadores (ex: CTRL, SHIFT)
        KeyStroke keyStroke = KeyStroke.getKeyStroke(keyCode, modifiers);

        inputMap.put(keyStroke, actioName);
        actionMap.put(actioName, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }
}
