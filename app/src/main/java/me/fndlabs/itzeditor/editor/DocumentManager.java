package me.fndlabs.itzeditor.editor;

import java.util.ArrayList;
import java.util.List;

/** Mengelola daftar tab dokumen + tab aktif. Tanpa logika UI. */
public final class DocumentManager {

    private final List<Document> documents = new ArrayList<>();
    private int activeIndex = -1;

    public List<Document> all() { return documents; }

    public int size() { return documents.size(); }

    public Document active() {
        return activeIndex >= 0 && activeIndex < documents.size() ? documents.get(activeIndex) : null;
    }

    public int activeIndex() { return activeIndex; }

    public Document add(Document doc) {
        documents.add(doc);
        activeIndex = documents.size() - 1;
        return doc;
    }

    /**
     * Nama Untitled diambil dari tab yang SEDANG terbuka: tanpa nomor bila bebas,
     * selain itu nomor terkecil yang belum dipakai. Menutup tab membebaskan nomornya,
     * jadi penomoran tidak naik terus tanpa batas.
     */
    public Document newUntitled(String untitledBase) {
        String name = untitledBase;
        if (nameTaken(name)) {
            int n = 2;
            while (nameTaken(untitledBase + " " + n)) n++;
            name = untitledBase + " " + n;
        }
        return add(new Document(null, name));
    }

    private boolean nameTaken(String name) {
        for (Document d : documents) {
            if (name.equals(d.displayName)) return true;
        }
        return false;
    }

    public void setActive(int index) {
        if (index >= 0 && index < documents.size()) activeIndex = index;
    }

    /** Hapus tab; kembalikan indeks tab aktif baru (-1 bila tidak ada lagi). */
    public int remove(int index) {
        if (index < 0 || index >= documents.size()) return activeIndex;
        documents.remove(index);
        if (documents.isEmpty()) {
            activeIndex = -1;
        } else if (index <= activeIndex) {
            activeIndex = Math.max(0, activeIndex - 1);
        }
        return activeIndex;
    }

    /** Cari tab yang sudah membuka URI ini (agar tidak dobel). */
    public int indexOfUri(String uriString) {
        if (uriString == null) return -1;
        for (int i = 0; i < documents.size(); i++) {
            Document d = documents.get(i);
            if (d.uri != null && uriString.equals(d.uri.toString())) return i;
        }
        return -1;
    }
}
