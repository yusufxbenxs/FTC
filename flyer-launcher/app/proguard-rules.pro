# kotlinx.serialization: keep generated serializers for the versioned config schema.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.flyer.launcher.**$$serializer { *; }
-keepclassmembers class com.flyer.launcher.** { *** Companion; }
-keepclasseswithmembers class com.flyer.launcher.** { kotlinx.serialization.KSerializer serializer(...); }

# Hidden StatusBarManager methods are resolved reflectively (expand notification shade).
-keep class android.app.StatusBarManager { *; }
