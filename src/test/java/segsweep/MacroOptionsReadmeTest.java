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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The README macro tables must list exactly the keys the parsers accept. The
 * old table omitted {@code allow_oversized}, {@code no_display} and the
 * {@code show_*} flags.
 */
public class MacroOptionsReadmeTest {
    private static final Pattern CODE = Pattern.compile("`([a-z0-9_]+)`");

    @Test
    public void singleImageTableMatchesTheParser() throws Exception {
        List<Set<String>> tables = macroTables();
        Set<String> expected = new TreeSet<String>(SegSweepMacroOptionsParser.VALUE_KEYS);
        expected.addAll(SegSweepMacroOptionsParser.FLAGS);
        assertEquals(expected, tables.get(0));
    }

    @Test
    public void batchTableMatchesTheBatchParser() throws Exception {
        List<Set<String>> tables = macroTables();
        Set<String> expected = new TreeSet<String>(SegSweepBatch.VALUE_KEYS);
        expected.addAll(SegSweepBatch.FLAGS);
        assertEquals(expected, tables.get(1));
    }

    @Test
    public void everyListedKeyIsAcceptedAndOthersAreRefused() {
        for (String flag : SegSweepMacroOptionsParser.FLAGS) {
            SegSweepMacroOptionsParser.parse(flag);
        }
        try {
            SegSweepMacroOptionsParser.parse("show_everything");
            fail("unknown flag must be refused");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("show_everything"));
        }
    }

    /** Option names from the first cell of each table in the README's Macro section. */
    private static List<Set<String>> macroTables() throws Exception {
        String base = System.getProperty("basedir");
        File readme = new File(base == null ? new File(".") : new File(base), "README.md");
        List<String> lines = Files.readAllLines(readme.toPath(), StandardCharsets.UTF_8);
        List<Set<String>> tables = new ArrayList<Set<String>>();
        boolean inMacro = false;
        Set<String> current = null;
        for (String line : lines) {
            if (line.startsWith("## ")) {
                inMacro = line.trim().equals("## Macro");
                continue;
            }
            if (!inMacro) continue;
            if (!line.startsWith("|")) {
                current = null;
                continue;
            }
            String[] cells = line.split("\\|");
            if (cells.length < 2 || cells[1].trim().startsWith("---")) continue;
            if (current == null) {
                current = new TreeSet<String>(); // header row
                tables.add(current);
                continue;
            }
            Matcher m = CODE.matcher(cells[1]);
            while (m.find()) current.add(m.group(1));
        }
        assertEquals("README Macro section should hold two tables", 2, tables.size());
        return tables;
    }
}
