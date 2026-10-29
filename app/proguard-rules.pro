# Proguard / R8 rules for RecompressBypass

# Retain attributes for reflection & debugging
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable

# Preserve Data Classes
-keep class com.recompress.bypass.CompressedVideoRecord { *; }
-keep class com.recompress.bypass.AppUpdateInfo { *; }
-keep class com.recompress.bypass.RemoteNotice { *; }

# Preserve Compose Runtime
-keep class androidx.compose.** { *; }

# Preserve AndroidX Activity / Lifecycle
-keep class androidx.lifecycle.** { *; }
-keep class androidx.activity.** { *; }

# Preserve Android FileProvider
-keep class androidx.core.content.FileProvider { *; }

# Preserve MP4 Optimizer internals
-keep class com.recompress.bypass.Mp4BypassOptimizer { *; }
-keep class com.recompress.bypass.UpdateManager { *; }
-keep class com.recompress.bypass.NoticeManager { *; }
-keep class com.recompress.bypass.HistoryRepository { *; }
