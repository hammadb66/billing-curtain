# Main class
-keep class com.example.billing.MainKt { *; }

# Puri app ki classes ko bachayein (Desktop Compose ke liye safe hai)
-keep class com.example.billing.** { *; }

# Optimization band rakhein (Compose Desktop crash se bachne ke liye)
-dontoptimize

# Warnings ignore karein
-dontwarn **

# Kotlin Metadata & Serialization
-keep class kotlin.Metadata { *; }
-keepattributes *Annotation*, InnerClasses
-keep @kotlinx.serialization.Serializable class * { *; }
-keep class **$$serializer { *; }
-keepclassmembers class * {
    *** Companion;
}

# Koin (Desktop bhi Koin use kar raha hai)
-keep class org.koin.** { *; }

# SQLDelight JDBC
-keep class app.cash.sqldelight.** { *; }