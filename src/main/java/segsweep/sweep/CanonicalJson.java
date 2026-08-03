/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep.sweep;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CanonicalJson {

    private CanonicalJson() {
    }

    public static LinkedHashMap<String, Object> object() {
        return new LinkedHashMap<String, Object>();
    }

    public static List<Object> list() {
        return new ArrayList<Object>();
    }

    public static String write(Object value) {
        StringBuilder out = new StringBuilder();
        append(out, canonicalize(value));
        return out.toString();
    }

    @SuppressWarnings("unchecked")
    private static Object canonicalize(Object value) {
        if (value instanceof Map<?, ?>) {
            Map<?, ?> source = (Map<?, ?>) value;
            List<Map.Entry<?, ?>> entries = new ArrayList<Map.Entry<?, ?>>(source.entrySet());
            Collections.sort(entries, new Comparator<Map.Entry<?, ?>>() {
                @Override
                public int compare(Map.Entry<?, ?> a, Map.Entry<?, ?> b) {
                    return String.valueOf(a.getKey()).compareTo(String.valueOf(b.getKey()));
                }
            });
            LinkedHashMap<String, Object> out = new LinkedHashMap<String, Object>();
            for (int i = 0; i < entries.size(); i++) {
                Map.Entry<?, ?> entry = entries.get(i);
                out.put(String.valueOf(entry.getKey()), canonicalize(entry.getValue()));
            }
            return out;
        }
        if (value instanceof List<?>) {
            List<?> source = (List<?>) value;
            List<Object> out = new ArrayList<Object>(source.size());
            for (int i = 0; i < source.size(); i++) {
                out.add(canonicalize(source.get(i)));
            }
            return out;
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private static void append(StringBuilder out, Object value) {
        if (value == null) {
            out.append("null");
            return;
        }
        if (value instanceof String) {
            appendString(out, (String) value);
            return;
        }
        if (value instanceof Number) {
            out.append(CanonicalScale.formatNumber((Number) value));
            return;
        }
        if (value instanceof Boolean) {
            out.append(((Boolean) value).booleanValue() ? "true" : "false");
            return;
        }
        if (value instanceof Map<?, ?>) {
            Map<String, Object> map = (Map<String, Object>) value;
            out.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if (!first) {
                    out.append(',');
                }
                appendString(out, entry.getKey());
                out.append(':');
                append(out, entry.getValue());
                first = false;
            }
            out.append('}');
            return;
        }
        if (value instanceof List<?>) {
            List<?> list = (List<?>) value;
            out.append('[');
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    out.append(',');
                }
                append(out, list.get(i));
            }
            out.append(']');
            return;
        }
        appendString(out, String.valueOf(value));
    }

    private static void appendString(StringBuilder out, String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\') {
                out.append('\\').append(c);
            } else if (c == '\b') {
                out.append("\\b");
            } else if (c == '\f') {
                out.append("\\f");
            } else if (c == '\n') {
                out.append("\\n");
            } else if (c == '\r') {
                out.append("\\r");
            } else if (c == '\t') {
                out.append("\\t");
            } else if (c < 0x20) {
                String hex = Integer.toHexString(c);
                out.append("\\u");
                for (int j = hex.length(); j < 4; j++) {
                    out.append('0');
                }
                out.append(hex);
            } else {
                out.append(c);
            }
        }
        out.append('"');
    }
}
