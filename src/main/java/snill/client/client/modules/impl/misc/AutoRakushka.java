package snill.client.client.modules.impl.misc;

import net.minecraft.block.Blocks;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.option.KeyBinding;
import snill.client.api.events.EventLink;
import snill.client.api.events.implement.EventUpdate;
import snill.client.client.modules.Module;
import snill.client.client.modules.settings.implement.FloatSetting;

import java.util.HashSet;
import java.util.Set;

public final class AutoRakushka extends Module {
    public static final AutoRakushka INSTANCE = new AutoRakushka();

    private static final BlockPos[] TARGETS = {
            new BlockPos(65, 60, -10),
            new BlockPos(69, 59, -1),
            new BlockPos(79, 58, 34),
            new BlockPos(98, 58, 42),
            new BlockPos(123, 59, 26),
            new BlockPos(91, 58, 18),
            new BlockPos(95, 58, -8),
            new BlockPos(125, 61, -1),
            new BlockPos(115, 59, -37),
            new BlockPos(85, 59, -41),
            new BlockPos(100, 59, -64),
            new BlockPos(116, 59, -71)
    };

    private final FloatSetting flightSpeed = new FloatSetting("Speed", 0.45F, 0.1F, 1.5F, 0.05F);
    private final FloatSetting flightHeight = new FloatSetting("Height", 3.0F, 1.0F, 8.0F, 0.5F);
    private final Set<BlockPos> completedTargets = new HashSet<>();

    private int targetIndex;
    private BlockPos landingPos;
    private boolean breakingTarget;
    private boolean flightKeysPressed;
    private int postBreakRiseTicks;

    private AutoRakushka() {
        super("AutoRakushka", "Fixed head route", ModuleCategory.MISC);
        addSettings(flightSpeed, flightHeight);
    }

    @Override
    public void onEnable() {
        targetIndex = 0;
        landingPos = null;
        breakingTarget = false;
        flightKeysPressed = false;
        postBreakRiseTicks = 0;
        completedTargets.clear();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        if (mc.player != null) {
            mc.player.setVelocity(Vec3d.ZERO);
        }
        releaseFlightKeys();
        if (breakingTarget && mc.interactionManager != null) {
            mc.interactionManager.cancelBlockBreaking();
        }
        breakingTarget = false;
        landingPos = null;
        postBreakRiseTicks = 0;
        completedTargets.clear();
        super.onDisable();
    }

    @EventLink
    public void onUpdate(EventUpdate event) {
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) {
            return;
        }
        if (targetIndex >= TARGETS.length) {
            if (breakingTarget && mc.interactionManager != null) {
                mc.interactionManager.cancelBlockBreaking();
                breakingTarget = false;
            }
            releaseFlightKeys();
            return;
        }

        BlockPos target = TARGETS[targetIndex];
        double targetDistanceSq = mc.player.squaredDistanceTo(target.toCenterPos());

        if (!isHead(target)) {
            completeTarget(target);
            return;
        }

        if (!mc.player.getAbilities().flying) {
            return;
        }

        if (postBreakRiseTicks > 0) {
            riseAfterBreak();
            postBreakRiseTicks--;
            return;
        }

        boolean aligned = flyToTarget(target);
        boolean canMine = isHead(target)
                && horizontalDistanceSqTo(target) <= 9.0D
                && mc.player.getY() >= target.getY() + 1.2D
                && mc.player.getEyePos().squaredDistanceTo(target.toCenterPos()) <= 25.0D;

        if (canMine && mc.interactionManager != null) {
            releaseFlightKeys();
            mc.player.setVelocity(mc.player.getVelocity().x * 0.2D, 0.0D, mc.player.getVelocity().z * 0.2D);
            lookAt(target.toCenterPos());
            Direction side = getBreakSide(target);
            if (!breakingTarget) {
                breakingTarget = mc.interactionManager.attackBlock(target, side);
            }
            mc.interactionManager.updateBlockBreakingProgress(target, side);
            mc.player.swingHand(Hand.MAIN_HAND);
            return;
        }

        if (breakingTarget && mc.interactionManager != null) {
            mc.interactionManager.cancelBlockBreaking();
            breakingTarget = false;
        }

