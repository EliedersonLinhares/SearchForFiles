package com.esl.searchforfiles.preview;


import com.esl.searchforfiles.configuration.UIConfig;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.swing.*;
import java.awt.*;
import java.io.File;

public class PdfViewerFrame extends JFrame {

    private final PdfPreviewPanel pdfPanel;
    private final PdfThumbnailPanel thumbnailPanel; // Nova propriedade
    private final JLabel lblZoomInfo;
    private final JButton btnPrev;
    private final JButton btnNext;
    private final JScrollPane scrollPane;
    private final JButton btnUnlock;
    private final JButton btnPrint;
    private JTextField txtPageInput;
    private JLabel lblTotalPages;
    private JButton btnRotate;

    private double zoomFactor = 1.0;

    public PdfViewerFrame(Window owner,File pdfFile) {
        setTitle("Visualizador de PDF — " + pdfFile.getName());
        setSize(1200, 750); // Janela um pouco mais larga para acomodar a barra lateral
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
        setVisible(true);

        if (owner != null) owner.setEnabled(false);

        // 1. Inicializa o Painel Lateral de Miniaturas (Esquerda)
        thumbnailPanel = new PdfThumbnailPanel();
        add(thumbnailPanel, BorderLayout.WEST);

        // 2. Painel Central de Exibição
        pdfPanel = new PdfPreviewPanel();
        scrollPane = new JScrollPane(pdfPanel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        add(scrollPane, BorderLayout.CENTER);

        // [Opcional] Passa o scroll do mouse também sobre as miniaturas para trocar a página direto
        scrollPane.getVerticalScrollBar().setUnitIncrement(20);

        // 3. Estruturação da Barra de Ferramentas Inferior
        JPanel bottomBar = new JPanel(new BorderLayout());
        bottomBar.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));

        btnPrev = new JButton("◀ Voltar");
        btnNext = new JButton("Avançar ▶");
        txtPageInput = new JTextField("1", 3); // Caixa com largura para até 3 dígitos
        txtPageInput.setHorizontalAlignment(JTextField.CENTER);

        btnUnlock = new JButton("🔓 Remover Senha");
        btnUnlock.setToolTipText("Salvar uma cópia deste PDF totalmente desbloqueada");
        btnUnlock.setVisible(false);

        btnRotate = new JButton("🔄 Girar");
        btnRotate.setToolTipText("Girar página em 90°");

        lblTotalPages = new JLabel("/ 0");


        btnPrint = new JButton("🖨️ Imprimir");


        leftPanel.add(btnPrev);
        leftPanel.add(txtPageInput);
        leftPanel.add(lblTotalPages);
        leftPanel.add(btnNext);
        leftPanel.add(btnUnlock);
        leftPanel.add(btnPrint);
        leftPanel.add(btnRotate);

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        JButton btnZoomOut = new JButton("   −   ");
        JButton btnZoom100 = new JButton(" 100% ");
        JButton btnZoomIn = new JButton("   +   ");
        lblZoomInfo = new JLabel("100%");

        lblZoomInfo.setForeground(Color.GRAY);

        double fileSizeInMB = (double) pdfFile.length() / (1024 * 1024);
        JLabel lblFileInfo = new JLabel(String.format("|  %.2f MB", fileSizeInMB));

        lblFileInfo.setForeground(Color.GRAY);

        rightPanel.add(btnZoomOut);
        rightPanel.add(btnZoom100);
        rightPanel.add(btnZoomIn);
        rightPanel.add(lblZoomInfo);
        rightPanel.add(lblFileInfo);

        bottomBar.add(leftPanel, BorderLayout.WEST);
        bottomBar.add(rightPanel, BorderLayout.EAST);
        add(bottomBar, BorderLayout.SOUTH);

