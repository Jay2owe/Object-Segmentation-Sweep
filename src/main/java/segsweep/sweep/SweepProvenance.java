/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep.sweep;

import java.awt.Rectangle;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SweepProvenance implements Serializable {
    private static final long serialVersionUID = 1L;

    private final CropSpec crop;
    private final int fullWidth;
    private final int fullHeight;
    private final int fullDepth;
    private final Map<ParameterId, ParameterValueList> displayedRanges;
    private final String calibrationUnit;
    private final double voxelVolume;

    public SweepProvenance(CropSpec crop,
                           int fullWidth,
                           int fullHeight,
                           int fullDepth,
                           Map<ParameterId, ParameterValueList> displayedRanges,
                           String calibrationUnit,
                           double voxelVolume) {
        if (crop == null) {
            throw new IllegalArgumentException("crop must not be null");
        }
        if (fullWidth <= 0 || fullHeight <= 0 || fullDepth <= 0) {
            throw new IllegalArgumentException("full image dimensions must be positive");
        }
        if (displayedRanges == null) {
            throw new IllegalArgumentException("displayedRanges must not be null");
        }
        if (!Double.isFinite(voxelVolume) || voxelVolume <= 0.0d) {
            throw new IllegalArgumentException("voxelVolume must be a positive finite value");
        }
        this.crop = crop;
        this.fullWidth = fullWidth;
        this.fullHeight = fullHeight;
        this.fullDepth = fullDepth;
        this.displayedRanges = Collections.unmodifiableMap(copyDisplayedRanges(displayedRanges));
        this.calibrationUnit = calibrationUnit == null ? "" : calibrationUnit.trim();
        this.voxelVolume = voxelVolume;
    }

    public CropSpec crop() {
        return crop;
    }

    public int fullWidth() {
        return fullWidth;
    }

    public int fullHeight() {
        return fullHeight;
    }

    public int fullDepth() {
        return fullDepth;
    }

    public Map<ParameterId, ParameterValueList> displayedRanges() {
        return displayedRanges;
    }

    public String calibrationUnit() {
        return calibrationUnit;
    }

    public double voxelVolume() {
        return voxelVolume;
    }

    public double cropFraction() {
        Rectangle bounds = crop.boundsFor(fullWidth, fullHeight);
        double cropArea = (double) bounds.width * (double) bounds.height;
        double fullArea = (double) fullWidth * (double) fullHeight;
        return fullArea <= 0.0d ? 0.0d : cropArea / fullArea;
    }

    public boolean belowMinimumFraction(double minimum) {
        if (!Double.isFinite(minimum)) {
            throw new IllegalArgumentException("minimum fraction must be finite");
        }
        return cropFraction() < minimum;
    }

    public boolean comparableWith(SweepProvenance other) {
        if (other == null) {
            return false;
        }
        if (!crop.equals(other.crop)) return false;
        if (fullWidth != other.fullWidth || fullHeight != other.fullHeight || fullDepth != other.fullDepth) {
            return false;
        }
        if (!displayedRanges.equals(other.displayedRanges)) return false;
        if (!calibrationUnit.equals(other.calibrationUnit)) return false;
        return Double.compare(voxelVolume, other.voxelVolume) == 0;
    }

    public String toCanonicalJson() {
        LinkedHashMap<String, Object> root = new LinkedHashMap<String, Object>();
        root.put("calibrationUnit", calibrationUnit);
        root.put("crop", crop.toCanonicalObject());
        root.put("displayedRanges", displayedRangesObject());
        root.put("fullDepth", Integer.valueOf(fullDepth));
        root.put("fullHeight", Integer.valueOf(fullHeight));
        root.put("fullWidth", Integer.valueOf(fullWidth));
        root.put("voxelVolume", Double.valueOf(voxelVolume));
        return CanonicalJson.write(root);
    }

    public static SweepProvenance fromCanonicalJson(String json) {
        Object parsed = new JsonParser(json).parse();
        if (!(parsed instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("Sweep provenance JSON must be an object");
        }
        Map<String, Object> root = stringObject(parsed, "root");
        CropSpec crop = CropSpec.fromCanonicalObject(stringObject(root.get("crop"), "crop"));
        Map<ParameterId, ParameterValueList> ranges =
                parseDisplayedRanges(stringObject(root.get("displayedRanges"), "displayedRanges"));
        return new SweepProvenance(
                crop,
                intValue(root.get("fullWidth"), "fullWidth"),
                intValue(root.get("fullHeight"), "fullHeight"),
                intValue(root.get("fullDepth"), "fullDepth"),
                ranges,
                stringValue(root.get("calibrationUnit"), "calibrationUnit"),
                doubleValue(root.get("voxelVolume"), "voxelVolume"));
    }

    private LinkedHashMap<String, Object> displayedRangesObject() {
        LinkedHashMap<String, Object> out = new LinkedHashMap<String, Object>();
        List<ParameterId> keys = new ArrayList<ParameterId>(displayedRanges.keySet());
        Collections.sort(keys);
        for (int i = 0; i < keys.size(); i++) {
            ParameterId id = keys.get(i);
            out.put(id.stableKey(), displayedRanges.get(id).values());
        }
        return out;
    }

    private static LinkedHashMap<ParameterId, ParameterValueList> copyDisplayedRanges(
            Map<ParameterId, ParameterValueList> source) {
        List<ParameterId> keys = new ArrayList<ParameterId>(source.keySet());
        Collections.sort(keys);
        LinkedHashMap<ParameterId, ParameterValueList> out =
                new LinkedHashMap<ParameterId, ParameterValueList>();
        for (int i = 0; i < keys.size(); i++) {
            ParameterId id = keys.get(i);
            if (id == null) {
                throw new IllegalArgumentException("displayed range id must not be null");
            }
            ParameterValueList values = source.get(id);
            if (values == null) {
                throw new IllegalArgumentException("displayed range values must not be null");
            }
            out.put(id, values);
        }
        return out;
    }

    private static Map<ParameterId, ParameterValueList> parseDisplayedRanges(Map<String, Object> raw) {
        LinkedHashMap<ParameterId, ParameterValueList> out =
                new LinkedHashMap<ParameterId, ParameterValueList>();
        List<String> keys = new ArrayList<String>(raw.keySet());
        Collections.sort(keys);
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            ParameterId id = ParameterId.fromStableKey(key);
            if (id == null) {
                throw new IllegalArgumentException("Unknown displayed range parameter: " + key);
            }
            Object value = raw.get(key);
            if (!(value instanceof List<?>)) {
                throw new IllegalArgumentException("Displayed range for " + key + " must be an array");
            }
            out.put(id, new ParameterValueList((List<?>) value));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> stringObject(Object value, String field) {
        if (!(value instanceof Map<?, ?>)) {
            throw new IllegalArgumentException(field + " must be an object");
        }
        LinkedHashMap<String, Object> out = new LinkedHashMap<String, Object>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
            out.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return out;
    }

    private static int intValue(Object value, String field) {
        if (!(value instanceof Number)) {
            throw new IllegalArgumentException(field + " must be a number");
        }
        long parsed = ((Number) value).longValue();
        if (parsed < Integer.MIN_VALUE || parsed > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(field + " is out of integer range");
        }
        return (int) parsed;
    }

    private static double doubleValue(Object value, String field) {
        if (!(value instanceof Number)) {
            throw new IllegalArgumentException(field + " must be a number");
        }
        double parsed = ((Number) value).doubleValue();
        if (!Double.isFinite(parsed)) {
            throw new IllegalArgumentException(field + " must be finite");
        }
        return parsed;
    }

    private static String stringValue(Object value, String field) {
        if (!(value instanceof String)) {
            throw new IllegalArgumentException(field + " must be a string");
        }
        return (String) value;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof SweepProvenance)) return false;
        SweepProvenance other = (SweepProvenance) obj;
        return comparableWith(other);
    }

    @Override
    public int hashCode() {
        int result = crop.hashCode();
        result = 31 * result + fullWidth;
        result = 31 * result + fullHeight;
        result = 31 * result + fullDepth;
        result = 31 * result + displayedRanges.hashCode();
        result = 31 * result + calibrationUnit.hashCode();
        long bits = Double.doubleToLongBits(voxelVolume);
        result = 31 * result + (int) (bits ^ (bits >>> 32));
        return result;
    }

    private static final class JsonParser {
        private final String text;
        private int index;

        JsonParser(String text) {
            this.text = text == null ? "" : text.trim();
        }

        Object parse() {
            Object value = parseValue();
            skipWhitespace();
            if (index != text.length()) {
                throw error("Unexpected trailing content");
            }
            return value;
        }

        private Object parseValue() {
            skipWhitespace();
            if (index >= text.length()) {
                throw error("Unexpected end of JSON");
            }
            char ch = text.charAt(index);
            if (ch == '{') return parseObject();
            if (ch == '[') return parseArray();
            if (ch == '"') return parseString();
            if (ch == '-' || (ch >= '0' && ch <= '9')) return parseNumber();
            if (startsWith("true")) {
                index += 4;
                return Boolean.TRUE;
            }
            if (startsWith("false")) {
                index += 5;
                return Boolean.FALSE;
            }
            if (startsWith("null")) {
                index += 4;
                return null;
            }
            throw error("Unexpected value");
        }

        private Map<String, Object> parseObject() {
            expect('{');
            LinkedHashMap<String, Object> out = new LinkedHashMap<String, Object>();
            skipWhitespace();
            if (peek('}')) {
                index++;
                return out;
            }
            while (true) {
                String key = parseString();
                skipWhitespace();
                expect(':');
                out.put(key, parseValue());
                skipWhitespace();
                if (peek('}')) {
                    index++;
                    return out;
                }
                expect(',');
            }
        }

        private List<Object> parseArray() {
            expect('[');
            List<Object> out = new ArrayList<Object>();
            skipWhitespace();
            if (peek(']')) {
                index++;
                return out;
            }
            while (true) {
                out.add(parseValue());
                skipWhitespace();
                if (peek(']')) {
                    index++;
                    return out;
                }
                expect(',');
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (index < text.length()) {
                char ch = text.charAt(index++);
                if (ch == '"') {
                    return sb.toString();
                }
                if (ch == '\\') {
                    if (index >= text.length()) throw error("Bad string escape");
                    char esc = text.charAt(index++);
                    if (esc == '"' || esc == '\\' || esc == '/') sb.append(esc);
                    else if (esc == 'b') sb.append('\b');
                    else if (esc == 'f') sb.append('\f');
                    else if (esc == 'n') sb.append('\n');
                    else if (esc == 'r') sb.append('\r');
                    else if (esc == 't') sb.append('\t');
                    else if (esc == 'u') {
                        if (index + 4 > text.length()) throw error("Bad unicode escape");
                        int code = Integer.parseInt(text.substring(index, index + 4), 16);
                        sb.append((char) code);
                        index += 4;
                    } else {
                        throw error("Bad string escape");
                    }
                } else {
                    sb.append(ch);
                }
            }
            throw error("Unterminated string");
        }

        private Number parseNumber() {
            int start = index;
            if (peek('-')) index++;
            while (index < text.length() && Character.isDigit(text.charAt(index))) index++;
            boolean decimal = false;
            if (peek('.')) {
                decimal = true;
                index++;
                while (index < text.length() && Character.isDigit(text.charAt(index))) index++;
            }
            if (index < text.length() && (text.charAt(index) == 'e' || text.charAt(index) == 'E')) {
                decimal = true;
                index++;
                if (index < text.length() && (text.charAt(index) == '+' || text.charAt(index) == '-')) index++;
                while (index < text.length() && Character.isDigit(text.charAt(index))) index++;
            }
            String raw = text.substring(start, index);
            if (decimal) {
                return Double.valueOf(Double.parseDouble(raw));
            }
            long parsed = Long.parseLong(raw);
            if (parsed >= Integer.MIN_VALUE && parsed <= Integer.MAX_VALUE) {
                return Integer.valueOf((int) parsed);
            }
            return Long.valueOf(parsed);
        }

        private void expect(char expected) {
            skipWhitespace();
            if (index >= text.length() || text.charAt(index) != expected) {
                throw error("Expected '" + expected + "'");
            }
            index++;
        }

        private boolean peek(char expected) {
            return index < text.length() && text.charAt(index) == expected;
        }

        private boolean startsWith(String value) {
            return text.startsWith(value, index);
        }

        private void skipWhitespace() {
            while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
                index++;
            }
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at JSON offset " + index);
        }
    }
}