        if (aligned && !isHead(target)) {
            completeTarget(target);
        }
    }

    private boolean flyToTarget(BlockPos target) {
        Vec3d playerPos = mc.player.getPos();
        Vec3d destination = getFlightDestination(target, playerPos);
        Vec3d delta = destination.subtract(playerPos);
        double horizontalSq = delta.x * delta.x + delta.z * delta.z;
        boolean horizontalClose = horizontalSq < 1.44D;
        boolean nearTarget = horizontalDistanceSqTo(target) <= 9.0D;
        boolean descendAtTarget = horizontalClose && delta.y < -0.20D;

        face(destination);
        if (!descendAtTarget && delta.y > 0.45D) {
            boostUpward(delta.y);
        }
        setFlightKeys(!descendAtTarget && !nearTarget, delta.y);

        return horizontalClose && Math.abs(delta.y) < 0.8D;
    }

    private void completeTarget(BlockPos target) {
        completedTargets.add(target.toImmutable());
        targetIndex++;
        landingPos = null;
        breakingTarget = false;
        releaseFlightKeys();
        postBreakRiseTicks = 12;
    }

    private BlockPos findLandingPos(BlockPos target) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int radius = 1; radius <= 3; radius++) {
            for (Direction direction : Direction.Type.HORIZONTAL) {
                BlockPos candidate = target.offset(direction, radius);
                if (!isSafeLanding(candidate)) {
                    continue;
                }
                double distance = mc.player.squaredDistanceTo(candidate.toCenterPos());
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = candidate;
                }
            }
        }
        return best;
    }

    private boolean isSafeLanding(BlockPos pos) {
        return mc.world.getBlockState(pos).getCollisionShape(mc.world, pos).isEmpty()
                && mc.world.getBlockState(pos.up()).getCollisionShape(mc.world, pos.up()).isEmpty()
                && !mc.world.getBlockState(pos.down()).getCollisionShape(mc.world, pos.down()).isEmpty();
    }

    private Direction getBreakSide(BlockPos pos) {
        return Direction.UP;
    }

    private boolean isHead(BlockPos pos) {
        var block = mc.world.getBlockState(pos).getBlock();
        return block == Blocks.PLAYER_HEAD || block == Blocks.PLAYER_WALL_HEAD;
    }

    private void lookAt(Vec3d pos) {
        Vec3d eye = mc.player.getEyePos();
        double dx = pos.x - eye.x;
        double dy = pos.y - eye.y;
        double dz = pos.z - eye.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        mc.player.setYaw((float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F);
        mc.player.setPitch((float) -Math.toDegrees(Math.atan2(dy, horizontal)));
    }

    private Vec3d getFlightDestination(BlockPos target, Vec3d playerPos) {
        double hoverY = findHoverY(target);
        Vec3d base = new Vec3d(target.getX() + 0.5D, hoverY, target.getZ() + 0.5D);
        if (horizontalDistanceSqTo(target) > 9.0D && hasPathCollision(playerPos, base)) {
            base = new Vec3d(base.x, Math.max(base.y, playerPos.y + 2.0D), base.z);
        }
        return base;
    }

    private double findHoverY(BlockPos target) {
        for (int y = target.getY() + 2; y <= target.getY() + 8; y++) {
            BlockPos pos = new BlockPos(target.getX(), y, target.getZ());
            if (isHoverSpace(pos)) {
                return y + 0.5D;
            }
        }
        return target.getY() + 8.5D;
    }

    private double horizontalDistanceSqTo(BlockPos pos) {
        double dx = mc.player.getX() - (pos.getX() + 0.5D);
        double dz = mc.player.getZ() - (pos.getZ() + 0.5D);
        return dx * dx + dz * dz;
    }

    private boolean isHoverSpace(BlockPos pos) {
        return mc.world.getBlockState(pos).getCollisionShape(mc.world, pos).isEmpty()
                && mc.world.getBlockState(pos.up()).getCollisionShape(mc.world, pos.up()).isEmpty();
    }

    private boolean hasPathCollision(Vec3d from, Vec3d to) {
        int samples = Math.max(6, (int) Math.ceil(from.distanceTo(to) * 2.0D));
        for (int i = 1; i <= samples; i++) {
            double t = (double) i / (double) samples;
            double x = from.x + (to.x - from.x) * t;
            double y = from.y + (to.y - from.y) * t;
            double z = from.z + (to.z - from.z) * t;
            BlockPos pos = BlockPos.ofFloored(x, y, z);
            if (!mc.world.getBlockState(pos).getCollisionShape(mc.world, pos).isEmpty()) {
                return true;
            }
            if (!mc.world.getBlockState(pos.up()).getCollisionShape(mc.world, pos.up()).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private void face(Vec3d pos) {
        Vec3d eye = mc.player.getEyePos();
        double dx = pos.x - eye.x;
        double dz = pos.z - eye.z;
        mc.player.setYaw((float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F);
    }

    private void setFlightKeys(boolean forward, double verticalDelta) {
        setKey(mc.options.forwardKey, forward);
        setKey(mc.options.backKey, false);
        setKey(mc.options.leftKey, false);
        setKey(mc.options.rightKey, false);
        setKey(mc.options.sneakKey, verticalDelta < -0.20D);
        setKey(mc.options.jumpKey, false);
        flightKeysPressed = forward || verticalDelta < -0.20D;
    }

    private void riseAfterBreak() {
        releaseFlightKeys();
        Vec3d velocity = mc.player.getVelocity();
        mc.player.setVelocity(velocity.x * 0.35D, Math.max(velocity.y, 0.28D), velocity.z * 0.35D);
    }

    private void boostUpward(double deltaY) {
        Vec3d velocity = mc.player.getVelocity();
        double rise = Math.min(0.30D, Math.max(0.12D, deltaY * 0.08D));
        mc.player.setVelocity(velocity.x, Math.max(velocity.y, rise), velocity.z);
    }

    private void releaseFlightKeys() {
        if (!flightKeysPressed) {
            return;
        }
        releaseHorizontalKeys();
        setKey(mc.options.jumpKey, false);
        setKey(mc.options.sneakKey, false);
        flightKeysPressed = false;
    }

    private void releaseHorizontalKeys() {
        setKey(mc.options.forwardKey, false);
        setKey(mc.options.backKey, false);
        setKey(mc.options.leftKey, false);
        setKey(mc.options.rightKey, false);
    }

    private void setKey(KeyBinding key, boolean pressed) {
        if (key.isPressed() != pressed) {
            key.setPressed(pressed);
        }
    }
}
