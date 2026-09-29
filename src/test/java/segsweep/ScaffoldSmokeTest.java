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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertTrue;

/**
 * The public release files exist in a clean checkout and agree on the version
 * the pom builds. {@code PUBLISHING_AUDIT.md} is a local, ignored file and is
 * deliberately not required.
 */
public class ScaffoldSmokeTest {
    @Test
    public void releaseFilesAgreeOnTheBuiltVersion() throws Exception {
        File root = new File(System.getProperty("basedir", "."));
        for (String name : new String[] {
                "README.md", "CITATION.cff", "CHANGELOG.md", "VERSIONING.md", "LICENSE"
        }) {
            assertTrue(name + " should exist", new File(root, name).isFile());
        }
        String version = projectVersion(read(root, "pom.xml"));
        if (version.endsWith("-SNAPSHOT")) {
            // Between releases main builds the next version: the changelog
            // already has its section, while README and CITATION still name
            // the last release until the release commit updates them.
            String next = version.substring(0, version.length() - "-SNAPSHOT".length());
            assertTrue("CHANGELOG.md has a section for " + next,
                    read(root, "CHANGELOG.md").contains("## [" + next + "] - "));
            return;
        }
        assertTrue(read(root, "CITATION.cff").contains("version: \"" + version + "\""));
        assertTrue(read(root, "CHANGELOG.md").contains("## [" + version + "] - "));
        assertTrue(read(root, "README.md").contains("Object-Segmentation-Sweep-" + version + ".jar"));
    }

    private static String projectVersion(String pom) {
        Matcher m = Pattern.compile(
                "<artifactId>Object-Segmentation-Sweep</artifactId>\\s*<version>([^<]+)</version>")
                .matcher(pom);
        assertTrue("project version in pom.xml", m.find());
        return m.group(1).trim();
    }

    private static String read(File root, String name) throws Exception {
        return new String(Files.readAllBytes(new File(root, name).toPath()), StandardCharsets.UTF_8);
    }
}
