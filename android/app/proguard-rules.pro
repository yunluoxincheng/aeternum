# ==============================================================================
# Aeternum ProGuard Rules
# ==============================================================================
# 后量子安全密钥管理系统 - Android UI 层混淆规则
#
# 版本: 1.0.0
# 最后更新: 2026-02-15
#
# 本文件定义了 Aeternum Android 应用的 ProGuard 混淆规则，确保 Release 构建时：
# 1. 保留所有必要的公共 API
# 2. 正确处理 Compose UI 组件
# 3. 保留 Kotlin 协程和反射代码
# 4. 保留 UniFFI 生成的桥接代码
# 5. 保留序列化数据类
# ==============================================================================

# ==============================================================================
# Kotlin 协程 (Coroutines)
# ==============================================================================
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepnames class kotlinx.coroutines.android.AndroidDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineDispatcher {}

# 协程调试
-keep class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keep class kotlinx.coroutines.CoroutineExceptionHandler {}

# ==============================================================================
# Kotlin 反射 & 元数据
# ==============================================================================
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes SourceFile
-keepattributes LineNumberTable

# Kotlin metadata
-keep class kotlin.Metadata { *; }
-keepclassmembers class * {
    @kotlin.Metadata *;
}

# ==============================================================================
# Jetpack Compose
# ==============================================================================
# Compose 运行时
-keep class androidx.compose.** { *; }
-keep class kotlin.Metadata { *; }

# Compose 生成的类
-keep class androidx.compose.** { *; }
-keepclassmembers class androidx.compose.** { *; }

# 保留 Compose UI 组件的可组合函数
-keepclassmembers,allowobfuscation class * {
    @androidx.compose.runtime.Composable *;
}

# 保留 @Composable inflatable 方法
-keep,allowobfuscation,allowshrinking class androidx.compose.** {
    <init>(...);
}

# Compose 节点
-keepclassmembers class androidx.compose.** {
    <init>(...);
}

# ==============================================================================
# Material Design 3
# ==============================================================================
-keep class androidx.compose.material3.** { *; }
-keep class androidx.compose.material.** { *; }

# Material 主题
-keep class androidx.compose.material3.MaterialTheme { *; }
-keep class androidx.compose.material.Surface { *; }

# ==============================================================================
# Navigation Compose
# ==============================================================================
-keep class androidx.navigation.** { *; }
-keepclassmembers class androidx.navigation.** { *; }

# 导航图
-keepclasseswithmembers class * {
    @androidx.navigation.NavGraph* <methods>;
}

# ==============================================================================
# Lifecycle & ViewModel
# ==============================================================================
-keep class androidx.lifecycle.** { *; }
-keepclassmembers class androidx.lifecycle.** { *; }

# ViewModel
-keep class * extends androidx.lifecycle.ViewModel {
    public <init>(...);
}

-keep class * extends androidx.lifecycle.AndroidViewModel {
    public <init>(...);
}

# ==============================================================================
# UniFFI 桥接代码 (生成代码，禁止混淆)
# ==============================================================================
-keep class aeternum.** { *; }
-keepclassmembers class aeternum.** { *; }

# UniFFI 生成的 Kotlin 接口
-keep class aeternum.uniffi.** { *; }
-keepclassmembers class aeternum.uniffi.** { *; }

# UniFFI scaffolding (Rust FFI)
-keep class uniffi.** { *; }
-keepclassmembers class uniffi.** { *; }

# ==============================================================================
# JNA (Java Native Access) - UniFFI 依赖
# ==============================================================================
-keep class com.sun.jna.** { *; }
-keepclassmembers class com.sun.jna.** { *; }

# JNA Native 库
-keepnames class com.sun.jna.Native
-keepclassmembers class * extends com.sun.jna.** {
    *;
}

# JNA Callback
-keep class * extends com.sun.jna.Callback {
    *;
}

# JNA Structure
-keep class * extends com.sun.jna.Structure {
    *;
}

# JNA Library 接口
-keep @com.sun.jna.NativeInterface interface * {
    *;
}

# ==============================================================================
# 数据类与序列化
# ==============================================================================
# 保留数据类 (用于状态机、ViewModel 等)
-keep @androidx.annotation.Keep class * { *; }
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}

# Kotlinx Serialization
-keepattributes *Annotation*
-keepclassmembers,allowobfuscation class * {
    @kotlinx.serialization.SerialName *;
}

# Parcelable
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ==============================================================================
# Koin 依赖注入
# ==============================================================================
-keep class org.koin.** { *; }
-keepclassmembers class org.koin.** { *; }

# Koin 模块定义
-keepclassmembers class * {
    public static <methods>;
}

# ==============================================================================
# SQLCipher & 数据库
# ==============================================================================
-keep class net.sqlcipher.** { *; }
-keepclassmembers class net.sqlcipher.** { *; }

