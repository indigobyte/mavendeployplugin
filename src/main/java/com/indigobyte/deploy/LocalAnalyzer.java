package com.indigobyte.deploy;

import org.apache.maven.plugin.logging.Log;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

public class LocalAnalyzer implements IAnalyzer {
    @NotNull
    private final Set<Path> existingFiles;
    @NotNull
    private final TreeSet<Checksum> oldChecksums;
    @NotNull
    private final TreeSet<Checksum> newChecksums;
    @NotNull
    private final Path sourceFolder;

    public LocalAnalyzer(@NotNull Log log, @NotNull Path sourceFolder, @Nullable byte[] oldChecksumBytes) throws IOException {
        if (oldChecksumBytes != null) {
            log.info("Reading old checksums");
            TreeSet<Checksum> oldChecksums;
            try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(oldChecksumBytes))) {
                oldChecksums = (TreeSet<Checksum>) ois.readObject();
            } catch (IOException | ClassNotFoundException e) {
                log.warn("Unable to read old checksums, skipping", e);
                oldChecksums = new TreeSet<>();
            }
            this.oldChecksums = oldChecksums;

        } else {
            log.info("No old checksums found");
            oldChecksums = new TreeSet<>();
        }

        Map<String, Checksum> oldChecksumMap = oldChecksums.stream()
                .collect(Collectors.toMap(
                        Checksum::getFilePath,
                        Function.identity()
                ));

        log.info("Searching for existing files at " + sourceFolder);
        existingFiles = Collections.unmodifiableSet(MyFileIterator.getAllFilesAndFoldersRecursively(sourceFolder).stream()
                .filter(path -> !path.getFileName().toString().equals(".gitignore"))
                .collect(Collectors.toSet())
        );
        log.info("Calculating checksums for " + existingFiles.size() + " files");
        TreeSet<Checksum> tempNewChecksums = new TreeSet<>();
        AtomicInteger checksumsCalculated = new AtomicInteger();
        for (Path path : existingFiles) {
            tempNewChecksums.add(new Checksum(path, sourceFolder, oldChecksumMap.get(Checksum.extractFilePath(path, sourceFolder)), checksumsCalculated));
        }
        newChecksums = tempNewChecksums;
        this.sourceFolder = sourceFolder;
        log.info("Checksum calculation complete. Actual checksums calculated: " + checksumsCalculated.get() + ", old checksums used: " + (existingFiles.size() - checksumsCalculated.get()));
    }

    @Override
    @NotNull
    public TreeSet<Path> getFilesToCopy() {
        TreeSet<Checksum> filesToCopy = new TreeSet<>(newChecksums);
        filesToCopy.removeAll(oldChecksums);
        TreeSet<Path> result = new TreeSet<>();
        for (Checksum checksum : filesToCopy) {
            result.add(checksum.getPath());
        }
        return result;
    }

    @Override
    @NotNull
    public TreeSet<Path> getFilesToRemove() {
        TreeSet<Path> filesToRemove = new TreeSet<>();
        for (Checksum checksum : oldChecksums) {
            filesToRemove.add(checksum.getPath());
        }
        for (Path existingPath : existingFiles) {
            filesToRemove.remove(sourceFolder.toAbsolutePath().relativize(existingPath.toAbsolutePath()));
        }
        return filesToRemove;
    }

    public void writeNewChecksums(@NotNull Path fileWithChecksums) throws IOException {
        fileWithChecksums.getParent().toFile().mkdirs();
        try (ObjectOutputStream ois = new ObjectOutputStream(new FileOutputStream(fileWithChecksums.toFile(), false))) {
            ois.writeObject(newChecksums);
        }
    }
}
