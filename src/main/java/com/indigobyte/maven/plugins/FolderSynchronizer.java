package com.indigobyte.maven.plugins;

import com.indigobyte.deploy.LocalAnalyzer;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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
            analyzer = new LocalAnalyzer(getLog(), sourceFolderPath, fileWithChecksums);
        } catch (IOException e) {
            throw new MojoExecutionException("Unable to initialize folder synchronization", e);
        }
        Set<Path> filesToRemove = analyzer.getFilesToRemove();
        for (Path path : filesToRemove) {
            try {
                getLog().debug("Deleting path " + path);
                Files.delete(path);
            } catch (IOException e) {
                throw new MojoExecutionException("Unable to remove file / folder " + path, e);
            }
        }

        Path destFolderPath = Paths.get(this.destFolder);
        Set<Path> filesToCopy = analyzer.getFilesToCopy();
        for (Path path : filesToCopy) {
            Path relativeFileName = sourceFolderPath.relativize(path).normalize();
            Path targetPath = destFolderPath.resolve(relativeFileName).normalize();
            if (path.toFile().isFile()) {
                if (targetPath.toFile().isDirectory()) {
                    try {
                        getLog().debug("Deleting path " + path);
                        Files.delete(targetPath);
                    } catch (IOException e) {
                        throw new MojoExecutionException("Unable to remove target folder " + path + " in order to replace it with file " + path, e);
                    }
                }
                try {
                    targetPath.getParent().toFile().mkdirs();
                    getLog().debug("Copying file " + path + " to " + targetPath);
                    Files.copy(path, targetPath, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new MojoExecutionException("Unable to copy file " + path + " to " + targetPath, e);
                }
            } else {
                getLog().debug("Creating folder " + path + " as " + targetPath);
                targetPath.toFile().mkdirs();
            }
        }
        try {
            analyzer.writeNewChecksums(fileWithChecksums);
        } catch (IOException e) {
            throw new MojoExecutionException("Unable to save new checksums in file " + fileWithChecksums, e);
        }
    }
}
