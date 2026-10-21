package me.fndlabs.itzeditor.util;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;

import java.util.Locale;

import me.fndlabs.itzeditor.data.Prefs;

/**
 * Penerapan tema & bahasa manual (tanpa AppCompat):
 * - Bahasa: bungkus konteks dengan Locale terpilih.
 * - Tema Terang/Gelap: paksa uiMode malam ya/tidak agar values-night ikut terpilih,
 *   lalu pilih style Light/Dark yang cocok di Activity.
 */
public final class LocaleHelper {

    private LocaleHelper() {}

    public static Context wrap(Context base) {
        int lang = Prefs.raw(base).getInt("language", Prefs.LANG_SYSTEM);
        int theme = Prefs.raw(base).getInt("theme", Prefs.THEME_SYSTEM);
        Locale locale = localeFor(lang);
        Configuration config = new Configuration(base.getResources().getConfiguration());
        if (locale != null) {
            config.setLocale(locale);
            Locale.setDefault(locale);
        }
        if (theme == Prefs.THEME_DARK) {
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                    | Configuration.UI_MODE_NIGHT_YES;
        } else if (theme == Prefs.THEME_LIGHT) {
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                    | Configuration.UI_MODE_NIGHT_NO;
        }
        return base.createConfigurationContext(config);
    }

    private static Locale localeFor(int lang) {
        switch (lang) {
            case Prefs.LANG_EN:
                return new Locale("en");
            case Prefs.LANG_ID:
                return new Locale("in");
            default:
                return null; // sistem
        }
    }

    /** True bila konfigurasi saat ini malam (setelah override diterapkan). */
    public static boolean isNight(Resources res) {
        return (res.getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }
}
