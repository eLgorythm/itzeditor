package me.fndlabs.itzeditor.util;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

/** Crash reporter permanen: tulis stack trace ke crash.log, tampilkan di start berikutnya. */
public final class CrashReporter {

    private CrashReporter() {}

    public static void install(Context context) {
        final Context app = context.getApplicationContext();
        Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                StringWriter sw = new StringWriter();
                error.printStackTrace(new PrintWriter(sw));
                String text = "ItzEditor crash\n" + new java.util.Date() + "\n\n" + sw;
                try (FileOutputStream out = new FileOutputStream(logFile(app))) {
                    out.write(text.getBytes(StandardCharsets.UTF_8));
                }
            } catch (Exception ignored) {
            }
            if (prev != null) prev.uncaughtException(thread, error);
        });
    }

    private static File logFile(Context context) {
        return new File(context.getFilesDir(), "crash.log");
    }

    /** @return isi log crash terakhir, atau null bila tidak ada; log dihapus setelah dibaca. */
    public static String consumeLastCrash(Context context) {
        File f = logFile(context);
        if (!f.exists()) return null;
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] bytes = new byte[(int) f.length()];
            int read = 0;
            while (read < bytes.length) {
                int n = in.read(bytes, read, bytes.length - read);
                if (n < 0) break;
                read += n;
            }
            return new String(bytes, 0, read, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        } finally {
            f.delete();
        }
    }
}
