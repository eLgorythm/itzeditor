# ItzEditor — aturan R8/ProGuard untuk build manual tanpa Gradle.
# Yang di-keep hanya titik masuk yang dipanggil framework lewat refleksi:
# Application, Activity (dari AndroidManifest), dan View kustom (dari layout XML).
# Sisanya bebas di-shrink + di-obfuscate.

-keepattributes SourceFile,LineNumberTable

# Application & Activity di-instantiate oleh sistem berdasarkan nama di manifest
-keep class me.fndlabs.itzeditor.ItzEditorApp { public <init>(); }
-keep class me.fndlabs.itzeditor.ui.MainActivity { public <init>(); }
-keep class me.fndlabs.itzeditor.ui.SettingsActivity { public <init>(); }
-keep class me.fndlabs.itzeditor.ui.BaseActivity { public <init>(); }

# EditorView di-inflate dari layout XML lewat refleksi (semua konstruktor View)
-keep class me.fndlabs.itzeditor.editor.EditorView { public <init>(...); }

# Buang pemanggilan Log dari rilis
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

-dontwarn **
