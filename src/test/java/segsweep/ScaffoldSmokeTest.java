/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertTrue;

public class ScaffoldSmokeTest {
    @Test
    public void testHarnessRuns() {
        assertTrue(true);
    }

    @Test
    public void releaseFurnitureMatchesVersion() throws Exception {
        File root = new File(System.getProperty("basedir", "."));
        for (String name : new String[] {
                "README.md", "CITATION.cff", "CHANGELOG.md", "PUBLISHING_AUDIT.md"
        }) {
            assertTrue(name + " should exist", new File(root, name).isFile());
        }
        String pom = new String(Files.readAllBytes(
                new File(root, "pom.xml").toPath()), StandardCharsets.UTF_8);
        String citation = new String(Files.readAllBytes(
                new File(root, "CITATION.cff").toPath()), StandardCharsets.UTF_8);
        assertTrue(pom.contains("<version>0.2.0</version>"));
        assertTrue(citation.contains("version: \"0.2.0\""));
    }
}
