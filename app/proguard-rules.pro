# Keep kotlinx.serialization classes
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keep,includedescriptorclasses class com.aikeyboard.app.**$$serializer { *; }
-keepclassmembers class com.aikeyboard.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.aikeyboard.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# Keep IME service
-keep class com.aikeyboard.app.ime.** { *; }
