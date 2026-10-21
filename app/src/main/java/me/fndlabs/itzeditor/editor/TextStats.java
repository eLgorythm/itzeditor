package me.fndlabs.itzeditor.editor;

/** Statistik teks — logika murni tanpa Android, diuji di JVM. */
public final class TextStats {

    public final int lines;
    public final int words;
    public final int chars;

    private TextStats(int lines, int words, int chars) {
        this.lines = lines;
        this.words = words;
        this.chars = chars;
    }

    public static TextStats of(CharSequence text) {
        if (text == null || text.length() == 0) return new TextStats(0, 0, 0);
        int lines = 1;
        int words = 0;
        boolean inWord = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n') lines++;
            if (Character.isWhitespace(c)) {
                inWord = false;
            } else if (!inWord) {
                inWord = true;
                words++;
            }
        }
        return new TextStats(lines, words, text.length());
    }

    /** Posisi kursor 1-based: baris & kolom dari offset. */
    public static int[] lineCol(CharSequence text, int offset) {
        int line = 1;
        int lineStart = 0;
        int end = Math.min(offset, text.length());
        for (int i = 0; i < end; i++) {
            if (text.charAt(i) == '\n') {
                line++;
                lineStart = i + 1;
            }
        }
        return new int[]{line, offset - lineStart + 1};
    }
}
