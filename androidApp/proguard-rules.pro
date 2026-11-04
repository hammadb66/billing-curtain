# ==========================================
# 1. Compose & Kotlin Core
# ==========================================
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
-keep class androidx.compose.material.** { *; }
-keep class androidx.compose.navigation.** { *; }
-keep class kotlinx.coroutines.** { *; }

# Kotlin Metadata (Reflection ke liye zaroori)
-keep class kotlin.Metadata { *; }

# Enums ko obfuscate hone se bachayein (warna valueOf() crash karega)
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ==========================================
# 2. Aapki App ki Main Classes & ViewModels
# ==========================================
-keep class com.example.billing.MainActivity { *; }
-keep class * extends androidx.lifecycle.ViewModel { *; }

# ==========================================
# 3. Koin (Dependency Injection)
# ==========================================
-keep class org.koin.** { *; }
-keep class com.example.billing.di.** { *; }

# ==========================================
# 4. Kotlin Serialization & Navigation
# ==========================================
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keep class kotlinx.serialization.** { *; }
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class **$$serializer { *; }

# ==========================================
# 5. SQLDelight (Database)
# ==========================================
# Generated database aur queries ko delete hone se bachayein
-keep class com.example.billing.BillingDatabase { *; }
-keep class com.example.billing.*Queries { *; }
-keep class app.cash.sqldelight.** { *; }