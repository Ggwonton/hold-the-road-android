package com.ashenlantern.holdtheroad;

import android.app.Application;
import android.content.Context;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;

/** Saves any crash to a file so the next launch can show it (and the game keeps a record). */
public class App extends Application {
    static final String CRASH_FILE = "last_crash.txt";

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            record(base, "Crash on thread " + t.getName(), e);
            mark(base, "CRASH " + e);
            if (prev != null) prev.uncaughtException(t, e);
        });
    }

    static final String BOOT_FILE = "boot_log.txt";

    /** Adds a line to this run's start-up log. */
    static void mark(Context c, String step) {
        try (FileWriter w = new FileWriter(new File(c.getFilesDir(), BOOT_FILE), true)) {
            w.write(String.format(java.util.Locale.US, "%tT.%<tL  %s%n", System.currentTimeMillis(), step));
        } catch (Throwable ignored) { }
    }
    static void startBoot(Context c) {
        new File(c.getFilesDir(), BOOT_FILE).delete();
        mark(c, "start  app " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ") " + (BuildConfig.DEBUG ? "debug" : "release")
                + ", Android " + android.os.Build.VERSION.RELEASE + " (API " + android.os.Build.VERSION.SDK_INT + "), "
                + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL);
    }
    /** The game ran for a while without dying: forget this run's log. */
    static void bootOk(Context c) { new File(c.getFilesDir(), BOOT_FILE).delete(); new File(c.getFilesDir(), CRASH_FILE).delete(); }
    static String read(Context c, String name) {
        File f = new File(c.getFilesDir(), name);
        if (!f.exists()) return null;
        try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream(); byte[] b = new byte[4096]; int n;
            while ((n = in.read(b)) > 0) bo.write(b, 0, n);
            return bo.toString("UTF-8");
        } catch (Throwable t) { return null; }
    }
    /** If the last run died early, returns a report of what happened; otherwise null. */
    static String previousRunProblem(Context c) {
        String boot = read(c, BOOT_FILE), crash = read(c, CRASH_FILE);
        if (boot == null && crash == null) return null;
        StringBuilder sb = new StringBuilder();
        if (crash != null) sb.append(crash).append("\n\n");
        if (boot != null) sb.append("Start-up log of the last run (it closed before 10 seconds):\n").append(boot);
        return sb.toString();
    }

    static void record(Context c, String what, Throwable e) {
        try {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            String s = what + "\nApp " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + "), Android "
                    + android.os.Build.VERSION.RELEASE + " (API " + android.os.Build.VERSION.SDK_INT + "), "
                    + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL + "\n\n" + sw;
            File f = new File(c.getFilesDir(), CRASH_FILE);
            try (FileWriter w = new FileWriter(f, false)) { w.write(s.length() > 12000 ? s.substring(0, 12000) : s); }
        } catch (Throwable ignored) { }
    }
}
