package com.indigobyte.deploy;

import org.jetbrains.annotations.NotNull;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
}
