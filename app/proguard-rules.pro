# Keep accessibility service class so it is not obfuscated
-keep class com.example.tiktokcleanmode.TikTokCleanService { *; }
-keep class com.example.tiktokcleanmode.PermissionHelper { *; }

# General Android rules
-keepattributes *Annotation*
-dontwarn okhttp3.**
