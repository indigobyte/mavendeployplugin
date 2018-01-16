package com.indigobyte.deploy;

import org.apache.maven.plugin.logging.Log;
import org.jetbrains.annotations.NotNull;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
            Path relativeFileName = basePath.relativize(fileToCopy);
            String filename = relativeFileName.toString().replace('\\', '/');
//                if (VERBOSE)
//                    System.out.println("New file: " + filename);
            ZipEntry e = new ZipEntry(filename);
            zipFile.putNextEntry(e);
            zipFile.write(Files.readAllBytes(fileToCopy));
            zipFile.closeEntry();
        }
        zipFile.close();
    }
    public static String linuxPath(@NotNull Path path) {
        return path.toString().replace('\\', '/');
    }
    public static String linuxPathWithoutSlash(@NotNull Path path)
    {
        String result = linuxPath(path);
        if (result.startsWith("/"))
            return result.substring(1);
        return result;
    }

    public static String getHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();

        for (byte aByte : bytes) {
            result.append(Integer.toString((aByte & 0xff) + 0x100, 16).substring(1));
        }
        return result.toString();
    }

    public static @NotNull SortedMap<String, Long> getCrc32OfJarFile(@NotNull Path filePath) throws IOException {
        SortedMap<String, Long> crcMap = new TreeMap<>();
        JarFile jf = new JarFile(filePath.toFile());
        Enumeration e = jf.entries();
        while (e.hasMoreElements()) {
            JarEntry je = (JarEntry) e.nextElement();
            if (!je.isDirectory()) {
                String name = je.getName();
                long crc = je.getCrc();
                if (crcMap.put(name, crc) != null)
                    throw new IllegalStateException("File " + name + " is duplicated in " + filePath);
            }
        }
        return crcMap;
    }
    public static<T> void logFiles(@NotNull Log log, @NotNull Collection<T> files, @NotNull String header, @NotNull PrintCallback<T> printer) {
        log.info("-------- " + header + ": " + files.size() + " --------");
        List<T> filesWrapped = new ArrayList<>(files);
        for (int i = 0; i < filesWrapped.size(); ++i) {
            if (i < MAX_SHOW_FILE_COUNT)
                log.info(printer.getData(filesWrapped.get(i)));
            else
                log.debug(printer.getData(filesWrapped.get(i)));
        }
    }

    public static void logFiles(@NotNull Log log, @NotNull Collection<String> files, @NotNull String header) {
        logFiles(log, files, header, f -> f);
    }


    @FunctionalInterface
    public interface PrintCallback<T> {
        String getData(@NotNull T obj);
    }

}
