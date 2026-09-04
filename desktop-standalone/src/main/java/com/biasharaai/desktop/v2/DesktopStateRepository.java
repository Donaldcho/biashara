package com.biasharaai.desktop.v2;

import java.nio.file.Path;

interface DesktopStateRepository {
    boolean exists();

    AppState load(Path dataDir);

    void save(AppState state);

    void backupTo(Path target);
}
