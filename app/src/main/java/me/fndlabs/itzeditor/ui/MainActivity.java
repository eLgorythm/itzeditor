package me.fndlabs.itzeditor.ui;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.ViewGroup;
import android.text.InputType;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import me.fndlabs.itzeditor.R;
import me.fndlabs.itzeditor.data.FileRepository;
import me.fndlabs.itzeditor.data.Prefs;
import me.fndlabs.itzeditor.data.SessionStore;
import me.fndlabs.itzeditor.editor.Document;
import me.fndlabs.itzeditor.editor.DocumentManager;
import me.fndlabs.itzeditor.editor.EditorController;
import me.fndlabs.itzeditor.editor.EditorView;
import me.fndlabs.itzeditor.editor.FileNames;
import me.fndlabs.itzeditor.editor.FindController;
import me.fndlabs.itzeditor.editor.TextStats;
import me.fndlabs.itzeditor.util.CrashReporter;

/**
 * Orkestrator UI: tab, operasi file (SAF), dialog, menu, status bar.
 * Logika edit ada di EditorController/FindController, logika murni di
 * TextStats/FindEngine/UndoHistory, IO di FileRepository/SessionStore.
 */
public final class MainActivity extends BaseActivity {

    private static final int REQ_OPEN = 1001;
    private static final int REQ_SAVE_AS = 1002;

    private Prefs prefs;
    private FileRepository files;
    private SessionStore session;
    private final DocumentManager docs = new DocumentManager();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private EditorController editorCtrl;
    private FindController findCtrl;
    private LinearLayout tabsContainer;
    private TextView tvStatusLeft;
    private TextView tvStatusRight;
    private ImageButton btnUndo;
    private ImageButton btnRedo;

    private int appliedTheme;
    private int appliedLanguage;
    private Document pendingCloseDoc; // tab yang ditutup setelah simpannya berhasil
    private boolean firstResume = true;

    private final Runnable autosaveTask = () -> {
        Document d = docs.active();
        if (d != null && prefs.autosave() && d.uri != null && d.dirty) {
            captureCurrent();
            writeToUri(d, false);
        }
    };

    private final Runnable sessionTask = () -> {
        captureCurrent();
        session.save(docs.all(), docs.activeIndex());
    };

    // ---------------- Lifecycle ----------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = new Prefs(this);
        files = new FileRepository(this);
        session = new SessionStore(this);
        appliedTheme = prefs.theme();
        appliedLanguage = prefs.language();

        tabsContainer = findViewById(R.id.tabsContainer);
        tvStatusLeft = findViewById(R.id.tvStatusLeft);
        tvStatusRight = findViewById(R.id.tvStatusRight);
        btnUndo = findViewById(R.id.btnUndo);
        btnRedo = findViewById(R.id.btnRedo);

        EditorView editorView = findViewById(R.id.etEditor);
        editorCtrl = new EditorController(
                editorView,
                findViewById(R.id.tvLineNumbers),
                findViewById(R.id.gutterClip),
                findViewById(R.id.gutterDivider),
                findViewById(R.id.editorScroll),
                new EditorController.Listener() {
                    @Override public void onTextChanged() {
                        onEditorTextChanged();
                    }
                    @Override public void onSelectionChanged() {
                        updateStatus();
                    }
                    @Override public void onUndoStateChanged() {
                        updateUndoButtons();
                    }
                });

        findCtrl = new FindController(editorCtrl, findViewById(android.R.id.content),
                getColor(R.color.highlight), getColor(R.color.highlight_current),
                new FindController.Listener() {
                    @Override public void onTextReplaced() {
                        markDirtyAndRefresh();
                    }
                    @Override public void toast(String message) {
                        showToast(message);
                    }
                });

        wireToolbar();

        // Pulihkan sesi / dokumen awal
        boolean restored = false;
        if (prefs.restoreSession()) {
            SessionStore.Restored r = session.load();
            if (!r.documents.isEmpty()) {
                for (Document d : r.documents) docs.all().add(d);
                docs.setActive(r.activeIndex);
                restored = true;
            }
        }
        if (!restored) docs.newUntitled(getString(R.string.untitled));

        loadActiveIntoEditor();
        applyEditorPrefs();
        renderTabs();
        updateStatus();
        updateUndoButtons();

