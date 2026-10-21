package me.fndlabs.itzeditor.editor;

import android.text.Editable;
import android.text.Spannable;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import me.fndlabs.itzeditor.R;

/**
 * Mengikat bilah cari/ganti ke EditorController: sorot semua kecocokan,
 * navigasi berikutnya/sebelumnya (membungkus), ganti satu, ganti semua.
 * Logika pencariannya sendiri ada di FindEngine (murni, teruji).
 */
public final class FindController {

    public interface Listener {
        /** Dipanggil setelah ganti/ganti-semua mengubah teks. */
        void onTextReplaced();
        void toast(String message);
    }

    private final EditorController editorCtrl;
    private final View findBar;
    private final View findDivider;
    private final EditText etFind;
    private final EditText etReplace;
    private final TextView tvCount;
    private final CheckBox cbCase;
    private final CheckBox cbWholeWord;
    private final CheckBox cbRegex;
    private final int colorHighlight;
    private final int colorHighlightCurrent;
    private final Listener listener;
    private final android.content.res.Resources res;

    private List<int[]> matches = new ArrayList<>();
    private int currentIndex = -1;
    private boolean visible = false;

    public FindController(EditorController editorCtrl, View root, int colorHighlight,
                          int colorHighlightCurrent, Listener listener) {
        this.editorCtrl = editorCtrl;
        this.findBar = root.findViewById(R.id.findBar);
        this.findDivider = root.findViewById(R.id.findDivider);
        this.etFind = root.findViewById(R.id.etFind);
        this.etReplace = root.findViewById(R.id.etReplace);
        this.tvCount = root.findViewById(R.id.tvFindCount);
        this.cbCase = root.findViewById(R.id.cbCase);
        this.cbWholeWord = root.findViewById(R.id.cbWholeWord);
        this.cbRegex = root.findViewById(R.id.cbRegex);
        this.colorHighlight = colorHighlight;
        this.colorHighlightCurrent = colorHighlightCurrent;
        this.listener = listener;
        this.res = root.getResources();

        root.findViewById(R.id.btnFindClose).setOnClickListener(v -> hide());
        root.findViewById(R.id.btnFindNext).setOnClickListener(v -> next());
        root.findViewById(R.id.btnFindPrev).setOnClickListener(v -> prev());
        root.findViewById(R.id.btnReplace).setOnClickListener(v -> replaceCurrent());
        root.findViewById(R.id.btnReplaceAll).setOnClickListener(v -> replaceAll());

        etFind.addTextChangedListener(new SimpleWatcher(this::refresh));
        android.widget.CompoundButton.OnCheckedChangeListener opt =
                (button, checked) -> refresh();
        cbCase.setOnCheckedChangeListener(opt);
        cbWholeWord.setOnCheckedChangeListener(opt);
        cbRegex.setOnCheckedChangeListener(opt);

        etFind.setOnEditorActionListener((v, actionId, event) -> {
            next();
            return true;
        });
    }

    public boolean isVisible() { return visible; }

    public void show() {
        visible = true;
        findBar.setVisibility(View.VISIBLE);
        findDivider.setVisibility(View.VISIBLE);
        // Awali pencarian dari teks yang sedang terseleksi, kebiasaan editor umumnya
        int ss = editorCtrl.view().getSelectionStart();
        int se = editorCtrl.view().getSelectionEnd();
        if (se > ss) {
            String sel = editorCtrl.text().substring(ss, se);
            if (!sel.contains("\n")) etFind.setText(sel);
        }
        etFind.requestFocus();
        etFind.selectAll();
        refresh();
    }

    public void hide() {
        visible = false;
        findBar.setVisibility(View.GONE);
        findDivider.setVisibility(View.GONE);
        clearSpans();
        editorCtrl.view().requestFocus();
    }

    /** Hitung ulang kecocokan + sorotan. Dipanggil juga setelah teks berubah. */
    public void refresh() {
        if (!visible) return;
        clearSpans();
        String query = etFind.getText().toString();
        if (query.isEmpty()) {
            matches = new ArrayList<>();
            currentIndex = -1;
            tvCount.setText("");
            return;
        }
        FindEngine.Result result = FindEngine.findAll(editorCtrl.text(), query, options());
        matches = result.matches;
        if (result.invalidPattern) {
            tvCount.setText(R.string.find_invalid_regex);
            currentIndex = -1;
            return;
        }
        if (matches.isEmpty()) {
            tvCount.setText(R.string.find_no_match);
            currentIndex = -1;
            return;
        }
        if (currentIndex < 0 || currentIndex >= matches.size()) {
            currentIndex = FindEngine.nextIndex(matches, editorCtrl.cursor());
        }
        applySpans();
        updateCountLabel();
        selectCurrent(false);
    }

    public void next() {
        if (matches.isEmpty()) { refresh(); return; }
        currentIndex = (currentIndex + 1) % matches.size();
        applySpans();
        updateCountLabel();
        selectCurrent(true);
    }

    public void prev() {
        if (matches.isEmpty()) { refresh(); return; }
        currentIndex = (currentIndex - 1 + matches.size()) % matches.size();
        applySpans();
        updateCountLabel();
        selectCurrent(true);
    }

    private void replaceCurrent() {
        if (matches.isEmpty() || currentIndex < 0) return;
        int[] m = matches.get(currentIndex);
        Editable e = editorCtrl.view().getText();
        if (e == null) return;
        e.replace(m[0], m[1], etReplace.getText().toString());
        if (listener != null) listener.onTextReplaced();
        refresh();
    }

    private void replaceAll() {
        String query = etFind.getText().toString();
        if (query.isEmpty()) return;
        FindEngine.ReplaceResult r = FindEngine.replaceAll(
                editorCtrl.text(), query, etReplace.getText().toString(), options());
        if (r.count == 0) {
            refresh();
            return;
        }
        editorCtrl.replaceAllText(r.text);
        if (listener != null) {
            listener.toast(res.getString(R.string.find_replaced_all, r.count));
            listener.onTextReplaced();
        }
        refresh();
    }

    private FindEngine.Options options() {
        return new FindEngine.Options(cbCase.isChecked(), cbWholeWord.isChecked(), cbRegex.isChecked());
    }

    private void applySpans() {
        Editable e = editorCtrl.view().getText();
        if (e == null) return;
        for (int i = 0; i < matches.size(); i++) {
            int[] m = matches.get(i);
            int color = i == currentIndex ? colorHighlightCurrent : colorHighlight;
            e.setSpan(new BackgroundColorSpan(color), m[0], m[1], Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private void clearSpans() {
        Editable e = editorCtrl.view().getText();
        if (e == null) return;
        for (BackgroundColorSpan span : e.getSpans(0, e.length(), BackgroundColorSpan.class)) {
            e.removeSpan(span);
        }
    }

    private void selectCurrent(boolean moveCursor) {
        if (currentIndex < 0 || currentIndex >= matches.size()) return;
        int[] m = matches.get(currentIndex);
        if (moveCursor || editorCtrl.cursor() < m[0] || editorCtrl.cursor() > m[1]) {
            editorCtrl.view().setSelection(m[0], m[1]);
        }
    }

    private void updateCountLabel() {
        tvCount.setText(res.getString(R.string.find_match_count, currentIndex + 1, matches.size()));
    }

    private static final class SimpleWatcher implements TextWatcher {
        private final Runnable action;

        SimpleWatcher(Runnable action) { this.action = action; }

        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        @Override public void afterTextChanged(Editable s) { action.run(); }
    }
}
