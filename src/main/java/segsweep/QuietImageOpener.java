/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College London.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep;

import ij.IJ;
import ij.ImagePlus;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

/**
 * Opens an image without ImageJ's own error reporting.
 *
 * <p>{@code IJ.openImage} reports an unreadable file itself: a modal dialog in
 * the Fiji GUI (one per file in a batch), or a line on standard output when no
 * ImageJ window exists. Inside a macro that report can also abort the macro.
 * The callers here report failures in their own words (a batch failure row, a
 * macro error), so ImageJ's report is redirected to the Log in the GUI and
 * swallowed when there is no GUI, and the failure message is returned instead.</p>
 */
public final class QuietImageOpener {
    private QuietImageOpener() {
    }

    /** The opened image, or null with {@code error[0]} set to a one-line reason. */
    public static ImagePlus open(String path, String[] error) {
        PrintStream original = System.out;
        ThreadFilteringStream filter = null;
        if (IJ.getInstance() == null) {
            filter = new ThreadFilteringStream(original, Thread.currentThread());
            System.setOut(filter);
        }
        IJ.redirectErrorMessages(true);
        ImagePlus image;
        try {
            image = IJ.openImage(path);
        } finally {
            IJ.redirectErrorMessages(false);
            if (filter != null && System.out == filter) {
                System.setOut(original);
            }
        }
        String reported = IJ.getErrorMessage();
        if (image == null && error != null && error.length > 0) {
            error[0] = reported == null || reported.trim().isEmpty()
                    ? "Could not open image."
                    : "Could not open image: " + oneLine(reported);
        }
        return image;
    }

    public static String oneLine(String text) {
        if (text == null) return "";
        return text.replaceAll("\\s*[\\r\\n]+\\s*", " ").trim();
    }

    /** Swallows output written by one thread; every other thread passes through. */
    private static final class ThreadFilteringStream extends PrintStream {
        ThreadFilteringStream(final PrintStream delegate, final Thread owner) {
            super(new OutputStream() {
                private final ByteArrayOutputStream swallowed = new ByteArrayOutputStream();

                @Override public void write(int b) throws IOException {
                    if (Thread.currentThread() == owner) swallowed.write(b);
                    else delegate.write(b);
                }

                @Override public void write(byte[] b, int off, int len) throws IOException {
                    if (Thread.currentThread() == owner) swallowed.write(b, off, len);
                    else delegate.write(b, off, len);
                }

                @Override public void flush() {
                    delegate.flush();
                }
            }, true);
        }
    }
}
