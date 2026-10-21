package me.fndlabs.itzeditor.editor;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.EditText;

/** EditText yang melaporkan perubahan seleksi (EditText bawaan tidak punya listener seleksi). */
public final class EditorView extends EditText {

    public interface SelectionListener {
        void onSelectionChanged(int start, int end);
    }

    private SelectionListener selectionListener;

    public EditorView(Context context) { super(context); }

    public EditorView(Context context, AttributeSet attrs) { super(context, attrs); }

    public EditorView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setSelectionListener(SelectionListener listener) {
        this.selectionListener = listener;
    }

    @Override
    protected void onSelectionChanged(int selStart, int selEnd) {
        super.onSelectionChanged(selStart, selEnd);
        if (selectionListener != null) selectionListener.onSelectionChanged(selStart, selEnd);
    }
}
