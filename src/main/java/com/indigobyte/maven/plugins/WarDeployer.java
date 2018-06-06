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

import com.indigobyte.deploy.Analyzer;
import com.indigobyte.deploy.Utils;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import net.oneandone.sushi.fs.Node;
import net.oneandone.sushi.fs.Settings;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileFilesystem;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.fs.memory.MemoryFilesystem;
import net.oneandone.sushi.fs.ssh.SshFilesystem;
import net.oneandone.sushi.fs.ssh.SshNode;
import net.oneandone.sushi.fs.ssh.SshRoot;
import net.oneandone.sushi.io.Buffer;
import net.oneandone.sushi.io.OS;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.*;

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

    @Parameter(property = "deploy.predeployScript", required = true)
    private String predeployScript;

    @Parameter(property = "deploy.postdeployScript", required = true)
    private String postdeployScript;

    @Parameter(defaultValue = "true")
    private boolean touchWebXml;

    public void execute() throws MojoExecutionException, MojoFailureException {
        getLog().info("WarDeployer mojo has started");

        try (World world = new World(OS.CURRENT, new Settings("UTF-8", "\n"), new Buffer(), (FileFilesystem) null, (MemoryFilesystem) null, new String[]{"**/.svn", "**/.svn/**/*"})) {
            Path remoteAppRoot = Paths.get(remoteWebApps, warName);
            Path localAppRoot = Paths.get(projectBuildDir, warName);
            Path nginxCache = Paths.get(nginxCacheDir);

            world.withStandardFilesystems(false);
            world.loadNetRcOpt();
            JSch jSch = new JSch();
            jSch.addIdentity(sshKeyFile);
            Session session = jSch.getSession(userName, hostName, port);
            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.connect();
            SshFilesystem remoteFs = new SshFilesystem(world, "remoteFs", jSch);
            SshRoot root = new SshRoot(remoteFs, session);

            //Execute pre-deploy script
            root.exec(predeployScript);

            List<String> digestLines = Collections.emptyList();
            Map<String, Map<String, Long>> crc32 = new HashMap<>();
            //Create remote app root directory
            SshNode remoteAppRootNode = root.node(Utils.linuxPathWithoutSlash(remoteAppRoot), null);
            if (!remoteAppRootNode.exists()) {
                getLog().info("Creating remote directory for application at " + Utils.linuxPath(remoteAppRoot));
                remoteAppRootNode.mkdir();
            } else {
                if (!remoteAppRootNode.isDirectory()) {
                    getLog().error(Utils.linuxPath(remoteAppRoot) + " is not a directory!");
                    throw new MojoFailureException(Utils.linuxPath(remoteAppRoot) + " is not a directory!");
                }
                String countFilesCommand = "find " + Utils.linuxPath(remoteAppRoot) + " -type f | wc -l";
                getLog().info("Counting files (recursively) in remote folder " + Utils.linuxPath(remoteAppRoot) + " using command " + countFilesCommand);
                String fileCountStr = root.exec(countFilesCommand).trim();
                getLog().info("Files found: " + fileCountStr);
                int fileCount = Integer.parseInt(fileCountStr);
                if (fileCount > 0) {
                    getLog().info("Retrieving checksums from non-empty remote directory: " + Utils.linuxPath(remoteAppRoot));
                    String generateChecksumsCommand = "cd " + Utils.linuxPath(remoteAppRoot) + ";find . -type f -not -name \"*.jar\" -print0 | xargs -0 md5sum";
                    getLog().info("Generating checksums with command:\n" + generateChecksumsCommand);
                    String checksumString = root.exec(generateChecksumsCommand);
                    getLog().debug("Checksum string:\n" + checksumString);
                    digestLines = Arrays.asList(checksumString.split("\r+\n"));
                    getLog().info("Received " + digestLines.size() + " checkums");
                    getLog().info("Retrieving CRCs of remote JARs");
                    String allCrc = root.exec("cd " + Utils.linuxPath(remoteAppRoot) + ";find . -type f -name \"*.jar\" -exec unzip -vl '{}' \\;");
                    getLog().info("Retrieved CRCs of remote JARs");
                    for (String archiveCrc: allCrc.split("Archive:")) {
                        archiveCrc = archiveCrc.trim();
                        if (archiveCrc.isEmpty())
                            continue; //Skip first empty line
                        String[] archiveLines = archiveCrc.split("\r+\n");
                        String archiveFileName = archiveLines[0];
                        getLog().debug("Analyzing CRCs of archive \"" + archiveFileName + "\"");
                        SortedMap<String, Long> curArchiveCrcMap = new TreeMap<>();
                        //archiveLines have this format:
                        /*
                        ./sshwebproxy/WEB-INF/lib/commons-fileupload.jar                      -- line 0
                         Length   Method    Size  Cmpr    Date    Time   CRC-32   Name        -- line 1
                        --------  ------  ------- ---- ---------- ----- --------  ----        -- line 2
                               0  Stored        0   0% 06-25-2003 23:12 00000000  META-INF/   -- line 3
                                             ...
                        --------          -------  ---                            -------     -- line N-1
                           36295            18057  50%                            24 files    -- line N
                         which means that the file data starts at line 3 and ends at line N-1
                         */
                        int startLine = 2;
                        for (;startLine < archiveLines.length && !archiveLines[startLine].startsWith("--------"); ++startLine);
                        for (int lineNo = startLine + 1; !archiveLines[lineNo].startsWith("--------"); ++lineNo) {
                            String[] fileInfo = archiveLines[lineNo].trim().split("\\s+");
                            String curFileName = fileInfo[7].trim();
                            String curFileCrc = fileInfo[6].trim();

                            if (curFileName.endsWith("/")) //It's a directory - skip it. We don't care about directories inside archives
                                continue;
                            curArchiveCrcMap.put(curFileName, Long.parseLong(curFileCrc, 16));
                        }
                        crc32.put(archiveFileName, curArchiveCrcMap);
                    }
                } else {
                    getLog().info(Utils.linuxPath(remoteAppRoot) + " is empty");
                }
            }
            Analyzer analyzer = new Analyzer(getLog(), localAppRoot, digestLines, crc32);

            Set<Path> filesToCopy = analyzer.getFilesToCopy();
            Set<Path> filesToRemove = analyzer.getFilesToRemove();
            if (filesToCopy != null) {
                Utils.logFiles(getLog(), filesToCopy, "Changed files were found", Path::toString);

                FileNode tempFile = world.getTemp().createTempFile();
                getLog().info("Archive containing changed and new files will be created in temporary file " + tempFile.getName());
                Utils.createAchive(filesToCopy, localAppRoot, tempFile.getAbsolute());
                Node remoteTempArchive = root.node("tmp/" + tempFile.getName(), null);
                getLog().info("Copying local file " + tempFile.getAbsolute() + " to " + remoteTempArchive.getPath());
                tempFile.copyFile(remoteTempArchive);
                getLog().info("Unpacking remote archive");
                String jarOutput = root.exec("cd " + Utils.linuxPath(remoteAppRoot) + ";jar xvf /tmp/" + tempFile.getName());
                getLog().info("Temp archive was unpacked. Removing temporary files");
                remoteTempArchive.deleteFile();
                tempFile.deleteFile();
                getLog().info("Changed file(s) were uploaded to the remote machine");
            }
            if (filesToRemove != null) {
                Utils.logFiles(getLog(), filesToRemove, "files must be deleted from the remote machine", Path::toString);

                for (Path curFile : filesToRemove) {
                    Path pathOfFileToDelete = remoteAppRoot.resolve(curFile);
                    String nameOfFileToDelete = Utils.linuxPathWithoutSlash(pathOfFileToDelete);
                    getLog().info("Removing " + Utils.linuxPath(pathOfFileToDelete));
                    root.node(nameOfFileToDelete, null).deleteFile();
                }
                getLog().info("Old file(s) were deleted from the remote machine");
            }
            if (filesToCopy != null || filesToRemove != null) {
                if (touchWebXml) {
                    root.exec("touch " + Utils.linuxPath(remoteAppRoot) + "/WEB-INF/web.xml");
                    getLog().info("web.xml was touched");
                } else {
                    getLog().info("web.xml was not touched because touchWebXml is " + touchWebXml);
                }
                SshNode nginxCacheNode = root.node(Utils.linuxPathWithoutSlash(nginxCache), null);
                if (!nginxCacheNode.exists()) {
                    getLog().info("Nginx cache dir " + Utils.linuxPath(nginxCache) + " doesn't exist");
                } else if (nginxCacheNode.list().isEmpty()) {
                    getLog().info("Nginx cache dir " + Utils.linuxPath(nginxCache) + " is empty");
                } else {
                    String purgeCommand = "sudo /usr/bin/find " + Utils.linuxPath(nginxCache) + " -mindepth 1 -delete";
                    getLog().info("Purging nginx cache dir with command " + purgeCommand);
                    root.exec(purgeCommand);
                    getLog().info("Done");
                }
            } else {
                getLog().info("Nothing to do: local files are identical to the remote machine's ones");
            }
            root.exec(postdeployScript);
        } catch (IOException | JSchException e) {
            getLog().error(e);
            e.printStackTrace();
            throw new MojoExecutionException("Error during execution of the deploy script", e);
        }
    }
}
