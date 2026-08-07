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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class PackagingIT {

    @Test
    public void packagedJarContainsPrivateCoreLicenceAndPluginEntries() throws Exception {
        File project = new File(requiredProperty("segsweep.project.basedir"));
        File jarPath = new File(requiredProperty("segsweep.project.jar"));
        assertTrue(jarPath.isFile());

        byte[] expectedLicence = Files.readAllBytes(new File(project, "LICENSE").toPath());
        JarFile jar = new JarFile(jarPath);
        try {
            JarEntry licence = jar.getJarEntry("META-INF/LICENSE");
            assertNotNull(licence);
            assertArrayEquals(expectedLicence, read(jar.getInputStream(licence)));
            assertNotNull(jar.getJarEntry("plugins.config"));
            assertNotNull(jar.getJarEntry("segsweep/SegSweep_.class"));
            assertNotNull(jar.getJarEntry("segsweep/SegSweepBatchRunner.class"));
            assertNotNull(jar.getJarEntry(
                    "segsweep/internal/core/io/RegexGroupDiscovery.class"));
            assertTrue(jar.getJarEntry(
                    "sc/fiji/oc3d/core/io/RegexGroupDiscovery.class") == null);
            assertTrue(jar.getJarEntry("ij/IJ.class") == null);

            Attributes attributes = jar.getManifest().getMainAttributes();
            assertEquals(gitHead(project), attributes.getValue("Implementation-Build"));
        } finally {
            jar.close();
        }
    }

    @Test
    public void packagedJarRunsBatchPreviewWithoutAnExternalCoreJar() throws Exception {
        File jarPath = new File(requiredProperty("segsweep.project.jar"));
        File imageJ = new File(ij.IJ.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
        File input = Files.createTempDirectory("segsweep-packaged-batch").toFile();
        assertTrue(new File(input, "sample_A.tif").createNewFile());
        assertTrue(new File(input, "sample_B.tif").createNewFile());

        URLClassLoader loader = new URLClassLoader(
                new URL[]{jarPath.toURI().toURL(), imageJ.toURI().toURL()}, null);
        try {
            Class<?> parametersClass = loader.loadClass("segsweep.SegSweepBatchParameters");
            Object builder = parametersClass.getMethod(
                            "builder", File.class, String.class, Integer.TYPE)
                    .invoke(null, input, "sample_([AB])\\.tif", 1);
            builder.getClass().getMethod("recursive", Boolean.TYPE)
                    .invoke(builder, false);
            builder.getClass().getMethod("autoSave", Boolean.TYPE)
                    .invoke(builder, false);
            Object parameters = builder.getClass().getMethod("build").invoke(builder);
            Class<?> runner = loader.loadClass("segsweep.SegSweepBatchRunner");
            String preview = (String) runner.getMethod("preview", parametersClass)
                    .invoke(null, parameters);

            assertTrue(preview.contains("1 folder(s), 1 group(s), 2 files"));
            assertTrue(preview.contains("sample_*.tif"));
            assertNotNull(loader.loadClass(
                    "segsweep.internal.core.io.RegexGroupDiscovery"));
        } finally {
            loader.close();
            delete(input);
        }
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException("Required test property is missing: " + name);
        }
        return value;
    }

    private static String gitHead(File project) throws Exception {
        Process process = new ProcessBuilder("git", "rev-parse", "HEAD")
                .directory(project)
                .redirectErrorStream(true)
                .start();
        String output;
        try {
            output = new String(read(process.getInputStream()), "UTF-8").trim();
        } finally {
            process.getInputStream().close();
        }
        assertEquals(0, process.waitFor());
        return output;
    }

    private static byte[] read(InputStream stream) throws Exception {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = stream.read(buffer)) >= 0) {
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        } finally {
            stream.close();
        }
    }

    private static void delete(File file) throws Exception {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (int i = 0; i < children.length; i++) delete(children[i]);
            }
        }
        Files.deleteIfExists(file.toPath());
    }
}
