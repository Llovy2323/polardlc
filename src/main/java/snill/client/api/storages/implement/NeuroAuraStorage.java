package snill.client.api.storages.implement;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import snill.client.api.QClient;
import snill.client.api.utils.rotate.Rotation;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Records one unmodified rotation sample for every player attack. */
public class NeuroAuraStorage implements QClient {
    private static final int MAX_SAMPLES = 100_000;
    private static final int SAMPLES_PER_ATTACK = 10;
    private static final File DIRECTORY = new File("config/neuro");
    private static final String EXTENSION = ".neuro";

    @Getter private final List<AttackSample> samples = new CopyOnWriteArrayList<>();
    @Getter @Setter private boolean isRecording;
    @Getter @Setter private boolean isUsingNeuro;
    @Getter @Setter private String currentPatternName;
    @Getter @Setter private boolean showStats = true;
    @Getter private int recordedThisSession;
    @Getter private String lastDebugMessage = "Ready";

    private long lastAttackTime;
    private float lastAttackYaw;
    private float lastAttackPitch;
    private int playbackIndex;
    private AttackSample activePlaybackSample;
    private final Deque<RotationSnapshot> rotationHistory = new ArrayDeque<>();

    public void recordTick(LivingEntity target, float currentYaw, float currentPitch) {
        if (!isRecording || mc.player == null) return;
        rotationHistory.addLast(new RotationSnapshot(currentYaw, currentPitch, System.currentTimeMillis()));
        while (rotationHistory.size() > SAMPLES_PER_ATTACK) rotationHistory.removeFirst();
    }

    public void recordAttack(LivingEntity target, float yaw, float pitch) {
        if (!isRecording || mc.player == null || target == null) return;

        long now = System.currentTimeMillis();
        List<RotationSnapshot> trajectory = new ArrayList<>(rotationHistory);
        if (trajectory.isEmpty() || trajectory.get(trajectory.size() - 1).yaw != yaw || trajectory.get(trajectory.size() - 1).pitch != pitch) {
            trajectory.add(new RotationSnapshot(yaw, pitch, now));
        }
        RotationSnapshot previous = lastAttackTime == 0L ? null : new RotationSnapshot(lastAttackYaw, lastAttackPitch, lastAttackTime);
        for (int index = 0; index < trajectory.size(); index++) {
            RotationSnapshot snapshot = trajectory.get(index);
            float[] hitPoint = getHitRatios(target, snapshot.yaw, snapshot.pitch);
            Aim aim = getAim(target, hitPoint[0], hitPoint[1], hitPoint[2]);
            long elapsed = previous == null ? 50L : Math.max(1L, snapshot.time - previous.time);
            float yawDelta = previous == null ? 0.0f : MathHelper.wrapDegrees(snapshot.yaw - previous.yaw);
            float pitchDelta = previous == null ? 0.0f : snapshot.pitch - previous.pitch;
            float ticks = Math.max(1.0f, elapsed / 50.0f);
            samples.add(new AttackSample(snapshot.yaw, snapshot.pitch,
                    MathHelper.wrapDegrees(snapshot.yaw - aim.yaw), snapshot.pitch - aim.pitch,
                    yawDelta, pitchDelta, Math.abs(yawDelta) / ticks, Math.abs(pitchDelta) / ticks,
                    elapsed, aim.distance, hitPoint[0], hitPoint[1], hitPoint[2], index == trajectory.size() - 1));
            previous = snapshot;
        }
        while (samples.size() > MAX_SAMPLES) samples.remove(0);

        lastAttackTime = now;
        lastAttackYaw = yaw;
        lastAttackPitch = pitch;
        recordedThisSession++;
        lastDebugMessage = "Recorded attack sample " + recordedThisSession;
    }