        btnPrev.setFont(UIConfig.FONT_DEFAULT);
        btnNext.setFont(UIConfig.FONT_DEFAULT);
        txtPageInput.setFont(UIConfig.FONT_DEFAULT);
        lblTotalPages.setFont(UIConfig.FONT_DEFAULT);
        btnUnlock.setFont(UIConfig.FONT_DEFAULT);
        btnPrint.setFont(UIConfig.FONT_DEFAULT);
        btnRotate.setFont(UIConfig.FONT_DEFAULT);
        lblZoomInfo.setFont(UIConfig.FONT_DEFAULT);
        btnZoomOut.setFont(UIConfig.FONT_DEFAULT);
        btnZoom100.setFont(UIConfig.FONT_DEFAULT);
        btnZoomIn.setFont(UIConfig.FONT_DEFAULT);
        lblFileInfo.setFont(UIConfig.FONT_DEFAULT);

        // ── SINCRONIZAÇÃO DE EVENTOS ENTRE COMPONENTES ──

        // Evento A: Quando clicar na miniatura da esquerda, muda a folha principal no centro
        thumbnailPanel.setOnThumbnailClicked(pageIndex -> {
            pdfPanel.goToPage(pageIndex);
            updateInterfaceUI();
            scrollPane.getVerticalScrollBar().setValue(0);
        });

        // Evento B: Quando o Scroll do Mouse ou botões mudarem a página, atualiza o foco na miniatura
        pdfPanel.addPropertyChangeListener("pageChanged", evt -> {
            updateInterfaceUI();
            thumbnailPanel.updateSelection(pdfPanel.getCurrentPage());
        });

        if (pdfPanel.open(pdfFile)) {
            updateInterfaceUI();

            PDFRenderer currentRenderer = pdfPanel.getPdfRenderer();
            if (currentRenderer != null) {
                thumbnailPanel.loadThumbnails(currentRenderer, pdfPanel.getTotalPages());
            }

            // ── CONFIGURAÇÃO DO BOTÃO DE DESBLOQUEIO ──
            // Se o documento original usar senha, exibe o botão na barra de ferramentas
            if (pdfPanel.isDocumentEncrypted()) {
                btnUnlock.setVisible(true);
            }
        }

        btnUnlock.addActionListener(e -> {
            int resposta = JOptionPane.showConfirmDialog(
                    this,
                    "Deseja remover permanentemente a senha deste arquivo?\n" +
                            "O arquivo original será sobrescrevido e não pedirá mais senha ao ser aberto.",
                    "Confirmar Desbloqueio",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );

            if (resposta == JOptionPane.YES_OPTION) {
                // Executa o salvamento sem senha
                boolean sucesso = pdfPanel.saveWithoutPassword(pdfFile);

                if (sucesso) {
                    JOptionPane.showMessageDialog(
                            this,
                            "Senha removida com sucesso!\nO arquivo agora está livre para acesso.",
                            "Sucesso",
                            JOptionPane.INFORMATION_MESSAGE
                    );
                    btnUnlock.setVisible(false); // Esconde o botão já que o arquivo foi limpo
                } else {
                    JOptionPane.showMessageDialog(
                            this,
                            "Não foi possível salvar o arquivo.\nVerifique se ele não está aberto em outro programa.",
                            "Erro ao Salvar",
                            JOptionPane.ERROR_MESSAGE
                    );
                }
            }
        });


        // Listeners operacionais básicos dos botões inferiores
        btnPrev.addActionListener(e -> {
            pdfPanel.prevPage();
            updateInterfaceUI();
            thumbnailPanel.updateSelection(pdfPanel.getCurrentPage());
            scrollPane.getVerticalScrollBar().setValue(0);
        });

        btnNext.addActionListener(e -> {
            pdfPanel.nextPage();
            updateInterfaceUI();
            thumbnailPanel.updateSelection(pdfPanel.getCurrentPage());
            scrollPane.getVerticalScrollBar().setValue(0);
        });

        btnZoomIn.addActionListener(e -> {
            zoomFactor = Math.min(4.0, zoomFactor + 0.1);
            pdfPanel.setZoom(zoomFactor);
            updateZoomLabel();
        });

        btnZoomOut.addActionListener(e -> {
            zoomFactor = Math.max(0.2, zoomFactor - 0.1);
            pdfPanel.setZoom(zoomFactor);
            updateZoomLabel();
        });

