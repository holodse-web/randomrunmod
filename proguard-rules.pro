-dontshrink
-dontoptimize
-ignorewarnings
-dontwarn **
-dontnote **
-allowaccessmodification
-repackageclasses 'r'

-keepattributes Signature,InnerClasses,EnclosingMethod,Exceptions,RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault,Record,PermittedSubclasses,NestHost,NestMembers,LineNumberTable,SourceFile
-renamesourcefileattribute SourceFile

-keep class com.randomrun.infrastructure.RandomRunMod { *; }
-keep class com.randomrun.mixin.** { *; }

-keepclassmembers class com.randomrun.** { <fields>; }
-keepclassmembers enum com.randomrun.** { public static **[] values(); public static ** valueOf(java.lang.String); }
-keepclassmembers class * extends java.lang.Record { *; }
