package snill.client.client.modules.impl.combat;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

import snill.client.api.events.EventLink;
import snill.client.api.events.implement.EventAttackEntity;
import snill.client.api.utils.combat.IdealHitUtils;
import snill.client.client.modules.Module;

public class PacketCriticals extends Module {

    public static PacketCriticals INSTANCE = new PacketCriticals();

    public PacketCriticals() {
        super("PacketCriticals", "Бьет критами под эффект плавного падения / в паутине", ModuleCategory.COMBAT);
    }
    @EventLink
    public void onAttack(final EventAttackEntity event) {
        if (mc.player == null || mc.world == null) return;

        boolean inWeb = IdealHitUtils.isInCobweb();

        double x = mc.player.getX();
        double y = mc.player.getY();
        double z = mc.player.getZ();
        if (inWeb) {
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y + 0.00300, z, false, false));
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, false, false));
        }
    }
}
