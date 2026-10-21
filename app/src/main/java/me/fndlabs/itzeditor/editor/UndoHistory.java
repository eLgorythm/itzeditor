package me.fndlabs.itzeditor.editor;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Riwayat undo/redo berbasis snapshot teks — logika murni, diuji di JVM.
 * Controller memanggil record() SEBELUM perubahan diterapkan (dengan teks lama),
 * lalu undo()/redo() mengembalikan snapshot yang harus dipasang.
 */
public final class UndoHistory {

    /** Satu keadaan dokumen: teks + posisi kursor. */
    public static final class Snapshot {
        public final String text;
        public final int cursor;

        Snapshot(String text, int cursor) {
            this.text = text;
            this.cursor = cursor;
        }
    }

    private static final int MAX_STEPS = 200;

    private final Deque<Snapshot> undoStack = new ArrayDeque<>();
    private final Deque<Snapshot> redoStack = new ArrayDeque<>();
    private long lastRecordTime = 0;
    private int lastRecordEnd = -1;

    /**
     * Catat keadaan saat ini sebelum diubah.
     * Pengetikan beruntun yang berurutan dalam jeda singkat digabung jadi satu langkah,
     * supaya undo tidak mundur satu huruf demi satu huruf.
     *
     * @param changeStart    posisi awal perubahan yang akan terjadi
     * @param insertedCount  jumlah karakter yang akan disisipkan (after dari TextWatcher)
     * @param now            System.currentTimeMillis() — dipisah agar bisa diuji
     */
    public void record(String currentText, int cursor, int changeStart, int insertedCount, long now) {
        // Gabung bila perubahan baru mulai tepat di akhir perubahan sebelumnya
        // (ketik maju), atau satu posisi sebelumnya (backspace beruntun).
        boolean adjacent = changeStart == lastRecordEnd || changeStart == lastRecordEnd - 1;
        boolean coalesce = now - lastRecordTime < 800 && adjacent && lastRecordEnd >= 0;
        if (!coalesce) {
            undoStack.push(new Snapshot(currentText, cursor));
            while (undoStack.size() > MAX_STEPS) undoStack.removeLast();
        }
        redoStack.clear();
        lastRecordTime = now;
        lastRecordEnd = changeStart + insertedCount;
    }

    /** Akhiri penggabungan (mis. setelah kursor pindah jauh / operasi non-ketik). */
    public void breakCoalescing() {
        lastRecordTime = 0;
        lastRecordEnd = -1;
    }

    public boolean canUndo() { return !undoStack.isEmpty(); }

    public boolean canRedo() { return !redoStack.isEmpty(); }

    /** @param currentText teks saat ini (disimpan ke tumpukan redo) */
    public Snapshot undo(String currentText, int cursor) {
        if (undoStack.isEmpty()) return null;
        redoStack.push(new Snapshot(currentText, cursor));
        breakCoalescing();
        return undoStack.pop();
    }

    public Snapshot redo(String currentText, int cursor) {
        if (redoStack.isEmpty()) return null;
        undoStack.push(new Snapshot(currentText, cursor));
        breakCoalescing();
        return redoStack.pop();
    }

    public void clear() {
        undoStack.clear();
        redoStack.clear();
        breakCoalescing();
    }
}
