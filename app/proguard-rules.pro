# ================================================================================================
# REGLAS DE OFUSCACIÓN PARA PROTECCIÓN DE API KEY
# ================================================================================================

# Ofuscación agresiva - Renombrar todo lo posible
-repackageclasses ''
-allowaccessmodification
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5

# Ocultar nombres de archivos fuente
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable

# ================================================================================================
# PROTECCIÓN ESPECÍFICA PARA SEGURIDAD
# ================================================================================================

# Ofuscar TODO el paquete de seguridad (excepto métodos públicos necesarios)
-keep class com.hanserlod.debateia.security.ApiKeyProtection {
    public static java.lang.String getApiKey();
    public static boolean isApiKeyConfigured();
}

# Ofuscar nombres de métodos privados y constantes
-keepclassmembers class com.hanserlod.debateia.security.ApiKeyProtection {
    private static <methods>;
    private static <fields>;
}

# ================================================================================================
# ANDROID & KOTLIN
# ================================================================================================

# Keep Android components
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# Keep ViewBinding
-keep class * implements androidx.viewbinding.ViewBinding {
    public static ** inflate(android.view.LayoutInflater);
    public static ** bind(android.view.View);
}

# Keep ViewModel
-keep class * extends androidx.lifecycle.ViewModel {
    <init>();
}
-keep class * extends androidx.lifecycle.AndroidViewModel {
    <init>(android.app.Application);
}

# Kotlin
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings {
    <fields>;
}

# ================================================================================================
# GEMINI AI SDK
# ================================================================================================

-keep class com.google.ai.client.generativeai.** { *; }
-keep interface com.google.ai.client.generativeai.** { *; }

# ================================================================================================
# MARKWON (Markdown)
# ================================================================================================

-keep class io.noties.markwon.** { *; }

# ================================================================================================
# SERIALIZACIÓN
# ================================================================================================

-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# ================================================================================================
# REMOVER LOGS EN PRODUCCIÓN
# ================================================================================================

-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# ================================================================================================
# ADVERTENCIAS
# ================================================================================================

-dontwarn java.lang.invoke.StringConcatFactory
-dontwarn javax.annotation.**
-dontwarn org.jetbrains.annotations.**