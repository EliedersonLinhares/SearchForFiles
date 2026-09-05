package com.esl.searchforfiles.actions.compressFile;

import com.esl.searchforfiles.model.FileInfo;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.AesKeyStrength;
import net.lingala.zip4j.model.enums.CompressionMethod;
import net.lingala.zip4j.model.enums.EncryptionMethod;
import net.lingala.zip4j.progress.ProgressMonitor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ZipCompressionService {

    private static final long PROGRESS_INTERVAL = 100;
    private volatile boolean cancelRequested;
    private volatile ZipFile currentZipFile;

    public ZipCompressionService() {}

    public void cancel() {
        cancelRequested = true;
        ZipFile zip = currentZipFile;

        if (zip != null) {
            try {
                //zip.getProgressMonitor().cancelAllTasks();
                zip.getProgressMonitor().fullReset();
            } catch (Exception ignored) {
            }
        }
    }


    public ZipResult compress(
            List<FileInfo> items,
            File destination,
            CompressionProfile profile,
            boolean preserveDirectories,
            ProgressCallback callback) {

        long start = System.currentTimeMillis();
        cancelRequested = false;
        currentZipFile = null;

        ZipPlan plan = null;
        try {
            validateInput(items, destination);

            plan = buildPlan(items, destination, preserveDirectories);
            if (plan.isEmpty()) {
                throw new IOException(
                        "Nenhum arquivo válido para compactar."
                );
            }
            if (destination.exists()) {
                Files.delete(destination.toPath());}
            ZipFile zipFile = createZipFile(destination, profile);
            currentZipFile = zipFile;

            /*
             * O Zip4j executará as operações
             * em background e disponibilizará
             * o ProgressMonitor.
             */
            zipFile.setRunInThread(true);
            long processedBytes = 0;
            long processedFiles = 0;

            for (ZipEntryInfo entry : plan.entries()) {

                if (cancelRequested) {cancelZip(destination);

                    return ZipResult.cancelled(destination, plan.totalBytes(), processedBytes, plan.fileCount(),
                            processedFiles, elapsed(start)
                    );
                }
                if (!Files.exists(entry.source().toPath())) {
                    throw new IOException("Arquivo não encontrado durante " + "a compactação:\n" + entry.source());
                }
                if (!entry.source().isFile()) {continue;}

                /*
                 * Detecta se o arquivo é o próprio ZIP.
                 */
                if (sameFile(entry.source(), destination))
                {
                    throw new IOException("O arquivo ZIP de destino " + "não pode ser incluído nele mesmo.");
                }

                long actualSize = Files.size(entry.source().toPath());
                ZipParameters parameters = createParameters(entry, profile);
                zipFile.addFile(entry.source(), parameters);

                ProgressMonitor monitor = zipFile.getProgressMonitor();

                waitForOperation(monitor, callback, plan, processedBytes, processedFiles, start, entry);

                if (cancelRequested) {
                    cancelZip(destination);

                    return ZipResult.cancelled(destination, plan.totalBytes(), processedBytes, plan.fileCount(),
                            processedFiles, elapsed(start)
                    );
                }

                if (monitor.getResult() == ProgressMonitor.Result.ERROR) {
                    Throwable error = monitor.getException();

                    if (error == null) {
                        error = new IOException("Erro desconhecido.");
                    }
                    throw error;
                }

                /*
                 * Usa o tamanho real lido do disco,
                 * caso o arquivo tenha mudado desde
                 * a criação do plano.
                 */
                processedBytes = safeAdd(processedBytes, actualSize);
                processedFiles++;

                publishProgress(callback, plan, processedBytes, processedFiles, start, entry, 100);
            }

            /*
             * O ZIP deve existir e ter conteúdo.
             */
            if (!destination.exists()) {
                throw new IOException("O arquivo ZIP não foi criado.");
            }
            if (destination.length() <= 0) {
                throw new IOException("O arquivo ZIP foi criado vazio.");
            }

            publishProgress(callback, plan, plan.totalBytes(), plan.fileCount(), start,
                    null, 100);
            return ZipResult.success(destination, plan.totalBytes(), processedBytes, plan.fileCount(),
                    processedFiles, elapsed(start)
            );

        } catch (Throwable error) {
            if (cancelRequested) {
                cancelZip(destination);
                long total = plan != null ? plan.totalBytes() : 0;
                long files = plan != null ? plan.fileCount() : 0;

                return ZipResult.cancelled(destination, total, 0, files, 0, elapsed(start));
            }

            deleteIncompleteZip(
                    destination
            );

            long total = plan != null ? plan.totalBytes() : 0;
            long files = plan != null ? plan.fileCount() : 0;

            return ZipResult.error(destination, total, 0, files, 0, elapsed(start),
                    error
            );

        } finally {
            currentZipFile = null;
        }
    }

    private ZipFile createZipFile(File destination, CompressionProfile profile) throws ZipException {
        if (profile.hasPassword()) {
            return new ZipFile(destination, profile.password());
        }
        return new ZipFile(destination);
    }

    private ZipParameters createParameters(
            ZipEntryInfo entry,
            CompressionProfile profile) {

        ZipParameters parameters = new ZipParameters();

        /*
         * Caminho que será armazenado dentro
         * do ZIP.
         */
        parameters.setFileNameInZip(entry.zipPath());
        CompressionMethod method = profile.compressionMethodFor(entry.source());
        parameters.setCompressionMethod(method);

        if (method == CompressionMethod.DEFLATE) {
            parameters.setCompressionLevel(profile.compressionLevel());}

        if (profile.hasPassword()) {parameters.setEncryptFiles(true);
            parameters.setEncryptionMethod(EncryptionMethod.AES);
            parameters.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256);
        }
        return parameters;
    }

    private ZipPlan buildPlan(List<FileInfo> items, File destination, boolean preserveDirectories) throws IOException {

        List<ZipEntryInfo> entries = new ArrayList<>();
        Set<String> physicalFiles = new HashSet<>();

        Path destinationPath = destination.toPath().toAbsolutePath().normalize();

        for (FileInfo info : items) {
            if (cancelRequested) {break;}
            if (info == null) {continue;}

            Path sourcePath = Path.of(info.getPath()).toAbsolutePath().normalize();

            if (!Files.exists(sourcePath)) {continue;}
            if (sourcePath.equals(destinationPath)) {continue;}

            if (Files.isDirectory(sourcePath)) {
                addDirectoryToPlan(sourcePath, destinationPath, preserveDirectories, entries, physicalFiles);
            } else if (Files.isRegularFile(sourcePath)) {
                addFileToPlan(sourcePath, info.getSize(), determineFileEntryName(sourcePath, preserveDirectories),
                        entries, physicalFiles
                );
            }
        }
        return new ZipPlan(
                entries
        );
    }

    private void addDirectoryToPlan(Path directory, Path destination, boolean preserveDirectories,
                                    List<ZipEntryInfo> entries, Set<String> physicalFiles) throws IOException {

        String rootName = directory.getFileName().toString();
        try (var stream = Files.walk(directory)) {

            stream.filter(Files::isRegularFile).forEach(
                            file -> {
                                if (cancelRequested) {return;}
                                try {
                                    if (file.equals(destination)) {return;}
                                    String key = normalizePhysicalPath(file);
                                    if (!physicalFiles.add(key)) {return;}

                                    String relative = directory.relativize(file)
                                                    .toString()
                                                    .replace(File.separatorChar,
                                                            '/');
                                    String zipPath;

                                    if (preserveDirectories) {zipPath = rootName + "/" + relative;
                                    } else {
                                        zipPath = file.getFileName().toString();
                                    }
                                    long size = Files.size(file);
                                    entries.add(new ZipEntryInfo(file.toFile(), zipPath, size)
                                    );

                                } catch (IOException e) {
                                    throw new PlanBuildException(e
                                   );
                                }
                            }
                    );
        } catch (PlanBuildException e) {
            throw e.getCause();
        }
    }

    private void addFileToPlan(Path file, long knownSize, String zipPath, List<ZipEntryInfo> entries,
            Set<String> physicalFiles) throws IOException {

        String key = normalizePhysicalPath(file);
        if (!physicalFiles.add(key)) {
            return;
        }
        long size;

        /*
         * Para arquivos individuais,
         * aproveitamos FileInfo.getSize().
         *
         * Mas se o valor não for válido,
         * consultamos o filesystem.
         */
        if (knownSize >= 0) {size = knownSize;
        } else {
            size = Files.size(file);
        }

        entries.add(new ZipEntryInfo(file.toFile(), zipPath, size));
    }

    private String determineFileEntryName(Path file, boolean preserveDirectories) {
        /*
         * Um arquivo individual selecionado
         * não possui uma raiz de diretório
         * explícita.
         *
         * Portanto:
         *
         * preserveDirectories = false
         *     foto.jpg
         *
         * preserveDirectories = true
         *     foto.jpg
         *
         * Para diretórios, a estrutura é
         * preservada em addDirectoryToPlan().
         */
        return file.getFileName().toString();
    }

    private void waitForOperation(ProgressMonitor monitor, ProgressCallback callback, ZipPlan plan,
            long processedBytes, long processedFiles, long start, ZipEntryInfo currentEntry)
            throws InterruptedException, IOException {

        long lastPublish = 0;
        while (monitor.getState()
                == ProgressMonitor.State.BUSY) {
            /*
             * Cancelamento solicitado pela interface.
             */
            if (cancelRequested) {
               // monitor.cancelAllTasks();
                monitor.fullReset();
                break;
            }
            long now = System.currentTimeMillis();

            /*
             * Atualiza a interface no máximo
             * a cada 100 ms.
             */
            if (now - lastPublish
                    >= PROGRESS_INTERVAL) {
                publishProgress(callback, plan, processedBytes, processedFiles, start, currentEntry,
                        monitor.getPercentDone());
                lastPublish = now;
            }
            Thread.sleep(50);
        }

        /*
         * O Zip4j terminou.
         *
         * Agora verificamos se houve erro.
         */
        if (monitor.getResult() == ProgressMonitor.Result.ERROR) {
            Throwable error = monitor.getException();
            if (error == null) {error =
                        new IOException("Erro desconhecido " + "durante a compactação.");
            }
            throw new IOException("Erro do Zip4j durante " + "a compactação.", error);
        }

        /*
         * Última atualização da interface.
         */
        publishProgress(callback, plan, processedBytes, processedFiles, start, currentEntry, monitor.getPercentDone());
    }

    private void publishProgress(ProgressCallback callback, ZipPlan plan, long processedBytes, long processedFiles,
            long start, ZipEntryInfo currentEntry, int currentPercent) {

        if (callback == null) {
            return;
        }

        long currentSize = currentEntry != null ? currentEntry.size() : 0;

        long currentProcessed = (long) (currentSize * (Math.max(0, Math.min(100, currentPercent)) / 100.0));

        long globalProcessed = safeAdd(processedBytes, currentProcessed);

        long total = plan.totalBytes();

        if (total > 0) {globalProcessed = Math.min(globalProcessed, total);}

        int percent;
        if (total <= 0) {percent = 0;
        } else {
            percent =
                    (int) Math.round(globalProcessed * 100.0 / total);
        }

        long elapsed = elapsed(start);

        long speed = elapsed > 0 ? (long) (globalProcessed / (elapsed / 1000.0)) : 0;

        long remaining;
        if (speed > 0 && total > globalProcessed) {
            remaining = (long) ((double) (total - globalProcessed) / ( speed)*1000.0);
        } else {
            remaining = -1;
        }

        String fileName = currentEntry != null ? currentEntry.zipPath() : "";

        callback.onProgress(new ZipProgress(percent, fileName, "Compactando", globalProcessed,
                        total, processedFiles, plan.fileCount(), elapsed, speed, remaining));
    }

    private void validateInput(List<FileInfo> items, File destination) throws IOException {

        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Nenhum arquivo foi selecionado.");
        }
        if (destination == null) {
            throw new IllegalArgumentException("Destino ZIP não informado.");
        }

        File parent = destination.getAbsoluteFile().getParentFile();

        if (parent != null && !parent.exists()) {
            if (!parent.mkdirs() && !parent.exists()) {
                throw new IOException("Não foi possível criar a pasta de destino."
                );
            }
        }
        if (destination.isDirectory()) {
            throw new IOException("O destino ZIP é uma pasta.");
        }
    }

    private boolean sameFile(File a, File b) {
        try {
            return a.toPath().toRealPath().equals(b.toPath().toRealPath());

        } catch (IOException e) {return a.toPath().toAbsolutePath().normalize().equals(b.toPath()
                                    .toAbsolutePath().normalize());
        }
    }

    private String normalizePhysicalPath(
            Path path) {
        try {
            return path.toRealPath().toString().toLowerCase();

        } catch (IOException e) {
            return path.toAbsolutePath().normalize().toString().toLowerCase();
        }
    }

    private void cancelZip(File destination) {deleteIncompleteZip(destination);}

    private void deleteIncompleteZip(
            File destination) {

        if (destination == null) {return;}
        try {
            Files.deleteIfExists(destination.toPath());

        } catch (IOException e) {
            System.err.println("Não foi possível remover "
                            + "ZIP incompleto: " + destination);
        }
    }

    private long safeAdd(long a, long b) {
        if (b > 0 && a > Long.MAX_VALUE - b) {
            return Long.MAX_VALUE;
        }
        return a + b;
    }

    private long elapsed(long start) {
        return Math.max(0, System.currentTimeMillis() - start);
    }

    public interface ProgressCallback {
        void onProgress(ZipProgress progress);
    }

    public record ZipEntryInfo(File source, String zipPath, long size) { }

    public record ZipPlan(List<ZipEntryInfo> entries) {

        public long totalBytes() {
            long total = 0;
            for (ZipEntryInfo entry : entries) {
                if (Long.MAX_VALUE - total < entry.size()) {
                    return Long.MAX_VALUE;
                }
                total += entry.size();
            }

            return total;
        }

        public long fileCount() {
            return entries.size();
        }

        public boolean isEmpty() {
            return entries.isEmpty();
        }
    }

    private static class PlanBuildException extends RuntimeException {
        private final IOException cause;
        public PlanBuildException(
                IOException cause) {

            this.cause = cause;
        }

        @Override
        public IOException getCause() {
            return cause;
        }
    }
}
