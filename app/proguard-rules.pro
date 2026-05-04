# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# ── Keep source file + line numbers for crash reports ─────────────────────
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── kotlinx.serialization ─────────────────────────────────────────────────
# Keep serializers used by Supabase Postgrest (reflection-based decode)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.bkdiagnostic.**$$serializer { *; }
-keepclassmembers class com.example.bkdiagnostic.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.bkdiagnostic.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ── Supabase / Ktor ──────────────────────────────────────────────────────
-keep class io.github.jan.supabase.** { *; }
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# ── USB Serial for Android ───────────────────────────────────────────────
-keep class com.hoho.android.usbserial.** { *; }

# ── Coil SVG ─────────────────────────────────────────────────────────────
-keep class coil3.svg.** { *; }
