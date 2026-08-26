package com.esl.searchforfiles.preview;


import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class PdfPreviewPanel extends JPanel implements Scrollable {

    // Resolução base padrão do PDFBox (72 DPI é o padrão do PDF, 96 ou 120 dá mais nitidez)
    private static final float BASE_DPI = 106f;
    private PDDocument document;
    private PDFRenderer pdfRenderer;
    private BufferedImage currentPageImage;
    private int rotationDegrees = 0; // Pode ser 0, 90, 180, 270
    private int currentPage = 0;
    private int totalPages = 0;
    private double zoomScale = 1.0;

    public PdfPreviewPanel() {
        setBackground(Color.DARK_GRAY);
        setOpaque(true);
    }

    /**
     * Abre o arquivo PDF e carrega o renderizador na memória.
     */
    public boolean open(File file) {
        close(); // Limpa caches anteriores

        String senhaInfo = ""; // Começa tentando abrir sem senha
        boolean carregadocomSucesso = false;

        while (!carregadocomSucesso) {
            try {
                // Tenta carregar o PDF com a senha atual (ou vazia na primeira tentativa)
                document = Loader.loadPDF(file, senhaInfo);

                // Se chegou aqui sem dar erro, valida as propriedades internas
                if (document.isEncrypted()) {
                    // Remove restrições temporárias de leitura em memória para renderizar os frames
                    document.setAllSecurityToBeRemoved(true);
                }

                pdfRenderer = new PDFRenderer(document);
                totalPages = document.getNumberOfPages();
                currentPage = 0;

                renderPage();
                carregadocomSucesso = true; // Sai do laço while

            }  catch (InvalidPasswordException e) { // [1]
                // Define a mensagem com base na tentativa anterior
                String mensagemTexto = (senhaInfo.isEmpty())
                        ? "Este arquivo PDF está protegido por senha.\nDigite a senha de acesso:"
                        : "Senha incorreta!\nTente novamente:";

                // Cria o campo de senha mascarado
                JPasswordField campoSenha = new JPasswordField();

                // Cria um array de objetos para agrupar o texto explicativo e o campo de input
                Object[] corpoMensagem = {
                        mensagemTexto,
                        campoSenha
                };

                // Exibe a caixa com os botões OK e Cancelar, renderizando o campo de senha
                int opcao = JOptionPane.showConfirmDialog(
                        SwingUtilities.getWindowAncestor(this),
                        corpoMensagem,
                        "Acesso Protegido",
                        JOptionPane.OK_CANCEL_OPTION,
                        JOptionPane.QUESTION_MESSAGE
                );

                // Se o usuário clicar em "Cancelar", fechar a janela ou deixar a senha vazia
                if (opcao != JOptionPane.OK_OPTION) {
                    System.out.println("[PDF Panel] Abertura cancelada pelo usuário.");
                    close();
                    return false;
                }

                // Captura os caracteres e converte para String para alimentar o Loader do PDFBox
                senhaInfo = new String(campoSenha.getPassword());

            }catch (IOException e) {
                // Trata outros erros comuns de arquivo corrompido ou inacessível
                System.err.println("[PDF Panel] Erro crítico ao ler arquivo: " + e.getMessage());
                close();
                return false;
            }
        }

        return true;
    }

    // 4. Altere a chamada final do método renderPage() para incluir a atualização de tamanho:
    private void renderPage() {
        if (pdfRenderer == null || totalPages == 0) return;
        try {
            float scale = (float) zoomScale * (BASE_DPI / 72f);
            currentPageImage = pdfRenderer.renderImage(currentPage, scale);

            updatePanelSize(); // Atualiza baseado na rotação atual
            repaint();
        } catch (IOException e) {
            System.err.println("[PDF Viewer] Erro ao renderizar: " + e.getMessage());
        }
    }

    public boolean saveWithoutPassword(File targetFile) {
        if (document == null) return false;

        try {
            // Remove formalmente as estruturas de segurança internas do PDFBox
            document.setAllSecurityToBeRemoved(true);

            // Garante que o documento tenha permissões totais de modificação ao salvar
            org.apache.pdfbox.pdmodel.encryption.AccessPermission ap =
                    new org.apache.pdfbox.pdmodel.encryption.AccessPermission();
            ap.setCanModify(true);
            ap.setCanPrint(true);

            // Salva o arquivo limpo por cima do original
            document.save(targetFile);
            return true;
        } catch (IOException e) {
            System.err.println("[PDF Panel] Erro ao salvar PDF sem senha: " + e.getMessage());
            return false;
        }
    }

    // 3. Modifique o método updatePanelSize (ou adicione-o caso não exista) para escutar as rotações:
    private void updatePanelSize() {
        if (currentPageImage == null) return;

        // Se a rotação for de 90 ou 270 graus, a largura e altura se invertem na tela!
        if (rotationDegrees == 90 || rotationDegrees == 270) {
            setPreferredSize(new Dimension(currentPageImage.getHeight(), currentPageImage.getWidth()));
        } else {
            setPreferredSize(new Dimension(currentPageImage.getWidth(), currentPageImage.getHeight()));
        }
        revalidate();
    }

    /**
     * Verifica se o documento original carregado veio de uma fonte criptografada.
     */
    public boolean isDocumentEncrypted() {
        return document != null && document.isEncrypted();
    }

    public void nextPage() {
        if (currentPage < totalPages - 1) {
            currentPage++;
            renderPage();
        }
    }

    public void prevPage() {
        if (currentPage > 0) {
            currentPage--;
            renderPage();
        }
    }

    public void goToPage(int pageIndex) {
        if (pageIndex >= 0 && pageIndex < totalPages) {
            this.currentPage = pageIndex;
            renderPage();
        }
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public double getZoom() {
        return zoomScale;
    }

    public void setZoom(double scale) {
        this.zoomScale = Math.max(0.1, scale);
        renderPage();
    }

    public void close() {
        if (document != null) {
            try {
                document.close();
            } catch (IOException ignored) {
            }
            document = null;
        }
        pdfRenderer = null;
        currentPageImage = null;
        totalPages = 0;
        currentPage = 0;
    }


    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (currentPageImage == null) return;

        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        int panelW = getWidth();
        int panelH = getHeight();
        int imgW = currentPageImage.getWidth();
        int imgH = currentPageImage.getHeight();

        // Centraliza o contexto gráfico baseado na rotação ativa
        if (rotationDegrees == 90 || rotationDegrees == 270) {
            g2d.translate(panelW / 2, panelH / 2);
            g2d.rotate(Math.toRadians(rotationDegrees));
            g2d.drawImage(currentPageImage, -imgW / 2, -imgH / 2, null);
        } else {
            g2d.translate(panelW / 2, panelH / 2);
            g2d.rotate(Math.toRadians(rotationDegrees));
            g2d.drawImage(currentPageImage, -imgW / 2, -imgH / 2, null);
        }

        g2d.dispose();
    }

    // ── Métodos da interface Scrollable para navegação fluida com o mouse ──
    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle vr, int o, int d) {
        return 24;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle vr, int o, int d) {
        return o == SwingConstants.VERTICAL ? vr.height : vr.width;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return getPreferredSize().width <= (getParent() != null ? getParent().getWidth() : getWidth());
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return getPreferredSize().height <= (getParent() != null ? getParent().getHeight() : getHeight());
    }

    // 2. Adicione os métodos públicos para controlar a rotação:
    public void rotateClockwise() {
        rotationDegrees = (rotationDegrees + 90) % 360;
        updatePanelSize(); // Força o JScrollPane a recalcular as barras de rolagem
        repaint();
    }

    public int getRotationDegrees() {
        return rotationDegrees;
    }

    public org.apache.pdfbox.pdmodel.PDDocument getDocument() {
        return this.document;
    }

    public PDFRenderer getPdfRenderer() {
        return pdfRenderer;
    }

}