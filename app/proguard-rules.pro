# TV Tools R8 rules
#
# The app talks to TVs through libadb-android, which layers Conscrypt TLS on
# top of a repackaged Bouncy Castle (sun-security-android). Those libraries
# reach classes and native methods by name, so their packages are kept whole.
# Everything else (our code, Compose, Material icons) is fair game for R8.

# ---- Wireless ADB ----
-keep class io.github.muntashirakon.adb.** { *; }
-keep class org.conscrypt.** { *; }
-keep class android.sun.security.** { *; }
-dontwarn org.conscrypt.**
-dontwarn android.sun.security.**
-dontwarn io.github.muntashirakon.adb.**

# ---- SFTP (JSch builds cipher lists from class names at runtime) ----
-keep class com.jcraft.jsch.** { *; }
-dontwarn com.jcraft.jsch.**

# ---- QR codes (ZXing) ----
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# Annotations and generic signatures are used by libraries at runtime.
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# Kotlin metadata keeps readable stack traces and reflection-friendly types.
-keep class kotlin.Metadata { *; }
