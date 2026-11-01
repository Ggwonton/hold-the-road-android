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
            if (prev != null) prev.uncaughtException(t, e);
        });
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
