package me.fndlabs.itzeditor.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Mesin cari/ganti — logika murni tanpa Android, diuji di JVM.
 * Mendukung teks biasa, case-sensitive, kata utuh, dan regex.
 */
public final class FindEngine {

    public static final class Options {
        public boolean caseSensitive;
        public boolean wholeWord;
        public boolean regex;

        public Options(boolean caseSensitive, boolean wholeWord, boolean regex) {
            this.caseSensitive = caseSensitive;
            this.wholeWord = wholeWord;
            this.regex = regex;
        }
    }

    public static final class Result {
        /** Pasangan [start, end) untuk setiap kecocokan. */
        public final List<int[]> matches = new ArrayList<>();
        /** True bila mode regex dan polanya tidak valid. */
        public boolean invalidPattern;
    }

    private FindEngine() {}

    public static Result findAll(CharSequence text, String query, Options opt) {
        Result result = new Result();
        if (text == null || query == null || query.isEmpty()) return result;

        Pattern pattern = buildPattern(query, opt);
        if (pattern == null) {
            result.invalidPattern = opt.regex;
            if (!opt.regex) return result;
            return result;
        }
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            if (m.end() == m.start()) { // hindari loop pada kecocokan kosong
                if (m.end() >= text.length()) break;
                m.region(m.end() + 1, text.length());
                continue;
            }
            result.matches.add(new int[]{m.start(), m.end()});
        }
        return result;
    }

    /** Hasil ganti-semua: teks baru + jumlah penggantian. */
    public static final class ReplaceResult {
        public final String text;
        public final int count;

        ReplaceResult(String text, int count) {
            this.text = text;
            this.count = count;
        }
    }

    public static ReplaceResult replaceAll(CharSequence text, String query, String replacement, Options opt) {
        if (text == null || query == null || query.isEmpty()) return new ReplaceResult(text == null ? "" : text.toString(), 0);
        Pattern pattern = buildPattern(query, opt);
        if (pattern == null) return new ReplaceResult(text.toString(), 0);
        Matcher m = pattern.matcher(text);
        StringBuffer sb = new StringBuffer();
        int count = 0;
        while (m.find()) {
            if (m.end() == m.start()) continue;
            // Teks pengganti selalu diperlakukan harfiah (bukan referensi grup $1).
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            count++;
        }
        m.appendTail(sb);
        return new ReplaceResult(sb.toString(), count);
    }

    private static Pattern buildPattern(String query, Options opt) {
        String patternText;
        if (opt.regex) {
            patternText = query;
        } else {
            patternText = Pattern.quote(query);
            if (opt.wholeWord) patternText = "\\b" + patternText + "\\b";
        }
        int flags = opt.caseSensitive ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        try {
            return Pattern.compile(patternText, flags);
        } catch (PatternSyntaxException e) {
            return null;
        }
    }

    /** Indeks kecocokan berikutnya setelah posisi kursor (membungkus ke awal). */
    public static int nextIndex(List<int[]> matches, int cursor) {
        for (int i = 0; i < matches.size(); i++) {
            if (matches.get(i)[0] >= cursor) return i;
        }
        return matches.isEmpty() ? -1 : 0;
    }

    /** Indeks kecocokan sebelumnya sebelum posisi kursor (membungkus ke akhir). */
    public static int prevIndex(List<int[]> matches, int cursor) {
        for (int i = matches.size() - 1; i >= 0; i--) {
            if (matches.get(i)[1] <= cursor) return i;
        }
        return matches.isEmpty() ? -1 : matches.size() - 1;
    }
}
