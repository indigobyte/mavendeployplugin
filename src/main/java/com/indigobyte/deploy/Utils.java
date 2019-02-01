package com.indigobyte.deploy;

//import com.fasterxml.jackson.core.JsonProcessingException;
//import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.maven.plugin.logging.Log;
import org.jetbrains.annotations.NotNull;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class Utils {
    public static final int MAX_SHOW_FILE_COUNT = 10;

    public static void createAchive(@NotNull Set<Path> filenames, @NotNull Path basePath, @NotNull String outFilename) throws IOException {
        ZipOutputStream zipFile = new ZipOutputStream(new FileOutputStream(outFilename));
        for (Path fileToCopy : filenames) {
            String filename = fileToCopy.toString().replace('\\', '/');
//                if (VERBOSE)
//                    System.out.println("New file: " + filename);
            ZipEntry e = new ZipEntry(filename);
            zipFile.putNextEntry(e);
            zipFile.write(Files.readAllBytes(basePath.resolve(fileToCopy)));
            zipFile.closeEntry();
        }
        zipFile.close();
    }

    public static String linuxPath(@NotNull Path path) {
        return path.toString().replace('\\', '/');
    }

    public static String linuxPathWithoutSlash(@NotNull Path path) {
        String result = linuxPath(path);
        if (result.startsWith("/")) {
            return result.substring(1);
        }
        return result;
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
        Enumeration e = jf.entries();
        while (e.hasMoreElements()) {
            JarEntry je = (JarEntry) e.nextElement();
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
            InputStream is = Files.newInputStream(filePath);
            getDigest(is);
        }
        return "";
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
