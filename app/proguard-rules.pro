# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class app.dongyu.** {
    *** Companion;
}
-keepclasseswithmembers class app.dongyu.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class app.dongyu.**$$serializer { *; }
# MapLibre
-keep class org.maplibre.** { *; }
-dontwarn org.maplibre.**
-dontwarn com.google.android.gms.**
