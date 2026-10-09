# Reglas ProGuard/R8 del proyecto.
# Conservar información de línea para stacktraces legibles en producción.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- OpenCV ---
# Usa JNI: los nombres de clases/métodos nativos no pueden ofuscarse.
-keep class org.opencv.** { *; }
-dontwarn org.opencv.**

# --- LiteRT (mantiene el namespace Java org.tensorflow.lite en la API Interpreter) ---
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.**

# --- Gson ---
# Necesario para deserializar genéricos (TypeToken) y campos por reflexión.
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# --- Modelos serializados (Gson y lectores de formatos legados) ---
# Estas clases se (de)serializan por reflexión: conservar nombres de campos.
-keep class com.monsteraltech.skincare.body.mole.model.** { *; }
-keep class com.monsteraltech.skincare.data.SessionData { *; }
-keep class com.monsteraltech.skincare.account.AccountSettings { *; }
-keep class com.monsteraltech.skincare.account.UserInfo { *; }
-keep class com.monsteraltech.skincare.body.mole.performance.LocalCacheManager$CacheEntry { *; }

# --- Firebase ---
# Firebase incluye sus propias reglas consumer; solo silenciar avisos opcionales.
-dontwarn com.google.firebase.**

# --- Eliminar logging de depuración en release ---
# R8 elimina estas llamadas (y sus argumentos) del bytecode final.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
