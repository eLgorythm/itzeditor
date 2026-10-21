package me.fndlabs.itzeditor.ui;

import android.os.Bundle;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import me.fndlabs.itzeditor.R;
import me.fndlabs.itzeditor.data.Prefs;

/**
 * Layar pengaturan: semua kontrol menulis langsung ke Prefs.
 * Tema & bahasa diterapkan ulang oleh BaseActivity saat activity dibuat ulang;
 * di sini kita panggil recreate() agar perubahan langsung terlihat.
 */
public final class SettingsActivity extends BaseActivity {

    private Prefs prefs;
    private boolean binding = false; // cegah loop saat mengisi nilai awal

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        prefs = new Prefs(this);

        RadioGroup rgTheme = findViewById(R.id.rgTheme);
        RadioGroup rgLanguage = findViewById(R.id.rgLanguage);
        RadioGroup rgFontFamily = findViewById(R.id.rgFontFamily);
        SeekBar seekFontSize = findViewById(R.id.seekFontSize);
        TextView tvFontSizeValue = findViewById(R.id.tvFontSizeValue);
        Switch swWordWrap = findViewById(R.id.swWordWrap);
        Switch swLineNumbers = findViewById(R.id.swLineNumbers);
        Switch swAutosave = findViewById(R.id.swAutosave);
        Switch swRestoreSession = findViewById(R.id.swRestoreSession);

        binding = true;
        rgTheme.check(themeRadioId(prefs.theme()));
        rgLanguage.check(languageRadioId(prefs.language()));
        rgFontFamily.check(fontRadioId(prefs.fontFamily()));
        seekFontSize.setProgress(prefs.fontSizeSp() - 10); // rentang 10–22sp
        tvFontSizeValue.setText(prefs.fontSizeSp() + "sp");
        swWordWrap.setChecked(prefs.wordWrap());
        swLineNumbers.setChecked(prefs.lineNumbers());
        swAutosave.setChecked(prefs.autosave());
        swRestoreSession.setChecked(prefs.restoreSession());
        binding = false;

        rgTheme.setOnCheckedChangeListener((group, checkedId) -> {
            if (binding) return;
            prefs.setTheme(checkedId == R.id.rbThemeLight ? Prefs.THEME_LIGHT
                    : checkedId == R.id.rbThemeDark ? Prefs.THEME_DARK : Prefs.THEME_SYSTEM);
            recreate();
        });
        rgLanguage.setOnCheckedChangeListener((group, checkedId) -> {
            if (binding) return;
            prefs.setLanguage(checkedId == R.id.rbLangEn ? Prefs.LANG_EN
                    : checkedId == R.id.rbLangId ? Prefs.LANG_ID : Prefs.LANG_SYSTEM);
            recreate();
        });
        rgFontFamily.setOnCheckedChangeListener((group, checkedId) -> {
            if (binding) return;
            prefs.setFontFamily(checkedId == R.id.rbFontSans ? Prefs.FONT_SANS
                    : checkedId == R.id.rbFontSerif ? Prefs.FONT_SERIF : Prefs.FONT_MONO);
        });
        seekFontSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int sp = progress + 10;
                tvFontSizeValue.setText(sp + "sp");
                if (fromUser) prefs.setFontSizeSp(sp);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        swWordWrap.setOnCheckedChangeListener((b, checked) -> { if (!binding) prefs.setWordWrap(checked); });
        swLineNumbers.setOnCheckedChangeListener((b, checked) -> { if (!binding) prefs.setLineNumbers(checked); });
        swAutosave.setOnCheckedChangeListener((b, checked) -> { if (!binding) prefs.setAutosave(checked); });
        swRestoreSession.setOnCheckedChangeListener((b, checked) -> { if (!binding) prefs.setRestoreSession(checked); });
    }

    private int themeRadioId(int theme) {
        return theme == Prefs.THEME_LIGHT ? R.id.rbThemeLight
                : theme == Prefs.THEME_DARK ? R.id.rbThemeDark : R.id.rbThemeSystem;
    }

    private int languageRadioId(int lang) {
        return lang == Prefs.LANG_EN ? R.id.rbLangEn
                : lang == Prefs.LANG_ID ? R.id.rbLangId : R.id.rbLangSystem;
    }

    private int fontRadioId(int font) {
        return font == Prefs.FONT_SANS ? R.id.rbFontSans
                : font == Prefs.FONT_SERIF ? R.id.rbFontSerif : R.id.rbFontMono;
    }
}
