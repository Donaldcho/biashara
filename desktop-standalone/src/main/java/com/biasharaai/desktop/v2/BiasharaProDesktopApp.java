package com.biasharaai.desktop.v2;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.nio.file.Path;

public final class BiasharaProDesktopApp {
    private BiasharaProDesktopApp() {
    }

    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");

        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // The app still works with Swing's fallback look and feel.
            }

            Path dataDir = Path.of(System.getProperty("user.home"), ".biasharaai-desktop-pro");
            DesktopStore store = new DesktopStore(dataDir);
            AppState state = store.load();
            DesktopShell shell = new DesktopShell(store, state);
            shell.show();
        });
    }
}
