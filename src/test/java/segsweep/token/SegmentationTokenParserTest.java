/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep.token;

import org.junit.After;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SegmentationTokenParserTest {

    @After
    public void resetWarningSink() {
        SegmentationTokenParser.setWarningSinkForTest(null);
    }

    @Test
    public void classicalSettingsTokenRoundTripsByteForByte() {
        String token = "classical;thresh=42.5;minSize=100;maxSize=5000;"
                + "morph=sphericity%3E%3D0.6%2Celongation%3C%3D2.0";

        SegmentationMethod method = SegmentationTokenParser.parse(token);

        assertTrue(method.isClassical());
        assertEquals(token, SegmentationTokenParser.format(method));
        assertEquals(42.5, SegmentationMethod.threshold(method), 0.001);
        assertEquals(100, SegmentationMethod.minSize(method));
        assertEquals(5000, SegmentationMethod.maxSize(method));
        assertEquals(2, SegmentationMethod.morphPredicates(method).size());
    }

    @Test
    public void parentEnhancedClassicalTokenParsesAsClassicalSettings() {
        SegmentationMethod method = SegmentationTokenParser.parse(
                "enhanced_classical:thresh=10:minSize=20:maxSize=300:"
                        + "morph=sphericity>=0.6,elongation<=2.0");

        assertTrue(method.isClassical());
        assertEquals("classical;thresh=10;minSize=20;maxSize=300;"
                        + "morph=sphericity%3E%3D0.6%2Celongation%3C%3D2.0",
                SegmentationTokenParser.format(method));
    }

    @Test
    public void futureEngineTokensParseButCurrentExecutionDeclinesThem() {
        SegmentationMethod starDist = SegmentationTokenParser.parse("stardist:0.5:0.4:model=my_stardist");
        SegmentationMethod cellpose = SegmentationTokenParser.parse("cellpose:30:0.4:0.0:model=cyto3");

        assertTrue(starDist.isStarDist());
        assertTrue(cellpose.isCellpose());
        assertTrue(starDist.v01ExecutionDecision().declined());
        assertTrue(starDist.v01ExecutionDecision().reason().contains("not executable in v0.2.0"));
        assertTrue(cellpose.v01ExecutionDecision().declined());
    }

    @Test
    public void lenientMalformedTokenFallsBackAndWarns() {
        final List<String> warnings = new ArrayList<String>();
        SegmentationTokenParser.setWarningSinkForTest(new SegmentationTokenParser.WarningSink() {
            @Override public void warn(String message) {
                warnings.add(message);
            }
        });

        SegmentationMethod method = SegmentationTokenParser.parseLenient("future_engine:value");

        assertTrue(method.isClassical());
        assertTrue(method.shouldPreserveRawTokenOnWrite());
        assertEquals("future_engine:value", method.rawToken);
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("future_engine:value"));
    }

    @Test
    public void strictMalformedTokenThrows() {
        try {
            SegmentationTokenParser.parse("cellpose:not-a-number:cyto3");
            fail("Expected malformed Cellpose token to throw.");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Cellpose diameter"));
        }
    }

    @Test
    public void morphPredicateEncodedAndUnencodedRoundTripAndMapsToTree() {
        MorphPredicate predicate = MorphPredicate.parse("sphericity>=0.6");

        assertTrue(predicate.matches(0.7));
        assertFalse(predicate.matches(0.5));
        assertEquals("sphericity>=0.6", predicate.toTreePredicate().format());

        MorphPredicate unknown = MorphPredicate.parse("future_metric>999.0");
        assertTrue(unknown.matches(0.0));
        try {
            unknown.toTreePredicate();
            fail("Expected unknown predicate to have no v0.2 tree mapping.");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("not a v0.2"));
        }
    }

    @Test
    public void morphPredicateInclusiveOperatorsKeepExactBoundaryValues() {
        assertTrue(MorphPredicate.parse("volume>=27").matches(27));
        assertTrue(MorphPredicate.parse("volume<=27").matches(27));
        assertFalse(MorphPredicate.parse("volume>27").matches(27));
        assertFalse(MorphPredicate.parse("volume<27").matches(27));
    }
}
