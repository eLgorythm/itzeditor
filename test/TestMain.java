import me.fndlabs.itzeditor.editor.DocumentManager;
import me.fndlabs.itzeditor.editor.FileNames;
import me.fndlabs.itzeditor.editor.FindEngine;
import me.fndlabs.itzeditor.editor.GutterNumbers;
import me.fndlabs.itzeditor.editor.TextStats;
import me.fndlabs.itzeditor.editor.UndoHistory;

import java.util.List;

/** Test JVM sederhana tanpa JUnit: java TestMain -> "ALL PASS" atau exit 1. */
public class TestMain {

    private static int passed = 0;
    private static int failed = 0;

    private static void check(String name, boolean cond) {
        if (cond) { passed++; }
        else { failed++; System.out.println("FAIL: " + name); }
    }

    private static void eq(String name, Object expected, Object actual) {
        check(name + " (expected=" + expected + ", actual=" + actual + ")",
                expected == null ? actual == null : expected.equals(actual));
    }

    public static void main(String[] args) {
        testStats();
        testFind();
        testReplace();
        testUndo();
        testUntitledNames();
        testGutter();
        testFileNames();
        System.out.println("Tests: " + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
        System.out.println("ALL PASS");
    }

    private static void testStats() {
        TextStats s = TextStats.of("halo dunia\nbaris kedua");
        eq("stats lines", 2, s.lines);
        eq("stats words", 4, s.words);
        eq("stats chars", 22, s.chars);
        eq("stats empty words", 0, TextStats.of("").words);
        eq("stats empty lines", 0, TextStats.of("").lines);
        int[] lc = TextStats.lineCol("ab\ncde\nf", 7);
        eq("linecol line", 3, lc[0]);
        eq("linecol col", 1, lc[1]);
        int[] lcMid = TextStats.lineCol("ab\ncde\nf", 6);
        eq("linecol mid line", 2, lcMid[0]);
        eq("linecol mid col", 4, lcMid[1]);
        int[] lc2 = TextStats.lineCol("ab\ncde", 4);
        eq("linecol2 line", 2, lc2[0]);
        eq("linecol2 col", 2, lc2[1]);
    }

    private static void testFind() {
        FindEngine.Options plain = new FindEngine.Options(false, false, false);
        List<int[]> m = FindEngine.findAll("Kata kata KATA", "kata", plain).matches;
        eq("find insensitive count", 3, m.size());

        FindEngine.Options cs = new FindEngine.Options(true, false, false);
        eq("find case count", 1, FindEngine.findAll("Kata kata KATA", "kata", cs).matches.size());

        FindEngine.Options ww = new FindEngine.Options(false, true, false);
        eq("find whole word", 1, FindEngine.findAll("katak kata berkatak", "kata", ww).matches.size());

        FindEngine.Options rx = new FindEngine.Options(false, false, true);
        List<int[]> rm = FindEngine.findAll("a1 b22 c333", "\\d+", rx).matches;
        eq("find regex count", 3, rm.size());
        eq("find regex span", 3, rm.get(2)[1] - rm.get(2)[0]);

        check("find invalid regex flagged",
                FindEngine.findAll("teks", "([", rx).invalidPattern);
        eq("find empty query", 0, FindEngine.findAll("teks", "", plain).matches.size());
        eq("find literal dot", 1, FindEngine.findAll("a.b axb", "a.b", plain).matches.size());

        List<int[]> nm = FindEngine.findAll("satu dua satu dua", "satu", plain).matches;
        eq("nextIndex wrap", 0, FindEngine.nextIndex(nm, 99));
        eq("nextIndex mid", 1, FindEngine.nextIndex(nm, 5));
        eq("prevIndex wrap", 1, FindEngine.prevIndex(nm, 0));
    }

    private static void testReplace() {
        FindEngine.Options plain = new FindEngine.Options(false, false, false);
        FindEngine.ReplaceResult r = FindEngine.replaceAll("kucing dan kucing", "kucing", "anjing", plain);
        eq("replaceAll text", "anjing dan anjing", r.text);
        eq("replaceAll count", 2, r.count);

        FindEngine.Options rx = new FindEngine.Options(false, false, true);
        FindEngine.ReplaceResult r2 = FindEngine.replaceAll("a1b22", "\\d+", "#", rx);
        eq("replaceAll regex", "a#b#", r2.text);

        // Replacement berisi $ harus harfiah, bukan referensi grup
        FindEngine.ReplaceResult r3 = FindEngine.replaceAll("harga lama", "lama", "$5", plain);
        eq("replaceAll dollar literal", "harga $5", r3.text);
    }

    private static void testUndo() {
        UndoHistory h = new UndoHistory();
        check("undo empty", !h.canUndo());
        h.record("", 0, 0, 1, 1000);
        h.record("a", 1, 1, 1, 2000); // jeda > 800ms = langkah terpisah
        check("canUndo after records", h.canUndo());
        UndoHistory.Snapshot s = h.undo("ab", 2);
        eq("undo returns prev", "a", s.text);
        check("canRedo after undo", h.canRedo());
        UndoHistory.Snapshot s2 = h.redo("a", 1);
        eq("redo returns next", "ab", s2.text);

        // Pengetikan beruntun cepat = satu langkah undo
        UndoHistory h2 = new UndoHistory();
        h2.record("", 0, 0, 1, 1000);
        h2.record("a", 1, 1, 1, 1100);
        h2.record("ab", 2, 2, 1, 1200);
        UndoHistory.Snapshot u = h2.undo("abc", 3);
        eq("coalesced undo to start", "", u.text);
        check("coalesced single step", !h2.canUndo());

        h2.clear();
        check("clear empties", !h2.canUndo() && !h2.canRedo());
    }

    private static void testUntitledNames() {
        DocumentManager dm = new DocumentManager();
        eq("untitled 1", "Untitled", dm.newUntitled("Untitled").displayName);
        eq("untitled 2", "Untitled 2", dm.newUntitled("Untitled").displayName);
        eq("untitled 3", "Untitled 3", dm.newUntitled("Untitled").displayName);
        dm.remove(1); // tutup "Untitled 2" -> nomor 2 bebas lagi
        eq("untitled reuse 2", "Untitled 2", dm.newUntitled("Untitled").displayName);
        dm.remove(0); // tutup "Untitled"
        dm.remove(0); // tutup "Untitled 3"
        dm.remove(0); // tutup "Untitled 2"
        eq("untitled reset", "Untitled", dm.newUntitled("Untitled").displayName);
    }

    private static void testGutter() {
        // Tanpa wrap: tiap baris visual = baris logis
        eq("gutter plain", "1\n2",
                GutterNumbers.build("abc\ndef", new int[]{0, 4}));
        // Baris pertama wrap jadi 2 baris visual: baris lanjutan kosong, nomor 2 tetap di tempatnya
        eq("gutter wrapped", "1\n\n2",
                GutterNumbers.build("abcdef\ngh", new int[]{0, 3, 7}));
        // Teks kosong: satu baris visual
        eq("gutter empty", "1",
                GutterNumbers.build("", new int[]{0}));
        // Diakhiri newline: baris kosong terakhir tetap baris logis baru
        eq("gutter trailing newline", "1\n2",
                GutterNumbers.build("abc\n", new int[]{0, 4}));
        // Wrap di tengah + beberapa baris
        eq("gutter mixed", "1\n\n2\n3",
                GutterNumbers.build("aaaa bbbb\ncc\ndd", new int[]{0, 5, 10, 13}));
    }

    private static void testFileNames() {
        // sanitize: spasi tepi dipotong, pemisah path dibuang, nama tak layak -> ""
        eq("sanitize plain", "catatan.md", FileNames.sanitize("catatan.md"));
        eq("sanitize trim", "catatan.md", FileNames.sanitize("  catatan.md  "));
        eq("sanitize slash", "abc.txt", FileNames.sanitize("a/b\\c.txt"));
        eq("sanitize empty", "", FileNames.sanitize("   "));
        eq("sanitize dotdot", "", FileNames.sanitize(".."));
        eq("sanitize null", "", FileNames.sanitize(null));
        // extensionOf: huruf kecil, berkas tersembunyi tanpa ekstensi
        eq("ext md", "md", FileNames.extensionOf("catatan.md"));
        eq("ext upper", "txt", FileNames.extensionOf("NOTES.TXT"));
        eq("ext multi", "gz", FileNames.extensionOf("arsip.tar.gz"));
        eq("ext none", "", FileNames.extensionOf("README"));
        eq("ext hidden", "", FileNames.extensionOf(".gitignore"));
        eq("ext trailing dot", "", FileNames.extensionOf("nama."));
        // mimeTypeFor: ekstensi dikenal -> MIME cocok; tak dikenal -> */*
        eq("mime txt", "text/plain", FileNames.mimeTypeFor("a.txt"));
        eq("mime md", "text/markdown", FileNames.mimeTypeFor("a.md"));
        eq("mime json", "application/json", FileNames.mimeTypeFor("a.json"));
        eq("mime py bebas", "*/*", FileNames.mimeTypeFor("script.py"));
        eq("mime polos bebas", "*/*", FileNames.mimeTypeFor("LICENSE"));
    }
}
