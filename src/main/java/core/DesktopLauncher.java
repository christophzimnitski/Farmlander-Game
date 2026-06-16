package core;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

/**
 * Desktop (LWJGL3) entry point. Creates the OS window and starts libGDX, which
 * then drives {@link FarmlanderGame}. Run via: ./gradlew runGdx
 */
public class DesktopLauncher {
    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle(Config.TITLE);
        // Size the window to the display, like the Swing version (which used the
        // full screen size). Keeps the same amount of world visible on screen.
        com.badlogic.gdx.Graphics.DisplayMode dm = Lwjgl3ApplicationConfiguration.getDisplayMode();
        config.setWindowedMode(dm.width, dm.height);
        config.useVsync(Config.VSYNC);
        new Lwjgl3Application(new FarmlanderGame(), config);
    }
}
