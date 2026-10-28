package me.abilitysmp;

import java.util.ArrayList;
import java.util.List;

/**
 * An original, slow and eerie music-box piece made from note-block sounds. It is 64 steps of half a second
 * (32 seconds) and loops. A bell melody in A minor with a tritone (D#) that clashes with the A drone, a low
 * didgeridoo and bass drone, a "lub-dub" heartbeat and dissonant flute whispers.
 * The closer the countdown gets, the faster the heartbeat and the louder the dread (intensity 0, 1 or 2).
 */
final class SpookyScore {

    static final int STEPS = 64;

    static final class Note {
        final String sound;
        final float volume;
        final float pitch;

        Note(String sound, float volume, float pitch) {
            this.sound = sound;
            this.volume = volume;
            this.pitch = pitch;
        }
    }

    private static final String BELL = "minecraft:block.note_block.bell";
    private static final String CHIME = "minecraft:block.note_block.chime";
    private static final String DIDGERIDOO = "minecraft:block.note_block.didgeridoo";
    private static final String BASS = "minecraft:block.note_block.bass";
    private static final String DRUM = "minecraft:block.note_block.basedrum";
    private static final String FLUTE = "minecraft:block.note_block.flute";

    /** Bell melody. Note-block numbers: 0 is the instrument's lowest note, 12 the middle and 24 the highest. -1 = rest. */
    static final int[] MELODY = {
            15, -1, 18, -1, 17, -1, 15, -1,   // A  C  B  A
            14, -1, 15, -1, 10, -1, -1, -1,   // G# A  E
            13, -1, 17, -1, 15, -1, 13, -1,   // G  B  A  G   (G natural against G# makes it uneasy)
            12, -1, 11, -1, 10, -1, -1, -1,   // F# F  E      (sinking by half steps)
            20, -1, 18, -1, 17, -1, 15, -1,   // D  C  B  A   (higher)
            9, -1, 15, -1, 10, -1, -1, -1,    // D# A  E      (D# is the tritone against the A drone)
            18, 17, 18, -1, 15, -1, 14, -1,   // C  B  C  A  G#
            15, -1, -1, -1, -1, -1, -1, -1};  // A, left hanging

    /** The drone note for each of the 8 bars (bass register, 0 = F#1). */
    private static final int[] DRONE = {3, 3, 3, 3, 3, 9, 10, 3};

    private SpookyScore() {
    }

    /** Note-block number (0 to 24) to playback pitch (0.5 to 2.0). */
    static float pitch(int n) {
        return (float) Math.pow(2.0, (n - 12) / 12.0);
    }

    /** Every sound to play on this step. Intensity: 0 = calm, 1 = under 10 minutes left, 2 = under a minute. */
    static List<Note> at(int step, int intensity) {
        int s = Math.floorMod(step, STEPS);
        int in = Math.max(0, Math.min(2, intensity));
        List<Note> out = new ArrayList<>();

        int m = MELODY[s];
        if (m >= 0) {
            out.add(new Note(BELL, 0.7f, pitch(m)));
            out.add(new Note(CHIME, 0.22f, pitch(m)));
        }
        if (s % 8 == 0) {
            int d = DRONE[s / 8];
            out.add(new Note(DIDGERIDOO, 0.5f, pitch(d)));
            out.add(new Note(BASS, 0.7f, pitch(d)));
        }
        int beat = in >= 2 ? 4 : in == 1 ? 8 : 16; // the heartbeat speeds up
        if (s % beat == 0) out.add(new Note(DRUM, 0.55f + 0.1f * in, 0.5f));
        if (s % beat == 2) out.add(new Note(DRUM, 0.4f + 0.1f * in, 0.6f));
        if (s % 16 == 0) { // a minor second: A and A# whispering together
            out.add(new Note(FLUTE, 0.18f + 0.06f * in, pitch(3)));
            out.add(new Note(FLUTE, 0.16f + 0.06f * in, pitch(4)));
        }
        if (s == 40) { // the tritone, once per loop
            out.add(new Note(FLUTE, 0.28f, pitch(9)));
            out.add(new Note(FLUTE, 0.28f, pitch(3)));
        }
        if (in >= 1 && s % 8 == 4) out.add(new Note(DIDGERIDOO, 0.35f, pitch(0))); // low growl between the beats
        return out;
    }
}
