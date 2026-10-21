package me.fndlabs.itzeditor.editor;

import android.net.Uri;

/**
 * Model satu tab dokumen. Teks otoritatif selama tab tidak aktif disimpan di sini;
 * saat tab aktif, teks hidup di EditText dan disalin balik sebelum pindah tab.
 */
public final class Document {

    public Uri uri;               // null = belum pernah disimpan ke file
    public String displayName;    // nama file atau Untitled
    public String text = "";
    public int cursor = 0;
    public boolean dirty = false;
    public boolean readOnly = false;

    public Document(Uri uri, String displayName) {
        this.uri = uri;
        this.displayName = displayName;
    }

    public String tabTitle() {
        return (dirty ? "• " : "") + displayName;
    }
}