# ==============================================================================
# Biometric (生物识别)
# ==============================================================================
-keep class androidx.biometric.** { *; }
-keepclassmembers class androidx.biometric.** { *; }

# ==============================================================================
# Accompanist (UI 工具库)
# ==============================================================================
-keep class com.google.accompanist.** { *; }
-keepclassmembers class com.google.accompanist.** { *; }

# ==============================================================================
# AndroidX DataStore
# ==============================================================================
-keep class androidx.datastore.** { *; }
-keepclassmembers class androidx.datastore.** { *; }

# ==============================================================================
# Rust Native 库 (JNI)
# ==============================================================================
# 保留 native 方法
-keepclasseswithmembernames class * {
    native <methods>;
}

# JNI 相关类
-keepclassmembers class * {
    @java.lang.JniTarget *;
}

# ==============================================================================
# Rust 对象句柄 (禁止优化移除)
# ==============================================================================
# VaultSession 句柄
-keep class io.aeternum.bridge.VaultSessionHandle { *; }
-keepclassmembers class io.aeternum.bridge.VaultSessionHandle {
    *;
}

# AeternumBridge
-keep class io.aeternum.bridge.AeternumBridge { *; }
-keepclassmembers class io.aeternum.bridge.AeternumBridge {
    *;
}

# ==============================================================================
# 安全边界 (禁止混淆敏感类)
# ==============================================================================
# AndroidSecurityManager (安全层)
-keep class io.aeternum.security.AndroidSecurityManager { *; }
-keepclassmembers class io.aeternum.security.AndroidSecurityManager {
    *;
}

# VaultRepository (数据层)
-keep class io.aeternum.data.VaultRepository { *; }
-keepclassmembers class io.aeternum.data.VaultRepository {
    *;
}

# ==============================================================================
# UI 组件 (禁止混淆 Composable 函数)
# ==============================================================================
-keepclassmembers class io.aeternum.ui.** {
    @androidx.compose.runtime.Composable public * (...);
}

# UI 状态类
-keep class io.aeternum.ui.state.** { *; }
-keepclassmembers class io.aeternum.ui.state.** { *; }

# ViewModel 类
-keep class io.aeternum.ui.viewmodel.** { *; }
-keepclassmembers class io.aeternum.ui.viewmodel.** {
    public <init>(...);
}

# ==============================================================================
# 错误与异常处理
# ==============================================================================
-keepclassmembers class * extends java.lang.Throwable {
    public <init>(...);
    public <init>(java.lang.String);
    public <init>(java.lang.String, java.lang.Throwable);
}

# Sealed classes (用于错误处理)
-keep class io.aeternum.** { *; }

# ==============================================================================
# 性能优化
# ==============================================================================
# 激进的优化
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-dontpreverify
-verbose

# 优化
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*,!code/allocation/variable

# ==============================================================================
# 日志与调试 (Release 构建移除)
# ==============================================================================
# 移除日志 (使用 Timber/自定义日志工具时建议)
# -assumenosideeffects class android.util.Log {
#     public static boolean isLoggable(java.lang.String, int);
#     public static int v(...);
#     public static int d(...);
#     public static int i(...);
#     public static int w(...);
#     public static int e(...);
# }

# ==============================================================================
# 警告抑制
# ==============================================================================
# 忽略警告
-dontwarn androidx.compose.**
-dontwarn androidx.databinding.**
-dontwarn kotlinx.coroutines.**
-dontwarn org.koin.**
-dontwarn java.lang.invoke.**
-dontwarn javax.lang.**
-dontwarn kotlin.**
-dontwarn kotlin.annotations.**
-dontwarn kotlin.coroutines.**
-dontwarn kotlin.jvm.**
-dontwarn kotlin.text.**
-dontwarn kotlin.time.**
-dontwarn uniffi.**
-dontwarn aeternum.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn java.awt.**

# ==============================================================================
# 保留行号信息 (用于崩溃堆栈)
# ==============================================================================
-keepattributes SourceFile,LineNumberTable

# ==============================================================================
# 自定义配置
# ==============================================================================
# 保留 Aeternum 包下的所有公共成员 (UI 层入口)
-keep public class io.aeternum.** {
    public *;
}

# 保留 Application 类
-keep class io.aeternum.AeternumApplication {
    public <init>();
    public void onCreate();
}

# ==============================================================================
# 测试规则 (单元测试、UI 测试)
# ==============================================================================
# 保留测试相关类
-keep class * extends android.app.Activity {
    public <init>(...);
}

-keep class * extends android.app.Fragment {
    public <init>(...);
}

-keep class * extends androidx.fragment.app.Fragment {
    public <init>(...);
}

# ==============================================================================
# 结束
# ==============================================================================
