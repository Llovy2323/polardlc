package snill.client.client.modules.impl.combat;

import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import snill.client.api.events.EventLink;
import snill.client.api.events.implement.EventMoveInput;
import snill.client.api.events.implement.EventPacket;
import snill.client.api.events.implement.EventUpdate;
import snill.client.api.utils.input.MovingUtil;
import snill.client.client.modules.Module;
import snill.client.client.modules.settings.implement.BooleanSetting;
import snill.client.client.modules.settings.implement.FloatSetting;
import snill.client.client.modules.settings.implement.ModeSetting;

public class Velocity extends Module {
    public static Velocity INSTANCE = new Velocity();

    public final ModeSetting mode = new ModeSetting("Режим", "Grim", "Grim", "Cancel", "Custom");
    public final FloatSetting chance = new FloatSetting("Шанс", 100.0f, 0.0f, 100.0f, 1.0f);
    public final BooleanSetting onlyAura = new BooleanSetting("Только с таргетом", false);
    public final BooleanSetting onlyMoving = new BooleanSetting("Только в движении", false);
    public final FloatSetting horizontal = new FloatSetting("По горизонтали", 0.0f, 0.0f, 100.0f, 1.0f).visible(() -> mode.is("Custom"));
    public final FloatSetting vertical = new FloatSetting("По вертикали", 0.0f, 0.0f, 100.0f, 1.0f).visible(() -> mode.is("Custom"));

    private boolean needJumpReset = false;
    private int resetTicks = 0;

    public Velocity() {
        super("Velocity", "Уменьшает или полностью убирает отдачу от ударов и взрывов", ModuleCategory.COMBAT);
        addSettings(mode, chance, onlyAura, onlyMoving, horizontal, vertical);
    }

    @EventLink
    public void onPacket(final EventPacket event) {
        if (mc.player == null || mc.world == null) return;
        if (event.getType() != EventPacket.Type.RECEIVE) return;

        if (event.getPacket() instanceof EntityVelocityUpdateS2CPacket packet) {
            if (packet.getEntityId() != mc.player.getId()) return;

            if (chance.get() < 100.0f && Math.random() * 100.0 > chance.get()) return;
            if (onlyAura.isState() && (Aura.INSTANCE == null || !Aura.INSTANCE.isEnable() || Aura.INSTANCE.getTarget() == null)) return;
            if (onlyMoving.isState() && !MovingUtil.hasPlayerMovement()) return;

            if (mode.is("Grim")) {
                // GrimAC bypass: JumpReset on vanilla simulation
                needJumpReset = true;
                resetTicks = 2;
            } else if (mode.is("Cancel")) {
                event.cancel();
            } else if (mode.is("Custom")) {
                if (horizontal.get() == 0.0f && vertical.get() == 0.0f) {
                    event.cancel();
                } else {
                    double hFactor = horizontal.get() / 100.0;
                    double vFactor = vertical.get() / 100.0;
                    mc.player.setVelocity(
                            packet.getVelocityX() * hFactor,
                            packet.getVelocityY() * vFactor,
                            packet.getVelocityZ() * hFactor
                    );
                    event.cancel();
                }
            }
        } else if (event.getPacket() instanceof ExplosionS2CPacket) {
            if (chance.get() < 100.0f && Math.random() * 100.0 > chance.get()) return;
            if (onlyAura.isState() && (Aura.INSTANCE == null || !Aura.INSTANCE.isEnable() || Aura.INSTANCE.getTarget() == null)) return;
            if (onlyMoving.isState() && !MovingUtil.hasPlayerMovement()) return;

            if (mode.is("Grim")) {
                needJumpReset = true;
                resetTicks = 2;
            } else if (mode.is("Cancel")) {
                event.cancel();
            } else if (mode.is("Custom")) {
                if (horizontal.get() == 0.0f && vertical.get() == 0.0f) {
                    event.cancel();
                }
            }
        }
    }

    @EventLink
    public void onMoveInput(final EventMoveInput event) {
        if (mc.player == null) return;
        if (needJumpReset && resetTicks > 0) {
            if (mc.player.isOnGround()) {
                event.setJump(true);
                needJumpReset = false;
                resetTicks = 0;
            }
        }
    }

    @EventLink
    public void onUpdate(final EventUpdate event) {
        if (mc.player == null) return;
        if (resetTicks > 0) {
            resetTicks--;
            if (mc.player.isOnGround() && needJumpReset) {
                mc.player.jump();
                needJumpReset = false;
            }
        } else {
            needJumpReset = false;
        }
    }

    @Override
    public void onDisable() {
        needJumpReset = false;
        resetTicks = 0;
        super.onDisable();
    }
}
