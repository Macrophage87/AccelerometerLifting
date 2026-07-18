# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keep,includedescriptorclasses class com.macrophage.accelerometerlifting.**$$serializer { *; }
-keepclassmembers class com.macrophage.accelerometerlifting.** { *** Companion; }
-keepclasseswithmembers class com.macrophage.accelerometerlifting.** { kotlinx.serialization.KSerializer serializer(...); }
