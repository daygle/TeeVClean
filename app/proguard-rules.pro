# R8 keep rules for the release build. AndroidX (including Compose, WorkManager and
# Lifecycle) ships its own consumer rules, so nothing here is needed to keep them working;
# these only keep stack traces readable in Play Console crash reports.

# Keep the source file name and line numbers so crash reports can be retraced against the
# R8 mapping embedded in the .aab (or the mapping.txt workflow artifact).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
