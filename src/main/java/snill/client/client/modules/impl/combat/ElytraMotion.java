package snill.client.client.modules.impl.combat;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.Vec3d;
import snill.client.api.events.EventLink;
import snill.client.api.events.implement.EventMove;
import snill.client.api.events.implement.EventUpdate;
import snill.client.api.storages.implement.helpertstorages.enumvar.ModuleClass;
import snill.client.api.utils.math.TimerUtils;
import snill.client.api.utils.player.InventoryUtils;
import snill.client.client.modules.Module;
import snill.client.client.modules.settings.implement.BooleanSetting;
import snill.client.client.modules.settings.implement.FloatSetting;
import snill.client.client.modules.settings.implement.ModeSetting;
import ru.virtuoz.convert.Convert;

@Convert(Convert.ConvertType.MUTATION)
public class ElytraMotion extends Module {

    public static ElytraMotion INSTANCE = new ElytraMotion();

    private final ModeSetting mode = new ModeSetting("Mode", "New", "New", "Rw");
    public final BooleanSetting moment = new BooleanSetting("Moment", true);
    public final BooleanSetting speedCheck = new BooleanSetting("Speed Check", true);
    public final FloatSetting attackDistance = new FloatSetting("Distance", 3.0f, 1.0f, 6.0f, 0.1f);
    private final BooleanSetting autoFirework = new BooleanSetting("Auto Firework", false);

    private final TimerUtils timer = new TimerUtils();

    public boolean freeze = false;
    private boolean wasLocked = false;
    private Vec3d frozenPosition = null;
    private Vec3d beforeFreezeVelocity = null;

    public ElytraMotion() {
        super("Elytra Motion", "Останавливает движение при бою на элитрах", ModuleCategory.MOVEMENT);
        addSettings(mode, moment, speedCheck, attackDistance, autoFirework);
    }

    @EventLink
    public void eventUpdate(EventUpdate event) {
        if (mc.player == null || mc.world == null) {
            freeze = false;
            unlockKeys();
            return;
        }

        if (mode.is("New")) {
            updateNew();
        } else {
            updateOld();
        }

        if (autoFirework.isState()) {
            Aura killAura = ModuleClass.aura;
            if (killAura != null && killAura.getTarget() != null && timer.finished(500L)) {
                InventoryUtils.swapAndUseHvH(Items.FIREWORK_ROCKET);
                timer.reset();
            }
        }
    }

    private void updateNew() {
        if (!mc.player.isGliding() || shouldSuspendForElytraTarget()) {
            unfreezeNew();
            return;
        }

        Aura aura = ModuleClass.aura;
        LivingEntity target = aura != null ? aura.getTarget() : null;

        boolean isSpeed = speedCheck.isState() && target != null && getEntityBPS(target) >= 20;

        if (target != null
                && getHeadDistance(target) <= attackDistance.getValue().doubleValue()
                && !isSpeed
                && (!moment.isState() || !target.isGliding())) {
            if (!freeze) {
                frozenPosition = mc.player.getPos();
                beforeFreezeVelocity = mc.player.getVelocity();
            }
            freeze = true;
            lockKeys();
        } else {
            if (beforeFreezeVelocity != null) {
                mc.player.setVelocity(beforeFreezeVelocity.x, 0, beforeFreezeVelocity.z);
            }
            unfreezeNew();
        }
    }

    private void updateOld() {
        if (!mc.player.isGliding()) {
            freeze = false;
            unlockKeys();
            return;
        }

        Aura aura = ModuleClass.aura;
        ElytraTarget elytraTarget = ElytraTarget.INSTANCE;
        freeze = checkOld(aura, elytraTarget);
        unlockKeys();
    }

    @EventLink
    public void eventMotion(EventMove event) {
        if (mc.player == null || !freeze) return;

        if (mode.is("New") && mc.player.isGliding() && frozenPosition != null) {
            event.setMovePos(Vec3d.ZERO);
            mc.player.setVelocity(0, 0, 0);
            mc.player.updatePosition(frozenPosition.x, frozenPosition.y, frozenPosition.z);
        }

        if (mode.is("Rw") && mc.player.isGliding()) {
            event.setMovePos(Vec3d.ZERO);
        }
    }

    private boolean checkOld(Aura aura, ElytraTarget elytraTarget) {
        if (aura == null || elytraTarget == null) return false;

        LivingEntity target = aura.getTarget();
        if (target != null && mc.player.isGliding()) {
            boolean canTarget = elytraTarget.shouldSyncTargetFlight(target);
            return !canTarget && mc.player.distanceTo(target) < attackDistance.getValue().floatValue();
        }

        return false;
    }

    private boolean shouldSuspendForElytraTarget() {
        ElytraTarget elytraTarget = ElytraTarget.INSTANCE;
        if (elytraTarget == null || !elytraTarget.isPredictionActive()) return false;

        Aura aura = ModuleClass.aura;
        LivingEntity target = aura != null && aura.isEnable() ? aura.getTarget() : null;

        boolean auraFlightActive = mc.player.isGliding()
                && target != null
                && target.isGliding();

        if (auraFlightActive) {
            freeze = false;
            frozenPosition = null;
            beforeFreezeVelocity = null;
        }
        return auraFlightActive;
    }

    private double getHeadDistance(LivingEntity target) {
        Vec3d playerEye = mc.player.getEyePos();
        Vec3d targetHead = target.getEyePos().add(0.0, (target.getY() - target.prevY) * 2.0, 0.0);
        return playerEye.distanceTo(targetHead);
    }

    private double getEntityBPS(LivingEntity entity) {
        double dx = entity.getX() - entity.prevX;
        double dz = entity.getZ() - entity.prevZ;
        return Math.sqrt(dx * dx + dz * dz) * 20.0;
    }

    private void unfreezeNew() {
        freeze = false;
        frozenPosition = null;
        beforeFreezeVelocity = null;
        unlockKeys();
    }

    private void lockKeys() {
        if (!wasLocked) {
            mc.options.forwardKey.setPressed(false);
            mc.options.backKey.setPressed(false);
            mc.options.leftKey.setPressed(false);
            mc.options.rightKey.setPressed(false);
            mc.options.jumpKey.setPressed(false);
            mc.options.sneakKey.setPressed(false);
            wasLocked = true;
        }
    }

    private void unlockKeys() {
        if (wasLocked) {
            wasLocked = false;
        }
    }

    @Override
    public void onDisable() {
        freeze = false;
        frozenPosition = null;
        beforeFreezeVelocity = null;
        unlockKeys();
        super.onDisable();
    }
}
