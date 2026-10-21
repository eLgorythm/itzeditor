package me.fndlabs.itzeditor.editor;

import android.graphics.Typeface;
import android.text.Editable;
import android.text.Layout;
import android.text.TextWatcher;
import android.text.method.KeyListener;
import android.util.TypedValue;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;

import me.fndlabs.itzeditor.data.Prefs;

/**
 * Mengikat EditorView + gutter nomor baris ke UndoHistory.
 * Semua perubahan teks programatik (buka file, undo, ganti-semua) lewat kelas ini
 * agar riwayat undo & penomoran baris tetap konsisten.
 */
public final class EditorController {

    public interface Listener {
        void onTextChanged();
        void onSelectionChanged();
        void onUndoStateChanged();
    }

    private final EditorView editor;
    private final TextView gutter;
    private final View gutterClip;
    private final View gutterDivider;
    private final ScrollView scrollView;
    private final UndoHistory history = new UndoHistory();
    private final Listener listener;

    private boolean applying = false; // true saat memasang teks programatik (bukan ketikan user)
    private String lastGutterSignature = "";
    private KeyListener originalKeyListener;

    public EditorController(EditorView editor, TextView gutter, View gutterClip,
                            View gutterDivider, ScrollView scrollView, Listener listener) {
        this.editor = editor;
        this.gutter = gutter;
        this.gutterClip = gutterClip;
        this.gutterDivider = gutterDivider;
        this.scrollView = scrollView;
        this.listener = listener;
        this.originalKeyListener = editor.getKeyListener();

        // Editor setinggi minimal viewport agar area kosong di bawah teks
        // tetap bisa diketuk untuk fokus; teks lebih panjang -> editor tumbuh.
        scrollView.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            int h = scrollView.getHeight();
            if (h > 0 && editor.getMinHeight() != h) editor.setMinHeight(h);
        });

        editor.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                if (!applying) {
                    history.record(s.toString(), editor.getSelectionStart(), start, after,
                            System.currentTimeMillis());
                }
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                updateGutter();
                // Perubahan programatik (muat dokumen/undo) tidak boleh
                // menandai dokumen kotor lewat listener.
                if (!applying && listener != null) {
                    listener.onTextChanged();
                    listener.onUndoStateChanged();
                }
            }
        });

        editor.setSelectionListener((start, end) -> {
            trackActiveEnd(start, end);
            // Caret-follow HANYA untuk perubahan dari keyboard/programatik.
            // Selama gesture sentuh (scroll manual / seret handle), posisi layar
            // adalah pilihan jari user — jangan pernah ditarik balik.
            if (!touchActive && draggingHandle == HANDLE_NONE) {
                ensureCaretVisible(activeEndOffset());
            }
            if (listener != null) {
                listener.onSelectionChanged();
                listener.onUndoStateChanged();
            }
        });

        // Pembagian gesture yang benar saat ada seleksi:
        // - Sentuhan TEPAT di handle seleksi -> editor yang menangani (seret handle),
        //   ScrollView dilarang intercept.
        // - Sentuhan di tempat lain -> ScrollView bebas intercept untuk scroll manual;
        //   editor tidak menerima gesture-nya, jadi seleksi TIDAK batal.
        editor.setOnTouchListener((v, ev) -> {
            float x = ev.getX();
            float y = ev.getY();
            switch (ev.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    touchActive = true;
                    lastTouchX = x;
                    lastTouchY = y;
                    draggingHandle = handleAt(x, y);
                    if (draggingHandle != HANDLE_NONE) {
                        dragAnchor = draggingHandle == HANDLE_END
                                ? editor.getSelectionStart() : editor.getSelectionEnd();
                        scrollView.requestDisallowInterceptTouchEvent(true);
                    }
                    break;
                case android.view.MotionEvent.ACTION_MOVE:
                    lastTouchX = x;
                    lastTouchY = y;
                    if (draggingHandle != HANDLE_NONE) startAutoScrollIfNeeded();
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    touchActive = false;
                    draggingHandle = HANDLE_NONE;
                    autoScrollRunning = false;
                    scrollView.requestDisallowInterceptTouchEvent(false);
                    break;
                default:
                    break;
            }
            return false; // penanganan default editor tetap jalan
        });
    }

    // ---------- Seleksi: handle & auto-scroll ----------

    private static final int HANDLE_NONE = 0;
    private static final int HANDLE_START = 1;
    private static final int HANDLE_END = 2;

    private int draggingHandle = HANDLE_NONE;
    private int dragAnchor = -1;
    private float lastTouchX;
    private float lastTouchY;
    private boolean autoScrollRunning = false;
    private boolean touchActive = false;

    // Ujung seleksi yang "aktif" (terakhir digerakkan). Default: ujung bawah
    // (selectionEnd) — itulah yang diikuti caret-follow, BUKAN selectionStart.
    private boolean activeEndIsEnd = true;
    private int prevSelStart = -1;
    private int prevSelEnd = -1;

    private void trackActiveEnd(int start, int end) {
        boolean startMoved = start != prevSelStart;
        boolean endMoved = end != prevSelEnd;
        if (endMoved && !startMoved) {
            activeEndIsEnd = true;
        } else if (startMoved && !endMoved) {
            activeEndIsEnd = false;
        } else if (startMoved && endMoved) {
            activeEndIsEnd = true; // seleksi baru: anggap ujung bawah yang aktif
        }
        prevSelStart = start;
        prevSelEnd = end;
    }

    private int activeEndOffset() {
        return activeEndIsEnd ? editor.getSelectionEnd() : editor.getSelectionStart();
    }

    /** Handle mana yang kena sentuh (jari dalam radius genggam handle), atau HANDLE_NONE. */
    private int handleAt(float x, float y) {
        int ss = editor.getSelectionStart();
        int se = editor.getSelectionEnd();
        if (ss == se) return HANDLE_NONE;
        boolean nearStart = nearHandle(x, y, ss);
        boolean nearEnd = nearHandle(x, y, se);
        if (nearStart && nearEnd) {
            // Seleksi pendek: pilih handle yang lebih dekat secara horizontal
            Layout layout = editor.getLayout();
            float sx = layout.getPrimaryHorizontal(ss);
            float ex = layout.getPrimaryHorizontal(se);
            return Math.abs(x - sx) <= Math.abs(x - ex) ? HANDLE_START : HANDLE_END;
        }
        if (nearStart) return HANDLE_START;
        if (nearEnd) return HANDLE_END;
        return HANDLE_NONE;
    }

    private boolean nearHandle(float x, float y, int offset) {
        Layout layout = editor.getLayout();
        if (layout == null) return false;
        int line = layout.getLineForOffset(offset);
        float hx = layout.getPrimaryHorizontal(offset) + editor.getPaddingLeft() - editor.getScrollX();
        float top = layout.getLineTop(line) + editor.getPaddingTop();
        float bottom = layout.getLineBottom(line) + editor.getPaddingTop();
        float r = dp(24);
        return Math.abs(x - hx) <= r && y >= top - r && y <= bottom + r;
    }

    private void startAutoScrollIfNeeded() {
        if (autoScrollRunning || edgeStep() == 0) return;
        autoScrollRunning = true;
        editor.post(autoScrollLoop);
    }

    /** Loop: gulir konten mengikuti jari di zona tepi + panjangkan seleksi ke posisi jari. */
    private final Runnable autoScrollLoop = new Runnable() {
        @Override
        public void run() {
            if (draggingHandle == HANDLE_NONE) {
                autoScrollRunning = false;
                return;
            }
            int step = edgeStep();
            if (step == 0) {
                autoScrollRunning = false;
                return;
            }
            scrollView.scrollBy(0, step);
            extendSelectionToFinger();
            editor.postDelayed(this, 16);
        }
    };

    /** Langkah scroll per tick (0 = jari di luar zona tepi). Tanda: - ke atas, + ke bawah. */
    private int edgeStep() {
        float viewportY = editor.getTop() + lastTouchY - scrollView.getScrollY();
        float zone = dp(56);
        float height = scrollView.getHeight();
        float ratio;
        int sign;
        if (viewportY < zone) {
            ratio = (zone - viewportY) / zone;
            sign = -1;
        } else if (viewportY > height - zone) {
            ratio = (viewportY - (height - zone)) / zone;
            sign = 1;
        } else {
            return 0;
        }
        ratio = Math.max(0f, Math.min(1f, ratio));
        return sign * (4 + Math.round(26 * ratio));
    }

    private void extendSelectionToFinger() {
        int off = editor.getOffsetForPosition(lastTouchX, lastTouchY);
        if (off < 0) return;
        if (draggingHandle == HANDLE_END) {
            editor.setSelection(dragAnchor, Math.max(dragAnchor, off));
        } else if (draggingHandle == HANDLE_START) {
            editor.setSelection(Math.min(dragAnchor, off), dragAnchor);
        }
    }

    private float dp(float value) {
        return value * editor.getResources().getDisplayMetrics().density;
    }

    /**
     * ScrollView tidak tahu posisi karet; jaga agar baris pada offset fokus terlihat.
     * Offset yang diikuti adalah UJUNG AKTIF seleksi (lihat trackActiveEnd),
     * bukan selalu selectionStart — mengikuti handle atas saat user bekerja di
     * handle bawah membuat tampilan terseret balik ke atas ("kedut").
     * Gutter tidak perlu disinkronkan manual — ia satu konten dengan editor
     * di dalam ScrollView yang sama, jadi ikut tergulir persis.
     */
    private void ensureCaretVisible(int focusOffset) {
        editor.post(() -> {
            Layout layout = editor.getLayout();
            if (layout == null) return;
            int line = layout.getLineForOffset(Math.max(0, Math.min(focusOffset, text().length())));
            int top = editor.getTop() + layout.getLineTop(line);
            int bottom = editor.getTop() + layout.getLineBottom(line);
            int lineHeight = Math.max(1, bottom - top);
            int y = scrollView.getScrollY();
            int h = scrollView.getHeight();
            if (top < y) {
                scrollView.scrollTo(0, Math.max(0, top - lineHeight));
            } else if (bottom > y + h) {
                scrollView.scrollTo(0, bottom - h + lineHeight);
            }
        });
    }

    // ---------- Teks ----------

    public String text() {
        Editable e = editor.getText();
        return e == null ? "" : e.toString();
    }

    public int cursor() { return editor.getSelectionStart(); }

    public int selectionEnd() { return editor.getSelectionEnd(); }

    /** Offset ujung seleksi yang aktif (untuk status Ln/Col & caret-follow). */
    public int focusOffset() { return activeEndOffset(); }

    /** Pasang teks sebagai dokumen baru: riwayat undo dibersihkan. */
    public void loadDocument(String text, int cursor) {
        applying = true;
        history.clear();
        editor.setText(text);
        editor.setSelection(clamp(cursor, text.length()));
        applying = false;
        lastGutterSignature = "";
        updateGutter();
        ensureCaretVisible(clamp(cursor, text.length()));
        if (listener != null) listener.onUndoStateChanged();
    }

    /** Ganti seluruh teks sebagai satu langkah yang bisa di-undo (mis. ganti-semua). */
    public void replaceAllText(String newText) {
        history.breakCoalescing();
        history.record(text(), cursor(), 0, newText.length(), System.currentTimeMillis());
        applying = true;
        editor.setText(newText);
        editor.setSelection(Math.min(cursor(), newText.length()));
        applying = false;
        updateGutter();
        if (listener != null) {
            listener.onTextChanged();
            listener.onUndoStateChanged();
        }
    }

    public void insertAtCursor(String insert) {
        int start = editor.getSelectionStart();
        int end = editor.getSelectionEnd();
        Editable e = editor.getText();
        if (e != null) e.replace(start, end, insert);
    }

    // ---------- Undo / redo ----------

    public boolean canUndo() { return history.canUndo(); }

    public boolean canRedo() { return history.canRedo(); }

    public void undo() {
        UndoHistory.Snapshot s = history.undo(text(), cursor());
        if (s != null) applySnapshot(s);
    }

    public void redo() {
        UndoHistory.Snapshot s = history.redo(text(), cursor());
        if (s != null) applySnapshot(s);
    }

    private void applySnapshot(UndoHistory.Snapshot s) {
        applying = true;
        editor.setText(s.text);
        editor.setSelection(clamp(s.cursor, s.text.length()));
        applying = false;
        updateGutter();
        if (listener != null) {
            listener.onTextChanged();
            listener.onUndoStateChanged();
        }
    }

    // ---------- Operasi baris ----------

    public void duplicateLine() {
        String t = text();
        int[] range = lineRange(t, cursor());
        String line = t.substring(range[0], range[1]);
        insertAt(range[1], "\n" + line);
    }

    public void deleteLine() {
        String t = text();
        int[] range = lineRange(t, cursor());
        int start = range[0];
        int end = range[1];
        if (end < t.length() && t.charAt(end) == '\n') end++;           // hapus newline sesudahnya
        else if (start > 0 && t.charAt(start - 1) == '\n') start--;    // baris terakhir: hapus newline sebelumnya
        Editable e = editor.getText();
        if (e != null) e.delete(start, end);
    }

    public void gotoLine(int lineOneBased) {
        String t = text();
        int line = 1;
        int pos = 0;
        while (line < lineOneBased && pos < t.length()) {
            int nl = t.indexOf('\n', pos);
            if (nl < 0) break;
            pos = nl + 1;
            line++;
        }
        editor.requestFocus();
        editor.setSelection(pos);
    }

    public int lineCount() {
        String t = text();
        int count = 1;
        for (int i = 0; i < t.length(); i++) if (t.charAt(i) == '\n') count++;
        return t.isEmpty() ? 1 : count;
    }

    private void insertAt(int pos, String insert) {
        Editable e = editor.getText();
        if (e != null) e.insert(pos, insert);
    }

    /** [start, end) baris yang memuat posisi pos (tanpa newline). */
    static int[] lineRange(String t, int pos) {
        int start = t.lastIndexOf('\n', pos - 1) + 1;
        int nl = t.indexOf('\n', pos);
        int end = nl < 0 ? t.length() : nl;
        return new int[]{start, end};
    }

    // ---------- Tampilan ----------

    public void applyTypography(int fontSizeSp, int fontFamily, boolean wordWrap) {
        Typeface tf;
        switch (fontFamily) {
            case Prefs.FONT_SANS:
                tf = Typeface.SANS_SERIF;
                break;
            case Prefs.FONT_SERIF:
                tf = Typeface.SERIF;
                break;
            default:
                tf = Typeface.MONOSPACE;
        }
        editor.setTypeface(tf);
        gutter.setTypeface(tf);
        editor.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp);
        gutter.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp);
        editor.setHorizontallyScrolling(!wordWrap);
        lastGutterSignature = "";
        updateGutter();
    }

    public void setLineNumbersVisible(boolean visible) {
        gutterClip.setVisibility(visible ? View.VISIBLE : View.GONE);
        gutterDivider.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    public void setReadOnly(boolean readOnly) {
        editor.setKeyListener(readOnly ? null : originalKeyListener);
        editor.setCursorVisible(true);
    }

    public EditorView view() { return editor; }

    /**
     * Gutter dihitung dari Layout editor (baris visual), BUKAN dari jumlah '\n':
     * baris yang membungkus (wrap) menempati >1 baris visual, dan nomor harus
     * tetap sejajar dengan awal baris logisnya. Dihitung ulang tiap layout teks
     * siap; signature mencegah bangun ulang yang tidak perlu.
     */
    private void updateGutter() {
        editor.post(() -> {
            Layout layout = editor.getLayout();
            if (layout == null) return;

            // Samakan tinggi baris gutter dengan tinggi baris editor secara eksplisit
            int editorLineHeight = editor.getLineHeight();
            int gutterLineHeight = gutter.getLineHeight();
            if (editorLineHeight != gutterLineHeight) {
                gutter.setLineSpacing(editorLineHeight - gutterLineHeight, 1f);
            }

            int visual = layout.getLineCount();
            String signature = visual + ":" + editor.getWidth() + ":" + text().length()
                    + ":" + layout.getLineStart(Math.max(0, visual - 1));
            if (signature.equals(lastGutterSignature)) return;
            lastGutterSignature = signature;

            int[] starts = new int[visual];
            for (int i = 0; i < visual; i++) starts[i] = layout.getLineStart(i);
            gutter.setText(GutterNumbers.build(text(), starts));
        });
    }

    private static int clamp(int v, int max) {
        return Math.max(0, Math.min(v, max));
    }
}
