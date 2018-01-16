package com.indigobyte.deploy;

import org.jetbrains.annotations.NotNull;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class Utils {
    public static void createAchive(List<Path> filenames, Path basePath, String outFilename) throws IOException {
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
                System.out.println(name + " is a directory");
            }
        }
        return crcMap;
    }
}
