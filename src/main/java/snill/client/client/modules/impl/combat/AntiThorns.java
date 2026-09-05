package snill.client.client.modules.impl.combat;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import snill.client.api.events.EventLink;
import snill.client.api.events.implement.EventPacket;
import snill.client.api.events.implement.EventThorns;
import snill.client.api.events.implement.EventUpdate;
import snill.client.client.modules.Module;

public class AntiThorns extends Module {
    private static final int THORNS_VELOCITY_SUPPRESS_TICKS = 8;
    private static final byte THORNS_ENTITY_STATUS_OPCODE = 33;

    public static final AntiThorns INSTANCE = new AntiThorns();
    private int suppressVelocityTicks;

    public AntiThorns() {
        super("AntiThorns", "Убирает урон и откидывание от шипов", ModuleCategory.COMBAT);
    }

    @EventLink
    public void onThornsDamage(EventThorns event) {
        if (shouldCancelThornsDamage(event.getAttacker())) event.cancel();
    }
    @EventLink
    public void onPacket(EventPacket event) {
        if (event.getType() != EventPacket.Type.RECEIVE || mc.player == null || mc.world == null) return;

        if (event.getPacket() instanceof EntityStatusS2CPacket packet) {
            if (packet.getStatus() == THORNS_ENTITY_STATUS_OPCODE
                    && packet.getEntity(mc.world) == mc.player
                    && isLocalPlayerGliding()) {
                armVelocitySuppress();
            }
            return;
        }

        if (event.getPacket() instanceof EntityVelocityUpdateS2CPacket packet
                && shouldCancelVelocityPacket(packet)) {
            event.cancel();
            resetVelocitySuppress();
        }
    }

    @EventLink
    public void onUpdate(EventUpdate event) {
        tickSuppressTimer();
    }

    public void tickSuppressTimer() {
        if (mc.player == null || !isLocalPlayerGliding()) {
            resetVelocitySuppress();
            return;
        }
        if (suppressVelocityTicks > 0) --suppressVelocityTicks;
    }

    public boolean shouldCancelThornsDamage(Entity attacker) {
        if (!isEnable() || mc.player == null || attacker != mc.player) return false;
        if (isLocalPlayerGliding()) armVelocitySuppress();
        return true;
    }

    public boolean shouldCancelVelocityPacket(EntityVelocityUpdateS2CPacket packet) {
        return isVelocitySuppressActive() && packet != null && mc.player != null
                && packet.getEntityId() == mc.player.getId();
    }

    public boolean isVelocitySuppressActive() {
        return suppressVelocityTicks > 0 && isLocalPlayerGliding();
    }

    public int getSuppressVelocityTicks() {
        return suppressVelocityTicks;
    }

    public void armVelocitySuppress() {
        suppressVelocityTicks = THORNS_VELOCITY_SUPPRESS_TICKS;
    }

    public void resetVelocitySuppress() {
        suppressVelocityTicks = 0;
    }

    @Override
    public void onEnable() {
        resetVelocitySuppress();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        resetVelocitySuppress();
        super.onDisable();
    }

    private boolean isLocalPlayerGliding() {
        return mc.player != null && mc.player.isGliding();
    }
}
