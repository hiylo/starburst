# Add project specific ProGuard rules here.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# MNN JNI callback interface — native code resolves its name via FindClass,
# so it must not be obfuscated or renamed.
-keep class org.hiylo.starburst.ml.MnnLlm$StreamingCallback { *; }
-keepclassmembers class org.hiylo.starburst.ml.MnnLlm {
    native <methods>;
}

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class org.hiylo.starburst.**$$serializer { *; }
-keepclassmembers class org.hiylo.starburst.** {
    *** Companion;
}
-keepclasseswithmembers class org.hiylo.starburst.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp (Ktor OkHttp engine + 直接调用)
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# JSch (SSH 隧道)——加密类经反射加载，R8 误删会导致
# ClassNotFoundException: com.jcraft.jsch.jce.Random，SSH 连接全线失败。
-keep class com.jcraft.jsch.** { *; }
-keep class com.jcraft.jzlib.** { *; }
-dontwarn com.jcraft.jsch.**
-dontwarn com.jcraft.jzlib.**

# Ktor
-keep class io.ktor.** { *; }
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.atomicfu.**
-dontwarn io.netty.**
-dontwarn com.typesafe.**
-dontwarn org.slf4j.**
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean
