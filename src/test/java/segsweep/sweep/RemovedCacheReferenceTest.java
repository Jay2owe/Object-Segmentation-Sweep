/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep.sweep;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;

public class RemovedCacheReferenceTest {
    @Test
    public void productionCodeDoesNotReferenceRemovedCacheArchitecture() throws Exception {
        List<String> forbidden = new ArrayList<String>();
        forbidden.add("Variation" + "Cache");
        forbidden.add("source" + "Image" + "Hash");
        forbidden.add("cache" + "Namespace");
        forbidden.add("cache" + "Budget" + "Bytes");
        forbidden.add("Disk" + "Cache" + "Poisoning");
        forbidden.add("shared mutable");

        List<String> hits = new ArrayList<String>();
        scan(Paths.get("src/main/java"), forbidden, hits);

        assertTrue(hits.toString(), hits.isEmpty());
    }

    private static void scan(Path path, List<String> forbidden, List<String> hits)
            throws Exception {
        if (Files.isDirectory(path)) {
            for (Path child : Files.newDirectoryStream(path)) {
                scan(child, forbidden, hits);
            }
            return;
        }
        if (!path.toString().endsWith(".java")) {
            return;
        }
        String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        for (int i = 0; i < forbidden.size(); i++) {
            String needle = forbidden.get(i);
            if (text.contains(needle)) {
                hits.add(path + " contains " + needle);
            }
        }
    }
}
