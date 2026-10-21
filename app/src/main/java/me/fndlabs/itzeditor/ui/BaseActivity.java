package me.fndlabs.itzeditor.ui;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;

import me.fndlabs.itzeditor.R;
import me.fndlabs.itzeditor.util.LocaleHelper;

/** Base: terapkan bahasa + tema dari Prefs sebelum konten dibuat. */
public abstract class BaseActivity extends Activity {

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Style harus dipilih sebelum super/setContentView agar widget default
        // (dialog, radio, switch) mengikuti terang/gelap yang benar.
        setTheme(LocaleHelper.isNight(getResources())
                ? R.style.Theme_ItzEditor_Dark
                : R.style.Theme_ItzEditor_Light);
        super.onCreate(savedInstanceState);
    }
}
