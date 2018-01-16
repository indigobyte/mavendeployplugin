package com.indigobyte.deploy;

import org.apache.maven.plugin.logging.Log;

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
    private Map<Path, String> localChecksums;
    private Map<Path, Map<String, Long>> localCrcs;
    private List<Path> filesToCopy;
    private List<Path> filesToRemove;
    private List<String> remoteChecksums;
    private Map<String, Map<String, Long>> remoteCrcs;
    private Log log;

    public Analyzer(Log log, Path localResourcePath, List<String> remoteChecksums, Map<String, Map<String, Long>> remoteCrcs) throws NoSuchAlgorithmException, IOException {
        this.log = log;
        this.resourcePath = localResourcePath;
        this.remoteChecksums = remoteChecksums;
        this.remoteCrcs = remoteCrcs;
        localChecksums = new HashMap<>();
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

//    public long getCrc32(Path filePath) throws IOException {
//        try (CheckedInputStream in = new CheckedInputStream(new BufferedInputStream(new FileInputStream(filePath.toFile())), new CRC32())) {
//            final byte[] buf = new byte[4096];
//            while (in.read(buf) != -1) ;
//            return in.getChecksum().getValue();
//        } catch (IOException e) {
//            log.error("Error during crc32 calculation of file " + filePath);
//            throw e;
//        }
//    }

    public void addFile(Path filePath) throws IOException {
        if (filePath.endsWith(".jar")) {
            SortedMap<String, Long> jarCrc = Utils.getCrc32OfJarFile(filePath);
            localCrcs.put(filePath.normalize(), jarCrc);
        } else {
            localChecksums.put(filePath.normalize(), getDigest(filePath));
        }
    }

    private void generateChecksums() throws IOException {
        new MyFileIterator(resourcePath, this).iterate();
    }

    private void findChangedFiles() throws IOException {
        filesToCopy = new ArrayList<>();
        filesToRemove = new ArrayList<>();
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
                    if (!localChecksums.get(filePath).equals(checksumOfRemoteFile))
                        filesToCopy.add(filePath);
                } else {
                    filesToRemove.add(filePath);
                }
            }
            //Find files which do not exist in remote location
            Set<Path> localPaths = new HashSet<>(localChecksums.keySet());
            localPaths.removeAll(remotePaths);
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
                    if (!localCrcs.get(filePath).equals(crcsOfFilesInsideRemoteJarFile))
                        filesToCopy.add(filePath);
                } else {
                    filesToRemove.add(filePath);
                }
            }
            //Find files which do not exist in remote location
            Set<Path> localPaths = new HashSet<>(localCrcs.keySet());
            localPaths.removeAll(remotePaths);
            filesToCopy.addAll(localPaths);
        }
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
