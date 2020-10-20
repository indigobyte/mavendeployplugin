package com.indigobyte.deploy;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

class MyFileIterator extends SimpleFileVisitor<Path> {
    private final List<Path> files = new ArrayList<>();

    public static List<Path> getAllFilesAndFoldersRecursively(Path path) throws IOException {
        MyFileIterator fileIterator = new MyFileIterator();
        Files.walkFileTree(path, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, fileIterator);
        return fileIterator.files;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
        files.add(file);
        return FileVisitResult.CONTINUE;
    }
}
