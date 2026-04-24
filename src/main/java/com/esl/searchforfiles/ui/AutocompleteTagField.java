package com.esl.searchforfiles.ui;


import com.esl.searchforfiles.database.DatabaseManager;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.Set;

// ════════════════════════════════════════════════════════════════
// 2. AutocompleteTagField — campo de texto com dropdown de sugestões
//    Componente reutilizável, independente do Dialog
// ════════════════════════════════════════════════════════════════
public class AutocompleteTagField extends JPanel {

    // ── Constantes visuais ────────────────────────────────────────
    private static final int    MAX_SUGGESTIONS  = 8;
    private static final int    DEBOUNCE_MS      = 180;   // espera antes de consultar
    private static final Color POPUP_BG         = new Color(40, 40, 40);
    private static final Color  POPUP_FG         = Color.WHITE;
    private static final Color  POPUP_SEL_BG     = new Color(33, 150, 243);
    private static final Color  POPUP_BORDER     = new Color(80, 80, 80);
    private static final Color  HINT_FG          = new Color(140, 140, 140);

    // ── Componentes ───────────────────────────────────────────────
    private final JTextField     textField;
    private final JPopupMenu     popup;
    private final JList<String>  suggestionList;
    private final DefaultListModel<String> listModel;

    // ── Estado ────────────────────────────────────────────────────
    private final DatabaseManager dbManager;
    private final Set<String> alreadyApplied; // tags já no arquivo — exclui do dropdown
    private Timer                  debounceTimer;
    private TagSelectedListener    selectedListener;

