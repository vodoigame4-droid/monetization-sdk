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

-keep class com.appsflyer.** { *; }
-keep class kotlin.jvm.internal.** {
    public <init>(...);
}
-keep public class com.android.installreferrer.** { *; }
-keep class kotlin.collections.**{ *; }
-keep class kotlin.Result$Companion { *; }
-keep class androidx.startup.** { *; }
-keepclassmembers class androidx.startup.** { *; }
-keep class com.google.ads.mediation.admob.AdMobAdapter {
    public <init>(...);
}
-keep class com.google.ads.mediation.** {
    public <init>(...);
}

-keep class com.google.android.gms.common.ConnectionResult {
   int SUCCESS;
}
-keep class com.google.android.gms.ads.identifier.AdvertisingIdClient {
   com.google.android.gms.ads.identifier.AdvertisingIdClient$Info getAdvertisingIdInfo(android.content.Context);
}
-keep class com.google.android.gms.ads.identifier.AdvertisingIdClient$Info {
   java.lang.String getId();
   boolean isLimitAdTrackingEnabled();
}

-dontwarn com.facebook.infer.annotation.Nullsafe$Mode
-dontwarn com.facebook.infer.annotation.Nullsafe

-keep public class com.android.installreferrer.** { *; }
