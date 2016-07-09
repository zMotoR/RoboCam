# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in C:\Android\sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

-keep class com.android.vending.billing.**

-dontwarn javax.servlet.**
-dontwarn org.osgi.framework.**
-dontwarn org.jboss.marshalling.**
-dontwarn org.jboss.logging.**
-dontwarn org.osgi.util.tracker.**
-dontwarn java.nio.channels.**
-dontwarn org.jboss.netty.channel.socket.http.**
-dontwarn com.google.protobuf.**
-dontwarn org.apache.log4j.**
-dontwarn org.osgi.service.log.**
-dontwarn org.slf4j.**
-dontwarn sun.misc.**


