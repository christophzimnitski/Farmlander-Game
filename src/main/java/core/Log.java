package core;

import java.io.PrintStream;

/**
 * A tiny hand-rolled leveled logger — deliberately NOT libGDX's {@code Gdx.app.log}, so it works the same
 * in the running game, in headless tests, and in one-off tools (where {@code Gdx.app} is null and would
 * NPE). Each call carries a short {@code tag} (usually the class/subsystem) and a level; messages at or
 * above {@link #setLevel the threshold} print, warnings and errors to {@code stderr}, the rest to
 * {@code stdout}. Swap the sink or format here in one place if richer logging is ever needed.
 */
public final class Log {
    public enum Level { DEBUG, INFO, WARN, ERROR, OFF }

    private static Level threshold = Level.INFO;

    private Log() {}

    /** Messages below this level are dropped. {@code OFF} silences everything. */
    public static void setLevel(Level level) { threshold = level; }

    public static void debug(String tag, String message) { log(Level.DEBUG, tag, message, null); }

    public static void info(String tag, String message) { log(Level.INFO, tag, message, null); }

    public static void warn(String tag, String message) { log(Level.WARN, tag, message, null); }

    public static void warn(String tag, String message, Throwable cause) { log(Level.WARN, tag, message, cause); }

    public static void error(String tag, String message, Throwable cause) { log(Level.ERROR, tag, message, cause); }

    private static void log(Level level, String tag, String message, Throwable cause) {
        if (level.ordinal() < threshold.ordinal()) return;
        PrintStream out = level.ordinal() >= Level.WARN.ordinal() ? System.err : System.out;
        out.println("[" + level + "] " + tag + ": " + message);
        if (cause != null) cause.printStackTrace(out);
    }
}
