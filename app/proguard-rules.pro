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
-keep class javax.naming.** { *; }
-dontwarn javax.naming.**
-keep class org.ietf.jgss.** { *; }
-dontwarn org.ietf.jgss.**
# Keep Google Sign In classes
-keep class com.google.android.gms.auth.** { *; }
-keep class com.google.android.gms.common.** { *; }
-dontwarn android.net.http.AndroidHttpClient
-dontwarn org.apache.http.**
# Keep Google Sheets API classes
-keep class com.google.api.** { *; }
-keep class com.google.apis.** { *; }
-dontwarn com.sun.net.httpserver.**
-dontwarn java.awt.**
-dontwarn com.google.api.client.extensions.java6.auth.oauth2.**
-dontwarn com.google.api.client.extensions.jetty.auth.oauth2.**
# Keep Gson classes
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Keep your model classes
-keep class com.example.txe.Expander { *; }
-keep class com.example.txe.MainViewModel { *; }
-keep class com.example.txe.ExpanderManager { *; }
-keep class com.example.txe.GoogleSheetsManager { *; }

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile