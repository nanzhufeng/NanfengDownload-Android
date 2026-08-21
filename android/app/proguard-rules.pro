# Chaquopy loads this application module by its fully qualified Python name.
# Keep only the generated bridge surface required for that runtime lookup.
-keep class com.nanzhufeng.videodownloader.probe.YtDlpProbe { *; }
-keep class com.chaquo.python.** { *; }
