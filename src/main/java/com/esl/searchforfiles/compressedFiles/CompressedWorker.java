package com.esl.searchforfiles.compressedFiles;

import com.esl.searchforfiles.ui.FileExplorerSwing;
import com.github.junrar.Archive;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.progress.ProgressMonitor;
import com.github.junrar.Junrar;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.List;

public class CompressedWorker extends SwingWorker<Void, Integer> {

    private final File arquivoCompactado;
  //  private final JFrame janelaPrincipal;
    private CompressedDialog dialogoProgresso;
    private String statusAtual = "Iniciando...";

    // Flags de controle de estado para o método done()
    private boolean canceladoPeloUsuario = false;
    private boolean senhaIncorreta = false;

    private final FileExplorerSwing fileExplorerSwing;

    public CompressedWorker(FileExplorerSwing janelaPrincipal, File arquivoCompactado) {
        this.fileExplorerSwing = janelaPrincipal;
        this.arquivoCompactado = arquivoCompactado;
    }

    @Override
    protected Void doInBackground() throws Exception {
        // 1. Define o caminho conceitual da nova pasta (NÃO cria no disco ainda)
        String nomeArquivo = arquivoCompactado.getName();
        String nomeSemExtensao = nomeArquivo.substring(0, nomeArquivo.lastIndexOf('.'));
        File pastaDestino = new File(arquivoCompactado.getParentFile(), nomeSemExtensao);

        if (pastaDestino.exists()) {
            final int[] resposta = new int[1];
            SwingUtilities.invokeAndWait(() -> {
                resposta[0] = JOptionPane.showConfirmDialog(
                        fileExplorerSwing,
                        "A pasta '" + nomeSemExtensao + "' já existe.\nDeseja descompactar o conteúdo dentro dela assim mesmo?",
                        "Pasta já existente",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE
                );
            });
            if (resposta[0] != JOptionPane.YES_OPTION) {
                canceladoPeloUsuario = true;
                return null;
            }
        }

        String nomeMinusculo = nomeArquivo.toLowerCase();
        String senha = null;

        // ==========================================
        // FLUXO PARA ARQUIVOS ZIP
        // ==========================================
        if (nomeMinusculo.endsWith(".zip")) {
            try (ZipFile zip = new ZipFile(arquivoCompactado)) {
                // Pede a senha ANTES de criar a pasta
                if (zip.isEncrypted()) {
                    senha = solicitarSenhaSegura();
                    if (senha == null) {
                        canceladoPeloUsuario = true;
                        return null; // Usuário clicou em cancelar no diálogo de senha
                    }
                    zip.setPassword(senha.toCharArray());

                    // Valida se a senha está correta testando a leitura dos headers
                    if (!zip.isValidZipFile()) {
                        senhaIncorreta = true;
                        throw new Exception("Senha incorreta ou arquivo ZIP corrompido.");
                    }
                }

                // Agora que passou pelas validações e senhas, criamos a pasta de forma segura
                criarPastaDestinoSeNaoExistir(pastaDestino);
                publish(1); // Exibe a barra de progresso apenas aqui

                zip.setRunInThread(true);
                zip.extractAll(pastaDestino.getAbsolutePath());
                ProgressMonitor monitor = zip.getProgressMonitor();

                while (!monitor.getState().equals(ProgressMonitor.State.READY)) {
                    statusAtual = monitor.getCurrentTask() != null ? monitor.getCurrentTask().toString() : "Extraindo ZIP...";
                    publish(monitor.getPercentDone());
                    Thread.sleep(100);
                }

                if (monitor.getResult().equals(ProgressMonitor.Result.ERROR)) {
                    // Verifica se o erro do Zip4j foi por senha errada durante a extração real
                    if (monitor.getException() != null && monitor.getException().getMessage().contains("Wrong password")) {
                        senhaIncorreta = true;
                    }
                    throw monitor.getException();
                }
            }

            // ==========================================
            // FLUXO PARA ARQUIVOS RAR
            // ==========================================
        } else if (nomeMinusculo.endsWith(".rar")) {
            boolean precisaSenha = false;

            // Tenta abrir o arquivo para checar criptografia ANTES de criar a pasta
            try (Archive archive = new Archive(arquivoCompactado)) {
                if (archive.isEncrypted()) {
                    precisaSenha = true;
                }
            } catch (Exception e) {
                precisaSenha = true;
            }

            if (precisaSenha) {
                senha = solicitarSenhaSegura();
                if (senha == null) {
                    canceladoPeloUsuario = true;
                    return null; // Usuário cancelou
                }
            }

            // Passou nas validações? Criamos a pasta física no disco
            criarPastaDestinoSeNaoExistir(pastaDestino);
            publish(1); // Exibe a barra de progresso

            statusAtual = "Extraindo arquivos do RAR...";
            publish(40);

            try {
                if (senha != null) {
                    Junrar.extract(arquivoCompactado, pastaDestino, senha);
                } else {
                    Junrar.extract(arquivoCompactado, pastaDestino);
                }
            } catch (Exception e) {
                // Se a extração falhar no Junrar geralmente é senha inválida (erro de CRC/Header)
                senhaIncorreta = true;
                throw e;
            }
            publish(100);
        }

        return null;
    }

