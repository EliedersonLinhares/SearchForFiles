package com.esl.searchforfiles.ui;

import javax.swing.*;
import java.awt.*;

public class ConfigurationPanel extends JFrame {

    private final ResultsPanel resultsPanel;


    public ConfigurationPanel(Window owner, ResultsPanel resultsPanel ) {
        super("Configurações");
        this.resultsPanel = resultsPanel;

        if (owner != null) owner.setEnabled(false);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(1000, 600);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout());

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                if (owner != null) owner.setEnabled(true);
                owner.toFront();
            }
        });

        // 2. Cria o componente de abas
        JTabbedPane tabbedPane = new JTabbedPane();

        // 3. Cria o Painel para a Aba 1
        JPanel painel1 = new JPanel();
        painel1.add(new JLabel("Opções de Cache"));

        // 4. Cria o Painel para a Aba 2
        JPanel painel2 = new JPanel();
        painel2.add(new JLabel("Conteúdo da Aba 2"));

        // 5. Adiciona os painéis ao JTabbedPane como abas
        tabbedPane.addTab("Cache", painel1);
        tabbedPane.addTab("Aba 2", painel2);

        // 6. Adiciona o JTabbedPane ao frame
        this.add(tabbedPane, BorderLayout.CENTER);

        setVisible(true);
    }
}
