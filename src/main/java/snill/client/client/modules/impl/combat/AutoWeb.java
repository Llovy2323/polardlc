package snill.client.client.modules.impl.combat;

import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import snill.client.Snill;
import snill.client.api.events.EventLink;
import snill.client.api.events.implement.EventUpdate;
import snill.client.api.utils.player.InventoryUtils;
import snill.client.client.modules.Module;
import snill.client.client.modules.settings.implement.BooleanSetting;
import snill.client.client.modules.settings.implement.FloatSetting;
import snill.client.client.modules.settings.implement.ModeSetting;

public class AutoWeb extends Module {

    public static AutoWeb INSTANCE = new AutoWeb();

    private final ModeSetting server = new ModeSetting("Сервер", "FunTime", "FunTime", "HolyWorld", "ReallyWorld", "Универсальный");
    private final FloatSetting range = new FloatSetting("Дистанция", 4.0f, 2.0f, 5.0f, 0.1f);
    private final BooleanSetting predict = new BooleanSetting("Предикт", true);
    private final FloatSetting delay = new FloatSetting("Задержка (мс)", 150f, 50f, 500f, 25f)
            .visible(() -> server.is("Универсальный"));

    private long lastPlaceTime = 0L;

    public AutoWeb() {
        super("AutoWeb", "[FunTime / HolyWorld / ReallyWorld] Автоматически ставит паутину под ноги цели", ModuleCategory.COMBAT);
        addSettings(server, range, predict, delay);
    }

    private long getEffectiveDelay() {
        if (server.is("FunTime")) return 120L;
        if (server.is("HolyWorld")) return 100L;
        if (server.is("ReallyWorld")) return 150L;
        return (long) delay.get();
    }

    @EventLink
    public void onUpdate(EventUpdate event) {
        if (mc.player == null || mc.world == null) return;

        long now = System.currentTimeMillis();
        if (now - lastPlaceTime < getEffectiveDelay()) return;

        PlayerEntity target = getTarget();
        if (target == null) return;

        int webSlot = InventoryUtils.find(Items.COBWEB, 0, 9);
        if (webSlot == -1) return;

        BlockPos targetPos = target.getBlockPos();

        // Optional motion prediction
        if (predict.isState() && target.getVelocity().horizontalLengthSquared() > 0.02) {
            Vec3d motion = target.getVelocity().normalize().multiply(0.8);
            BlockPos predPos = BlockPos.ofFloored(target.getPos().add(motion));
            if (isValidWebPos(predPos)) {
                targetPos = predPos;
            }
        }

        if (!isValidWebPos(targetPos)) {
            // Check adjacent feet positions
            BlockPos[] neighbors = new BlockPos[]{
                    targetPos.north(), targetPos.south(), targetPos.east(), targetPos.west()
            };
            boolean found = false;
            for (BlockPos n : neighbors) {
                if (isValidWebPos(n) && mc.player.getEyePos().distanceTo(Vec3d.ofCenter(n)) <= range.get()) {
                    targetPos = n;
                    found = true;
                    break;
                }
            }
            if (!found) return;
        }

        if (mc.player.getEyePos().distanceTo(Vec3d.ofCenter(targetPos)) > range.get()) return;

        BlockPos support = targetPos.down();
        if (!mc.world.getBlockState(support).isSolidBlock(mc.world, support)) return;

        int oldSlot = mc.player.getInventory().selectedSlot;
        selectSlot(webSlot);

        Vec3d hitVec = new Vec3d(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5);
        BlockHitResult hit = new BlockHitResult(hitVec, Direction.UP, support, false);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        mc.player.swingHand(Hand.MAIN_HAND);

        selectSlot(oldSlot);
        lastPlaceTime = System.currentTimeMillis();
    }

    private boolean isValidWebPos(BlockPos pos) {
        if (mc.world.getBlockState(pos).isOf(Blocks.COBWEB)) return false;
        if (!mc.world.getBlockState(pos).isAir() && !mc.world.getBlockState(pos).isLiquid()) return false;
        BlockPos support = pos.down();
        return mc.world.getBlockState(support).isSolidBlock(mc.world, support);
    }

    private void selectSlot(int slot) {
        if (slot >= 0 && slot < 9 && mc.player != null) {
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
            mc.player.getInventory().selectedSlot = slot;
        }
    }

    private PlayerEntity getTarget() {
        if (Aura.INSTANCE != null && Aura.INSTANCE.isEnable() && Aura.INSTANCE.getTarget() instanceof PlayerEntity p) {
            return p;
        }

        PlayerEntity nearest = null;
        double nearestDist = range.get();

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || !player.isAlive() || player.isSpectator()) continue;
            if (Snill.INSTANCE.friendStorage != null && Snill.INSTANCE.friendStorage.isFriend(player.getName().getString())) continue;

            double dist = mc.player.distanceTo(player);
            if (dist <= nearestDist) {
                nearestDist = dist;
                nearest = player;
            }
        }
        return nearest;
    }
}
