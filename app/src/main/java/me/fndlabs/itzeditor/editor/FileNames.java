package me.fndlabs.itzeditor.editor;

import java.util.Locale;

/**
 * Logika murni nama file untuk alur "Simpan sebagai": membersihkan nama
 * ketikan pengguna dan menurunkan tipe MIME dari ekstensinya.
 *
 * MIME yang cocok dengan ekstensi itu penting: picker file sistem
 * menempelkan ekstensi sesuai MIME yang dideklarasikan intent (nama
 * "catatan.md" dengan MIME text/plain bisa berakhir sebagai
 * "catatan.md.txt"). Ekstensi yang tidak dikenal dipetakan ke "*\/\*"
 * agar tidak ada ekstensi yang dipaksakan.
 */
public final class FileNames {

    private FileNames() {
    }

    /**
     * Bersihkan nama file ketikan pengguna: potong spasi di tepi dan buang
     * pemisah path. Mengembalikan "" bila hasilnya tidak layak jadi nama
     * file (kosong, "." atau "..").
     */
    public static String sanitize(String raw) {
        if (raw == null) return "";
        String name = raw.replace("/", "").replace("\\", "").trim();
        if (name.equals(".") || name.equals("..")) return "";
        return name;
    }

    /**
     * Ekstensi huruf kecil tanpa titik, atau "" bila tidak ada. Berkas
     * tersembunyi ala ".gitignore" dianggap tidak berekstensi.
     */
    public static String extensionOf(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) return "";
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** Tipe MIME untuk intent ACTION_CREATE_DOCUMENT, diturunkan dari ekstensi nama. */
    public static String mimeTypeFor(String name) {
        switch (extensionOf(name)) {
            case "txt":
            case "text":
            case "log":
                return "text/plain";
            case "md":
            case "markdown":
                return "text/markdown";
            case "json":
                return "application/json";
            case "xml":
                return "application/xml";
            case "html":
            case "htm":
                return "text/html";
            case "csv":
                return "text/csv";
            default:
                return "*/*";
        }
    }
}
