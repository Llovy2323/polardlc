package snill.client.client.modules.impl.combat.components.rotations;

import net.minecraft.entity.LivingEntity;
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
import snill.client.client.modules.impl.combat.components.interpolation.BestPoint;

public class FunTimeRotation extends RotationsSystem implements QClient {

    private final Aura aura;
    private LivingEntity trackedTarget;
    private float lastYaw;
    private float lastPitch;
    private float speedAcceleration;
    private boolean back;
    private boolean initialized;
    private float jitterOffset;
    private int tickCounter;

    public FunTimeRotation(Aura aura) {
        this.aura = aura;
    }

    public void reset() {
        trackedTarget = null;
        speedAcceleration = 0.0F;
        back = false;
        jitterOffset = 0.0F;
        tickCounter = 0;
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
        if (mc.player != null && mc.player.isGliding()) {
            speedAcceleration = Math.min(speedAcceleration, 0.45F);
            back = false;
        }
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
            speedAcceleration = 0.0F;
            back = false;
            tickCounter = 0;
        }

        tickCounter++;
        // Natural micro-noise for GrimAC bypass
        jitterOffset = (float) (Math.sin(tickCounter * 0.14D) * 0.06D + (Math.random() - 0.5D) * 0.02D);

        // Find best multipoint on hitbox (chest / head area)
        Vec3d point = BestPoint.getMultipoint(target, 128.0);
        if (point == null) {
            point = target.getBoundingBox().getCenter();
        }
        if (shouldUseElytraPredict(target)) {
            point = getPredictedPoint(target, point);
        }

        Vec2f angle = RotationUtils.getRotations(point);
        float targetYaw = angle.x;
        float targetPitch = angle.y;

        double targetDistance = mc.player.getEyePos().distanceTo(point);
        Vec3d targetOffset = point.subtract(mc.player.getPos());
        Vec3d flightVelocity = new Vec3d(mc.player.getVelocity().x, 0.0D, mc.player.getVelocity().z);
        boolean passedTarget = mc.player.isGliding()
                && flightVelocity.lengthSquared() > 0.0025D
                && flightVelocity.normalize().dotProduct(new Vec3d(targetOffset.x, 0.0D, targetOffset.z)) < -0.45D;
        if (mc.player.isGliding() && targetDistance <= 4.25D) {
            targetPitch = MathHelper.clamp(targetPitch, -35.0F, 25.0F);
        }

        float yawDiff = Math.abs(MathHelper.wrapDegrees(targetYaw - lastYaw));
        boolean readyToAttack = mc.player.getAttackCooldownProgress(1.0F) > 0.85F && aura.getWhiteRiseTicksToAttack() <= 1;

        // Smooth acceleration curve
        if (!back) {
            float gain = 0.045F;
            if (yawDiff > 60.0F) {
                gain += 0.016F * 1.8F;
            } else if (yawDiff > 50.0F) {
                gain += 0.042F * 1.9F;
            } else {
                gain += 0.016F * 1.7F;
            }
            if (readyToAttack) {
                gain += 0.017F / 1.4F;
            }
            speedAcceleration += gain * (1.0F + jitterOffset);
            if (speedAcceleration >= 1.15F) back = true;
        } else {
            float loss = readyToAttack ? 0.12F : 0.07F;
            speedAcceleration -= loss * (1.0F + jitterOffset);
            if (speedAcceleration <= 0.30F) back = false;
        }

        float smooth = MathHelper.clamp(speedAcceleration, mc.player.isGliding() ? 2.8F : 0.7F, mc.player.isGliding() ? 7.3F : 6.7F);
        if (readyToAttack) {
            smooth = Math.min(smooth + 0.1F, mc.player.isGliding() ? 7.0F : 6.0F);
        }
        smooth += jitterOffset;

        float deltaYaw = MathHelper.wrapDegrees(targetYaw - lastYaw);
        float deltaPitch = targetPitch - lastPitch;

        float yawLimit = mc.player.isGliding() ? (passedTarget ? 180.0F : 150.0F) : (readyToAttack ? 7.7F : 7.9F);
        float pitchLimit = mc.player.isGliding() ? 55.0F : (readyToAttack ? 3.5F : 3.8F);

        deltaYaw = MathHelper.clamp(deltaYaw, -yawLimit, yawLimit);
        deltaPitch = MathHelper.clamp(deltaPitch, -pitchLimit, pitchLimit);

        float pitchSpeed = mc.player.isGliding() ? MathHelper.clamp(smooth * 0.18F, 0.35F, 0.92F) : smooth * 0.06F;
        float yawSpeed = mc.player.isGliding()
                ? MathHelper.clamp(smooth * (passedTarget ? 0.30F : 0.22F), passedTarget ? 0.78F : 0.52F, 0.98F)
                : Math.max(0.02F, smooth * (0.61F + jitterOffset));

        float newYaw = lastYaw + deltaYaw * yawSpeed;
        float newPitch = lastPitch + deltaPitch * pitchSpeed;

        // Apply GCD fix with real player sensitivity
        float gcd = GCDUtil.getGCDValue();
        if (gcd > 0.0F) {
            newYaw = lastYaw + Math.round((newYaw - lastYaw) / gcd) * gcd;
            newPitch = lastPitch + Math.round((newPitch - lastPitch) / gcd) * gcd;
        }

        newPitch = MathHelper.clamp(newPitch, -89.0F, 89.0F);

        Rotation finalRot = new Rotation(newYaw, newPitch);
        RotationStorage.update(finalRot, 116, 116, 46, 46, 0, 1, Aura.clientLook.isState());

        rotate = new Vec2f(finalRot.getYaw(), finalRot.getPitch());
        lastYaw = finalRot.getYaw();
        lastPitch = finalRot.getPitch();
    }
}
