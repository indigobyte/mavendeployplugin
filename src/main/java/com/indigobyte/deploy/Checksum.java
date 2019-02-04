package com.indigobyte.deploy;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

public class Checksum implements Serializable, Comparable<Checksum> {
    private static final long serialVersionUID = 2L;
    @NotNull
    private final String filePath;
    private final boolean folder;
    private final long lastModified;
    @Nullable
    private final String digest;
//    @Nullable
//    private final TreeMap<String, Long> jarFilesCrc32;

    public Checksum(@NotNull Path file, @NotNull Path baseFolder, @Nullable Checksum oldChecksum, @NotNull AtomicInteger checksumsCalculated) throws IOException {
        filePath = extractFilePath(file, baseFolder);
        File file1 = file.toFile();
        folder = file1.isDirectory();
        lastModified = file1.lastModified();
        if (folder) {
            digest = null;
        } else {
            if (oldChecksum != null && !oldChecksum.folder && oldChecksum.lastModified == lastModified) {
                digest = oldChecksum.digest;
            } else if (!file.getName(file.getNameCount() - 1).toString().endsWith(".jar")) {
                digest = Utils.getDigest(file);
                checksumsCalculated.incrementAndGet();
                //jarFilesCrc32 = null;
            } else {
                TreeMap<String, Long> crc32 = Utils.getCrc32OfJarFile(file);
                try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    try (ObjectOutputStream ois = new ObjectOutputStream(baos)) {
                        ois.writeObject(crc32);
                    }
                    try (ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray())) {
                        digest = Utils.getDigest(bais);
                        checksumsCalculated.incrementAndGet();
                    }
                }
//                jarFilesCrc32 = null;
            }
        }
    }

    @NotNull
    public static String extractFilePath(@NotNull Path file, @NotNull Path baseFolder) {
        return Utils.linuxPath(baseFolder.normalize().toAbsolutePath().relativize(file.normalize().toAbsolutePath()).toString());
    }

    @NotNull
    public Path getPath() {
        return Paths.get(filePath);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(filePath);
        if (folder) {
            sb.append(", folder");
        } else {
            sb.append(", file, ");
            if (digest != null) {
                sb.append(", digest = ").append(digest);
            }
        }
        return sb.toString();
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
        return folder == checksum.folder &&
                filePath.equals(checksum.filePath) &&
                Objects.equals(digest, checksum.digest)
                //&&  Objects.equals(jarFilesCrc32, checksum.jarFilesCrc32)
                ;
    }

    @Override
    public int hashCode() {
        return Objects.hash(filePath, folder, digest
                //, jarFilesCrc32
        );
    }

    @Override
    public int compareTo(@NotNull Checksum o) {
        return Comparator
                .comparing(Checksum::getFilePath)
                .thenComparing(Checksum::isFolder)
                .thenComparing(Checksum::getDigest, Comparator.nullsFirst(String::compareTo))
                //.thenComparing(Checksum::getJarFilesCrc32, Comparator.nullsFirst(Comparator.comparing(Utils::toJson)))
                .compare(this, o);
    }

    @NotNull
    public String getFilePath() {
        return filePath;
    }

    public boolean isFolder() {
        return folder;
    }

    @Nullable
    public String getDigest() {
        return digest;
    }

//    @Nullable
//    public TreeMap<String, Long> getJarFilesCrc32() {
//        return jarFilesCrc32;
//    }
}
