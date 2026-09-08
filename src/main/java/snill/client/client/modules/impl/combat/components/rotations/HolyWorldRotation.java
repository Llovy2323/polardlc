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
import snill.client.client.modules.impl.combat.components.interpolation.BestPoint;

public class HolyWorldRotation extends RotationsSystem implements QClient {

    private final Aura aura;
    private LivingEntity trackedTarget;
    private float lastYaw;
    private float lastPitch;
    private float speedAcc;
    private boolean initialized;
    private int ticks;

    public HolyWorldRotation(Aura aura) {
        this.aura = aura;
    }

    public void reset() {
        trackedTarget = null;
        speedAcc = 0.0F;
        ticks = 0;
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
        speedAcc = Math.min(speedAcc, 0.60F);
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
            speedAcc = 0.0F;
            ticks = 0;
        }

        ticks++;

        // Target upper body (chest/head) for max crit chance on HolyWorld
        Box box = target.getBoundingBox();
        Vec3d aimPoint = new Vec3d(
                box.minX + (box.maxX - box.minX) * 0.5D,
                box.minY + (box.maxY - box.minY) * 0.72D,
                box.minZ + (box.maxZ - box.minZ) * 0.5D
        );

        if (shouldUseElytraPredict(target)) {
            aimPoint = getPredictedPoint(target, aimPoint);
        }

        Vec2f targetRot = RotationUtils.getRotations(aimPoint);
        float targetYaw = targetRot.x;
        float targetPitch = targetRot.y;

        float yawDelta = MathHelper.wrapDegrees(targetYaw - lastYaw);
        float pitchDelta = targetPitch - lastPitch;

        boolean readyToHit = mc.player.getAttackCooldownProgress(1.0F) > 0.88F;
        float jitter = (float) (Math.sin(ticks * 0.16D) * 0.05D);

        // Accelerated turn towards target on HolyWorld
        float speed = readyToHit ? 0.82F : 0.65F;
        speedAcc = MathHelper.clamp(speedAcc + 0.06F, 0.25F, 1.0F);

        float newYaw = lastYaw + yawDelta * (speed * speedAcc + jitter);
        float newPitch = lastPitch + pitchDelta * (speed * speedAcc * 0.85F);

        // GCD alignment with true player sensitivity
        float gcd = GCDUtil.getGCDValue();
        if (gcd > 0.0F) {
            newYaw = lastYaw + Math.round((newYaw - lastYaw) / gcd) * gcd;
            newPitch = lastPitch + Math.round((newPitch - lastPitch) / gcd) * gcd;
        }

        newPitch = MathHelper.clamp(newPitch, -89.0F, 89.0F);

        Rotation rot = new Rotation(newYaw, newPitch);
        RotationStorage.update(rot, 130, 130, 50, 50, 0, 1, Aura.clientLook.isState());

        rotate = new Vec2f(rot.getYaw(), rot.getPitch());
        lastYaw = rot.getYaw();
        lastPitch = rot.getPitch();
    }
}
