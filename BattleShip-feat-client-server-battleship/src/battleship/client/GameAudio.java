package battleship.client;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Random;
import java.util.function.Consumer;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;

final class GameAudio implements AutoCloseable {
    private static final float SAMPLE_RATE = 22050f;
    private static final AudioFormat FORMAT = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
    private static final int CANNON = 0;
    private static final int IMPACT = 1;
    private static final int SHIP_SUNK = 2;
    private static final int MATCH_FOUND = 3;

    private enum Scene { SILENT, LOBBY, BATTLE }

    private final Consumer<String> errorHandler;
    private final Clip[] effectClips = new Clip[4];
    private Scene scene = Scene.SILENT;
    private Clip musicClip;
    private boolean muted;
    private boolean failed;

    GameAudio(Consumer<String> errorHandler) {
        this.errorHandler = errorHandler;
    }

    void setScene(Scene next) {
        if (scene == next) return;
        scene = next;
        applyScene();
    }

    private void applyScene() {
        closeMusicClip();
        if (muted || scene == Scene.SILENT || failed) return;
        try {
            musicClip = createClip(scene == Scene.LOBBY ? lobbyMusic() : battleMusic());
            musicClip.loop(Clip.LOOP_CONTINUOUSLY);
        } catch (IOException | LineUnavailableException | UnsupportedAudioFileException e) {
            reportFailure(e);
        }
    }

    void setLobbyMusic() {
        setScene(Scene.LOBBY);
    }

    void setBattleMusic() {
        setScene(Scene.BATTLE);
    }

    void playMatchFound() {
        scene = Scene.SILENT;
        closeMusicClip();
        playEffect(MATCH_FOUND, matchFoundSound());
    }

    void stopMusic() {
        setScene(Scene.SILENT);
    }

    void toggleMuted() {
        muted = !muted;
        applyScene();
        if (muted) stopEffects();
    }

    boolean isMuted() {
        return muted;
    }

    void playCannonShot() {
        if (scene == Scene.BATTLE) playEffect(CANNON, cannonSound());
    }

    void playImpact(boolean hit) {
        if (scene == Scene.BATTLE) playEffect(IMPACT, impactSound(hit));
    }

    void playShipSunk() {
        if (scene == Scene.BATTLE) playEffect(SHIP_SUNK, shipSunkSound());
    }

    private void playEffect(int index, byte[] sound) {
        if (muted || failed) return;
        try {
            if (effectClips[index] == null) effectClips[index] = createClip(sound);
            Clip clip = effectClips[index];
            clip.stop();
            clip.setFramePosition(0);
            clip.start();
        } catch (IOException | LineUnavailableException | UnsupportedAudioFileException e) {
            reportFailure(e);
        }
    }

    @Override
    public void close() {
        scene = Scene.SILENT;
        closeMusicClip();
        stopEffects();
    }

    private void closeMusicClip() {
        if (musicClip != null) {
            musicClip.stop();
            musicClip.close();
            musicClip = null;
        }
    }

    private void stopEffects() {
        for (int i = 0; i < effectClips.length; i++) {
            if (effectClips[i] != null) {
                effectClips[i].stop();
                effectClips[i].close();
                effectClips[i] = null;
            }
        }
    }

    private void reportFailure(Exception error) {
        failed = true;
        closeMusicClip();
        stopEffects();
        errorHandler.accept("Không thể phát âm thanh: " + error.getMessage());
    }

    private static Clip createClip(byte[] data)
            throws IOException, LineUnavailableException, UnsupportedAudioFileException {
        try (ByteArrayInputStream bytes = new ByteArrayInputStream(data);
             AudioInputStream stream = new AudioInputStream(bytes, FORMAT, data.length / FORMAT.getFrameSize())) {
            Clip clip = AudioSystem.getClip();
            clip.open(stream);
            return clip;
        }
    }

    private static byte[] lobbyMusic() {
        int stepFrames = Math.round(SAMPLE_RATE * 0.27f);
        int steps = 32;
        int frames = stepFrames * steps;
        int[] melody = {
            76, 79, 83, 79, 74, 79, 81, 79,
            72, 76, 79, 76, 74, 76, 79, 76,
            76, 79, 83, 86, 84, 83, 81, 79,
            77, 81, 84, 81, 79, 76, 79, 76
        };
        int[] bass = {48, 48, 55, 55, 53, 53, 60, 60, 57, 57, 53, 53, 55, 55, 48, 48};
        int[][] chords = {
            {60, 64, 67}, {55, 59, 62}, {53, 57, 60}, {57, 60, 64},
            {60, 64, 67}, {53, 57, 60}, {55, 59, 62}, {48, 55, 60}
        };
        return synthesize(frames, (frame, time) -> {
            int step = frame / stepFrames;
            int within = frame % stepFrames;
            double melodyEnvelope = Math.min(1, within / (SAMPLE_RATE * 0.015))
                    * Math.min(1, (stepFrames - within) / (SAMPLE_RATE * 0.045));
            double value = tone(melody[step], time, 0.105, 0.12) * melodyEnvelope;
            int beat = (int) (time / 0.54);
            double beatTime = time % 0.54;
            value += tone(bass[beat % bass.length], time, 0.08, 0.035)
                    * Math.max(0, 1 - beatTime / 0.45);
            int[] chord = chords[(int) (time / 2.16) % chords.length];
            for (int note : chord)
                value += tone(note, time, 0.06, 0.012);
            return value;
        });
    }

