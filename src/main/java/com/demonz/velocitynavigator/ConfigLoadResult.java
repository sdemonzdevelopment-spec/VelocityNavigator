package com.demonz.velocitynavigator;

import java.nio.file.Path;
import java.util.List;

public record ConfigLoadResult(
        Config config,
        List<String> warnings,
        boolean createdDefault,
        boolean migrated,
        Integer previousVersion,
        Path backupPath,
        boolean normalized
) {
}

