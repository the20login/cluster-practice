package com.github.the20login.test.utils;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.IOException;

public class GitUtils {
    private static final Object SYNC = new Object();
    private static volatile String REVISION;

    public static String getRevision() {
        if (REVISION != null) {
            return REVISION;
        }
        synchronized (SYNC) {
            if (REVISION != null) {
                return REVISION;
            }
            REVISION = extractRevision();

            return REVISION;
        }
    }

    private static String extractRevision() {
        //when run with gradle
        String systemPropertyRevision = System.getProperty("revision");
        if (systemPropertyRevision != null) {
            return systemPropertyRevision;
        }

        //when run with IDE
        //TODO: find less ugly solution
        try {
            Repository existingRepo = new FileRepositoryBuilder()
                    .findGitDir()
                    .build();
            StringBuilder revision = new StringBuilder();
            existingRepo.resolve("HEAD").copyTo(new char[40], revision);
            return revision.toString();
        } catch (IOException e) {
            throw new RuntimeException("Unable to extract git revision", e);
        }
    }
}
