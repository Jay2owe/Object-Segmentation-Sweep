/*
 * Copyright (c) 2026 Jamie Malcolm
 *
 * Developed at the Brancaccio Lab, UK Dementia Research Institute,
 * Imperial College License.
 *
 * Released under the BSD 3-Clause License. See LICENSE for terms.
 */
package segsweep.ui.grid;

/**
 * A one-shot delay that runs an action on the event thread once a mouse button
 * has been held for a while. It only runs between a press and its release or
 * drag, never continuously: the cells hold no repaint timer.
 */
final class HoldDelay {
    private final javax.swing.Timer timer;

    HoldDelay(int delayMs, final Runnable action) {
        timer = new javax.swing.Timer(delayMs, new java.awt.event.ActionListener() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                action.run();
            }
        });
        timer.setRepeats(false);
    }

    void restart() {
        timer.restart();
    }

    void stop() {
        timer.stop();
    }

    boolean isRunning() {
        return timer.isRunning();
    }
}
