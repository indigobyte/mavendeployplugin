package com.indigobyte.maven.plugins;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.indigobyte.deploy.LocalAnalyzer;
import com.indigobyte.deploy.Utils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.keyverifier.AcceptAllServerKeyVerifier;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.client.subsystem.sftp.SftpClient;
import org.apache.sshd.client.subsystem.sftp.impl.DefaultSftpClientFactory;
import org.apache.sshd.common.keyprovider.FileKeyPairProvider;
import org.apache.sshd.common.subsystem.sftp.SftpConstants;
import org.apache.sshd.common.subsystem.sftp.SftpException;
import org.apache.sshd.common.util.io.IoUtils;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.function.Predicate;

@Mojo(name = "deploy-war")
public class WarDeployer extends AbstractMojo {
    @Parameter(property = "deploy.warName", required = true)
    private String warName;

    @Parameter(property = "deploy.hostName", required = true)
    private String hostName;

    @Parameter(property = "deploy.port", required = false, defaultValue = "22")
    private int port;

    @Parameter(property = "deploy.userName", required = true)
    private String userName;

    @Parameter(property = "deploy.sshKeyFile", required = true)
    private String sshKeyFile;

    @Parameter(property = "deploy.remoteWebApps", required = true)
    private String remoteWebApps;

    @Parameter(defaultValue = "${project.build.directory}")
    private String projectBuildDir;

    @Parameter(property = "deploy.nginxCacheDir", required = true)
    private String nginxCacheDir;

    @Parameter(defaultValue = "true")
    private boolean touchWebXml;

    private boolean folderExists(
            @NotNull SftpClient sftpClient,
            @NotNull String remoteDest
    ) throws IOException {
        return checkExistenceOfRemotePath(sftpClient, remoteDest, SftpClient.Attributes::isDirectory);
    }

    private boolean regularFileExists(
            @NotNull SftpClient sftpClient,
            @NotNull String remoteDest
    ) throws IOException {
        return checkExistenceOfRemotePath(sftpClient, remoteDest, SftpClient.Attributes::isRegularFile);
    }

    private boolean symbolicLinkExists(
            @NotNull SftpClient sftpClient,
            @NotNull String remoteDest
    ) throws IOException {
        return checkExistenceOfRemotePath(sftpClient, remoteDest, SftpClient.Attributes::isSymbolicLink);
    }

    private boolean checkExistenceOfRemotePath(
            @NotNull SftpClient sftpClient,
            @NotNull String remoteDest,
            Predicate<SftpClient.Attributes> pathTypeValidator
    ) throws IOException {
        try {
            SftpClient.Attributes stat = sftpClient.lstat(remoteDest);
            if (stat == null) {
                return false;
            }
            return pathTypeValidator.test(stat);
        } catch (SftpException e) {
            if (e.getStatus() != SftpConstants.SSH_FX_NO_SUCH_FILE) {
                getLog().error("Error finding file " + remoteDest, e);
                throw e;
            }
            return false;
        }
    }

    private void copyLocalToRemote(
            @NotNull SftpClient sftpClient,
            @NotNull File localFile,
            @NotNull String fullRemoteFileName
    ) throws IOException {
        getLog().info("Copying local file " + localFile.getAbsolutePath() + " to " + fullRemoteFileName);
        try (OutputStream remoteOutputStream = sftpClient.write(
                fullRemoteFileName,
                SftpClient.OpenMode.Write,
                SftpClient.OpenMode.Create,
                SftpClient.OpenMode.Truncate
        )) {
            try (FileInputStream localArchiveInputStream = new FileInputStream(localFile)) {
                IoUtils.copy(localArchiveInputStream, remoteOutputStream);
            }
        }
    }


