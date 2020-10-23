package com.indigobyte.sshj;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import org.apache.maven.plugin.logging.Log;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

class SshjResource implements AutoCloseable {
    @NotNull
    private final Log log;
    @NotNull
    private final String hostName;
    private final int port;
    @NotNull
    private final String userName;
    @NotNull
    private final String sshKeyFile;
    private SSHClient sshClient;
    private SFTPClient sftpClient;

    public SshjResource(
            @NotNull Log log,
            @NotNull String hostName,
            int port,
            @NotNull String userName,
            @NotNull String sshKeyFile
    ) {
        this.log = log;
        this.hostName = hostName;
        this.port = port;
        this.userName = userName;
        this.sshKeyFile = sshKeyFile;
    }

    @NotNull
    public SSHClient getSshClient() throws IOException {
        if (sshClient == null) {
            sshClient = new SSHClient();
            sshClient.addHostKeyVerifier(new PromiscuousVerifier());
            log.info("Connecting to " + hostName + ":" + port);
            sshClient.connect(hostName, port);
            log.info("Authorization in progress");
            sshClient.authPublickey(userName, sshKeyFile);
        }
        return sshClient;
    }

    @NotNull
    public SFTPClient getSftpClient() throws IOException {
        if (sftpClient == null) {
            sftpClient = getSshClient().newSFTPClient();
        }
        return sftpClient;
    }

    @Override
    public void close() throws IOException {
        if (sftpClient != null) {
            try {
                sftpClient.close();
            } finally {
                sftpClient = null;
            }
        }
        if (sshClient != null) {
            try {
                sshClient.close();
            } finally {
                sshClient = null;
            }
        }
    }
}
