package com.indigobyte.deploy;

//import com.fasterxml.jackson.core.JsonProcessingException;
//import com.fasterxml.jackson.databind.ObjectMapper;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.CompressionLevel;
import net.lingala.zip4j.model.enums.CompressionMethod;
import org.apache.maven.plugin.logging.Log;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class Utils {
    public static final int MAX_SHOW_FILE_COUNT = 10;

    public static void createAchive(
            @NotNull Set<Path> filenames,
            @NotNull Path basePath,
            @NotNull String outFilename
    ) throws IOException {
        Files.deleteIfExists(Paths.get(outFilename)); // We have to delete the empty file, otherwise ZipFile constructor will fail on empty file because it'll think it's an invalid ZIP file
        ZipFile zipFile = new ZipFile(outFilename);
        for (Path filePath : filenames) {
            String fileToCopy = filePath.toString().replace('\\', '/');
            ZipParameters zipParameters = new ZipParameters();
            zipParameters.setFileNameInZip(fileToCopy);
            zipParameters.setCompressionLevel(CompressionLevel.ULTRA);
            zipParameters.setCompressionMethod(CompressionMethod.DEFLATE);
            zipParameters.setOverrideExistingFilesInZip(false);
            zipFile.addFile(basePath.resolve(fileToCopy).toFile(), zipParameters);
        }
        if (!zipFile.isValidZipFile()) {
            throw new IllegalStateException("File " + outFilename + " is not a valid ZIP file");
        }
    }

    @NotNull
    public static String linuxPath(@NotNull Path path) {
        return linuxPath(path.toString());
    }

    @NotNull
    public static String linuxPath(@NotNull String path) {
        return path.replace('\\', '/');
    }

    public static String getHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();

        for (byte aByte : bytes) {
            result.append(Integer.toString((aByte & 0xff) + 0x100, 16).substring(1));
        }
        return result.toString();
    }

    @NotNull
    public static TreeMap<String, Long> getCrc32OfJarFile(@NotNull Path filePath) throws IOException {
        TreeMap<String, Long> crcMap = new TreeMap<>();
        JarFile jf = new JarFile(filePath.toFile());
        Enumeration<JarEntry> e = jf.entries();
        while (e.hasMoreElements()) {
            JarEntry je = e.nextElement();
            if (!je.isDirectory()) {
                String name = je.getName();
                long crc = je.getCrc();
                if (crcMap.put(name, crc) != null) {
                    throw new IllegalStateException("File " + name + " is duplicated in " + filePath);
                }
            }
        }
        return crcMap;
    }

    public static <T> void logFiles(@NotNull Log log, @NotNull Collection<T> files, @NotNull String header, @NotNull PrintCallback<T> printer) {
        if (files.isEmpty()) {
            return;
        }
        log.info("-------- " + header + ": " + files.size() + " --------");
        List<T> filesWrapped = new ArrayList<>(files);
        for (int i = 0; i < filesWrapped.size(); ++i) {
            if (i < MAX_SHOW_FILE_COUNT) {
                log.info(printer.getData(filesWrapped.get(i)));
            } else {
                log.debug(printer.getData(filesWrapped.get(i)));
            }
        }
    }

    public static void logFiles(@NotNull Log log, @NotNull Collection<String> files, @NotNull String header) {
        logFiles(log, files, header, f -> f);
    }

    @NotNull
    public static String getDigest(@NotNull Path filePath) throws IOException {
        if (filePath.toFile().exists() && filePath.toFile().isFile()) {
            try (InputStream is = Files.newInputStream(filePath)) {
                return getDigest(is);
            }
        } else {
            return "It's a folder";
        }
    }

    @NotNull
    public static String getDigest(@NotNull InputStream is) throws IOException {
        byte[] buffer = new byte[8192];
        MessageDigest md = null;
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        try (DigestInputStream dis = new DigestInputStream(is, md)) {
            while (dis.read(buffer) != -1) {
            }
            return Utils.getHex(md.digest());
        }
    }

//    @NotNull
//    public static String toJson(@Nullable Object value) {
//        try {
//            return (new ObjectMapper()).writeValueAsString(value);
//        } catch (JsonProcessingException e) {
//            throw new RuntimeException(e);
//        }
//    }

    @FunctionalInterface
    public interface PrintCallback<T> {
        String getData(@NotNull T obj);
    }

}