    private void criarPastaDestinoSeNaoExistir(File pasta) throws IOException {
        if (!pasta.exists() && !pasta.mkdirs()) {
            throw new IOException("Não foi possível criar a pasta de destino: " + pasta.getAbsolutePath());
        }
    }

    private String solicitarSenhaSegura() throws Exception {
        final String[] senhaContainer = new String[1];

        // Abre o prompt na EDT garantindo que a janela principal fique bloqueada temporariamente para inputs
        SwingUtilities.invokeAndWait(() -> {
            JPasswordField pf = new JPasswordField();
            int okCancell = JOptionPane.showConfirmDialog(fileExplorerSwing, pf,
                    "Este arquivo está protegido. Digite a senha:",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

            if (okCancell == JOptionPane.OK_OPTION) {
                senhaContainer[0] = new String(pf.getPassword());
            } else {
                senhaContainer[0] = null; // Indica cancelamento
            }
        });
        return senhaContainer[0];
    }

    @Override
    protected void process(List<Integer> chunks) {
        int ultimoProgresso = chunks.get(chunks.size() - 1);

        if (dialogoProgresso == null) {
            if (fileExplorerSwing != null) {
                fileExplorerSwing.setEnabled(false);
            }
            dialogoProgresso = new CompressedDialog(fileExplorerSwing, "Gerenciador de Arquivos - Extração");
            dialogoProgresso.setVisible(true);
        }

        dialogoProgresso.atualizar(ultimoProgresso, statusAtual);
    }

    @Override
    protected void done() {
        if (fileExplorerSwing != null) {
            fileExplorerSwing.setEnabled(true);
            fileExplorerSwing.toFront();
        }

        if (dialogoProgresso != null) {
            dialogoProgresso.dispose();
        }

        if (canceladoPeloUsuario) {
            JOptionPane.showMessageDialog(fileExplorerSwing, "Operação cancelada pelo usuário.", "Cancelado", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            get(); // Lança exceção se algo falhou no doInBackground
            JOptionPane.showMessageDialog(fileExplorerSwing, "Extração concluída com sucesso!", "Sucesso", JOptionPane.INFORMATION_MESSAGE);
          fileExplorerSwing.navigateTo(fileExplorerSwing.getSelectedPath(), true);
        } catch (Exception e) {
            if (senhaIncorreta) {
                JOptionPane.showMessageDialog(fileExplorerSwing, "Senha incorreta! Não foi possível descompactar o arquivo.", "Erro de Autenticação", JOptionPane.ERROR_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(fileExplorerSwing, "Erro durante a extração: " + e.getCause().getMessage(), "Falha na Extração", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
