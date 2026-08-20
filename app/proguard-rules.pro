# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# 保持指定的单个类不被混淆
-keep class epic.dumpdex.suianc.ArscProcessor { *; }
-keep class epic.dumpdex.suianc.AssetProcessor { *; }
-keep class epic.dumpdex.suianc.AxmlProcessor { *; }
-keep class epic.dumpdex.suianc.Decryptor { *; }
-keep class epic.dumpdex.suianc.ElfConfigParser { *; }
-keep class epic.dumpdex.suianc.FileUtils { *; }
-keep class epic.dumpdex.suianc.MainActivity { *; }
-keep class epic.dumpdex.suianc.SigBypassModifier { *; }
-keep class epic.dumpdex.suianc.WatermarkLayout { *; }
