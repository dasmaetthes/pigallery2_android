# ProGuard rules for PiGallery2 Android Client

# Keep all data classes used by Moshi and Retrofit
-keep class com.example.data.** { *; }

# Moshi rules
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.squareup.moshi.** { *; }
-keepclassmembers class com.squareup.moshi.** { *; }

# Retrofit/OkHttp
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions

# Room
-keep class androidx.room.** { *; }
-keepclassmembers class androidx.room.** { *; }

# Coil
-keep class coil.** { *; }
-keepclassmembers class coil.** { *; }

# Coroutines
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepnames class kotlinx.coroutines.** { *; }

# Keep line numbers for debugging
-keepattributes SourceFile,LineNumberTable
