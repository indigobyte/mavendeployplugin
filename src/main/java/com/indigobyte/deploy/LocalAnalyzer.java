package com.indigobyte.deploy;

import org.apache.maven.plugin.logging.Log;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class LocalAnalyzer implements IAnalyzer {
    private final Set<Path> existingFiles;
    private final Set<Checksum> oldChecksums;
    private final Set<Checksum> newChecksums;

    public LocalAnalyzer(@NotNull Log log, @NotNull Path sourceFolder, @NotNull Path fileWithChecksums) throws IOException {
        existingFiles = Collections.unmodifiableSet(MyFileIterator.getAllFilesAndFoldersRecursively(sourceFolder).stream()
                .filter(path -> !path.getFileName().toString().equals(".gitignore"))
                .collect(Collectors.toSet())
        );
        Set<Checksum> tempNewChecksums = new HashSet<>();
        for (Path path : existingFiles) {
            tempNewChecksums.add(new Checksum(path));
        }
        newChecksums = Collections.unmodifiableSet(tempNewChecksums);

        if (fileWithChecksums.toFile().exists()) {
            Set<Checksum> oldChecksums = Collections.emptySet();
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(fileWithChecksums.toFile()))) {
                oldChecksums = Collections.unmodifiableSet((Set<Checksum>) ois.readObject());
            } catch (IOException | ClassNotFoundException e) {
                log.warn("Unable to read contents of " + fileWithChecksums + " file, skipping", e);
                oldChecksums = Collections.emptySet();
            }
            this.oldChecksums = oldChecksums;
        } else {
            oldChecksums = Collections.emptySet();
        }
    }

    @Override
    @Nullable
    public Set<Path> getFilesToCopy() {
        Set<Checksum> filesToCopy = new HashSet<>(newChecksums);
        filesToCopy.removeAll(oldChecksums);
        Set<Path> result = new HashSet<>();
        for (Checksum checksum : filesToCopy) {
            result.add(checksum.getPath());
        }
        return result;
    }

    @Override
    @Nullable
    public Set<Path> getFilesToRemove() {
        Set<Path> filesToRemove = new HashSet<>();
        for (Checksum checksum : oldChecksums) {
            filesToRemove.add(checksum.getPath());
        }
        filesToRemove.removeAll(existingFiles);
        return filesToRemove;
    }

    @NotNull
    public void writeNewChecksums(@NotNull Path fileWithChecksums) throws IOException {
        fileWithChecksums.getParent().toFile().mkdirs();
        try (ObjectOutputStream ois = new ObjectOutputStream(new FileOutputStream(fileWithChecksums.toFile(), false))) {
            ois.writeObject(newChecksums);
        }
    }
}
