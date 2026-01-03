# --------------------------------------------------------------------------
# REGLAS GENERALES
# --------------------------------------------------------------------------
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose
-dontwarn androidx.**
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# Mantener la compatibilidad con Kotlin
-keep class kotlin.Metadata { *; }
-keepclassmembers class ** {
    @org.jetbrains.annotations.Nullable <methods>;
    @org.jetbrains.annotations.NotNull <methods>;
    @org.jetbrains.annotations.Nullable <fields>;
    @org.jetbrains.annotations.NotNull <fields>;
}

# --------------------------------------------------------------------------
# RETROFIT & GSON (¡CRÍTICO!)
# --------------------------------------------------------------------------
# Retrofit usa reflexión para ver los tipos de retorno
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions

# Gson: Necesita mantener los nombres de variables para mapear el JSON
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-dontwarn com.google.gson.reflect.TypeToken
-keep class com.google.gson.** { *; }

# --- [IMPORTANTE] TUS MODELOS DE DATOS ---
# Debes decirle a ProGuard que NO cambie el nombre de tus clases de datos (Data Classes)
# que reciben respuestas de la API.
# Si tus modelos están en 'com.ulpro.animalrecognizer.data.model', usa la línea de abajo:
# -keep class com.ulpro.animalrecognizer.data.** { *; }

# Si no estás seguro, puedes usar esta regla más general para serializados con Gson:
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# --------------------------------------------------------------------------
# GLIDE (Carga de imágenes)
# --------------------------------------------------------------------------
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
  **[] $VALUES;
  public *;
}
-dontwarn com.bumptech.glide.load.resource.bitmap.VideoDecoder

# --------------------------------------------------------------------------
# TENSORFLOW LITE
# --------------------------------------------------------------------------
# Evita que se eliminen las clases nativas de TensorFlow
-dontwarn org.tensorflow.**
-keep class org.tensorflow.** { *; }
-keep class org.tensorflow.lite.** { *; }

# --------------------------------------------------------------------------
# LOTTIE (Animaciones)
# --------------------------------------------------------------------------
-keep class com.airbnb.lottie.** { *; }

# --------------------------------------------------------------------------
# COROUTINES & OKHTTP
# --------------------------------------------------------------------------
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.android.AndroidExceptionPreHandler {
    <init>();
}