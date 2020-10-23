package com.indigobyte.sshj;

import com.indigobyte.ssh.AbstractSshClient;
import net.schmizz.sshj.common.IOUtils;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.sftp.*;
import org.apache.maven.plugin.logging.Log;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public class SshSshjImpl extends AbstractSshClient {
    @NotNull
    private final SshjResource ssh;

    public SshSshjImpl(
            @NotNull Log log,
            int chunkSize,
            int uploadRetryCount,
            @NotNull String hostName,
            int port,
            @NotNull String userName,
            @NotNull String sshKeyFile
    ) throws IOException {
        super(log, chunkSize, uploadRetryCount);
        this.ssh = new SshjResource(log, hostName, port, userName, sshKeyFile);
        ssh.getSshClient();
    }

    @Override
    public boolean folderExists(
            @NotNull String remoteDest
    ) throws IOException {
        return checkExistenceOfRemotePath(remoteDest, p -> p.getType() == FileMode.Type.DIRECTORY);
    }

    @Override
    public boolean regularFileExists(
            @NotNull String remoteDest
    ) throws IOException {
        return checkExistenceOfRemotePath(remoteDest, p -> p.getType() == FileMode.Type.REGULAR);
    }

    @Override
    public boolean symbolicLinkExists(
            @NotNull String remoteDest
    ) throws IOException {
        return checkExistenceOfRemotePath(remoteDest, p -> p.getType() == FileMode.Type.SYMLINK);
    }

    public boolean checkExistenceOfRemotePath(
            @NotNull String remoteDest,
            Predicate<FileAttributes> pathTypeValidator
    ) throws IOException {
        try {
            FileAttributes stat = ssh.getSftpClient().lstat(remoteDest);
            if (stat == null) {
                return false;
            }
            return pathTypeValidator.test(stat);
        } catch (SFTPException e) {
            if (e.getStatusCode() != Response.StatusCode.NO_SUCH_FILE) {
                log.error("Error finding file " + remoteDest, e);
                throw e;
            }
            return false;
        }
    }

    @Override
    protected void doUploadLocalFile(
            @NotNull String localFile,
            @NotNull String fullRemoteFileName
    ) throws IOException {
        log.info("Copying local file " + localFile + " to " + fullRemoteFileName);
        ssh.getSftpClient().put(localFile, fullRemoteFileName);
    }

    @Override
    public String executeCommand(@NotNull String command) throws IOException {
        try (Session session = ssh.getSshClient().startSession()) {
            Session.Command cmd = session.exec(command);
            String commandOutput = new String(IOUtils.readFully(cmd.getInputStream()).toByteArray(), StandardCharsets.UTF_8);
            cmd.join(15, TimeUnit.SECONDS);
            Integer exitStatus = cmd.getExitStatus();
            if (exitStatus == null || exitStatus != 0) {
                throw new IOException("Unable to execute command. Exit status: " + exitStatus);
            }
            log.debug("output of a command " + command + ": " + commandOutput);
            return commandOutput;
        }
    }

    @Override
    public void mkdir(@NotNull String fullRemotePath) throws IOException {
        ssh.getSftpClient().mkdirs(fullRemotePath);
    }

    @Override
    public void downloadRemoteFile(@NotNull String fullRemotePath, @NotNull String fullLocalPath) throws IOException {
        ssh.getSftpClient().get(fullRemotePath, fullLocalPath);
    }

    @Override
    public void rm(@NotNull String fullRemotePath) throws IOException {
        ssh.getSftpClient().rm(fullRemotePath);
    }

    @Override
    public boolean isEmptyFolder(@NotNull String fullRemotePath) throws IOException {
        List<RemoteResourceInfo> ls = ssh.getSftpClient().ls(fullRemotePath);
        boolean emptyDir = ls.isEmpty();
        return emptyDir;
    }

    @Override
    public void close() throws IOException {
        ssh.close();
    }
}

