package com.indigobyte.maven.plugins;

import com.indigobyte.deploy.LocalAnalyzer;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.IOException;
import java.nio.file.*;
import java.util.Set;

@Mojo(name = "sync-folder")
public class FolderSynchronizer extends AbstractMojo {
    @Parameter(property = "syncFolder.sourceFolder", required = true)
    private String sourceFolder;

    @Parameter(property = "syncFolder.destFolder", required = true)
    private String destFolder;

    @Parameter(property = "syncFolder.checksumFile", required = true)
    private String checksumFile;

    public void execute() throws MojoExecutionException, MojoFailureException {
        getLog().info("FolderSynchronizer mojo has started");
        getLog().info("Synchronizing folder " + destFolder + " with " + sourceFolder + " based on checksums from file " + checksumFile);

        Path sourceFolderPath = Paths.get(this.sourceFolder);
        Path fileWithChecksums = Paths.get(checksumFile);
        LocalAnalyzer analyzer = null;
        try {
            byte[] oldChecksumBytes = null;
            try {
                oldChecksumBytes = Files.readAllBytes(fileWithChecksums);
            } catch (NoSuchFileException ignored) {
            } catch (Throwable e) {
                getLog().warn("Unable to read old checksum bytes from  " + fileWithChecksums, e);
            }

            analyzer = new LocalAnalyzer(getLog(), sourceFolderPath, oldChecksumBytes);
        } catch (IOException e) {
            throw new MojoExecutionException("Unable to initialize folder synchronization", e);
        }

        Path destFolderPath = Paths.get(this.destFolder);

        Set<Path> filesToRemove = analyzer.getFilesToRemove();
        for (Path path : filesToRemove) {
            Path targetPath = destFolderPath.resolve(path).normalize();
            if (targetPath.toFile().exists()) {
                try {
                    getLog().debug("Deleting path " + targetPath);
                    Files.delete(targetPath);
                } catch (IOException e) {
                    throw new MojoExecutionException("Unable to remove file / folder " + targetPath, e);
                }
            } else {
                getLog().debug("Path " + targetPath + " is already deleted by third party");
            }
        }

        Set<Path> filesToCopy = analyzer.getFilesToCopy();
        for (Path path : filesToCopy) {
            Path sourcePath = sourceFolderPath.resolve(path).normalize();
            Path targetPath = destFolderPath.resolve(path).normalize();
            if (sourcePath.toFile().isFile()) {
                if (targetPath.toFile().isDirectory()) {
                    try {
                        getLog().debug("Deleting path " + targetPath);
                        Files.delete(targetPath);
                    } catch (IOException e) {
                        throw new MojoExecutionException("Unable to remove target folder " + targetPath + " in order to replace it with file " + sourcePath, e);
                    }
                }
                try {
                    targetPath.getParent().toFile().mkdirs();
                    getLog().debug("Copying file " + sourcePath + " to " + targetPath);
                    Files.copy(sourcePath, targetPath, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new MojoExecutionException("Unable to copy file " + sourcePath + " to " + targetPath, e);
                }
            } else {
                getLog().debug("Creating folder " + path + " as " + targetPath);
                targetPath.toFile().mkdirs();
            }
        }
        if (filesToCopy.isEmpty() && filesToRemove.isEmpty()) {
            getLog().info("Resources didn't change");
        } else {
            getLog().info("Files copied: " + filesToCopy.size() + ", files removed: " + filesToRemove.size());
        }
        try {
            analyzer.writeNewChecksums(fileWithChecksums);
        } catch (IOException e) {
            throw new MojoExecutionException("Unable to save new checksums in file " + fileWithChecksums, e);
        }
    }
}
