package com.indigobyte.deploy;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

class MyFileIterator extends SimpleFileVisitor<Path> {
    private Path path;
    private Analyzer analyzer;

    public MyFileIterator(Path path, Analyzer analyzer) {
        this.path = path;
        this.analyzer = analyzer;
    }

    @Override
    public FileVisitResult visitFile(Path file,
                                     BasicFileAttributes attributes) throws IOException {
        analyzer.addFile(file);
        return FileVisitResult.CONTINUE;
    }

    //    @Override
//    public FileVisitResult preVisitDirectory(Path dir,
//                                             BasicFileAttributes attributes) throws IOException {
//        return FileVisitResult.CONTINUE;
//    }
    public void iterate() throws java.io.IOException {
        Files.walkFileTree(path, this);
    }
}

public class Analyzer {
    private Path resourcePath;
    private Map<Path, String> checksumMap;
    private List<Path> filesToCopy;
    private List<Path> filesToRemove;
    private List<String> referenceChecksums;

    public Analyzer(Path resourcePath, List<String> referenceChecksums) throws NoSuchAlgorithmException, IOException {
        this.resourcePath = resourcePath;
        this.referenceChecksums = referenceChecksums;
        checksumMap = new HashMap<>();
        generateChecksums();
        findChangedFiles();
    }

    public static String getHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();

        for (byte aByte : bytes) {
            result.append(Integer.toString((aByte & 0xff) + 0x100, 16).substring(1));
        }
        return result.toString();
    }

    private String getDigest(Path filePath) throws IOException {
        byte[] buffer = new byte[8192];
        MessageDigest md = null;
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        InputStream is = Files.newInputStream(filePath);
        try (DigestInputStream dis = new DigestInputStream(is, md)) {
            while (dis.read(buffer) != -1) ;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return getHex(md.digest());
    }

    public void addFile(Path filePath) throws IOException {
        checksumMap.put(filePath.normalize(), getDigest(filePath));
    }

    private void generateChecksums() throws IOException {
        MyFileIterator fileIterator = new MyFileIterator(resourcePath, this);
        fileIterator.iterate();
    }

    private void findChangedFiles() throws IOException {
        filesToCopy = new ArrayList<>();
        filesToRemove = new ArrayList<>();
        Set<Path> remotePaths = new HashSet<>();
        for (String curLine: referenceChecksums) {
            int firstSpacePos = curLine.indexOf(' ');
            if (firstSpacePos == -1)
                throw new IllegalArgumentException("Space separator was not found in " + curLine);
            String digest = curLine.substring(0, firstSpacePos);
            String fileName = curLine.substring(firstSpacePos).trim();
            Path filePath = resourcePath.resolve(fileName).normalize();
            remotePaths.add(filePath);
            if (checksumMap.containsKey(filePath)) {
                if (!checksumMap.get(filePath).equals(digest))
                    filesToCopy.add(filePath);
            } else {
                filesToRemove.add(filePath);
            }
        }
        //Find files which do not exist in remote location
        Set<Path> localPaths = new HashSet<>(checksumMap.keySet());
        localPaths.removeAll(remotePaths);
        filesToCopy.addAll(localPaths);
    }

    public List<Path> getFilesToCopy() throws IOException {
        if (filesToCopy.isEmpty())
            return null;
        return new ArrayList<>(filesToCopy);
    }

    public List<Path> getFilesToRemove() {
        if (filesToRemove.isEmpty())
            return null;
        return filesToRemove.stream()
                .map(p -> resourcePath.relativize(p))
                .collect(Collectors.toList());
    }

}
