package com.indigobyte.deploy;

import org.apache.maven.plugin.logging.Log;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;

import static com.indigobyte.deploy.Utils.MAX_SHOW_FILE_COUNT;

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
    private Map<Path, String> localChecksums = new HashMap<>();
    private Map<Path, Map<String, Long>> localCrcs = new HashMap<>();
    private Set<Path> filesToCopy = new TreeSet<>();
    private Set<Path> filesToRemove = new TreeSet<>();
    private List<String> remoteChecksums;
    private Map<String, Map<String, Long>> remoteCrcs;
    private Log log;

    public Analyzer(Log log, Path localResourcePath, List<String> remoteChecksums, Map<String, Map<String, Long>> remoteCrcs) throws IOException {
        this.log = log;
        this.resourcePath = localResourcePath;
        this.remoteChecksums = remoteChecksums;
        this.remoteCrcs = remoteCrcs;
        generateChecksums();
        findChangedFiles();
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
            log.error("Error during calculation of digest of file " + filePath, e);
        }
        return Utils.getHex(md.digest());
    }

    public void addFile(Path filePath) throws IOException {
        if (filePath.getFileName().toString().toLowerCase().endsWith(".jar"))
            localCrcs.put(filePath.normalize(), Utils.getCrc32OfJarFile(filePath));
        else
            localChecksums.put(filePath.normalize(), getDigest(filePath));
    }

    private void generateChecksums() throws IOException {
        new MyFileIterator(resourcePath, this).iterate();
    }

    private void findChangedFiles() {
        //Check checksums of non-JAR files
        {
            Set<Path> remotePaths = new HashSet<>();
            for (String curLine : remoteChecksums) {
                int firstSpacePos = curLine.indexOf(' ');
                if (firstSpacePos == -1)
                    throw new IllegalArgumentException("Space separator was not found in " + curLine);
                String checksumOfRemoteFile = curLine.substring(0, firstSpacePos);
                String fileName = curLine.substring(firstSpacePos).trim();
                Path filePath = resourcePath.resolve(fileName).normalize();
                remotePaths.add(filePath);
                if (localChecksums.containsKey(filePath)) {
                    if (!Objects.equals(localChecksums.get(filePath), checksumOfRemoteFile))
                        filesToCopy.add(filePath);
                } else {
                    log.info("file " + fileName + " will be removed from remote host");
                    filesToRemove.add(filePath);
                }
            }
            //Find files which do not exist in remote location
            Set<Path> localPaths = new HashSet<>(localChecksums.keySet());
            localPaths.removeAll(remotePaths);
            Utils.logFiles(log, localPaths, "Adding non-existing file", Path::toString);
            filesToCopy.addAll(localPaths);
        }
        //Check CRCs of JAR files
        {
            Set<Path> remotePaths = new HashSet<>();
            for (Map.Entry<String, Map<String, Long>> remoteJarEntry : remoteCrcs.entrySet()) {
                Map<String, Long> crcsOfFilesInsideRemoteJarFile = remoteJarEntry.getValue();
                String fileName = remoteJarEntry.getKey();
                Path filePath = resourcePath.resolve(fileName).normalize();
                remotePaths.add(filePath);
                if (localCrcs.containsKey(filePath)) {
                    Map<String, Long> crcsOfFilesInsideLocalJarFile = localCrcs.get(filePath);
                    if (!Objects.equals(crcsOfFilesInsideLocalJarFile, crcsOfFilesInsideRemoteJarFile)) {
                        log.info("archive " + fileName + " contains files that were changed locally");
                        Set<String> newFiles = new TreeSet<>(crcsOfFilesInsideLocalJarFile.keySet());
                        newFiles.removeAll(crcsOfFilesInsideRemoteJarFile.keySet());
                        Set<String> filesToRemove = new TreeSet<>(crcsOfFilesInsideRemoteJarFile.keySet());
                        filesToRemove.removeAll(crcsOfFilesInsideLocalJarFile.keySet());
                        Set<String> filesProbablyModified = new TreeSet<>(crcsOfFilesInsideRemoteJarFile.keySet());
                        filesProbablyModified.removeAll(filesToRemove);
                        Map<String, CrcHolder> modifiedFiles = new TreeMap<>();
                        for (String fileToCheck : filesProbablyModified) {
                            long oldCrc = crcsOfFilesInsideRemoteJarFile.get(fileToCheck);
                            long newCrc = crcsOfFilesInsideLocalJarFile.get(fileToCheck);
                            if (oldCrc != newCrc)
                                modifiedFiles.put(fileToCheck, new CrcHolder(oldCrc, newCrc));
                        }
                        Utils.logFiles(log, newFiles, "new files in archive");
                        Utils.logFiles(log, filesToRemove, "files to remove from archive");
                        Utils.logFiles(log, modifiedFiles.entrySet(), "files modified in archive", Map.Entry::getKey);

                        filesToCopy.add(filePath);
                    } else {
                        log.debug("archive " + fileName + " didn't change");
                    }
                } else {
                    log.info("archive " + fileName + " will be removed from remote host");
                    filesToRemove.add(filePath);
                }
            }
            //Find files which do not exist in remote location
            Set<Path> localPaths = new HashSet<>(localCrcs.keySet());
            localPaths.removeAll(remotePaths);
            Utils.logFiles(log, localPaths, "Adding non-existing jar", Path::toString);
            filesToCopy.addAll(localPaths);
        }
    }

    public @Nullable Set<Path> getFilesToCopy() {
        if (filesToCopy.isEmpty())
            return null;
        return new TreeSet<>(filesToCopy);
    }

    public @Nullable Set<Path> getFilesToRemove() {
        if (filesToRemove.isEmpty())
            return null;
        return filesToRemove.stream()
                .map(p -> resourcePath.relativize(p))
                .collect(Collectors.toCollection(TreeSet::new));
    }

}
