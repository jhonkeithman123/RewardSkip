package com.opencode.rewardskip;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minimal JSON reader for on-device recipe configs. No external dependency. */
public final class Json {
    private final String src;
    private int pos;

    private Json(String src) {
        this.src = src;
    }

    public static Object parse(String text) {
        Json p = new Json(text);
        Object v = p.value();
        p.skipWs();
        return v;
    }

    private void skipWs() {
        while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) pos++;
    }

    private boolean eat(char c) {
        skipWs();
        if (pos < src.length() && src.charAt(pos) == c) {
            pos++;
            return true;
        }
        return false;
    }

    private Object value() {
        skipWs();
        if (pos >= src.length()) return null;
        char c = src.charAt(pos);
        if (c == '{') return object();
        if (c == '[') return array();
        if (c == '"') return string();
        if (c == 't' && src.startsWith("true", pos)) { pos += 4; return Boolean.TRUE; }
        if (c == 'f' && src.startsWith("false", pos)) { pos += 5; return Boolean.FALSE; }
        if (c == 'n' && src.startsWith("null", pos)) { pos += 4; return null; }
        return number();
    }

    private Map<String, Object> object() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        pos++; // {
        while (true) {
            skipWs();
            if (pos >= src.length()) break;
            if (src.charAt(pos) == '}') { pos++; break; }
            String key = string();
            eat(':');
            m.put(key, value());
            if (!eat(',')) {
                if (eat('}')) break;
                break;
            }
        }
        return m;
    }

    private List<Object> array() {
        List<Object> a = new ArrayList<Object>();
        pos++; // [
        while (true) {
            skipWs();
            if (pos >= src.length()) break;
            if (src.charAt(pos) == ']') { pos++; break; }
            a.add(value());
            if (!eat(',')) {
                if (eat(']')) break;
                break;
            }
        }
        return a;
    }

    private String string() {
        skipWs();
        StringBuilder sb = new StringBuilder();
        if (pos >= src.length() || src.charAt(pos) != '"') return "";
        pos++;
        while (pos < src.length()) {
            char c = src.charAt(pos++);
            if (c == '"') break;
            if (c == '\\' && pos < src.length()) {
                char e = src.charAt(pos++);
                if (e == 'n') sb.append('\n');
                else if (e == 'r') sb.append('\r');
                else if (e == 't') sb.append('\t');
                else sb.append(e);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private Object number() {
        int start = pos;
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.') pos++;
            else break;
        }
        String s = src.substring(start, pos);
        if (s.length() == 0) return null;
        try {
            if (s.indexOf('.') >= 0) return Double.valueOf(s);
            return Long.valueOf(s);
        } catch (NumberFormatException ignore) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asObj(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : null;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> asArr(Object o) {
        return o instanceof List ? (List<Object>) o : null;
    }

    public static String str(Map<String, Object> m, String key, String def) {
        Object v = m.get(key);
        return v instanceof String ? (String) v : def;
    }
}
