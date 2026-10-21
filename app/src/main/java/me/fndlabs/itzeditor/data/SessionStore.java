package me.fndlabs.itzeditor.data;

import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import me.fndlabs.itzeditor.editor.Document;

/**
 * Persistensi sesi: snapshot tiap tab ke filesDir/session/
 * (meta di SharedPreferences terpisah, isi teks sebagai file per tab).
 * Dipulihkan saat start bila pengaturan restore aktif — termasuk perubahan
 * yang belum disimpan, persis seperti terakhir ditinggalkan.
 */
public final class SessionStore {

    private static final String PREFS = "itzeditor_session";
    private final Context context;

    public SessionStore(Context context) {
        this.context = context.getApplicationContext();
    }

    private File dir() {
        File d = new File(context.getFilesDir(), "session");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    public void save(List<Document> docs, int activeIndex) {
        android.content.SharedPreferences sp =
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        android.content.SharedPreferences.Editor ed = sp.edit();
        ed.putInt("count", docs.size());
        ed.putInt("active", activeIndex);
        for (int i = 0; i < docs.size(); i++) {
            Document d = docs.get(i);
            ed.putString("name_" + i, d.displayName);
            ed.putString("uri_" + i, d.uri == null ? "" : d.uri.toString());
            ed.putInt("cursor_" + i, d.cursor);
            ed.putBoolean("dirty_" + i, d.dirty);
            writeFile(new File(dir(), "doc_" + i + ".txt"), d.text);
        }
        ed.apply();
        // Bersihkan file sisa dari sesi dengan tab lebih banyak
        File[] files = dir().listFiles();
        if (files != null) {
            for (File f : files) {
                String name = f.getName();
                if (name.startsWith("doc_") && name.endsWith(".txt")) {
                    try {
                        int idx = Integer.parseInt(name.substring(4, name.length() - 4));
                        if (idx >= docs.size()) f.delete();
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
    }

    public static final class Restored {
        public final List<Document> documents;
        public final int activeIndex;

        Restored(List<Document> documents, int activeIndex) {
            this.documents = documents;
            this.activeIndex = activeIndex;
        }
    }

    public Restored load() {
        android.content.SharedPreferences sp =
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int count = sp.getInt("count", 0);
        List<Document> docs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Document d = new Document(null, sp.getString("name_" + i, ""));
            String uri = sp.getString("uri_" + i, "");
            if (uri != null && !uri.isEmpty()) d.uri = Uri.parse(uri);
            d.cursor = sp.getInt("cursor_" + i, 0);
            d.dirty = sp.getBoolean("dirty_" + i, false);
            d.text = readFile(new File(dir(), "doc_" + i + ".txt"));
            docs.add(d);
        }
        int active = sp.getInt("active", 0);
        if (active < 0 || active >= docs.size()) active = docs.isEmpty() ? -1 : 0;
        return new Restored(docs, active);
    }

    public void clear() {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
        File[] files = dir().listFiles();
        if (files != null) for (File f : files) f.delete();
    }

    private static void writeFile(File f, String text) {
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }

    private static String readFile(File f) {
        if (!f.exists()) return "";
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
            return "";
        }
    }
}
