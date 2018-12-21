package com.indigobyte.deploy;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

public class Checksum implements Serializable {
    private final String filePath;
    private final long lastModifiedDate;
    private final boolean folder;

    public Checksum(@NotNull Path file) {
        filePath = file.toAbsolutePath().normalize().toString();
        File file1 = file.toFile();
        lastModifiedDate = file1.lastModified();
        folder = file1.isDirectory();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Checksum checksum = (Checksum) o;
        return lastModifiedDate == checksum.lastModifiedDate &&
                folder == checksum.folder &&
                Objects.equals(filePath, checksum.filePath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(filePath, lastModifiedDate, folder);
    }

    @NotNull
    public Path getPath() {
        return Paths.get(filePath);
    }

    @Override
    public String toString() {
        return filePath +
                "[" + lastModifiedDate + "]" +
                (folder ? ", folder" : "");
    }
}
