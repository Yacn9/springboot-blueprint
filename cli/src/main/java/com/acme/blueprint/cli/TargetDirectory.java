package com.acme.blueprint.cli;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * The directory a project is generated into: it must be new or empty, nothing
 * may be written outside it, and a failed run leaves no trace of itself.
 */
final class TargetDirectory {

    private final Path path;
    private final boolean existedBefore;

    private TargetDirectory(Path path, boolean existedBefore) {
        this.path = path;
        this.existedBefore = existedBefore;
    }

    /**
     * @param requested a directory that does not exist yet, or is empty
     * @throws ProjectGenerationException if it is a file, or holds anything
     */
    static TargetDirectory claim(Path requested) {
        Path path = requested.toAbsolutePath().normalize();
        boolean exists = Files.exists(path);
        if (exists) {
            requireEmptyDirectory(path);
        }
        return new TargetDirectory(path, exists);
    }

    Path path() {
        return path;
    }

    void create() throws IOException {
        Files.createDirectories(path);
    }

    /** Resolves a path from the template, refusing any that would leave the directory ("zip slip"). */
    Path resolve(String relativePath) {
        Path file = path.resolve(relativePath).normalize();
        if (!file.startsWith(path)) {
            throw new ProjectGenerationException("The template contains a path outside the project: " + relativePath);
        }
        return file;
    }

    /**
     * Removes everything this run wrote, and the directory itself only if this
     * run created it. Directories above it that {@link #create} had to make
     * stay. Best effort: the failure being reported matters more.
     */
    void discard() {
        if (!Files.isDirectory(path)) {
            return;
        }
        try (Stream<Path> tree = Files.walk(path)) {
            tree.sorted(Comparator.reverseOrder())
                    .filter(entry -> !existedBefore || !entry.equals(path))
                    .forEach(TargetDirectory::deleteQuietly);
        } catch (IOException | UncheckedIOException ignored) {
            // Nothing more to do.
        }
    }

    private static void requireEmptyDirectory(Path path) {
        if (!Files.isDirectory(path)) {
            throw new ProjectGenerationException("'%s' exists and is not a directory.".formatted(path));
        }
        try (Stream<Path> children = Files.list(path)) {
            if (children.findAny().isPresent()) {
                throw new ProjectGenerationException(
                        "'%s' already exists and is not empty. Choose another project name or parent directory.".formatted(path));
            }
        } catch (IOException failure) {
            throw new ProjectGenerationException("Cannot read '%s': %s".formatted(path, failure), failure);
        }
    }

    private static void deleteQuietly(Path entry) {
        try {
            Files.deleteIfExists(entry);
        } catch (IOException ignored) {
            // Best effort.
        }
    }
}
