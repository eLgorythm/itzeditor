package me.fndlabs.itzeditor.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/** Satu-satunya akses SharedPreferences. Semua kunci & default tinggal di sini. */
public final class Prefs {

    public static final int THEME_SYSTEM = 0;
    public static final int THEME_LIGHT = 1;
    public static final int THEME_DARK = 2;

    public static final int LANG_SYSTEM = 0;
    public static final int LANG_EN = 1;
    public static final int LANG_ID = 2;

    public static final int FONT_MONO = 0;
    public static final int FONT_SANS = 1;
    public static final int FONT_SERIF = 2;

    private static final String FILE = "itzeditor_prefs";
    private static final int MAX_RECENTS = 10;

    private final SharedPreferences sp;

    public Prefs(Context context) {
        sp = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** Untuk attachBaseContext: baca mentah tanpa instance (konteks belum siap penuh). */
    public static SharedPreferences raw(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public int theme() { return sp.getInt("theme", THEME_SYSTEM); }
    public void setTheme(int v) { sp.edit().putInt("theme", v).apply(); }

    public int language() { return sp.getInt("language", LANG_SYSTEM); }
    public void setLanguage(int v) { sp.edit().putInt("language", v).apply(); }

    public int fontSizeSp() { return sp.getInt("font_size", 15); }
    public void setFontSizeSp(int v) { sp.edit().putInt("font_size", v).apply(); }

    public int fontFamily() { return sp.getInt("font_family", FONT_MONO); }
    public void setFontFamily(int v) { sp.edit().putInt("font_family", v).apply(); }

    public boolean wordWrap() { return sp.getBoolean("word_wrap", true); }
    public void setWordWrap(boolean v) { sp.edit().putBoolean("word_wrap", v).apply(); }

    public boolean lineNumbers() { return sp.getBoolean("line_numbers", true); }
    public void setLineNumbers(boolean v) { sp.edit().putBoolean("line_numbers", v).apply(); }

    public boolean autosave() { return sp.getBoolean("autosave", true); }
    public void setAutosave(boolean v) { sp.edit().putBoolean("autosave", v).apply(); }

    public boolean restoreSession() { return sp.getBoolean("restore_session", true); }
    public void setRestoreSession(boolean v) { sp.edit().putBoolean("restore_session", v).apply(); }

    // ---------- File terakhir (URI persisten SAF), terbaru di depan ----------

    /** Format per entri: uriString + "\n" + displayName, digabung dengan pemisah . */
    private static final String RECENT_SEP = "\u0001";
    private static final String FIELD_SEP = "\u0002";

    public static final class Recent {
        public final String uri;
        public final String name;

        Recent(String uri, String name) {
            this.uri = uri;
            this.name = name;
        }
    }

    public List<Recent> recents() {
        List<Recent> out = new ArrayList<>();
        String raw = sp.getString("recents", "");
        if (raw == null || raw.isEmpty()) return out;
        for (String entry : raw.split(RECENT_SEP)) {
            String[] parts = entry.split(FIELD_SEP, 2);
            if (parts.length == 2) out.add(new Recent(parts[0], parts[1]));
        }
        return out;
    }

    public void addRecent(String uri, String name) {
        List<Recent> list = recents();
        list.removeIf(r -> r.uri.equals(uri));
        list.add(0, new Recent(uri, name));
        while (list.size() > MAX_RECENTS) list.remove(list.size() - 1);
        saveRecents(list);
    }

    public void removeRecent(String uri) {
        List<Recent> list = recents();
        list.removeIf(r -> r.uri.equals(uri));
        saveRecents(list);
    }

    public void clearRecents() { sp.edit().remove("recents").apply(); }

    private void saveRecents(List<Recent> list) {
        StringBuilder sb = new StringBuilder();
        for (Recent r : list) {
            if (sb.length() > 0) sb.append(RECENT_SEP);
            sb.append(r.uri).append(FIELD_SEP).append(r.name);
        }
        sp.edit().putString("recents", sb.toString()).apply();
    }
}
