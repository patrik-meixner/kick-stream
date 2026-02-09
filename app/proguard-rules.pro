# Retrofit
-keepattributes Signature
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# kotlinx-serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class com.kickstream.data.api.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.kickstream.data.api.model.**$$serializer { *; }

# Pusher
-keep class com.pusher.** { *; }
-dontwarn com.pusher.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# ZXing
-keep class com.google.zxing.** { *; }

# SLF4J (pulled in by Pusher)
-dontwarn org.slf4j.**

# Coil
-dontwarn coil.**
