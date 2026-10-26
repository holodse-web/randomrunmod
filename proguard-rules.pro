-dontshrink
-dontoptimize
-dontpreverify
-ignorewarnings
-dontwarn **
-dontnote **
-allowaccessmodification
-repackageclasses 'r'

-keepattributes Signature,InnerClasses,EnclosingMethod,Exceptions,RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault,Record,PermittedSubclasses,NestHost,NestMembers,LineNumberTable,SourceFile
-renamesourcefileattribute SourceFile

# Fabric entrypoint and Mixin classes are referenced by name from fabric.mod.json / randomrun.mixins.json
-keep class com.randomrun.infrastructure.RandomRunMod { *; }
-keep class com.randomrun.mixin.** { *; }

# Gson reads and writes fields by name (config, profiles, history, network models)
-keepclassmembers class com.randomrun.** { <fields>; }
-keepclassmembers enum com.randomrun.** { public static **[] values(); public static ** valueOf(java.lang.String); }
-keepclassmembers class * extends java.lang.Record { *; }
