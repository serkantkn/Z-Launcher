# ════════════════════════════════════════════════════════════
# ZUNE LAUNCHER — RELEASE SHRINKING
# ════════════════════════════════════════════════════════════
#
# Almost nothing here is about keeping our own code: the launcher reaches everything through
# ordinary calls, so R8 can follow it. What needs saying out loud is the code that is only ever
# reached by name — the mail library's providers, and the platform classes we call reflectively.

# A crash report is worth reading, so keep the file and line a stack trace was thrown from.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── JavaMail (Email hub) ────────────────────────────────────
# The session looks its transports and stores up by name, out of the javamail.* provider files
# in META-INF; the classes those name are never called directly and would otherwise go.
-keep class javax.mail.** { *; }
-keep class javax.activation.** { *; }
-keep class com.sun.mail.** { *; }
-keep class myjava.awt.datatransfer.** { *; }
-dontwarn javax.mail.**
-dontwarn javax.activation.**
-dontwarn com.sun.mail.**
-dontwarn myjava.awt.**
-dontwarn java.awt.**
-dontwarn javax.security.sasl.**
-dontwarn org.ietf.jgss.**

# ── Media3 ──────────────────────────────────────────────────
# The player picks its renderers and extractors up by name at runtime.
-dontwarn androidx.media3.**

# ── Platform reflection ─────────────────────────────────────
# The notification shade is opened through StatusBarManager, which is not in the SDK.
-dontwarn android.app.StatusBarManager

# ── Coil ────────────────────────────────────────────────────
-dontwarn coil.**
-dontwarn okhttp3.**
-dontwarn okio.**

# ── Logging ─────────────────────────────────────────────────
# ZuneLog.d already compiles itself out of a release; this catches anything that calls
# android.util.Log directly. Warnings and errors are kept: a crash report has to say something.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
