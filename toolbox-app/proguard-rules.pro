# Keep source/line metadata so release failures can be retraced to exact source evidence.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# No blanket keep rule is allowed here. Reflection/dynamic entry points must be
# explicitly registered and justified before they can be added.
