package com.indigobyte.deploy;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

class MyFileIterator extends SimpleFileVisitor<Path> {
    private final List<Path> files = new ArrayList<>();

    public static List<Path> getAllFilesAndFoldersRecursively(Path path) throws IOException {
        MyFileIterator fileIterator = new MyFileIterator();
        Files.walkFileTree(path, fileIterator);
        return fileIterator.files;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
        files.add(file);
        return FileVisitResult.CONTINUE;
    }
}