    public Rotation getNeuroRotation(LivingEntity target, float currentYaw, float currentPitch, boolean focusRotation) {
        if (!isUsingNeuro || target == null || mc.player == null || samples.isEmpty()) return null;

        if (activePlaybackSample == null) {
            playbackIndex = findNextAttackSample(playbackIndex);
            activePlaybackSample = samples.get(playbackIndex);
            lastDebugMessage = String.format("Neuro point %d/%d, %.3f/%.3f deg/tick", playbackIndex + 1, samples.size(), activePlaybackSample.yawSpeed, activePlaybackSample.pitchSpeed);
        }
        // Keep one recorded point inside the target hitbox until the next
        // attack, while recalculating its world position as the target moves.
        Aim aim = getAim(target, activePlaybackSample.aimX, activePlaybackSample.aimY, activePlaybackSample.aimZ);
        return new Rotation(aim.yaw, aim.pitch);
    }

    /** The aim point changes only after a real attack. */
    public void onPlaybackAttack() {
        if (!isUsingNeuro || samples.isEmpty()) return;
        playbackIndex = (playbackIndex + 1) % samples.size();
        activePlaybackSample = null;
    }

    public float getPlaybackYawSpeed() {
        return samples.isEmpty() ? 360.0f : Math.max(0.01f, (activePlaybackSample != null ? activePlaybackSample : samples.get(Math.floorMod(playbackIndex, samples.size()))).yawSpeed);
    }

    public float getPlaybackPitchSpeed() {
        return samples.isEmpty() ? 360.0f : Math.max(0.01f, (activePlaybackSample != null ? activePlaybackSample : samples.get(Math.floorMod(playbackIndex, samples.size()))).pitchSpeed);
    }

    public void startRecording() {
        samples.clear();
        isRecording = true;
        isUsingNeuro = false;
        currentPatternName = null;
        recordedThisSession = 0;
        lastAttackTime = 0L;
        rotationHistory.clear();
        resetState();
        lastDebugMessage = "Recording attack samples";
    }

    public void stopRecording() { isRecording = false; }

