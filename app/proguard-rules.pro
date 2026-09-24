# ==================== Room ====================
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-dontwarn androidx.room.paging.**

# ==================== Moshi ====================
-keepclassmembers class * { @com.squareup.moshi.JsonClass <fields>; }
-keep @com.squareup.moshi.JsonClass class * { *; }
-keep class **JsonAdapter { *; }

# ==================== OkHttp / Retrofit ====================
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations,RuntimeInvisibleParameterAnnotations
-keepattributes RuntimeVisibleTypeAnnotations,RuntimeInvisibleTypeAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# ==================== ML Kit OCR ====================
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.mlkit.**

# ==================== Hilt / Dagger ====================
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.lifecycle.HiltViewModel { *; }
-keep class * extends dagger.hilt.android.Hilt_Android { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
-keep @javax.inject.Inject class * { *; }
-keepclassmembers class * { @javax.inject.Inject *; }

# ==================== Kotlin Metadata ====================
-keep class kotlin.Metadata { *; }
-keepclassmembers class ** {
    @kotlin.Metadata *;
    <init>(...);
}

# ==================== Coroutines ====================
-keep class kotlinx.coroutines.android.** { *; }
-dontwarn kotlinx.coroutines.flow.**

# ==================== Compose ====================
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ==================== DataStore ====================
-keep class androidx.datastore.** { *; }

# ==================== Security Crypto ====================
-keep class androidx.security.crypto.** { *; }

# ==================== SAF / DocumentFile ====================
-keep class androidx.documentfile.** { *; }

# ==================== App 主类与实体 ====================
-keep class com.personalip.app.** { *; }
-keep class com.personalip.app.data.local.entity.** { *; }
-keep class com.personalip.app.data.ai.remote.** { *; }
-keep class com.personalip.app.data.ai.PostDraft { *; }

# 保留 R 文件，避免资源 ID 失联
-keep class com.personalip.app.R { *; }
-keep class com.personalip.app.R$* { *; }

# 保留枚举
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# 保留注解
-keepattributes *Annotation*
-keepattributes Signature, InnerClasses, EnclosingMethod
