package com.esl.searchforfiles.preview.model3DViewer;

import com.esl.searchforfiles.configuration.UIConfig;
import com.jme3.scene.Geometry;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

/**
 * Painel permanente exibindo a lista de materiais (com miniatura da textura,
 * ou um retângulo de cor sólida quando não há textura) do modelo carregado.
 */
/**
 * Painel permanente exibindo a lista de materiais do modelo carregado, em
 * formato de acordeão: clicar em um material o expande, revelando os slots
 * de textura (ColorMap, NormalMap, DiffuseMap, SpecularMap, AmbientMap)
 * que podem ser definidos manualmente a partir de um arquivo no disco.
 */
public class MaterialsPanel extends JPanel {

    private static final int THUMB_SIZE = 44;
    private static final int MAX_NAME_CHARS = 22;

    private final Obj3DApp app;
    private final JPanel listContainer;
    private final Map<String, MaterialRowPanel> rowsByName = new LinkedHashMap<>();
    private String expandedMaterialName = null;

    // Adicione essa variável de controle no topo da sua classe PainelMateriais:
    private Geometry geometriaAtual;

    public MaterialsPanel(Obj3DApp app) {
        this.app = app;
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Materiais"));

        listContainer = new JPanel();
        listContainer.setLayout(new BoxLayout(listContainer, BoxLayout.Y_AXIS));

        JScrollPane scrollPane = new JScrollPane(listContainer);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        add(scrollPane, BorderLayout.CENTER);

        setMinimumSize(new Dimension(200, 120));
        setPreferredSize(new Dimension(280, 260));
    }

    public void refresh() {
        String previouslyExpanded = expandedMaterialName;
        app.requestMaterialsList(materials ->
                SwingUtilities.invokeLater(() -> rebuild(materials, previouslyExpanded))
        );
    }

    private void rebuild(List<MaterialInfo> materials, String reExpandName) {
        listContainer.removeAll();
        rowsByName.clear();
        expandedMaterialName = null;

        for (MaterialInfo info : materials) {
            MaterialRowPanel row = new MaterialRowPanel(info);
            rowsByName.put(info.name, row);
            listContainer.add(row);
        }

        listContainer.revalidate();
        listContainer.repaint();

        if (reExpandName != null && rowsByName.containsKey(reExpandName)) {
            setExpanded(reExpandName, true);
        }
    }

    /**
     * Expande o material indicado (colapsando qualquer outro previamente
     * expandido — comportamento de acordeão) e destaca/desmarca em 3D
     * seguindo o mesmo estado.
     */
    private void setExpanded(String materialName, boolean expand) {
        if (expand && expandedMaterialName != null && !expandedMaterialName.equals(materialName)) {
            MaterialRowPanel previous = rowsByName.get(expandedMaterialName);
            if (previous != null) previous.setExpandedInternal(false);
        }

        MaterialRowPanel row = rowsByName.get(materialName);
        if (row == null) return;

        row.setExpandedInternal(expand);
        expandedMaterialName = expand ? materialName : null;

        app.setMaterialHighlight(expandedMaterialName, null);

        listContainer.revalidate();
        listContainer.repaint();
    }

    private static String abbreviate(String text) {
        if (text == null) return "Nenhuma textura";
        if (text.length() <= MAX_NAME_CHARS) return text;
        int keep = (MAX_NAME_CHARS - 3) / 2;
        return text.substring(0, keep) + "..." + text.substring(text.length() - keep);
    }

    /**
     * Uma linha do acordeão: cabeçalho (miniatura + nome, clicável) e,
     * quando expandida, uma lista de slots de textura logo abaixo.
     */
    private class MaterialRowPanel extends JPanel {
        private final MaterialInfo info;
        private final JPanel detailsPanel;
        private boolean expanded = false;

        MaterialRowPanel(MaterialInfo info) {
            this.info = info;
            setLayout(new BorderLayout());
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY));

            JPanel header = buildHeader();
            detailsPanel = buildDetailsPanel();
            detailsPanel.setVisible(false);

