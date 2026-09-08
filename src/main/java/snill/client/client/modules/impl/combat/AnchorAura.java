package snill.client.client.modules.impl.combat;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
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

public class AnchorAura extends Module {

    public static AnchorAura INSTANCE = new AnchorAura();

    private final ModeSetting server = new ModeSetting("Сервер", "FunTime", "FunTime", "HolyWorld", "ReallyWorld", "Универсальный");
    private final FloatSetting range = new FloatSetting("Дистанция", 4.2f, 2.0f, 5.0f, 0.1f);
    private final BooleanSetting antiSuicide = new BooleanSetting("Анти-суицид", true);
    private final BooleanSetting place = new BooleanSetting("Ставить якорь", true);
    private final BooleanSetting explode = new BooleanSetting("Взрывать", true);
    private final FloatSetting delay = new FloatSetting("Задержка (мс)", 60f, 0f, 200f, 10f)
            .visible(() -> server.is("Универсальный"));

    private long lastActionTime = 0L;

    public AnchorAura() {
        super("AnchorAura", "[FunTime / HolyWorld / Анархии] Автоматически заряжает и взрывает якорь", ModuleCategory.COMBAT);
        addSettings(server, range, antiSuicide, place, explode, delay);
    }

    private long getEffectiveDelay() {
        if (server.is("FunTime")) return 80L;
        if (server.is("HolyWorld")) return 60L;
        if (server.is("ReallyWorld")) return 100L;
        return (long) delay.get();
    }

    @EventLink
    public void onUpdate(EventUpdate event) {
        if (mc.player == null || mc.world == null) return;

        // In Nether, respawn anchor sets spawn point instead of exploding
        if (mc.world.getDimension().respawnAnchorWorks()) return;

        long now = System.currentTimeMillis();
        if (now - lastActionTime < getEffectiveDelay()) return;

        PlayerEntity target = getTarget();
        if (target == null) return;

        // 1. Check if there's already an active anchor near target
        BlockPos existingAnchor = findExistingAnchor(target);
        if (existingAnchor != null) {
            handleExistingAnchor(existingAnchor);
            return;
        }

        // 2. Otherwise, place a new anchor if enabled
        if (place.isState()) {
            tryPlaceAnchor(target);
        }
    }

    private void handleExistingAnchor(BlockPos anchorPos) {
        BlockState state = mc.world.getBlockState(anchorPos);
        if (!state.isOf(Blocks.RESPAWN_ANCHOR)) return;

        int charges = state.get(RespawnAnchorBlock.CHARGES);
        Vec3d anchorVec = Vec3d.ofCenter(anchorPos);

        if (charges == 0) {
            // Charge with Glowstone
            int glowSlot = InventoryUtils.find(Items.GLOWSTONE, 0, 9);
            if (glowSlot == -1) return;

            int oldSlot = mc.player.getInventory().selectedSlot;
            selectSlot(glowSlot);

            BlockHitResult hit = new BlockHitResult(anchorVec, Direction.UP, anchorPos, false);
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            mc.player.swingHand(Hand.MAIN_HAND);

            selectSlot(oldSlot);
            lastActionTime = System.currentTimeMillis();
        } else if (explode.isState()) {
            // Explode anchor (click with non-glowstone item)
            if (antiSuicide.isState() && isDangerousForPlayer(anchorPos)) {
                return;
            }

            // Find non-glowstone slot
            int safeSlot = -1;
            for (int i = 0; i < 9; i++) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                if (stack.isEmpty() || stack.getItem() != Items.GLOWSTONE) {
                    safeSlot = i;
                    break;
                }
            }
            if (safeSlot == -1) safeSlot = mc.player.getInventory().selectedSlot;

            int oldSlot = mc.player.getInventory().selectedSlot;
            selectSlot(safeSlot);

            BlockHitResult hit = new BlockHitResult(anchorVec, Direction.UP, anchorPos, false);
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            mc.player.swingHand(Hand.MAIN_HAND);

            selectSlot(oldSlot);
            lastActionTime = System.currentTimeMillis();
        }
    }

    private void tryPlaceAnchor(PlayerEntity target) {
        int anchorSlot = InventoryUtils.find(Items.RESPAWN_ANCHOR, 0, 9);
        if (anchorSlot == -1) return;

        BlockPos placePos = findBestPlacePos(target);
        if (placePos == null) return;

        BlockPos supportPos = placePos.down();
        if (!mc.world.getBlockState(supportPos).isSolidBlock(mc.world, supportPos)) return;

        int oldSlot = mc.player.getInventory().selectedSlot;
        selectSlot(anchorSlot);

        Vec3d hitVec = new Vec3d(placePos.getX() + 0.5, placePos.getY(), placePos.getZ() + 0.5);
        BlockHitResult hit = new BlockHitResult(hitVec, Direction.UP, supportPos, false);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        mc.player.swingHand(Hand.MAIN_HAND);

        selectSlot(oldSlot);
        lastActionTime = System.currentTimeMillis();
    }

    private BlockPos findExistingAnchor(PlayerEntity target) {
        BlockPos targetPos = target.getBlockPos();
        double bestDist = Double.MAX_VALUE;
        BlockPos bestPos = null;

        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos pos = targetPos.add(dx, dy, dz);
                    if (mc.world.getBlockState(pos).isOf(Blocks.RESPAWN_ANCHOR)) {
                        double playerDist = mc.player.getEyePos().distanceTo(Vec3d.ofCenter(pos));
                        if (playerDist <= range.get() && playerDist < bestDist) {
                            bestDist = playerDist;
                            bestPos = pos;
                        }
                    }
                }
            }
        }
        return bestPos;
    }

    private BlockPos findBestPlacePos(PlayerEntity target) {
        BlockPos feetPos = target.getBlockPos();
        BlockPos[] candidates = new BlockPos[]{
                feetPos,
                feetPos.north(), feetPos.south(), feetPos.east(), feetPos.west(),
                feetPos.up()
        };

        for (BlockPos pos : candidates) {
            if (canPlaceAnchorAt(pos)) {
                double playerDist = mc.player.getEyePos().distanceTo(Vec3d.ofCenter(pos));
                if (playerDist <= range.get()) {
                    if (!antiSuicide.isState() || !isDangerousForPlayer(pos)) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    private boolean canPlaceAnchorAt(BlockPos pos) {
        if (!mc.world.getBlockState(pos).isAir() && !mc.world.getBlockState(pos).isLiquid()) return false;
        BlockPos support = pos.down();
        return mc.world.getBlockState(support).isSolidBlock(mc.world, support);
    }

    private boolean isDangerousForPlayer(BlockPos anchorPos) {
        double dist = mc.player.getPos().distanceTo(Vec3d.ofCenter(anchorPos));
        if (dist > 3.0) return false;

        // If player has totem in offhand or main hand, danger is lower
        boolean hasTotem = mc.player.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING) ||
                mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);

        if (hasTotem) {
            return dist < 1.8 && mc.player.getHealth() < 8f;
        }
        return dist < 2.8 || mc.player.getHealth() < 14f;
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
