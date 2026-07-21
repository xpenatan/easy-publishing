package com.github.xpenatan.publish;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;

/** Merges prepared nested repositories into a final publication directory. */
public abstract class PrepareRepositoryTask extends DefaultTask {
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getSourceDirectories();

    @Internal
    public abstract DirectoryProperty getDestinationDirectory();

    @Input
    public abstract Property<Boolean> getNormalizeSnapshots();

    @Inject
    protected abstract FileSystemOperations getFileSystemOperations();

    @TaskAction
    public void prepare() {
        if (!getSourceDirectories().isEmpty()) {
            getFileSystemOperations().copy(spec -> {
                spec.from(getSourceDirectories());
                spec.into(getDestinationDirectory());
            });
        }
        if (getNormalizeSnapshots().get()) {
            SnapshotRepositoryNormalizer.normalize(getDestinationDirectory().get().getAsFile());
        }
    }
}
