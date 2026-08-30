# kotlinx.serialization keeps its generated serializers on the companion.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.niklauncher.** {
    *** Companion;
}
-keepclasseswithmembers class com.niklauncher.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# The native runtime layer is reached over JNI, which ProGuard cannot see.
-keepclasseswithmembernames class * {
    native <methods>;
}
