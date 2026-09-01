# Chaquopy loads this application module by its fully qualified Python name.
# Keep only the generated bridge surface required for that runtime lookup.
-keep class com.nanzhufeng.videodownloader.probe.YtDlpProbe { *; }
-keep class com.chaquo.python.** { *; }

# Compose saves mutable state through Android's Parcelable protocol. The framework finds
# CREATOR reflectively while restoring a cold-start Activity, so R8 must keep this exact field.
-keepclassmembers class androidx.compose.runtime.ParcelableSnapshotMutableState {
    public static final android.os.Parcelable$Creator CREATOR;
}