        btnZoom100.addActionListener(e -> {
            zoomFactor = 1.0;
            pdfPanel.setZoom(zoomFactor);
            updateZoomLabel();
        });

        // Adicione junto aos outros listeners no final do construtor:
        btnPrint.addActionListener(e -> {
            org.apache.pdfbox.pdmodel.PDDocument openDoc = pdfPanel.getDocument();
            if (openDoc == null) {
                JOptionPane.showMessageDialog(this, "Nenhum documento ativo para impressão.", "Aviso", JOptionPane.WARNING_MESSAGE);
                return;
            }

            // Cria o Job de impressão do Java AWT
            java.awt.print.PrinterJob job = java.awt.print.PrinterJob.getPrinterJob();

            // Configura o documento do PDFBox como a fonte paginável para a impressora
            job.setPageable(new org.apache.pdfbox.printing.PDFPageable(openDoc));

            // Exibe a janela nativa do sistema operacional para escolha da impressora e número de cópias
            if (job.printDialog()) {
                // Roda a transmissão de dados em uma Thread separada para não travar a UI do Swing
                new Thread(() -> {
                    try {
                        this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                        job.print(); // Envia os dados para a fila de impressão do sistema
                    } catch (java.awt.print.PrinterException ex) {
                        SwingUtilities.invokeLater(() -> {
                            JOptionPane.showMessageDialog(this,
                                    "Falha ao enviar documento para a impressora:\n" + ex.getMessage(),
                                    "Erro de Impressão",
                                    JOptionPane.ERROR_MESSAGE);
                        });
                    } finally {
                        SwingUtilities.invokeLater(() -> this.setCursor(Cursor.getDefaultCursor()));
                    }
                }, "PDF-Print-Thread").start();
            }
        });

        txtPageInput.addActionListener(e -> {
            try {
                int targetPage = Integer.parseInt(txtPageInput.getText().trim());
                int total = pdfPanel.getTotalPages();

                if (targetPage >= 1 && targetPage <= total) {
                    pdfPanel.goToPage(targetPage - 1);
                    // Chama a interface que atualiza a folha central e move as miniaturas sozinha
                    updateInterfaceUI();
                    scrollPane.getVerticalScrollBar().setValue(0);
                } else {
                    Toolkit.getDefaultToolkit().beep();
                    updateInterfaceUI();
                }
            } catch (NumberFormatException ex) {
                Toolkit.getDefaultToolkit().beep();
                updateInterfaceUI();
            }
        });

        txtPageInput.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent e) {
                txtPageInput.selectAll();
            }
        });

    // ── LISTENER: BOTÃO DE ROTAÇÃO ──
        btnRotate.addActionListener(e -> {
            pdfPanel.rotateClockwise();
            // Ajusta o ScrollPane para centralizar a nova geometria rotacionada
            scrollPane.revalidate();
        });

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                pdfPanel.close();
                if (owner != null) { owner.setEnabled(true); owner.toFront(); }
            }
        });
    }


    private void updateInterfaceUI() {
        int current = pdfPanel.getCurrentPage() + 1; // Transforma base 0 em base 1
        int total = pdfPanel.getTotalPages();
        int currentIdx = pdfPanel.getCurrentPage();  // Índice real (base 0) para a rolagem

        // Atualiza a caixinha de texto e a label de totalizadores separadamente
        txtPageInput.setText(String.valueOf(current));
        lblTotalPages.setText("/ " + total);

        btnPrev.setEnabled(current > 1);
        btnNext.setEnabled(current < total);
        updateZoomLabel();

        // ── AUTO-ROLAGEM INTEGRADA ──
        // Garante que as miniaturas da barra esquerda acompanhem a paginação
        if (thumbnailPanel != null) {
            thumbnailPanel.updateSelection(currentIdx);
            thumbnailPanel.scrollToPage(currentIdx);
        }
    }

    private void updateZoomLabel() {
        lblZoomInfo.setText(Math.round(zoomFactor * 100.0) + "%");
    }
}