    public AutocompleteTagField(DatabaseManager dbManager, Set<String> alreadyApplied) {
        this.dbManager    = dbManager;
        this.alreadyApplied = alreadyApplied;

        setLayout(new BorderLayout());
        setOpaque(false);

        // ── Campo de texto ────────────────────────────────────────
        textField = new JTextField();
        textField.setFont(new Font("SansSerif", Font.PLAIN, 13));
        textField.putClientProperty("JTextField.placeholderText",  // FlatLaf / Nimbus
                "Digite para buscar ou criar tag...");
        add(textField, BorderLayout.CENTER);

        // ── Lista de sugestões ────────────────────────────────────
        listModel      = new DefaultListModel<>();
        suggestionList = new JList<>(listModel);
        suggestionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        suggestionList.setBackground(POPUP_BG);
        suggestionList.setForeground(POPUP_FG);
        suggestionList.setSelectionBackground(POPUP_SEL_BG);
        suggestionList.setSelectionForeground(Color.WHITE);
        suggestionList.setFont(new Font("SansSerif", Font.PLAIN, 13));
        suggestionList.setFixedCellHeight(28);
        suggestionList.setCellRenderer(new SuggestionRenderer());

        // ── Popup ─────────────────────────────────────────────────
        popup = new JPopupMenu();
        popup.setLayout(new BorderLayout());
        popup.setBorder(BorderFactory.createLineBorder(POPUP_BORDER));
        popup.setBackground(POPUP_BG);
        popup.add(new JScrollPane(suggestionList) {{
            setBorder(BorderFactory.createEmptyBorder());
            getViewport().setBackground(POPUP_BG);
            setVerticalScrollBarPolicy(VERTICAL_SCROLLBAR_AS_NEEDED);
        }}, BorderLayout.CENTER);
        popup.setFocusable(false); // evita roubar foco do textField

        // ── Debounce timer ────────────────────────────────────────
        debounceTimer = new Timer(DEBOUNCE_MS, e -> fetchSuggestions());
        debounceTimer.setRepeats(false);

        // ── Listeners ─────────────────────────────────────────────
        textField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { scheduleSearch(); }
            @Override public void removeUpdate(DocumentEvent e)  { scheduleSearch(); }
            @Override public void changedUpdate(DocumentEvent e) { scheduleSearch(); }
        });

        textField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_DOWN  -> moveSuggestion(+1);
                    case KeyEvent.VK_UP    -> moveSuggestion(-1);
                    case KeyEvent.VK_ENTER -> confirmSelection();
                    case KeyEvent.VK_ESCAPE -> hidePopup();
                }
            }
        });

        // Clique na sugestão
        suggestionList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 1) confirmSelection();
            }
        });

        // Esconde popup ao perder foco (com pequeno delay para permitir clique na lista)
        textField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                Timer t = new Timer(150, ev -> {
                    if (!suggestionList.hasFocus()) hidePopup();
                });
                t.setRepeats(false);
                t.start();
            }
        });
    }

    // ── API pública ───────────────────────────────────────────────

    public String getText() { return textField.getText().trim(); }

    public void clear() {
        textField.setText("");
        hidePopup();
    }

    public void requestFieldFocus() { textField.requestFocus(); }

    /** Atualiza o conjunto de tags já aplicadas (para excluir do dropdown). */
    public void setAlreadyApplied(Set<String> tags) {
        alreadyApplied.clear();
        alreadyApplied.addAll(tags);
    }

    public void setTagSelectedListener(TagSelectedListener l) {
        this.selectedListener = l;
    }

    // ── Lógica interna ────────────────────────────────────────────

    private void scheduleSearch() {
        debounceTimer.restart(); // reinicia contagem a cada tecla digitada
    }

    private void fetchSuggestions() {
        String term = textField.getText().trim();

        if (term.isEmpty()) {
            hidePopup();
            return;
        }

        // Busca em background para não travar a EDT
        SwingWorker<List<String>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<String> doInBackground() throws Exception {
                return dbManager.searchTags(term, MAX_SUGGESTIONS + alreadyApplied.size());
            }

            @Override
            protected void done() {
                try {
                    List<String> all = get();

                    // Remove tags já aplicadas ao arquivo
                    List<String> filtered = all.stream()
                            .filter(t -> !alreadyApplied.contains(t))
                            .limit(MAX_SUGGESTIONS)
                            .collect(java.util.stream.Collectors.toList());

                    updatePopup(filtered, term);
                } catch (Exception ex) {
                    hidePopup();
                }
            }
        };
        worker.execute();
    }

    private void updatePopup(List<String> suggestions, String typedTerm) {
        listModel.clear();

        if (suggestions.isEmpty()) {
            // Mostra opção "Criar nova tag: <termo>"
            listModel.addElement("＋ Criar \"" + typedTerm + "\"");
        } else {
            for (String s : suggestions) listModel.addElement(s);

            // Se o termo digitado não é exatamente uma tag existente, oferece criação
            boolean exactMatch = suggestions.stream()
                    .anyMatch(s -> s.equalsIgnoreCase(typedTerm));
            if (!exactMatch)
                listModel.addElement("＋ Criar \"" + typedTerm + "\"");
        }

        int visibleRows = Math.min(listModel.size(), MAX_SUGGESTIONS);
        int popupH = visibleRows * 28 + 4;

        popup.setPopupSize(textField.getWidth(), popupH);

        if (!popup.isVisible()) {
            popup.show(textField, 0, textField.getHeight());
            textField.requestFocus(); // devolve foco ao campo
        } else {
            popup.revalidate();
            popup.repaint();
        }

        // Pré-seleciona primeiro item
        if (!listModel.isEmpty())
            suggestionList.setSelectedIndex(0);
    }

    private void moveSuggestion(int delta) {
        if (!popup.isVisible() || listModel.isEmpty()) return;
        int next = Math.max(0, Math.min(
                suggestionList.getSelectedIndex() + delta,
                listModel.size() - 1));
        suggestionList.setSelectedIndex(next);
        suggestionList.ensureIndexIsVisible(next);
    }

    private void confirmSelection() {
        String selected = suggestionList.getSelectedValue();
        if (selected == null && !listModel.isEmpty())
            selected = listModel.getElementAt(0);
        if (selected == null) return;

        // Resolve nome real (remove prefixo "＋ Criar "..." ")
        String tagName = selected.startsWith("＋ Criar \"")
                ? textField.getText().trim()
                : selected;

        hidePopup();
        textField.setText(tagName);

        if (selectedListener != null) selectedListener.onTagSelected(tagName);
    }

    private void hidePopup() {
        if (popup.isVisible()) popup.setVisible(false);
    }

    // ── Renderer ──────────────────────────────────────────────────

    private static class SuggestionRenderer extends DefaultListCellRenderer {
        private static final Color CREATE_FG = new Color(100, 200, 100);

        @Override
        public Component getListCellRendererComponent(
                JList<?> list, Object value, int index,
                boolean isSelected, boolean cellHasFocus) {

            JLabel lbl = (JLabel) super.getListCellRendererComponent(
                    list, value, index, isSelected, cellHasFocus);

            lbl.setBorder(BorderFactory.createEmptyBorder(2, 10, 2, 10));
            lbl.setBackground(isSelected ? POPUP_SEL_BG : POPUP_BG);

            String text = value.toString();
            if (text.startsWith("＋")) {
                lbl.setForeground(isSelected ? Color.WHITE : CREATE_FG);
                lbl.setFont(lbl.getFont().deriveFont(Font.ITALIC));
            } else {
                lbl.setForeground(isSelected ? Color.WHITE : POPUP_FG);
                lbl.setFont(lbl.getFont().deriveFont(Font.PLAIN));
            }

            // Ícone de tag nas sugestões existentes
            if (!text.startsWith("＋"))
                lbl.setText("🏷️  " + text);

            return lbl;
        }
    }

    // ── Interface de callback ─────────────────────────────────────

    public interface TagSelectedListener {
        void onTagSelected(String tagName);
    }
}