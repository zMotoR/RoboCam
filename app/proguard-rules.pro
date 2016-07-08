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
#Warning:org.jboss.netty.channel.socket.http.HttpTunnelingServlet: can't find superclass or interface javax.servlet.http.HttpServlet
#Warning:org.jboss.netty.container.osgi.NettyBundleActivator: can't find superclass or interface org.osgi.framework.BundleActivator
#Warning:org.jboss.netty.handler.codec.marshalling.ChannelBufferByteInput: can't find superclass or interface org.jboss.marshalling.ByteInput
#Warning:org.jboss.marshalling.ModularClassResolver: can't find referenced class org.jboss.modules.Module
#Warning:__redirected.__XMLEventFactory: can't find superclass or interface javax.xml.stream.XMLEventFactory
#Warning:org.jboss.marshalling.reflect.SerializableClass: can't find referenced class sun.reflect.ReflectionFactory
#Warning:org.jboss.marshalling.reflect.SerializableClass: can't find referenced class sun.reflect.ReflectionFactory
#Warning:com.sun.javafx.property.adapter.PropertyDescriptor$Listener: can't find superclass or interface java.beans.VetoableChangeListener
#Warning:com.sun.prism.j2d.J2DPrismGraphics$AdaptorPathIterator: can't find superclass or interface java.awt.geom.PathIterator
#Warning:com.sun.webkit.dom.JSObject: can't find superclass or interface netscape.javascript.JSObject


