package me.fndlabs.itzeditor.data;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Semua IO file: baca/tulis lewat ContentResolver (SAF), dekode charset,
 * dan nama tampilan. Decode: UTF-8 ketat, fallback windows-1252 agar file lama tetap kebuka.
 */
public final class FileRepository {

    private final ContentResolver resolver;

    public FileRepository(Context context) {
        resolver = context.getContentResolver();
    }

    public String readText(Uri uri) throws IOException {
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) throw new IOException("openInputStream null");
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int n;
            while ((n = in.read(chunk)) != -1) buf.write(chunk, 0, n);
            return decode(buf.toByteArray());
        }
    }

    public void writeText(Uri uri, String text) throws IOException {
        try (OutputStream out = resolver.openOutputStream(uri, "wt")) {
            if (out == null) throw new IOException("openOutputStream null");
            out.write(text.getBytes(StandardCharsets.UTF_8));
            out.flush();
        }
    }

    public String displayName(Uri uri, String fallback) {
        try (Cursor c = resolver.query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    String name = c.getString(idx);
                    if (name != null && !name.isEmpty()) return name;
                }
            }
        } catch (Exception ignored) {
        }
        String last = uri.getLastPathSegment();
        return last != null ? last : fallback;
    }

    /** UTF-8 valid -> pakai; tidak valid -> windows-1252 (tidak pernah gagal). */
    public static String decode(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        } catch (Exception e) {
            return new String(bytes, Charset.forName("windows-1252"));
        }
    }
}
