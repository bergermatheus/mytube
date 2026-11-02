# Rhino's optional Java-bean bridge refers to desktop JDK APIs absent on Android.
# MyTube evaluates extractor scripts in Rhino's safe standard scope, without Java beans.
-dontwarn java.beans.BeanDescriptor
-dontwarn java.beans.BeanInfo
-dontwarn java.beans.IntrospectionException
-dontwarn java.beans.Introspector
-dontwarn java.beans.PropertyDescriptor

# Upstream NewPipe's reflection-based time-ago parser and Rhino built-ins.
# https://github.com/TeamNewPipe/NewPipe/blob/dev/app/proguard-rules.pro
-keep class org.schabi.newpipe.extractor.timeago.patterns.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
# Protobuf uses generated field names for its compact message schemas.
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite { <fields>; }
# Desktop-only Rhino optimizer; the extractor explicitly selects interpreted mode.
-dontwarn jdk.dynalink.**