    public void execute() throws MojoExecutionException, MojoFailureException {
        getLog().info("WarDeployer mojo has started");
        SshClient sshClient = SshClient.setUpDefaultClient();
        sshClient.setKeyIdentityProvider(new FileKeyPairProvider(Paths.get(sshKeyFile)));
        sshClient.setServerKeyVerifier(AcceptAllServerKeyVerifier.INSTANCE);
        sshClient.start();
        getLog().info("Connecting to " + hostName + ":" + port);
        try (ClientSession session = sshClient.connect(userName, hostName, port)
                .verify(15000)
                .getSession()
        ) {
            getLog().info("Authorization in progress");
            session.auth().verify(15000);

            Path remoteAppRoot = Paths.get(remoteWebApps, warName);
            Path remoteAppChecksumFile = Paths.get(remoteWebApps, warName + ".checksums");
            Path localAppRoot = Paths.get(projectBuildDir, warName);

            try (SftpClient sftpClient = DefaultSftpClientFactory.INSTANCE.createSftpClient(session)) {
                getLog().info("Checking remote checksum file" + remoteAppChecksumFile);
                byte[] remoteChecksumFileBytes = null;

                String remoteAppChecksumFileNode = Utils.linuxPath(remoteAppChecksumFile);
                if (folderExists(sftpClient, remoteAppChecksumFileNode) ||
                        symbolicLinkExists(sftpClient, remoteAppChecksumFileNode)
                ) {
                    getLog().error(remoteAppChecksumFileNode + " is not a file!");
                    throw new MojoFailureException(remoteAppChecksumFileNode + " is not a file!");
                }

                //Create remote app root directory
                String remoteAppRootNode = Utils.linuxPath(remoteAppRoot);
                if (regularFileExists(sftpClient, remoteAppRootNode) ||
                        symbolicLinkExists(sftpClient, remoteAppRootNode)
                ) {
                    getLog().error(remoteAppRootNode + " is not a directory!");
                    throw new MojoFailureException(remoteAppRootNode + " is not a directory!");
                } else {
                    if (!folderExists(sftpClient, remoteAppRootNode)) {
                        getLog().info("Creating remote directory for application at " + Utils.linuxPath(remoteAppRoot));
                        sftpClient.mkdir(remoteAppRootNode);
                    } else {
                        if (regularFileExists(sftpClient, remoteAppChecksumFileNode)) {
                            getLog().info("Downloading remote checksum file" + remoteAppChecksumFile);
                            try (InputStream remoteAppChecksumFileInputStream = sftpClient.read(remoteAppChecksumFileNode)) {
                                remoteChecksumFileBytes = IoUtils.toByteArray(remoteAppChecksumFileInputStream);
                                getLog().info("Remote checksum file downloaded");
                            }
                        }
                    }
                }

                LocalAnalyzer analyzer = new LocalAnalyzer(getLog(), localAppRoot, remoteChecksumFileBytes);

                Set<Path> filesToCopy = analyzer.getFilesToCopy();
                Set<Path> filesToRemove = analyzer.getFilesToRemove();
                if (!filesToCopy.isEmpty()) {
                    Utils.logFiles(getLog(), filesToCopy, "Changed files were found", Path::toString);
                    File tempFile = File.createTempFile("war-deployer", ".tmp");
//                    FileNode tempFile = world.getTemp().createTempFile();
                    getLog().info("Archive containing changed and new files will be created in temporary file " + tempFile);
                    Utils.createAchive(filesToCopy, localAppRoot, tempFile.getAbsolutePath());
                    String remoteTempArchive = "/tmp/" + tempFile.getName();
                    copyLocalToRemote(sftpClient, tempFile, remoteTempArchive);
                    getLog().info("Unpacking remote archive");
                    String jarOutput = session.executeRemoteCommand("cd " + Utils.linuxPath(remoteAppRoot) + ";jar xvf /tmp/" + tempFile.getName());
                    getLog().info("Temp archive was unpacked. Removing temporary files");
                    sftpClient.remove(remoteTempArchive);
                    tempFile.delete();
                    getLog().info("Changed file(s) were uploaded to the remote machine");
                }
                if (!filesToRemove.isEmpty()) {
                    Utils.logFiles(getLog(), filesToRemove, "files must be deleted from the remote machine", Path::toString);

                    for (Path curFile : filesToRemove) {
                        Path pathOfFileToDelete = remoteAppRoot.resolve(curFile);
                        String nameOfFileToDelete = Utils.linuxPath(pathOfFileToDelete);
                        getLog().info("Removing " + pathOfFileToDelete);
                        sftpClient.remove(nameOfFileToDelete);
                    }
                    getLog().info("Old file(s) were deleted from the remote machine");
                }
                if (!filesToCopy.isEmpty() || !filesToRemove.isEmpty()) {
                    //Write new checksums
                    {
                        File tempFile = File.createTempFile("war-deployer-checksum", "tmp");
                        analyzer.writeNewChecksums(tempFile.toPath());

                        getLog().info("Deleting old remote checksum file " + remoteAppChecksumFileNode);
                        if (regularFileExists(sftpClient, remoteAppChecksumFileNode)) {
                            sftpClient.remove(remoteAppChecksumFileNode);
                        }
                        getLog().info("Copying local file " + tempFile.getAbsolutePath() + " to " + remoteAppChecksumFileNode);
                        copyLocalToRemote(sftpClient, tempFile, remoteAppChecksumFileNode);
                        getLog().info("Removing temporary local checksum file " + tempFile.getAbsolutePath());
                        tempFile.delete();
                        getLog().info("New checksum file was uploaded to the remote machine");
                    }
                    if (touchWebXml) {
                        session.executeRemoteCommand("touch " + Utils.linuxPath(remoteAppRoot) + "/WEB-INF/web.xml");
                        getLog().info("web.xml was touched");
                    } else {
                        getLog().info("web.xml was not touched because touchWebXml is " + touchWebXml);
                    }
                    if (nginxCacheDir != null && !nginxCacheDir.isEmpty()) {
                        Path nginxCache = Paths.get(nginxCacheDir);
                        String nginxCacheNode = Utils.linuxPath(nginxCache);
                        if (!folderExists(sftpClient, nginxCacheNode)) {
                            getLog().info("Nginx cache dir " + Utils.linuxPath(nginxCache) + " doesn't exist");
                        } else {
                            SftpClient.CloseableHandle nginxCacheDirHandle = sftpClient.openDir(nginxCacheNode);
                            Iterable<SftpClient.DirEntry> dirEntries = sftpClient.listDir(nginxCacheDirHandle);
                            boolean emptyDir = true;
                            for (SftpClient.DirEntry dirEntry : dirEntries) {
                                emptyDir = false;
                                break;
                            }
                            if (emptyDir) {
                                getLog().info("Nginx cache dir " + Utils.linuxPath(nginxCache) + " is empty");
                            } else {
                                String purgeCommand = "sudo /usr/bin/find " + Utils.linuxPath(nginxCache) + " -mindepth 1 -delete";
                                getLog().info("Purging nginx cache dir with command " + purgeCommand);
                                session.executeRemoteCommand(purgeCommand);
                                getLog().info("Done");
                            }
                        }
                    } else {
                        getLog().info("No Nginx cache dir specified: " + nginxCacheDir + " is empty");
                    }
                } else {
                    getLog().info("Nothing to do: local files are identical to the remote machine's ones");
                }
            }
        } catch (IOException e) {
            getLog().error(e);
            e.printStackTrace();
            throw new MojoExecutionException("Error during execution of the deploy script", e);
        }
    }
}