        handleViewIntent(getIntent());
        showCrashLogIfAny();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleViewIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (firstResume) {
            firstResume = false;
            return;
        }
        // Pengaturan tema/bahasa berubah di SettingsActivity -> buat ulang
        if (prefs.theme() != appliedTheme || prefs.language() != appliedLanguage) {
            captureCurrent();
            session.save(docs.all(), docs.activeIndex());
            recreate();
            return;
        }
        applyEditorPrefs();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(sessionTask);
        captureCurrent();
        session.save(docs.all(), docs.activeIndex());
    }

    @Override
    public void onBackPressed() {
        if (findCtrl.isVisible()) {
            findCtrl.hide();
            return;
        }
        super.onBackPressed();
    }

    // ---------------- Toolbar & menu ----------------

    private void wireToolbar() {
        findViewById(R.id.btnNew).setOnClickListener(v -> newTab());
        findViewById(R.id.btnNewTab).setOnClickListener(v -> newTab());
        findViewById(R.id.btnOpen).setOnClickListener(v -> openPicker());
        findViewById(R.id.btnSave).setOnClickListener(v -> saveActive());
        btnUndo.setOnClickListener(v -> editorCtrl.undo());
        btnRedo.setOnClickListener(v -> editorCtrl.redo());
        findViewById(R.id.btnFind).setOnClickListener(v -> findCtrl.show());
        findViewById(R.id.btnMore).setOnClickListener(this::showPopupMenu);
    }

    private void showPopupMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenuInflater().inflate(R.menu.main_menu, popup.getMenu());
        popup.getMenu().findItem(R.id.menuWordWrap).setChecked(prefs.wordWrap());
        popup.getMenu().findItem(R.id.menuLineNumbers).setChecked(prefs.lineNumbers());
        Document d = docs.active();
        popup.getMenu().findItem(R.id.menuReadOnly).setChecked(d != null && d.readOnly);
        popup.setOnMenuItemClickListener(this::onMenuItem);
        popup.show();
    }

    private boolean onMenuItem(MenuItem item) {
        int id = item.getItemId();
        Document d = docs.active();
        if (id == R.id.menuSaveAs) { saveAs(); return true; }
        if (id == R.id.menuRecent) { showRecents(); return true; }
        if (id == R.id.menuGotoLine) { showGotoDialog(); return true; }
        if (id == R.id.menuSelectAll) { editorCtrl.view().selectAll(); return true; }
        if (id == R.id.menuDuplicateLine) { editorCtrl.duplicateLine(); return true; }
        if (id == R.id.menuDeleteLine) { editorCtrl.deleteLine(); return true; }
        if (id == R.id.menuInsertDateTime) {
            String now = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date());
            editorCtrl.insertAtCursor(now);
            return true;
        }
        if (id == R.id.menuWordWrap) {
            prefs.setWordWrap(!prefs.wordWrap());
            applyEditorPrefs();
            return true;
        }
        if (id == R.id.menuLineNumbers) {
            prefs.setLineNumbers(!prefs.lineNumbers());
            applyEditorPrefs();
            return true;
        }
        if (id == R.id.menuReadOnly) {
            if (d != null) {
                captureCurrent();
                d.readOnly = !d.readOnly;
                editorCtrl.setReadOnly(d.readOnly);
            }
            return true;
        }
        if (id == R.id.menuShare) { shareText(); return true; }
        if (id == R.id.menuSettings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        if (id == R.id.menuAbout) { showAbout(); return true; }
        return false;
    }

    // ---------------- Tab ----------------

    private void newTab() {
        captureCurrent();
        docs.newUntitled(getString(R.string.untitled));
        loadActiveIntoEditor();
        renderTabs();
        scheduleSessionSave();
        editorCtrl.view().requestFocus();
    }

    private void switchTab(int index) {
        if (index == docs.activeIndex()) return;
        captureCurrent();
        docs.setActive(index);
        loadActiveIntoEditor();
        renderTabs();
        scheduleSessionSave();
    }

    private void closeTab(int index) {
        if (index < 0 || index >= docs.size()) return;
        if (index == docs.activeIndex()) captureCurrent();
        Document d = docs.all().get(index);
        if (d.dirty) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.unsaved_title)
                    .setMessage(getString(R.string.unsaved_message, d.displayName))
                    .setPositiveButton(R.string.btn_save, (dlg, w) -> {
                        if (index != docs.activeIndex()) switchTab(index);
                        // Tab ditutup oleh writeToUri HANYA setelah tulis berhasil;
                        // bila gagal, tab tetap terbuka agar data tidak hilang.
                        pendingCloseDoc = d;
                        saveActive();
                    })
                    .setNegativeButton(R.string.btn_discard, (dlg, w) -> finishCloseTab(index))
                    .setNeutralButton(R.string.btn_cancel, null)
                    .show();
        } else {
            finishCloseTab(index);
        }
    }

    private void finishCloseTab(int index) {
        if (index >= 0 && index < docs.size() && pendingCloseDoc == docs.all().get(index)) {
            pendingCloseDoc = null;
        }
        boolean wasActive = index == docs.activeIndex();
        docs.remove(index);
        if (docs.size() == 0) docs.newUntitled(getString(R.string.untitled));
        if (wasActive) loadActiveIntoEditor();
        renderTabs();
        scheduleSessionSave();
    }

    private void renderTabs() {
        tabsContainer.removeAllViews();
        int active = docs.activeIndex();
        for (int i = 0; i < docs.size(); i++) {
            Document d = docs.all().get(i);
            boolean isActive = i == active;
            final int idx = i;

            LinearLayout tab = new LinearLayout(this);
            tab.setOrientation(LinearLayout.HORIZONTAL);
            tab.setGravity(Gravity.CENTER_VERTICAL);
            tab.setPadding(dp(14), 0, dp(4), 0);
            if (isActive) tab.setBackgroundResource(R.drawable.bg_tab_active);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
            tab.setLayoutParams(lp);
            tab.setOnClickListener(v -> switchTab(idx));

            TextView title = new TextView(this);
            title.setText(d.tabTitle());
            title.setTextSize(13);
            title.setMaxLines(1);
            title.setTextColor(getColor(isActive ? R.color.text_primary : R.color.text_muted));
            tab.addView(title);

            // Tombol x per tab: satu-satunya cara menutup tab (menu titik tiga
            // tidak lagi memuat item tutup tab).
            ImageView close = new ImageView(this);
            close.setImageResource(R.drawable.ic_close);
            close.setColorFilter(getColor(R.color.text_faint));
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(dp(28), dp(28));
            close.setLayoutParams(clp);
            close.setPadding(dp(7), dp(7), dp(7), dp(7));
            close.setContentDescription(getString(R.string.action_close_tab));
            close.setOnClickListener(v -> closeTab(idx));
            tab.addView(close);

            tabsContainer.addView(tab);
        }
    }

    // ---------------- Sinkronisasi dokumen <-> editor ----------------

    private void captureCurrent() {
        Document d = docs.active();
        if (d == null) return;
        d.text = editorCtrl.text();
        d.cursor = editorCtrl.cursor();
    }

    private void loadActiveIntoEditor() {
        Document d = docs.active();
        if (d == null) return;
        editorCtrl.loadDocument(d.text, d.cursor);
        editorCtrl.setReadOnly(d.readOnly);
        updateStatus();
    }

    private void onEditorTextChanged() {
        markDirtyAndRefresh();
        handler.removeCallbacks(autosaveTask);
        handler.postDelayed(autosaveTask, 1500);
        scheduleSessionSave();
    }

    private void markDirtyAndRefresh() {
        Document d = docs.active();
        if (d != null && !d.dirty) {
            d.dirty = true;
            renderTabs();
        } else if (d != null) {
            // judul tab sudah ada titik dirty; cukup segarkan status
        }
        if (findCtrl.isVisible()) findCtrl.refresh();
        updateStatus();
    }

    private void scheduleSessionSave() {
        handler.removeCallbacks(sessionTask);
        handler.postDelayed(sessionTask, 1000);
    }

    // ---------------- Status bar ----------------

    private void updateStatus() {
        if (editorCtrl == null) return;
        String text = editorCtrl.text();
        int focus = editorCtrl.focusOffset();
        int selStart = editorCtrl.cursor();
        int selEnd = editorCtrl.selectionEnd();
        int[] lc = TextStats.lineCol(text, focus);
        String left = getString(R.string.status_position, lc[0], lc[1]);
        if (selEnd > selStart) {
            left += " · " + getString(R.string.status_selection, selEnd - selStart);
        }
        Document d = docs.active();
        if (d != null && d.dirty) left += " · " + getString(R.string.status_modified);
        tvStatusLeft.setText(left);

        TextStats stats = TextStats.of(text);
        tvStatusRight.setText(getString(R.string.status_stats, stats.lines, stats.words, stats.chars));
    }

    private void updateUndoButtons() {
        btnUndo.setEnabled(editorCtrl.canUndo());
        btnUndo.setAlpha(editorCtrl.canUndo() ? 1f : 0.35f);
        btnRedo.setEnabled(editorCtrl.canRedo());
        btnRedo.setAlpha(editorCtrl.canRedo() ? 1f : 0.35f);
    }

    private void applyEditorPrefs() {
        editorCtrl.applyTypography(prefs.fontSizeSp(), prefs.fontFamily(), prefs.wordWrap());
        editorCtrl.setLineNumbersVisible(prefs.lineNumbers());
    }

    // ---------------- File: buka / simpan ----------------

    private void openPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES,
                new String[]{"text/*", "application/json", "application/xml"});
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        startActivityForResult(intent, REQ_OPEN);
    }

    private void saveActive() {
        Document d = docs.active();
        if (d == null) return;
        captureCurrent();
        if (d.uri == null) {
            saveAs();
        } else {
            writeToUri(d, true);
        }
    }

    private void saveAs() {
        Document d = docs.active();
        if (d == null) return;
        captureCurrent();
        // Nama diketik di dialog sendiri, BUKAN di picker sistem: kolom nama
        // picker tertutup keyboard di sebagian ROM, dan MIME intent yang
        // dikunci text/plain membuat picker menempelkan .txt ke nama lain.
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setSingleLine(true);
        input.setHint(R.string.dialog_saveas_hint);
        input.setText(d.displayName);
        int dot = d.displayName.lastIndexOf('.');
        input.setSelection(0, dot > 0 ? dot : d.displayName.length());
        input.setTextColor(getColor(R.color.text_primary));
        int pad = dp(20);
        input.setPadding(pad, dp(8), pad, dp(8));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.action_save_as)
                .setView(input)
                .setPositiveButton(R.string.btn_save, (dlg, w) -> {
                    String name = FileNames.sanitize(input.getText().toString());
                    if (name.isEmpty()) {
                        pendingCloseDoc = null;
                        showToast(getString(R.string.toast_invalid_name));
                        return;
                    }
                    launchSaveAsPicker(d, name);
                })
                .setNegativeButton(R.string.btn_cancel, (dlg, w) -> pendingCloseDoc = null)
                .setOnCancelListener(dlg -> pendingCloseDoc = null)
                .create();
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                            | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
        input.requestFocus();
    }

    private void launchSaveAsPicker(Document d, String name) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        // MIME mengikuti ekstensi nama yang diketik agar picker tidak
        // menempelkan ekstensi lain (mis. .txt) ke nama tersebut.
        intent.setType(FileNames.mimeTypeFor(name));
        intent.putExtra(Intent.EXTRA_TITLE, name);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        startActivityForResult(intent, REQ_SAVE_AS);
    }

    private void writeToUri(Document d, boolean notify) {
        final String text = d.text;
        final Uri uri = d.uri;
        io.execute(() -> {
            try {
                files.writeText(uri, text);
                handler.post(() -> {
                    d.dirty = false;
                    renderTabs();
                    updateStatus();
                    if (notify) showToast(getString(R.string.toast_saved));
                    if (pendingCloseDoc == d && docs.all().contains(d)) {
                        finishCloseTab(docs.all().indexOf(d));
                    }
                });
            } catch (Exception e) {
                handler.post(() -> {
                    pendingCloseDoc = null;
                    showToast(getString(R.string.toast_save_failed));
                });
            }
        });
    }

    private void openUri(Uri uri, boolean fromRecent) {
        io.execute(() -> {
            try {
                String text = files.readText(uri);
                String name = files.displayName(uri, getString(R.string.untitled));
                handler.post(() -> {
                    captureCurrent();
                    int existing = docs.indexOfUri(uri.toString());
                    if (existing >= 0) {
                        docs.setActive(existing);
                    } else {
                        Document d = new Document(uri, name);
                        d.text = text;
                        // Tab Untitled kosong yang belum diubah -> pakai ulang
                        Document cur = docs.active();
                        if (cur != null && cur.uri == null && !cur.dirty && cur.text.isEmpty()
                                && docs.size() == 1) {
                            docs.all().set(0, d);
                            docs.setActive(0);
                        } else {
                            docs.add(d);
                        }
                    }
                    prefs.addRecent(uri.toString(), name);
                    loadActiveIntoEditor();
                    renderTabs();
                    scheduleSessionSave();
                    showToast(getString(R.string.toast_opened, name));
                });
            } catch (Exception e) {
                handler.post(() -> {
                    showToast(getString(fromRecent
                            ? R.string.toast_recent_gone : R.string.toast_open_failed));
                    if (fromRecent) prefs.removeRecent(uri.toString());
                });
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            if (requestCode == REQ_SAVE_AS) pendingCloseDoc = null;
            return;
        }
        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        } catch (Exception ignored) {
        }
        if (requestCode == REQ_OPEN) {
            openUri(uri, false);
        } else if (requestCode == REQ_SAVE_AS) {
            Document d = docs.active();
            if (d == null) return;
            captureCurrent();
            d.uri = uri;
            d.displayName = files.displayName(uri, d.displayName);
            prefs.addRecent(uri.toString(), d.displayName);
            renderTabs();
            writeToUri(d, true);
        }
    }

    private void handleViewIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (!Intent.ACTION_VIEW.equals(action) && !Intent.ACTION_EDIT.equals(action)) return;
        Uri uri = intent.getData();
        if (uri == null) return;
        try {
            getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
        }
        openUri(uri, false);
        intent.setAction(null); // jangan diproses ulang saat recreate
    }

    // ---------------- Dialog & lainnya ----------------

    private void showRecents() {
        List<Prefs.Recent> recents = prefs.recents();
        if (recents.isEmpty()) {
            showToast(getString(R.string.recent_empty));
            return;
        }
        String[] names = new String[recents.size()];
        for (int i = 0; i < recents.size(); i++) names[i] = recents.get(i).name;
        new AlertDialog.Builder(this)
                .setTitle(R.string.action_recent)
                .setItems(names, (dlg, which) -> openUri(Uri.parse(recents.get(which).uri), true))
                .setNeutralButton(R.string.recent_clear, (dlg, w) -> prefs.clearRecents())
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void showGotoDialog() {
        int max = editorCtrl.lineCount();
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint(getString(R.string.dialog_goto_hint, max));
        input.setTextColor(getColor(R.color.text_primary));
        int pad = dp(20);
        input.setPadding(pad, dp(8), pad, dp(8));
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_goto_title)
                .setView(input)
                .setPositiveButton(R.string.btn_go, (dlg, w) -> {
                    try {
                        int line = Integer.parseInt(input.getText().toString().trim());
                        editorCtrl.gotoLine(Math.max(1, Math.min(line, max)));
                    } catch (NumberFormatException ignored) {
                    }
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void shareText() {
        String text = editorCtrl.text();
        if (text.isEmpty()) {
            showToast(getString(R.string.toast_nothing_to_share));
            return;
        }
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(intent, getString(R.string.action_share)));
    }

    private static final String REPO_URL = "https://github.com/eLgorythm/ItzEditor";
    private static final String REPO_LABEL = "github.com/eLgorythm/ItzEditor";

    private void showAbout() {
        String version = "?";
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {
        }
        String text = getString(R.string.about_message, version) + "\n" + REPO_LABEL;
        android.text.SpannableString message = new android.text.SpannableString(text);
        int linkStart = text.indexOf(REPO_LABEL);
        message.setSpan(new android.text.style.URLSpan(REPO_URL), linkStart,
                linkStart + REPO_LABEL.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.about_title)
                .setMessage(message)
                .setPositiveButton(R.string.btn_ok, null)
                .show();
        TextView messageView = dialog.findViewById(android.R.id.message);
        if (messageView != null) {
            messageView.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
        }
    }

    private void showCrashLogIfAny() {
        String crash = CrashReporter.consumeLastCrash(this);
        if (crash == null) return;
        new AlertDialog.Builder(this)
                .setTitle(R.string.crash_title)
                .setMessage(getString(R.string.crash_message) + "\n\n" + crash)
                .setPositiveButton(R.string.btn_copy, (dlg, w) -> {
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("crash", crash));
                })
                .setNegativeButton(R.string.btn_close, null)
                .show();
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
