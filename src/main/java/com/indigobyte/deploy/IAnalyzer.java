package com.indigobyte.deploy;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.TreeSet;

public interface IAnalyzer {
    @NotNull
    TreeSet<Path> getFilesToCopy();

    @NotNull
    TreeSet<Path> getFilesToRemove();

    void writeNewChecksums(@NotNull Path fileWithChecksums) throws IOException;
}
