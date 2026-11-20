package de.robv.android.xposed.callbacks;
import android.content.pm.ApplicationInfo;
public class XC_LoadPackage {
    public static class LoadPackageParam {
        public boolean isFirstApplication;
        public String processName;
        public String packageName;
        public ApplicationInfo appInfo;
    }
}
