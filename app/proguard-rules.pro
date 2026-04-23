# R8 keep rules for PokerMaster

# JNI native bridges (M4 llama.cpp 통합 시 사용)
-keep class com.infocar.pokermaster.engine.llm.NativeBridge { native <methods>; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Hilt
-keep,allowobfuscation,allowshrinking class dagger.hilt.android.internal.managers.**
-keep class * extends androidx.lifecycle.ViewModel { <init>(...); }
-keep @dagger.hilt.android.HiltAndroidApp class *

# Kotlin Coroutines
-dontwarn kotlinx.coroutines.debug.**

# Compose runtime (BOM 이 처리하지만 보강)
-keep class androidx.compose.runtime.** { *; }

# Crash reporting / native stack traces (M4 NDK 통합 시 필요)
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
