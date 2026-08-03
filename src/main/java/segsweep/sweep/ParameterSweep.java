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

/**
 * Ordered parameter axes for the combinations displayed and reported to the user.
 *
 * <p>For the classical engine, {@code from}/{@code to}/{@code step} ranges and
 * explicit value lists are a display window over a component tree computed for the
 * whole crop. They are not a request to recompute only those settings.</p>
 */
public final class ParameterSweep {

    public enum Method {
        CLASSICAL("classical", "Classical"),
        STARDIST("stardist", "StarDist"),
        CELLPOSE("cellpose", "Cellpose");

        private final String stableKey;
        private final String label;

        Method(String stableKey, String label) {
            this.stableKey = stableKey;
            this.label = label;
        }

        public String stableKey() {
            return stableKey;
        }

        public String label() {
            return label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum ValueRole {
        DISPLAY_WINDOW("display_window");

        private final String stableKey;

        ValueRole(String stableKey) {
            this.stableKey = stableKey;
        }

        public String stableKey() {
            return stableKey;
        }
    }

    private final Method method;
    private final Map<ParameterKey, ParameterValueList> valueLists;
    private final CropSpec cropSpec;
    private final String channelName;

    public ParameterSweep(Method method,
                          Map<? extends ParameterKey, ParameterValueList> valueLists) {
        this(method, valueLists, CropSpec.full(), "");
    }

    public ParameterSweep(Method method,
                          Map<? extends ParameterKey, ParameterValueList> valueLists,
                          String channelName) {
        this(method, valueLists, CropSpec.full(), channelName);
    }

    public ParameterSweep(Method method,
                          Map<? extends ParameterKey, ParameterValueList> valueLists,
                          CropSpec cropSpec,
                          String channelName) {
        if (method == null) {
            throw new IllegalArgumentException("method must not be null");
        }
        if (valueLists == null) {
            throw new IllegalArgumentException("valueLists must not be null");
        }
        this.method = method;
        this.valueLists = Collections.unmodifiableMap(copyInKeyOrder(valueLists));
        this.cropSpec = cropSpec == null ? CropSpec.full() : cropSpec;
        this.channelName = channelName == null ? "" : channelName;
    }

    public Method method() {
        return method;
    }

    public Method getMethod() {
        return method;
    }

    public Map<ParameterKey, ParameterValueList> valueLists() {
        return valueLists;
    }

    public Map<ParameterKey, ParameterValueList> getValueLists() {
        return valueLists;
    }

    public CropSpec cropSpec() {
        return cropSpec;
    }

    public CropSpec getCropSpec() {
        return cropSpec;
    }

    public String channelName() {
        return channelName;
    }

    public String getChannelName() {
        return channelName;
    }

    public ValueRole valueRole() {
        return ValueRole.DISPLAY_WINDOW;
    }

    public boolean valuesSelectDisplayWindow() {
        return valueRole() == ValueRole.DISPLAY_WINDOW;
    }

    public List<ParameterKey> parameterKeys() {
        return new ArrayList<ParameterKey>(valueLists.keySet());
    }

    public List<ParameterId> parameterIds() {
        List<ParameterId> out = new ArrayList<ParameterId>();
        for (ParameterKey key : valueLists.keySet()) {
            if (key instanceof ParameterId) {
                out.add((ParameterId) key);
            }
        }
        return out;
    }

    public long cellCount() {
        long count = 1L;
        for (ParameterValueList values : valueLists.values()) {
            int size = values.size();
            if (size <= 0) {
                return 0L;
            }
            if (count > Long.MAX_VALUE / size) {
                return Long.MAX_VALUE;
            }
            count *= size;
        }
        return count;
    }

    /**
     * Returns combinations in parameter-key order. Earlier keys vary more slowly,
     * so a 3-by-4 sweep yields three outer groups of four inner-axis values.
     */
    public List<ParameterCombo> combos() {
        long count = cellCount();
        if (count > Integer.MAX_VALUE) {
            throw new IllegalStateException("too many parameter combinations: " + count);
        }
        List<ParameterKey> ids = new ArrayList<ParameterKey>(valueLists.keySet());
        List<ParameterCombo> out = new ArrayList<ParameterCombo>((int) count);
        buildCombos(ids, 0, new LinkedHashMap<ParameterKey, Object>(), out);
        return out;
    }

    public String toCanonicalJson() {
        LinkedHashMap<String, Object> root = new LinkedHashMap<String, Object>();
        root.put("channelName", channelName);
        root.put("crop", cropSpec.toCanonicalObject());
        root.put("method", method.stableKey());
        root.put("valueRole", valueRole().stableKey());
        LinkedHashMap<String, Object> values = new LinkedHashMap<String, Object>();
        List<String> keys = new ArrayList<String>();
        Map<String, ParameterValueList> byName = new LinkedHashMap<String, ParameterValueList>();
        for (Map.Entry<ParameterKey, ParameterValueList> entry : valueLists.entrySet()) {
            String key = ParameterCombo.canonicalJsonKey(entry.getKey());
            keys.add(key);
            byName.put(key, entry.getValue());
        }
        Collections.sort(keys);
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            values.put(key, byName.get(key).values());
        }
        root.put("values", values);
        return CanonicalJson.write(root);
    }

    private void buildCombos(List<ParameterKey> ids,
                             int index,
                             LinkedHashMap<ParameterKey, Object> current,
                             List<ParameterCombo> out) {
        if (index >= ids.size()) {
            out.add(new ParameterCombo(current));
            return;
        }
        ParameterKey id = ids.get(index);
        ParameterValueList values = valueLists.get(id);
        for (int i = 0; i < values.size(); i++) {
            current.put(id, values.get(i));
            buildCombos(ids, index + 1, current, out);
        }
        current.remove(id);
    }

    private static Map<ParameterKey, ParameterValueList> copyInKeyOrder(
            Map<? extends ParameterKey, ParameterValueList> source) {
        List<ParameterKey> ids = new ArrayList<ParameterKey>(source.keySet());
        Collections.sort(ids, new Comparator<ParameterKey>() {
            @Override
            public int compare(ParameterKey a, ParameterKey b) {
                return compareStorageKeys(a, b);
            }
        });
        LinkedHashMap<ParameterKey, ParameterValueList> out =
                new LinkedHashMap<ParameterKey, ParameterValueList>();
        for (int i = 0; i < ids.size(); i++) {
            ParameterKey id = ids.get(i);
            if (id == null) {
                throw new IllegalArgumentException("parameter id must not be null");
            }
            ParameterValueList values = source.get(id);
            if (values == null) {
                throw new IllegalArgumentException("value list must not be null for " + id);
            }
            out.put(id, values);
        }
        return out;
    }

    private static int compareStorageKeys(ParameterKey a, ParameterKey b) {
        if (a instanceof ParameterId && b instanceof ParameterId) {
            return ((ParameterId) a).compareTo((ParameterId) b);
        }
        String aKey = a == null ? "" : a.stableKey();
        String bKey = b == null ? "" : b.stableKey();
        int byStableKey = aKey.compareTo(bKey);
        if (byStableKey != 0) {
            return byStableKey;
        }
        return ParameterCombo.canonicalJsonKey(a).compareTo(ParameterCombo.canonicalJsonKey(b));
    }
}
