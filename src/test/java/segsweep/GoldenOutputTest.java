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
import ij.ImageStack;
import ij.measure.ResultsTable;
import org.junit.Test;
import segsweep.sweep.ParameterId;
import segsweep.sweep.VariationResult;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Pins every measurement the public API produces on seeded synthetic images.
 *
 * <p>A diff here means an output changed. Only a change that is stated in the
 * CHANGELOG may be accepted, by regenerating with
 * {@code -Dsegsweep.golden.update=true}; see
 * {@code src/test/resources/golden/README.md}.</p>
 */
public class GoldenOutputTest {
    static final String UPDATE_PROPERTY = "segsweep.golden.update";
    static final String[] SWEEPS = { "threshold", "size", "threshold_x_size", "threshold_knee" };

    @Test
    public void everyFixtureAndSweepMatchesItsGolden() throws Exception {
        boolean update = Boolean.getBoolean(UPDATE_PROPERTY);
        File dir = goldenDirectory();
        List<String> mismatches = new ArrayList<String>();
        List<String> written = new ArrayList<String>();
        for (GoldenFixtures.Fixture fixture : GoldenFixtures.all()) {
            for (String sweep : SWEEPS) {
                String name = fixture.name + "__" + sweep + ".txt";
                String actual = dump(runSweep(fixture, sweep));
                File file = new File(dir, name);
                if (update) {
                    Files.write(file.toPath(), actual.getBytes(StandardCharsets.UTF_8));
                    written.add(name);
                    continue;
                }
                if (!file.isFile()) {
                    mismatches.add(name + " (missing golden)");
                    continue;
                }
                String expected = new String(Files.readAllBytes(file.toPath()),
                        StandardCharsets.UTF_8).replace("\r\n", "\n");
                if (!expected.equals(actual)) {
                    mismatches.add(name + " first difference: " + firstDifference(expected, actual));
                }
            }
        }
        if (update) {
            fail("Rewrote " + written.size() + " golden files in " + dir
                    + ". Add a CHANGELOG line explaining the output change, then rerun without -D"
                    + UPDATE_PROPERTY + "=true.");
        }
        if (!mismatches.isEmpty()) {
            StringBuilder message = new StringBuilder("Measurement outputs changed:\n");
            for (String mismatch : mismatches) message.append("  ").append(mismatch).append('\n');
            message.append("If intended, add a CHANGELOG line and regenerate with -D")
                    .append(UPDATE_PROPERTY).append("=true.");
            fail(message.toString());
        }
    }

    @Test
    public void dumpIsDeterministicAcrossRepeatedRuns() throws Exception {
        GoldenFixtures.Fixture fixture = GoldenFixtures.all().get(1);
        assertEquals(dump(runSweep(fixture, "threshold_x_size")),
                dump(runSweep(fixture, "threshold_x_size")));
    }

    static Outcome runSweep(GoldenFixtures.Fixture fixture, String sweep) {
        SegSweepParameters.Builder builder = SegSweepParameters.builder()
                .image(fixture.image)
                .pickCriterion(SegSweepParameters.PickCriterion.BOTH);
        try {
            if ("threshold_knee".equals(sweep)) {
                builder.pickCriterion(SegSweepParameters.PickCriterion.KNEE);
                builder.axis(ParameterId.THRESHOLD, fixture.thresholdFrom,
                        fixture.thresholdTo, fixture.thresholdStep);
            } else if ("threshold".equals(sweep)) {
                builder.axis(ParameterId.THRESHOLD, fixture.thresholdFrom,
                        fixture.thresholdTo, fixture.thresholdStep);
            } else if ("size".equals(sweep)) {
                builder.axis(ParameterId.THRESHOLD, fixture.fixedThreshold,
                        fixture.fixedThreshold, 1);
                builder.axis(ParameterId.MIN_SIZE, fixture.sizeFrom,
                        fixture.sizeTo, fixture.sizeStep);
            } else {
                builder.axis(ParameterId.THRESHOLD, fixture.thresholdFrom,
                        fixture.thresholdTo, fixture.thresholdStep);
                builder.axis(ParameterId.MIN_SIZE, fixture.sizeFrom,
                        fixture.sizeTo, fixture.sizeStep);
            }
            return new Outcome(SegSweep.run(builder.build()), null);
        } catch (RuntimeException ex) {
            return new Outcome(null, ex);
        }
    }

