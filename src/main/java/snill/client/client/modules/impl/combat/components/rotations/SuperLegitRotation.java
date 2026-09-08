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

public class SuperLegitRotation extends RotationsSystem implements QClient {

    private final Aura aura;
    private LivingEntity trackedTarget;
    private float lastYaw;
    private float lastPitch;
    private boolean initialized;
    private int ticks;

    // Human overshoot state
    private float overshootYaw = 0.0F;
    private float overshootPitch = 0.0F;
    private boolean isOvershooting = false;

    public SuperLegitRotation(Aura aura) {
        this.aura = aura;
    }

    public void reset() {
        trackedTarget = null;
        ticks = 0;
        overshootYaw = 0.0F;
        overshootPitch = 0.0F;
        isOvershooting = false;
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
            // Generate subtle overshoot when locking onto a new target
            overshootYaw = (float) ((Math.random() - 0.5D) * 3.5D);
            overshootPitch = (float) ((Math.random() - 0.5D) * 2.0D);
            isOvershooting = true;
        }

        ticks++;

        // Aim at chest with slight natural breathing offset
        Box box = target.getBoundingBox();
        double breathY = Math.sin(ticks * 0.1D) * 0.06D;
        Vec3d aimPoint = new Vec3d(
                box.minX + (box.maxX - box.minX) * 0.5D,
                box.minY + (box.maxY - box.minY) * 0.65D + breathY,
                box.minZ + (box.maxZ - box.minZ) * 0.5D
        );

        if (shouldUseElytraPredict(target)) {
            aimPoint = getPredictedPoint(target, aimPoint);
        }

        Vec2f targetRot = RotationUtils.getRotations(aimPoint);
        float targetYaw = targetRot.x;
        float targetPitch = targetRot.y;

        // Apply overshoot if fresh lock
        if (isOvershooting) {
            targetYaw += overshootYaw;
            targetPitch += overshootPitch;
            // Gradually decay overshoot back to zero
            overshootYaw *= 0.72F;
            overshootPitch *= 0.72F;
            if (Math.abs(overshootYaw) < 0.2F && Math.abs(overshootPitch) < 0.2F) {
                isOvershooting = false;
            }
        }

        float yawDelta = MathHelper.wrapDegrees(targetYaw - lastYaw);
        float pitchDelta = targetPitch - lastPitch;

        // Human hand muscle tremor (8-12 Hz)
        float handTremor = (float) (Math.sin(ticks * 0.24D) * 0.04D + (Math.random() - 0.5D) * 0.02D);

        // Smooth human tracking speed
        float smoothYawSpeed = MathHelper.clamp(0.28F + (Math.abs(yawDelta) / 90.0F) * 0.22F, 0.20F, 0.55F);
        float smoothPitchSpeed = smoothYawSpeed * 0.75F;

        float newYaw = lastYaw + yawDelta * (smoothYawSpeed + handTremor);
        float newPitch = lastPitch + pitchDelta * smoothPitchSpeed;

        // GCD grid alignment with actual player sensitivity
        float gcd = GCDUtil.getGCDValue();
        if (gcd > 0.0F) {
            newYaw = lastYaw + Math.round((newYaw - lastYaw) / gcd) * gcd;
            newPitch = lastPitch + Math.round((newPitch - lastPitch) / gcd) * gcd;
        }

        newPitch = MathHelper.clamp(newPitch, -89.0F, 89.0F);

        Rotation rot = new Rotation(newYaw, newPitch);
        RotationStorage.update(rot, 95, 70, 40, 30, 0, 1, Aura.clientLook.isState());

        rotate = new Vec2f(rot.getYaw(), rot.getPitch());
        lastYaw = rot.getYaw();
        lastPitch = rot.getPitch();
    }
}