    private static byte[] battleMusic() {
        double beatLength = 0.52;
        int frames = (int) (SAMPLE_RATE * beatLength * 16);
        int beatFrames = (int) (SAMPLE_RATE * beatLength);
        double[] artilleryNoise = filteredNoise(frames, 71);
        int[] melody = {
            62, 65, 69, 65, 60, 63, 67, 63,
            58, 62, 65, 62, 57, 60, 64, 60
        };
        int[] bass = {38, 38, 45, 45, 36, 36, 43, 43, 34, 34, 41, 41, 36, 36, 43, 43};
        return synthesize(frames, (frame, time) -> {
            int beat = (int) (time / beatLength);
            double beatTime = time % beatLength;
            double pulse = Math.exp(-beatTime * 8);
            double value = tone(melody[beat % melody.length], time, 0.11, 0.085)
                    * Math.max(0, 1 - beatTime / 0.46);
            value += tone(bass[beat % bass.length] - 12, time, 0.07, 0.10)
                    * Math.max(0, 1 - beatTime / 0.48);
            value += Math.sin(2 * Math.PI * 48 * time) * pulse * 0.045;
            if (beat % 8 == 0 && beatTime < 0.9) {
                int boomFrame = frame % (8 * beatFrames);
                double decay = Math.exp(-beatTime * 3.2);
                value += artilleryNoise[boomFrame] * decay * 0.42;
                value += Math.sin(2 * Math.PI * (83 - 48 * Math.min(1, beatTime / 0.24)) * time)
                        * Math.exp(-beatTime * 8) * 0.27;
            }
            return value;
        });
    }

    private static byte[] cannonSound() {
        double duration = 0.72;
        int frames = (int) (SAMPLE_RATE * duration);
        double[] noise = filteredNoise(frames, 29);
        return synthesize(frames, (frame, time) -> {
            double decay = Math.exp(-time * 7.5);
            double boom = Math.sin(2 * Math.PI * (112 - 70 * time) * time) * Math.exp(-time * 5.2);
            return noise[frame] * decay * 0.75 + boom * 0.72;
        });
    }

    private static byte[] impactSound(boolean hit) {
        double duration = hit ? 0.65 : 0.34;
        int frames = (int) (SAMPLE_RATE * duration);
        double[] noise = filteredNoise(frames, hit ? 47 : 13);
        return synthesize(frames, (frame, time) -> {
            double decay = Math.exp(-time * (hit ? 5.0 : 10.0));
            double frequency = hit ? 96 - 50 * time : 520 - 310 * time;
            double thump = Math.sin(2 * Math.PI * frequency * time) * decay;
            return noise[frame] * decay * (hit ? 0.52 : 0.26) + thump * (hit ? 0.55 : 0.35);
        });
    }

    private static byte[] shipSunkSound() {
        double duration = 1.9;
        int frames = (int) (SAMPLE_RATE * duration);
        double[] noise = filteredNoise(frames, 101);
        return synthesize(frames, (frame, time) -> {
            double decay = Math.exp(-time * 2.45);
            double crack = noise[frame] * Math.exp(-time * 3.5);
            double lowRumble = Math.sin(2 * Math.PI * (67 - 25 * time) * time) * decay;
            double secondBlast = time > 0.32
                    ? Math.sin(2 * Math.PI * (74 - 28 * time) * (time - 0.32))
                        * Math.exp(-(time - 0.32) * 3.0)
                    : 0;
            return crack * 0.88 + lowRumble * 0.72 + secondBlast * 0.48;
        });
    }

    private static byte[] matchFoundSound() {
        int[] notes = {67, 72, 76, 79, 84};
        int stepFrames = Math.round(SAMPLE_RATE * 0.16f);
        int frames = stepFrames * (notes.length + 2);
        return synthesize(frames, (frame, time) -> {
            int step = frame / stepFrames;
            if (step >= notes.length) return 0;
            double localTime = (frame % stepFrames) / SAMPLE_RATE;
            double envelope = Math.exp(-localTime * 7.5);
            double fundamental = step == notes.length - 1 ? 0.22 : 0.16;
            return tone(notes[step], time, fundamental, fundamental * 0.3) * envelope;
        });
    }

    private static double[] filteredNoise(int frames, long seed) {
        Random random = new Random(seed);
        double[] noise = new double[frames];
        double lowPass = 0;
        for (int i = 0; i < frames; i++) {
            lowPass = lowPass * 0.84 + (random.nextDouble() * 2 - 1) * 0.16;
            noise[i] = lowPass;
        }
        return noise;
    }

    private static double tone(int midiNote, double time, double fundamental, double harmonics) {
        double frequency = 440 * Math.pow(2, (midiNote - 69) / 12.0);
        double phase = 2 * Math.PI * frequency * time;
        return fundamental * Math.sin(phase) + harmonics * Math.sin(phase * 2);
    }

    private static byte[] synthesize(int frames, SampleFunction sampleFunction) {
        byte[] output = new byte[frames * 2];
        for (int frame = 0; frame < frames; frame++) {
            double sample = Math.max(-0.95, Math.min(0.95, sampleFunction.sample(frame, frame / SAMPLE_RATE)));
            short pcm = (short) (sample * Short.MAX_VALUE);
            output[frame * 2] = (byte) pcm;
            output[frame * 2 + 1] = (byte) (pcm >>> 8);
        }
        return output;
    }

    @FunctionalInterface
    private interface SampleFunction {
        double sample(int frame, double time);
    }
}