            add(header, BorderLayout.NORTH);
            add(detailsPanel, BorderLayout.CENTER);
        }

        private JPanel buildHeader() {
            JPanel header = new JPanel(new BorderLayout(10, 0));
            header.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
            header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            JLabel iconLabel = new JLabel(new ImageIcon(
                    info.thumbnail.getScaledInstance(THUMB_SIZE, THUMB_SIZE, Image.SCALE_SMOOTH)));
            iconLabel.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));

            JLabel nameLabel = new JLabel(info.name);
            nameLabel.setFont(UIConfig.FONT_DEFAULT);

            // NOVO: botão para abrir o visualizador de UV, sem interferir no clique de expandir/colapsar
            JButton uvButton = new JButton("UV");
            uvButton.setFont(UIConfig.FONT_SMALL);
            uvButton.setMargin(new Insets(2, 8, 2, 8));
            uvButton.setToolTipText("Ver mapa UV deste material");
            uvButton.addActionListener(e -> showUVDialog());

            JPanel eastPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            eastPanel.setOpaque(false);
            eastPanel.add(uvButton);

            header.add(iconLabel, BorderLayout.WEST);
            header.add(nameLabel, BorderLayout.CENTER);
            header.add(eastPanel, BorderLayout.EAST);

            header.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    // Ignora o clique se foi sobre o botão UV — evita expandir/colapsar sem querer
                  // if (SwingUtilities.isDescendingFrom((Component) e.getSource(), uvButton)) return;
                    setExpanded(info.name, !expanded);
                }
            });

            return header;
        }

        private void showUVDialog() {
            Window owner = SwingUtilities.getWindowAncestor(this);
            JDialog dialog = new JDialog(owner, "Mapa UV — " + info.name, Dialog.ModalityType.MODELESS);
            dialog.setLayout(new BorderLayout());

            UVMapPanel uvPanel = new UVMapPanel();
            dialog.add(uvPanel, BorderLayout.CENTER);

            JLabel statusLabel = new JLabel("Carregando...", SwingConstants.CENTER);
            dialog.add(statusLabel, BorderLayout.SOUTH);

            dialog.setSize(560, 600);
            dialog.setLocationRelativeTo(owner);
            dialog.setVisible(true);

            app.requestUVLayout(info.name, edges ->
                    SwingUtilities.invokeLater(() -> {
                        uvPanel.setEdges(edges);
                        statusLabel.setText(edges.isEmpty()
                                ? "Nenhuma coordenada UV encontrada"
                                : edges.size() / 3 + " triângulos");
                    })
            );

            app.requestMaterialTexturePreview(info.name, 512, preview ->
                    SwingUtilities.invokeLater(() -> uvPanel.setBackgroundTexture(preview))
            );
        }

        private JPanel buildDetailsPanel() {
            JPanel details = new JPanel();
            details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
            details.setBorder(BorderFactory.createEmptyBorder(4, 12, 8, 8));
           // details.setBackground(new Color(0xF5, 0xF5, 0xF5));

            for (String slot : new String[]{"ColorMap", "NormalMap", "DiffuseMap", "SpecularMap", "AmbientMap"}) {
                details.add(buildSlotRow(slot));
            }
            return details;
        }

        private JPanel buildSlotRow(String slot) {
            boolean supported = Boolean.TRUE.equals(info.slotSupported.get(slot));
            String currentTexture = info.textureBySlot.get(slot);
            boolean hasTexture = currentTexture != null;

            JPanel row = new JPanel(new BorderLayout(6, 0));
          //  row.setBackground(new Color(0xF5, 0xF5, 0xF5));
            row.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

            JButton searchButton = new JButton("\uD83D\uDD0D"); // ícone de lupa
            searchButton.setFont(searchButton.getFont().deriveFont(12f));
            searchButton.setMargin(new Insets(2, 6, 2, 6));
            searchButton.setToolTipText(supported ? "Escolher textura para " + slot
                    : "Este material não suporta " + slot);
            searchButton.setEnabled(supported);
            searchButton.addActionListener(e -> chooseTextureForSlot(slot));

            JButton removeButton = new JButton("\u2715"); // ícone de X
            removeButton.setFont(removeButton.getFont().deriveFont(11f));
            removeButton.setMargin(new Insets(2, 5, 2, 5));
            removeButton.setToolTipText("Remover textura de " + slot);
          //  removeButton.setForeground(new Color(0x99, 0x33, 0x33));
            removeButton.setVisible(supported && hasTexture); // só aparece se houver algo a remover
            removeButton.addActionListener(e -> clearTextureForSlot(slot));

            JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
            buttonsPanel.setOpaque(false);
            buttonsPanel.add(searchButton);
            buttonsPanel.add(removeButton);

            JLabel slotLabel = new JLabel(slot + ": ");
            slotLabel.setFont(UIConfig.FONT_SMALL);

            JLabel valueLabel = new JLabel(abbreviate(currentTexture));
            valueLabel.setFont(UIConfig.FONT_SMALL);
            valueLabel.setForeground(supported ? Color.DARK_GRAY : Color.LIGHT_GRAY);
            valueLabel.setToolTipText(currentTexture);

            JPanel labelsPanel = new JPanel(new BorderLayout());
            labelsPanel.setOpaque(false);
            labelsPanel.add(slotLabel, BorderLayout.WEST);
            labelsPanel.add(valueLabel, BorderLayout.CENTER);

            row.add(buttonsPanel, BorderLayout.WEST);
            row.add(labelsPanel, BorderLayout.CENTER);

            return row;
        }

        private void clearTextureForSlot(String slot) {
            Window owner = SwingUtilities.getWindowAncestor(this);
            app.clearMaterialTexture(info.name, slot,
                    () -> SwingUtilities.invokeLater(MaterialsPanel.this::refresh),
                    (errorMsg) -> SwingUtilities.invokeLater(() ->
                            JOptionPane.showMessageDialog(owner,
                                    "Não foi possível remover a textura:\n" + errorMsg,
                                    "Erro", JOptionPane.ERROR_MESSAGE))
            );
        }

        private void chooseTextureForSlot(String slot) {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Selecionar textura para " + slot);
            chooser.setFileFilter(new FileNameExtensionFilter(
                    "Imagens", "png", "jpg", "jpeg", "tga", "bmp", "dds"));

            chooser.setCurrentDirectory(app.getLastLoadedObjFile());
            Window owner = SwingUtilities.getWindowAncestor(this);
            if (chooser.showOpenDialog(owner) != JFileChooser.APPROVE_OPTION) return;

            File selected = chooser.getSelectedFile();
            app.setMaterialTexture(info.name, slot, selected,
                    () -> SwingUtilities.invokeLater(MaterialsPanel.this::refresh),
                    (errorMsg) -> SwingUtilities.invokeLater(() ->
                            JOptionPane.showMessageDialog(owner,
                                    "Não foi possível aplicar a textura:\n" + errorMsg,
                                    "Erro", JOptionPane.ERROR_MESSAGE))
            );
        }

        /** Chamado internamente pelo painel pai — não altera o estado global, só o visual desta linha. */
        void setExpandedInternal(boolean expand) {
            this.expanded = expand;
            detailsPanel.setVisible(expand);
        }
    }
}