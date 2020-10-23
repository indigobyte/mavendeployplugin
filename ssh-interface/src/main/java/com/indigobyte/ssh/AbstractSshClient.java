package com.indigobyte.ssh;

import com.indigobyte.deploy.Utils;
import org.apache.maven.plugin.logging.Log;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

public abstract class AbstractSshClient implements AutoCloseable {
    @NotNull
    protected final Log log;
    private final int chunkSize;
    private final int uploadRetryCount;

    protected AbstractSshClient(
            @NotNull Log log,
            int chunkSize,
            int uploadRetryCount
    ) {
        this.log = log;
        this.chunkSize = chunkSize;
        this.uploadRetryCount = uploadRetryCount;
        log.info("Chunk size = " + chunkSize + ", upload retry count = " + uploadRetryCount);
    }

    public abstract boolean folderExists(
            @NotNull String remoteDest
    ) throws IOException;

    public abstract boolean regularFileExists(
            @NotNull String remoteDest
    ) throws IOException;

    public abstract boolean symbolicLinkExists(
            @NotNull String remoteDest
    ) throws IOException;

    public final void uploadLocalFile(
            @NotNull File localFile,
            @NotNull String fullRemoteFileName
    ) throws IOException {
        if (chunkSize <= 0 || uploadRetryCount <= 0) {
            log.info("Uploading file " + localFile + " to " + fullRemoteFileName + " in one go");
            doUploadLocalFile(localFile.toString(), fullRemoteFileName);
            return;
        }
        Path tempFile = Files.createTempFile("chunk", ".dat");
        uploadAndVerifyChecksum(tempFile.toString(), fullRemoteFileName);
        String uuid = System.currentTimeMillis() + Utils.getDigest(new ByteArrayInputStream(tempFile.toAbsolutePath().toString().getBytes(StandardCharsets.UTF_8)));
        byte[] buffer = new byte[chunkSize];
        try (FileInputStream fis = new FileInputStream(localFile)) {
            int chunkNumber = 0;
            while (true) {
                int read = fis.read(buffer);
                if (read == -1) { // End of file reached
                    return;
                }
                if (read != chunkSize) {
                    buffer = Arrays.copyOf(buffer, read);
                }
                Files.write(tempFile, buffer);
                String remoteFileNameForChunk = "/tmp/chunk-" + chunkNumber + "_" + uuid + ".dat";
                uploadAndVerifyChecksum(tempFile.toString(), remoteFileNameForChunk);
                executeCommand("cat " + remoteFileNameForChunk + " >> " + fullRemoteFileName);
                rm(remoteFileNameForChunk);
                ++chunkNumber;
            }
        }
    }

    private void uploadAndVerifyChecksum(
            @NotNull String localFile,
            @NotNull String fullRemoteFileName
    ) throws IOException {
        String localFileDigest = Utils.getDigest(Paths.get(localFile));
        IOException latestException = null;
        for (int attempt = 0; attempt < uploadRetryCount; ++attempt) {
            try {
                doUploadLocalFile(localFile, fullRemoteFileName);
                String remoteDigest = executeCommand("md5sum " + fullRemoteFileName);
                remoteDigest = remoteDigest.substring(0, remoteDigest.indexOf(' '));
                if (remoteDigest.equals(localFileDigest)) {
                    return; // uploaded contents matches
                } else {
                    latestException = new IOException("Checksum mismatch." +
                            "\nLocal checksum: " + localFileDigest +
                            "\nRemote checksum: " + remoteDigest
                    );
                }
            } catch (Throwable e) {
                log.error("Attempt #" + attempt + " to copy chunk " + localFile + " to " + fullRemoteFileName + " failed", e);
                if (e instanceof IOException) {
                    latestException = (IOException) e;
                } else {
                    latestException = new IOException(e);
                }
                try {
                    close(); // To reinitialize the connection
                } catch (Throwable closingException) {
                    log.error("An exception was thrown when connection was being closed", e);
                }
            }
        }
        if (latestException != null) {
            log.error("Max attempts to copy chunk reached", latestException);
            throw latestException;
        }
    }

    protected abstract void doUploadLocalFile(
            @NotNull String localFile,
            @NotNull String fullRemoteFileName
    ) throws IOException;

    public abstract String executeCommand(@NotNull String command) throws IOException;

    public abstract void mkdir(@NotNull String fullRemotePath) throws IOException;

    public abstract void downloadRemoteFile(
            @NotNull String fullRemotePath,
            @NotNull String fullLocalPath
    ) throws IOException;

    public abstract void rm(@NotNull String fullRemotePath) throws IOException;

    public abstract boolean isEmptyFolder(@NotNull String fullRemotePath) throws IOException;

    @Override
    public abstract void close() throws IOException;
}
