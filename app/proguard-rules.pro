# RewardSkip uses reflection against third-party ad SDKs.
# Keep all hook classes and their members intact.
-keep class com.opencode.rewardskip.** { *; }
-dontwarn de.robv.android.xposed.**
