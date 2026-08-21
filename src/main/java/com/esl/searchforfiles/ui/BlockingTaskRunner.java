package com.esl.searchforfiles.ui;


import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

// ═══════════════════════════════════════════════════════════════
// BlockingTaskRunner — executa uma tarefa em background
// mantendo o BlockingDialog visível até o fim.
//
// Uso simples:
//   BlockingTaskRunner.run(
//       owner,
//       "Aguarde, verificando mudanças...",
//       () -> { /* tarefa pesada */ },
//       () -> { /* callback na EDT após terminar */ }
//   );
//
// Uso com diálogo personalizado:
//   BlockingDialog dlg = new BlockingDialog(owner, "Sincronizando...", "Isso pode levar alguns segundos");
//   BlockingTaskRunner.run(dlg, () -> { /* tarefa */ }, () -> { /* done */ });
// ═══════════════════════════════════════════════════════════════
public class BlockingTaskRunner {

    /**
     * Atalho rápido: cria o diálogo internamente e executa a tarefa.
     *
     * @param owner    Janela pai
     * @param message  Mensagem exibida no diálogo
     * @param task     Tarefa executada em background (NÃO na EDT)
     * @param onDone   Callback executado na EDT após a tarefa terminar
     *                 (mesmo em caso de erro)
     */
    public static void run(Window owner,
                           String message,
                           Runnable task,
                           Runnable onDone) {
        run(new BlockingDialog(owner, message), task, onDone);
    }

    /**
     * Versão com mensagem secundária.
     */
    public static void run(Window owner,
                           String message,
                           String subMessage,
                           Runnable task,
                           Runnable onDone) {
        run(new BlockingDialog(owner, message, subMessage), task, onDone);
    }

    /**
     * Versão com diálogo já configurado (para casos que precisam
     * de atualização dinâmica da mensagem durante a tarefa).
     *
     * @param dialog  BlockingDialog já construído
     * @param task    Runnable que recebe acesso ao diálogo via closure
     * @param onDone  Callback na EDT após terminar
     */
    public static void run(BlockingDialog dialog,
                           Runnable task,
                           Runnable onDone) {

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                try {
                    task.run();
                } catch (Exception e) {
                    System.err.println("BlockingTaskRunner erro: " + e.getMessage());
                }
                return null;
            }

            @Override
            protected void done() {
                dialog.hide();
                if (onDone != null) onDone.run();
            }
        };

        worker.execute();   // inicia a tarefa em background
        dialog.show();      // exibe o diálogo (não bloqueia o código aqui,
        // mas bloqueia a interação do usuário)
    }

    /**
     * Versão com Consumer<BlockingDialog> — permite atualizar
     * a mensagem do diálogo durante a execução da tarefa.
     *
     * Exemplo:
     *   BlockingTaskRunner.run(owner, "Sincronizando...", dlg -> {
     *       dlg.setSubMessage("Lendo arquivos...");
     *       // faz algo
     *       dlg.setSubMessage("Atualizando banco...");
     *       // faz algo
     *   }, () -> System.out.println("Pronto!"));
     */
    public static void run(Window owner,
                           String message,
                           Consumer<BlockingDialog> task,
                           Runnable onDone) {
        BlockingDialog dialog = new BlockingDialog(owner, message);
        run(dialog, () -> task.accept(dialog), onDone);
    }
}