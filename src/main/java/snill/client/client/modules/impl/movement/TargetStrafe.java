package snill.client.client.modules.impl.movement;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import snill.client.api.events.EventLink;
import snill.client.api.events.implement.EventMoveInput;
import snill.client.client.modules.Module;
import snill.client.client.modules.impl.combat.Aura;
import snill.client.client.modules.settings.implement.BooleanSetting;
import snill.client.client.modules.settings.implement.FloatSetting;

public class TargetStrafe extends Module {
    public static TargetStrafe INSTANCE = new TargetStrafe();

    public final FloatSetting distance = new FloatSetting("Дистанция", 2.2f, 0.5f, 6.0f, 0.1f);
    public final BooleanSetting autoJump = new BooleanSetting("Авто прыжок", true);
    public final BooleanSetting avoidWalls = new BooleanSetting("Обходить стены", true);
    public final BooleanSetting onlySpace = new BooleanSetting("Только с пробелом", false);

    private int direction = 1;
    private int switchCooldown = 0;

    public TargetStrafe() {
        super("TargetStrafe", "Автоматически бегает по кругу вокруг цели киллауры", ModuleCategory.MOVEMENT);
        addSettings(distance, autoJump, avoidWalls, onlySpace);
    }

    @EventLink
    public void onMoveInput(final EventMoveInput event) {
        if (mc.player == null || mc.world == null) return;

        LivingEntity target = getTarget();
        if (target == null || !target.isAlive()) return;

        if (onlySpace.isState() && !mc.options.jumpKey.isPressed()) return;

        if (switchCooldown > 0) switchCooldown--;

        if (avoidWalls.isState() && switchCooldown <= 0 && mc.player.horizontalCollision) {
            direction = -direction;
            switchCooldown = 8;
        }

        double dx = mc.player.getX() - target.getX();
        double dz = mc.player.getZ() - target.getZ();
        double currentDist = Math.hypot(dx, dz);

        double angleToPlayer = Math.atan2(dz, dx);
        double targetRadius = distance.get();

        double radialBias = MathHelper.clamp((currentDist - targetRadius) / 2.0, -1.0, 1.0);
        double desiredAngle = angleToPlayer + (direction * (Math.PI / 2.0)) - (radialBias * 0.45 * direction);

        float moveYaw = (float) Math.toDegrees(desiredAngle) - 90.0f;
        float diffYaw = MathHelper.wrapDegrees(moveYaw - mc.player.getYaw());

        double rad = Math.toRadians(diffYaw);
        float forward = (float) Math.cos(rad);
        float strafe = (float) -Math.sin(rad);

        float max = Math.max(Math.abs(forward), Math.abs(strafe));
        if (max > 0.001f) {
            forward /= max;
            strafe /= max;
        }

        event.setForward(forward);
        event.setStrafe(strafe);

        if (autoJump.isState() && mc.player.isOnGround()) {
            event.setJump(true);
        }
    }

    private LivingEntity getTarget() {
        if (Aura.INSTANCE != null && Aura.INSTANCE.isEnable()) {
            return Aura.INSTANCE.getTarget();
        }
        return null;
    }
}