    public boolean savePatterns(String profileName) {
        if (samples.isEmpty() || !isValidName(profileName)) return false;
        if (!DIRECTORY.exists() && !DIRECTORY.mkdirs()) return false;
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(profileFile(profileName)))) {
            out.writeObject(new ArrayList<>(samples));
            currentPatternName = profileName;
            lastDebugMessage = "Saved " + samples.size() + " attack samples";
            return true;
        } catch (IOException exception) {
            lastDebugMessage = "Could not save profile";
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public boolean loadPatterns(String profileName) {
        File file = profileFile(profileName);
        if (!isValidName(profileName) || !file.isFile()) return false;
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(file))) {
            Object data = in.readObject();
            if (!(data instanceof List<?> list) || list.stream().anyMatch(value -> !(value instanceof AttackSample))) return false;
            samples.clear();
            samples.addAll((List<AttackSample>) list);
            currentPatternName = profileName;
            resetState();
            lastDebugMessage = "Loaded " + samples.size() + " attack samples";
            return !samples.isEmpty();
        } catch (IOException | ClassNotFoundException exception) {
            lastDebugMessage = "Could not load profile";
            return false;
        }
    }

    public boolean deletePatterns(String profileName) {
        File file = profileFile(profileName);
        return isValidName(profileName) && file.isFile() && file.delete();
    }

    public void clearPatterns() { samples.clear(); resetState(); }
    public int getPatternCount() { return samples.size(); }
    public int getFrameCount() { return samples.size(); }
    public void resetState() { playbackIndex = 0; activePlaybackSample = null; }
    public String getStatusString() { return "§8[§bNeuro§8] §f" + samples.size() + (isRecording ? " §a[REC]" : "") + (isUsingNeuro ? " §b[ON]" : ""); }

    public List<String> getPatternNames() {
        if (!DIRECTORY.isDirectory()) return List.of();
        File[] files = DIRECTORY.listFiles((dir, name) -> name.endsWith(EXTENSION));
        if (files == null) return List.of();
        List<String> names = new ArrayList<>();
        for (File file : files) names.add(file.getName().substring(0, file.getName().length() - EXTENSION.length()));
        return names;
    }

    private int findClosestDistanceSample(double distance) {
        int best = 0;
        double difference = Double.MAX_VALUE;
        for (int i = 0; i < samples.size(); i++) {
            double candidate = Math.abs(samples.get(i).distance - distance);
            if (candidate < difference) { difference = candidate; best = i; }
        }
        return best;
    }

    private int findNextAttackSample(int start) {
        for (int offset = 0; offset < samples.size(); offset++) {
            int index = Math.floorMod(start + offset, samples.size());
            if (samples.get(index).attackSample) return index;
        }
        return Math.floorMod(start, samples.size());
    }

    private Aim getAim(LivingEntity target, float aimX, float aimY, float aimZ) {
        Vec3d eye = mc.player.getEyePos();
        var box = target.getBoundingBox();
        Vec3d point = new Vec3d(
                box.minX + box.getLengthX() * MathHelper.clamp(aimX, 0.02f, 0.98f),
                box.minY + box.getLengthY() * MathHelper.clamp(aimY, 0.02f, 0.98f),
                box.minZ + box.getLengthZ() * MathHelper.clamp(aimZ, 0.02f, 0.98f)
        );
        double dx = point.x - eye.x, dy = point.y - eye.y, dz = point.z - eye.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        return new Aim((float) Math.toDegrees(Math.atan2(-dx, dz)), (float) Math.toDegrees(Math.atan2(-dy, horizontal)), eye.distanceTo(point));
    }

    private float[] getHitRatios(LivingEntity target, float yaw, float pitch) {
        var box = target.getBoundingBox();
        Vec3d eye = mc.player.getEyePos();
        Vec3d end = eye.add(Vec3d.fromPolar(pitch, yaw).multiply(6.0D));
        Vec3d point = box.raycast(eye, end).orElse(box.getCenter());
        return new float[] {
                box.getLengthX() < 1.0E-6D ? 0.5f : (float) ((point.x - box.minX) / box.getLengthX()),
                box.getLengthY() < 1.0E-6D ? 0.6f : (float) ((point.y - box.minY) / box.getLengthY()),
                box.getLengthZ() < 1.0E-6D ? 0.5f : (float) ((point.z - box.minZ) / box.getLengthZ())
        };
    }

    private boolean isValidName(String name) { return name != null && !name.isBlank() && !name.contains("/") && !name.contains("\\") && !name.contains(".."); }
    private File profileFile(String name) { return new File(DIRECTORY, name + EXTENSION); }

    private record Aim(float yaw, float pitch, double distance) { }
    private record RotationSnapshot(float yaw, float pitch, long time) { }

    private static final class AttackSample implements Serializable {
        private static final long serialVersionUID = 1L;
        final float yaw, pitch, yawOffset, pitchOffset, yawDelta, pitchDelta, yawSpeed, pitchSpeed;
        final long attackDelay;
        final double distance;
        final float aimX, aimY, aimZ;
        final boolean attackSample;
        AttackSample(float yaw, float pitch, float yawOffset, float pitchOffset, float yawDelta, float pitchDelta, float yawSpeed, float pitchSpeed, long attackDelay, double distance, float aimX, float aimY, float aimZ, boolean attackSample) {
            this.yaw = yaw; this.pitch = pitch; this.yawOffset = yawOffset; this.pitchOffset = pitchOffset;
            this.yawDelta = yawDelta; this.pitchDelta = pitchDelta; this.yawSpeed = yawSpeed; this.pitchSpeed = pitchSpeed;
            this.attackDelay = attackDelay; this.distance = distance; this.aimX = aimX; this.aimY = aimY; this.aimZ = aimZ; this.attackSample = attackSample;
        }
    }
}