    static String dump(Outcome outcome) throws Exception {
        StringBuilder b = new StringBuilder();
        if (outcome.error != null) {
            b.append("ERROR\t").append(outcome.error.getClass().getSimpleName())
                    .append('\t').append(outcome.error.getMessage()).append('\n');
            return b.toString();
        }
        SegSweepResult r = outcome.result;
        b.append("[sweep]\n");
        appendTable(b, r.sweepTable(), SegSweepResult.COL_DURATION_MS);
        b.append("[pick]\n");
        appendTable(b, r.pickTable(), null);
        b.append("[token]\n");
        String token = r.pickedSettingsToken();
        for (String line : token.split("\n", -1)) {
            if (line.startsWith("# Written ")) line = "# Written <masked>";
            b.append(line).append('\n');
        }
        b.append("[warnings]\n");
        for (String warning : r.warnings()) b.append(warning).append('\n');
        b.append("[labels]\n");
        if (r.pickedLabelMap() == null) {
            b.append("none\n");
        } else {
            ImagePlus labels = r.pickedLabelMap().get();
            b.append(labels.getWidth()).append('x').append(labels.getHeight()).append('x')
                    .append(labels.getStackSize()).append(" sha256=")
                    .append(labelHash(labels.getStack())).append('\n');
        }
        b.append("[result labels]\n");
        for (int i = 0; i < r.results().size(); i++) {
            VariationResult result = r.results().get(i);
            b.append(i + 1).append('\t');
            if (!result.hasLabelMap()) {
                b.append("none\n");
                continue;
            }
            ImagePlus labels = result.labelMap().get();
            b.append(labelHash(labels.getStack())).append('\n');
            labels.flush();
        }
        return b.toString();
    }

    private static void appendTable(StringBuilder b, ResultsTable t, String skip) {
        if (t == null) {
            b.append("null\n");
            return;
        }
        String[] headings = t.getHeadings();
        for (int row = 0; row < t.size(); row++) {
            for (String h : headings) {
                if (h == null || h.length() == 0 || h.equals(skip) || " ".equals(h)) continue;
                b.append(h).append('=').append(cell(t, h, row)).append('\t');
            }
            b.append('\n');
        }
    }

    private static String cell(ResultsTable t, String heading, int row) {
        double value = t.getValue(heading, row);
        if (Double.isNaN(value)) {
            String text = t.getStringValue(heading, row);
            return text == null ? "" : text;
        }
        return String.format(Locale.ROOT, "%.10g", Double.valueOf(value));
    }

    static String labelHash(ImageStack stack) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (int z = 1; z <= stack.getSize(); z++) {
            short[] pixels = (short[]) stack.getPixels(z);
            byte[] bytes = new byte[pixels.length * 2];
            for (int i = 0; i < pixels.length; i++) {
                bytes[2 * i] = (byte) (pixels[i] & 0xFF);
                bytes[2 * i + 1] = (byte) ((pixels[i] >> 8) & 0xFF);
            }
            digest.update(bytes);
        }
        StringBuilder hex = new StringBuilder();
        for (byte value : digest.digest()) hex.append(String.format(Locale.ROOT, "%02x", value));
        return hex.toString();
    }

    private static String firstDifference(String expected, String actual) {
        String[] e = expected.split("\n", -1);
        String[] a = actual.split("\n", -1);
        for (int i = 0; i < Math.max(e.length, a.length); i++) {
            String left = i < e.length ? e[i] : "<end>";
            String right = i < a.length ? a[i] : "<end>";
            if (!left.equals(right)) {
                return "line " + (i + 1) + "\n    expected: " + left + "\n    actual:   " + right;
            }
        }
        return "none";
    }

    static File goldenDirectory() throws IOException {
        String base = System.getProperty("basedir");
        File root = base == null ? new File(".") : new File(base);
        File dir = new File(root, "src/test/resources/golden");
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("Could not create " + dir);
        }
        return dir;
    }

    static final class Outcome {
        final SegSweepResult result;
        final RuntimeException error;

        Outcome(SegSweepResult result, RuntimeException error) {
            this.result = result;
            this.error = error;
        }
    }
}
