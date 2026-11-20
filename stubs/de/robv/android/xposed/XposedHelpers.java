package de.robv.android.xposed;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
public final class XposedHelpers {
    public static Class<?> findClassIfExists(String className, ClassLoader classLoader) { return null; }
    public static Object callMethod(Object obj, String methodName, Object... args) { return null; }
    public static Object callStaticMethod(Class<?> clazz, String methodName, Object... args) { return null; }
    public static XC_MethodHook.Unhook findAndHookMethod(Class<?> clazz, String methodName, Object... parameterTypesAndCallback) { return null; }
    public static Field findFirstFieldByExactType(Class<?> clazz, Class<?> type) { return null; }
    public static Object getObjectField(Object obj, String fieldName) { return null; }
    public static Object newInstance(Class<?> clazz, Object... args) { return null; }
}
