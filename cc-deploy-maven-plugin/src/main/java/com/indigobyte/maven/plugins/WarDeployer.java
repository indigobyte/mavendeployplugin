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
import com.indigobyte.ssh.AbstractSshClient;
import com.indigobyte.sshj.SshSshjImpl;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

@Mojo(name = "deploy-war", threadSafe = true)
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

    @Parameter(property = "deploy.chunkSize", defaultValue = "0")
    private int chunkSize;

    @Parameter(property = "deploy.uploadRetryCount", defaultValue = "0")
    private int uploadRetryCount;

    @Parameter(defaultValue = "true")
    private boolean touchWebXml;

    public void execute() throws MojoExecutionException, MojoFailureException {
        getLog().info("WarDeployer mojo has started");
        Path remoteAppRoot = Paths.get(remoteWebApps, warName);
        Path remoteAppChecksumFile = Paths.get(remoteWebApps, warName + ".checksums");
        Path localAppRoot = Paths.get(projectBuildDir, warName);
        try (AbstractSshClient sshClient = new SshSshjImpl(
                getLog(),
                chunkSize,
                uploadRetryCount,
                hostName,
                port,
                userName,
                sshKeyFile
        )) {
            getLog().info("Checking remote checksum file" + remoteAppChecksumFile);
            byte[] remoteChecksumFileBytes = null;

            String remoteAppChecksumFileNode = Utils.linuxPath(remoteAppChecksumFile);
            if (sshClient.folderExists(remoteAppChecksumFileNode) ||
                    sshClient.symbolicLinkExists(remoteAppChecksumFileNode)
            ) {
                getLog().error(remoteAppChecksumFileNode + " is not a file!");
                throw new MojoFailureException(remoteAppChecksumFileNode + " is not a file!");
            }

            //Create remote app root directory
            String remoteAppRootNode = Utils.linuxPath(remoteAppRoot);
            if (sshClient.regularFileExists(remoteAppRootNode) ||
                    sshClient.symbolicLinkExists(remoteAppRootNode)
            ) {
                getLog().error(remoteAppRootNode + " is not a directory!");
                throw new MojoFailureException(remoteAppRootNode + " is not a directory!");
            } else {
                if (!sshClient.folderExists(remoteAppRootNode)) {
                    getLog().info("Creating remote directory for application at " + Utils.linuxPath(remoteAppRoot));
                    sshClient.mkdir(remoteAppRootNode);
                } else {
                    if (sshClient.regularFileExists(remoteAppChecksumFileNode)) {
                        getLog().info("Downloading remote checksum file" + remoteAppChecksumFile);
                        File remoteChecksumTempFile = File.createTempFile("remote-checksum", ".tmp");
                        remoteChecksumTempFile.delete();
                        sshClient.downloadRemoteFile(remoteAppChecksumFileNode, remoteChecksumTempFile.getAbsolutePath());
                        remoteChecksumFileBytes = Files.readAllBytes(remoteChecksumTempFile.toPath());
                        remoteChecksumTempFile.delete();
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
                sshClient.uploadLocalFile(tempFile, remoteTempArchive);
                getLog().info("Unpacking remote archive");
                sshClient.executeCommand("cd " + Utils.linuxPath(remoteAppRoot) + ";jar xvf /tmp/" + tempFile.getName());
                getLog().info("Temp archive was unpacked. Removing temporary files");
                sshClient.rm(remoteTempArchive);
                tempFile.delete();
                getLog().info("Changed file(s) were uploaded to the remote machine");
            }
            if (!filesToRemove.isEmpty()) {
                Utils.logFiles(getLog(), filesToRemove, "files must be deleted from the remote machine", Path::toString);

                for (Path curFile : filesToRemove) {
                    Path pathOfFileToDelete = remoteAppRoot.resolve(curFile);
                    String nameOfFileToDelete = Utils.linuxPath(pathOfFileToDelete);
                    getLog().info("Removing " + pathOfFileToDelete);
                    sshClient.rm(nameOfFileToDelete);
                }
                getLog().info("Old file(s) were deleted from the remote machine");
            }
            if (!filesToCopy.isEmpty() || !filesToRemove.isEmpty()) {
                //Write new checksums
                {
                    File tempFile = File.createTempFile("war-deployer-checksum", "tmp");
                    analyzer.writeNewChecksums(tempFile.toPath());

                    getLog().info("Deleting old remote checksum file " + remoteAppChecksumFileNode);
                    if (sshClient.regularFileExists(remoteAppChecksumFileNode)) {
                        sshClient.rm(remoteAppChecksumFileNode);
                    }
                    getLog().info("Copying local file " + tempFile.getAbsolutePath() + " to " + remoteAppChecksumFileNode);
                    sshClient.uploadLocalFile(tempFile, remoteAppChecksumFileNode);
                    getLog().info("Removing temporary local checksum file " + tempFile.getAbsolutePath());
                    tempFile.delete();
                    getLog().info("New checksum file was uploaded to the remote machine");
                }
                if (touchWebXml) {
                    sshClient.executeCommand("touch " + Utils.linuxPath(remoteAppRoot) + "/WEB-INF/web.xml");
                    getLog().info("web.xml was touched");
                } else {
                    getLog().info("web.xml was not touched because touchWebXml is " + touchWebXml);
                }
                if (nginxCacheDir != null && !nginxCacheDir.isEmpty()) {
                    Path nginxCache = Paths.get(nginxCacheDir);
                    String nginxCacheNode = Utils.linuxPath(nginxCache);
                    if (!sshClient.folderExists(nginxCacheNode)) {
                        getLog().info("Nginx cache dir " + Utils.linuxPath(nginxCache) + " doesn't exist");
                    } else {
                        if (sshClient.isEmptyFolder(nginxCacheNode)) {
                            boolean emptyDir = sshClient.isEmptyFolder(nginxCacheNode);
                            if (emptyDir) {
                                getLog().info("Nginx cache dir " + Utils.linuxPath(nginxCache) + " is empty");
                            } else {
                                String purgeCommand = "sudo /usr/bin/find " + Utils.linuxPath(nginxCache) + " -mindepth 1 -delete";
                                getLog().info("Purging nginx cache dir with command " + purgeCommand);
                                sshClient.executeCommand(purgeCommand);
                                getLog().info("Done");
                            }
                        }
                    }
                } else {
                    getLog().info("No Nginx cache dir specified: " + nginxCacheDir + " is empty");
                }
            } else {
                getLog().info("Nothing to do: local files are identical to the remote machine's ones");
            }
        } catch (IOException e) {
            getLog().error(e);
            e.printStackTrace();
            throw new MojoExecutionException("Error during execution of the deploy script", e);
        }
    }
}
