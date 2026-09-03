# review-ktx reaches GMS Tasks, which reference an annotation that is not on the compile classpath.
# R8 treats a missing reference as an error rather than a warning, so without this the release build
# does not get past minifyReleaseWithR8
-dontwarn com.google.android.gms.common.annotation.NoNullnessRewrite
