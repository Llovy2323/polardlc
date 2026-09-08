package snill.client.client.modules.impl.combat.components.rotations;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import snill.client.api.QClient;
import snill.client.api.storages.implement.RotationStorage;
import snill.client.api.utils.rotate.Rotation;
import snill.client.api.utils.rotate.RotationUtils;
import snill.client.client.modules.impl.combat.Aura;
import snill.client.client.modules.impl.combat.components.RotationsSystem;
import snill.client.client.modules.impl.combat.components.gcd.GCDUtil;

public class ReallyWorldRotation extends RotationsSystem implements QClient {

    private final Aura aura;
    private LivingEntity trackedTarget;
    private float lastYaw;
    private float lastPitch;
    private boolean initialized;
    private int ticks;

    // Pitch damping and drifting for Matrix bypass
    private double driftAngle;

    public ReallyWorldRotation(Aura aura) {
        this.aura = aura;
    }

    public void reset() {
        trackedTarget = null;
        ticks = 0;
        driftAngle = 0.0D;
        initialized = mc.player != null;
        if (mc.player != null) {
            lastYaw = mc.player.getYaw();
            lastPitch = mc.player.getPitch();
        } else {
            lastYaw = 0.0F;
            lastPitch = 0.0F;
        }
    }

    public void onAttack() {
    }

    @Override
    public void updateRotations(LivingEntity target) {
        if (mc.player == null || target == null) return;

        if (mc.player.isBlocking()) {
            rotate = new Vec2f(mc.player.getYaw(), mc.player.getPitch());
            lastYaw = rotate.x;
            lastPitch = rotate.y;
            return;
        }

        if (!initialized) {
            lastYaw = mc.player.getYaw();
            lastPitch = mc.player.getPitch();
            initialized = true;
        }

        if (trackedTarget != target) {
            trackedTarget = target;
            ticks = 0;
            driftAngle = Math.random() * Math.PI * 2;
        }

        ticks++;
        driftAngle += 0.08D;

        // Subtle elliptical drift across the body to avoid Matrix static angle detection
        Box box = target.getBoundingBox();
        double width = (box.maxX - box.minX) * 0.35D;
        double height = (box.maxY - box.minY) * 0.20D;

        Vec3d center = box.getCenter();
        Vec3d aimPoint = new Vec3d(
                center.x + Math.sin(driftAngle) * width,
                box.minY + (box.maxY - box.minY) * 0.65D + Math.cos(driftAngle * 0.8D) * height,
                center.z + Math.cos(driftAngle) * width
        );

        if (shouldUseElytraPredict(target)) {
            aimPoint = getPredictedPoint(target, aimPoint);
        }

        Vec2f targetRot = RotationUtils.getRotations(aimPoint);
        float targetYaw = targetRot.x;
        float targetPitch = targetRot.y;

        float yawDelta = MathHelper.wrapDegrees(targetYaw - lastYaw);
        float pitchDelta = targetPitch - lastPitch;

        // Cubic ease-out interpolation to completely avoid Matrix linear check
        float distFactor = MathHelper.clamp(Math.abs(yawDelta) / 180.0F, 0.05F, 1.0F);
        float ease = 1.0F - (float) Math.pow(1.0F - distFactor, 3);
        float yawSpeed = MathHelper.clamp(0.45F + ease * 0.35F, 0.30F, 0.80F);
        // Pitch damping: Matrix heavily flags violent pitch spikes
        float pitchSpeed = yawSpeed * 0.62F;

        float newYaw = lastYaw + yawDelta * yawSpeed;
        float newPitch = lastPitch + pitchDelta * pitchSpeed;

        // Strict GCD rounding
        float gcd = GCDUtil.getGCDValue();
        if (gcd > 0.0F) {
            newYaw = lastYaw + Math.round((newYaw - lastYaw) / gcd) * gcd;
            newPitch = lastPitch + Math.round((newPitch - lastPitch) / gcd) * gcd;
        }

        newPitch = MathHelper.clamp(newPitch, -89.0F, 89.0F);

        Rotation rot = new Rotation(newYaw, newPitch);
        RotationStorage.update(rot, 110, 80, 45, 35, 0, 1, Aura.clientLook.isState());

        rotate = new Vec2f(rot.getYaw(), rot.getPitch());
        lastYaw = rot.getYaw();
        lastPitch = rot.getPitch();
    }
}
