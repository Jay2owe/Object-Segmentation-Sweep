/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep;

import ij.ImagePlus;
import ij.Prefs;
import segsweep.sweep.CropSpec;

import java.awt.Rectangle;

/**
 * Remembers the last accepted dialog settings between sessions.
 *
 * <p>Tuning a segmentation is iterative: a user runs a sweep, narrows the range,
 * runs it again, and closes Fiji in the middle of that. Losing the ranges on
 * every close makes the tool feel like it has no memory of the work.</p>
 *
 * <p>State is stored as the plugin's own macro-option string rather than as a
 * bespoke serialisation, so persistence rides on the parser and writer that are
 * already covered by tests, and a stored value can be pasted straight into a
 * macro. Two fields are deliberately not restored:</p>
 *
 * <ul>
 *   <li><b>The image.</b> A title or path from a previous session names an image
 *       that is usually not open now, and silently re-selecting it would be
 *       worse than starting from the active image.</li>
 *   <li><b>A custom crop that does not fit.</b> A rectangle recorded against a
 *       1024-pixel-wide stack is meaningless on a 512-pixel one. It falls back
 *       to the whole image rather than being clamped into a region the user
 *       never chose.</li>
 * </ul>
 *
 * <p>Nothing here throws. A corrupt or half-written preference must not stop the
 * dialog opening, so every failure path returns defaults.</p>
 */
public final class SweepStateStore {

    static final String PREF_KEY = "segsweep.options";
    static final String PREF_VERSION_KEY = "segsweep.optionsVersion";

    /** Bumped when the stored shape changes; older values are ignored, not guessed at. */
    static final String VERSION = "1";

    private SweepStateStore() {
    }

    /** Stores {@code options} for the next session. Never throws. */
    public static void save(SegSweepMacroOptions options) {
        String text = serialise(options);
        if (text == null) {
            return;
        }
        try {
            Prefs.set(PREF_VERSION_KEY, VERSION);
            Prefs.set(PREF_KEY, text);
        } catch (Throwable ignored) {
            // Preferences are a convenience. Never let them break a run.
        }
    }

    /** Last stored settings, or defaults when absent, stale or unreadable. */
    public static SegSweepMacroOptions restore() {
        return restoreFor(null);
    }

    /**
     * Last stored settings adapted to {@code image}, or defaults. The image
     * decides only whether a stored custom crop is still meaningful; every other
     * field is restored as saved.
     */
    public static SegSweepMacroOptions restoreFor(ImagePlus image) {
        String version;
        String stored;
        try {
            version = Prefs.get(PREF_VERSION_KEY, "");
            stored = Prefs.get(PREF_KEY, "");
        } catch (Throwable ignored) {
            return SegSweepMacroOptions.defaults();
        }
        if (!VERSION.equals(version == null ? "" : version.trim())) {
            return SegSweepMacroOptions.defaults();
        }
        return deserialise(stored, image);
    }

    /** Forgets the stored settings. Never throws. */
    public static void clear() {
        try {
            Prefs.set(PREF_KEY, "");
            Prefs.set(PREF_VERSION_KEY, "");
        } catch (Throwable ignored) {
            // As above.
        }
    }

    /**
     * The string that would be stored for {@code options}, or null when it
     * cannot be represented. Separated from {@link #save} so the round trip is
     * testable without touching a real preferences file.
     */
    static String serialise(SegSweepMacroOptions options) {
        if (options == null) {
            return null;
        }
        try {
            SegSweepMacroOptions detached =
                    SegSweepMacroOptionsParser.parse(options.toMacroOptions());
            detached.setImage("");
            return detached.toMacroOptions();
        } catch (Throwable ignored) {
            // An options object that does not validate is not worth storing.
            return null;
        }
    }

    /**
     * Rebuilds options from a stored string, applying the image-dependent rules.
     * Separated from {@link #restoreFor} for the same reason as
     * {@link #serialise}.
     */
    static SegSweepMacroOptions deserialise(String stored, ImagePlus image) {
        if (stored == null || stored.trim().isEmpty()) {
            return SegSweepMacroOptions.defaults();
        }
        SegSweepMacroOptions restored;
        try {
            restored = SegSweepMacroOptionsParser.parse(stored);
            restored.validate();
        } catch (Throwable ignored) {
            return SegSweepMacroOptions.defaults();
        }
        restored.setImage("");
        if (!cropFits(restored.crop(), image)) {
            restored.setCrop(CropSpec.full());
        }
        return restored;
    }

    /**
     * True when the stored crop can be applied to {@code image} as recorded.
     * A full or centred crop always can — both are defined relative to whatever
     * image they are given. A custom rectangle must lie inside the frame.
     */
    static boolean cropFits(CropSpec crop, ImagePlus image) {
        if (crop == null || crop.mode() != CropSpec.Mode.CUSTOM) {
            return true;
        }
        if (image == null) {
            return false;
        }
        Rectangle bounds = crop.bounds();
        if (bounds == null || bounds.width <= 0 || bounds.height <= 0) {
            return false;
        }
        return bounds.x >= 0
                && bounds.y >= 0
                && bounds.x + bounds.width <= image.getWidth()
                && bounds.y + bounds.height <= image.getHeight();
    }
}
