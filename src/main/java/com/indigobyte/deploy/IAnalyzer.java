package com.indigobyte.deploy;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

public interface IAnalyzer {
    @Nullable Set<Path> getFilesToCopy();

    @Nullable Set<Path> getFilesToRemove();
}
