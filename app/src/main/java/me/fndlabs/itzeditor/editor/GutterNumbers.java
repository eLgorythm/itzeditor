package me.fndlabs.itzeditor.editor;

/**
 * Membangun teks gutter nomor baris dari baris VISUAL editor — logika murni, diuji di JVM.
 *
 * Baris visual yang merupakan awal baris logis (offset 0, atau karakter sebelumnya '\n')
 * mendapat nomor urut logis. Baris visual lanjutan akibat word-wrap dibiarkan kosong,
 * supaya nomor selalu sejajar dengan awal baris yang sebenarnya di layar.
 */
public final class GutterNumbers {

    private GutterNumbers() {}

    /**
     * @param text             isi editor
     * @param visualLineStarts offset awal tiap baris visual (dari Layout.getLineStart)
     * @return teks gutter, satu segmen per baris visual, dipisah '\n'
     */
    public static String build(CharSequence text, int[] visualLineStarts) {
        StringBuilder sb = new StringBuilder();
        int logical = 0;
        for (int i = 0; i < visualLineStarts.length; i++) {
            if (i > 0) sb.append('\n');
            int start = visualLineStarts[i];
            boolean logicalStart = start == 0
                    || (start > 0 && start <= text.length() && text.charAt(start - 1) == '\n');
            if (logicalStart) {
                logical++;
                sb.append(logical);
            }
        }
        return sb.toString();
    }
}
