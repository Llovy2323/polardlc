package snill.client.client.modules.impl.render.pets;

import snill.client.api.QClient;
import snill.client.api.utils.math.MathUtil;
import snill.client.api.utils.math.TimerUtils;
import snill.client.client.modules.impl.combat.Aura;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.BlockView;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.MathHelper;
import net.minecraft.client.network.ClientPlayerEntity;

public final class DogBrain
implements QClient {
    private static final double STOP_ANIMATION_MOVE_THRESHOLD_SQ = 0.0025;
    private static final double COLLISION_RADIUS = 0.32;
    private static final double COLLISION_HEIGHT = 0.82;
    private static final double GROUND_CHECK_OFFSET = 0.04;
    private static final long HYPE_ANIMATION_DURATION_MS = 2300L;
    private static final long BURNOUT_ANIMATION_DURATION_MS = 900L;
    private static final long BURNOUT_2_ANIMATION_DURATION_MS = 3400L;
    private static final float BURNOUT_2_WARMUP_PART = 0.72f;
    private static final double BLOCK_JUMP_VELOCITY = 0.42;
    public static final int CLICK_ANIMATION_HYPE = 0;
    public static final int CLICK_ANIMATION_BURNOUT = 1;
    public static final int CLICK_ANIMATION_BURNOUT_2 = 2;
    private Vec3d pos;
    private Vec3d motion = Vec3d.ZERO;
    private float direction = MathUtil.random(0.0, 360.0);
    private float yaw;
    private float body;
    private int speed = 50;
    private final SmoothFloat x = new SmoothFloat();
    private final SmoothFloat y = new SmoothFloat();
    private final SmoothFloat z = new SmoothFloat();
    private final SmoothFloat bodyAnim = new SmoothFloat();
    private final SmoothFloat yawAnim = new SmoothFloat();
    private final SmoothFloat pitchAnim = new SmoothFloat();
    private float prevX;
    private float prevY;
    private float prevZ;
    private float prevBody;
    private float prevYaw;
    private float prevPitch;
    private boolean lay;
    private final TimerUtils staying = new TimerUtils();
    public float prevLimbSwing;
    public float prevLimbSwingAmount;
    public float limbSwingAmount;
    public float limbSwing;
    private boolean wasMoving;
    private int movingTicks;
    private int idleAnimationTicks;
    private float prevIdleAnimationTicks;
    private boolean idleAnimating;
    private boolean hypeAnimationActive;
    private long hypeAnimationStartMs;
    private long hypeAnimationDurationMs = 2300L;
    private int clickAnimationMode = 0;
    private PlayerEntity entity;

    public void setEntity(PlayerEntity entity) {
        this.entity = entity;
    }

    public void reset() {
        this.pos = null;
        this.motion = Vec3d.ZERO;
        this.staying.reset();
        this.prevZ = 0.0f;
        this.prevY = 0.0f;
        this.prevX = 0.0f;
        this.prevPitch = 0.0f;
        this.prevYaw = 0.0f;
        this.prevBody = 0.0f;
        this.prevLimbSwing = 0.0f;
        this.limbSwing = 0.0f;
        this.limbSwingAmount = 0.0f;
        this.prevLimbSwingAmount = 0.0f;
        this.wasMoving = false;
        this.movingTicks = 0;
        this.idleAnimationTicks = 0;
        this.prevIdleAnimationTicks = 0.0f;
        this.idleAnimating = false;
        this.hypeAnimationActive = false;
        this.hypeAnimationStartMs = 0L;
        this.hypeAnimationDurationMs = 2300L;
        this.clickAnimationMode = 0;
    }

    public void tick() {
        boolean frozenForBurnout;
        if (this.entity == null || DogBrain.mc.world == null) {
            return;
        }
        this.prevX = this.x.get();
        this.prevY = this.y.get();
        this.prevZ = this.z.get();
        this.prevBody = this.bodyAnim.get();
        this.prevYaw = this.yawAnim.get();
        this.prevPitch = this.pitchAnim.get();
        this.prevLimbSwing = this.limbSwing;
        this.prevLimbSwingAmount = this.limbSwingAmount;
        this.prevIdleAnimationTicks = this.idleAnimationTicks;
        this.updateHypeAnimation();
        Vec3d playerPos = this.entity.getPos();
        if (this.pos == null || this.pos.distanceTo(playerPos) > 10.0) {
            this.pos = playerPos;
            this.x.set((float)this.pos.x);
            this.y.set((float)this.pos.y);
            this.z.set((float)this.pos.z);
        }
        if (frozenForBurnout = this.isBurnoutAnimationActive()) {
            this.motion = new Vec3d(0.0, this.motion.y, 0.0);
        }
        Vec3d requestedMotion = this.motion = this.motion.add(0.0, (double)-0.2f, 0.0);
        Vec3d newPos = this.moveWithCollision(this.pos, this.motion);
        boolean horizontalBlocked = Math.abs(requestedMotion.x) > 0.001 && Math.abs(newPos.x - this.pos.x) < Math.abs(requestedMotion.x) * 0.35 || Math.abs(requestedMotion.z) > 0.001 && Math.abs(newPos.z - this.pos.z) < Math.abs(requestedMotion.z) * 0.35;
        this.motion = new Vec3d(this.motion.x, 0.0, this.motion.z);
        LivingEntity target = Aura.INSTANCE.getTarget();
        if (!frozenForBurnout && target != null && this.entity instanceof ClientPlayerEntity) {
            if (this.isOnGround(newPos)) {
                this.motion = this.motion.add(0.0, (double)0.62f, 0.0);
            }
            Vec3d p2 = this.getPos();
            Box box = new Box(p2.x - 0.4, p2.y, p2.z - 0.4, p2.x + 0.4, p2.y + 0.4, p2.z + 0.4);
            Box targetBox = target.getBoundingBox().expand(-0.1, 0.0, -0.1);
            this.motion = this.motion.add(target.getPos().subtract(newPos).normalize());
            if (box.intersects(targetBox)) {
                this.motion = new Vec3d(-this.motion.x, this.motion.y, -this.motion.z);
            }
        } else if (!frozenForBurnout && newPos.distanceTo(playerPos) > 2.0) {
            this.motion = this.motion.add(playerPos.subtract(newPos).normalize());
        }
        if (!frozenForBurnout && horizontalBlocked && this.isOnGround(newPos) && this.canJumpOverBlock(newPos, this.motion)) {
            this.motion = new Vec3d(this.motion.x, 0.42, this.motion.z);
        }
        this.handleRotation();
        double horizontalMoveSq = MathHelper.square((double)(newPos.x - this.pos.x)) + MathHelper.square((double)(newPos.z - this.pos.z));
        this.pos = newPos;
        if (!frozenForBurnout && this.pos.distanceTo(playerPos) < (double)0.1f) {
            this.direction = MathUtil.random(0.0, 360.0);
            double xMot = -Math.sin(Math.toRadians(this.direction)) * 0.1;
            double zMot = Math.cos(Math.toRadians(this.direction)) * 0.1;
            this.motion = this.motion.add(xMot, 0.0, zMot);
        }
        this.motion = frozenForBurnout ? Vec3d.ZERO : this.motion.multiply(0.5, 0.5, 0.5);
        this.speed = 150;
        this.x.animate((float)this.pos.x, this.speed);
        this.y.animate((float)this.pos.y, this.speed);
        this.z.animate((float)this.pos.z, this.speed);
        this.limbTick();
        this.updateStopAnimation(horizontalMoveSq > 0.0025 || this.limbSwingAmount > 0.08f);
        if (Math.abs(this.pos.x - (double)this.x.get()) > (double)0.1f || Math.abs(this.pos.z - (double)this.z.get()) > (double)0.1f) {
            this.staying.reset();
        }
        this.lay = false;
    }

    private void handleRotation() {
        float gradus1;
        if (this.motion.x != 0.0 || this.motion.z != 0.0) {
            double angle = Math.atan2(this.motion.z, this.motion.x);
            this.yaw = (float)Math.toDegrees(angle) - 90.0f;
            this.yaw %= 360.0f;
            if (this.yaw < 0.0f) {
                this.yaw += 360.0f;
            }
        }
        Vec2f rotation = this.getRotations(this.pos, this.entity.getEyePos());
        LivingEntity target = Aura.INSTANCE.getTarget();
        if (target != null && this.entity instanceof ClientPlayerEntity) {
            rotation = this.getRotations(this.pos, target.getPos());
        }
        float gradus = this.lay ? 200.0f : 150.0f;
        float f2 = gradus1 = this.lay ? 100.0f : 50.0f;
        if (rotation.x - this.yaw < -gradus || rotation.x - this.yaw > gradus) {
            this.yaw = rotation.x;
        }
        float shortestYawPath = ((this.yaw - this.body) % 360.0f + 540.0f) % 360.0f - 180.0f;
        if (!this.lay) {
            this.bodyAnim.animate(this.body + shortestYawPath, 150.0f);
        }
        this.yawAnim.animate(MathHelper.clamp((float)(rotation.x - this.yaw), (float)(-gradus1), (float)gradus1), 150.0f);
        this.pitchAnim.animate(rotation.y, 150.0f);
        this.body += shortestYawPath;
    }

    private Vec2f getRotations(Vec3d from, Vec3d to) {
        Vec3d delta = to.subtract(from);
        double distanceXZ = Math.hypot(delta.x, delta.z);
        float yaw = (float)(Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0);
        float pitch = (float)(-Math.toDegrees(Math.atan2(delta.y, distanceXZ)));
        return new Vec2f(yaw, pitch);
    }

    public void limbTick() {
        double d2;
        double d0 = (double)this.x.get() - this.pos.x;
        float f2 = MathHelper.sqrt((float)((float)(d0 * d0 + (d2 = (double)this.z.get() - this.pos.z) * d2))) * 4.0f;
        if (f2 > 1.0f) {
            f2 = 1.0f;
        }
        this.limbSwingAmount += (f2 - this.limbSwingAmount) * 0.4f;
        this.limbSwing += this.limbSwingAmount;
    }

    public Vec3d getRenderPos(float partialTicks) {
        float rx = MathHelper.lerp((float)partialTicks, (float)this.prevX, (float)this.x.get());
        float ry = MathHelper.lerp((float)partialTicks, (float)this.prevY, (float)this.y.get());
        float rz = MathHelper.lerp((float)partialTicks, (float)this.prevZ, (float)this.z.get());
        return new Vec3d((double)rx, (double)ry, (double)rz);
    }

    public float getRenderBody(float partialTicks) {
        return MathHelper.lerp((float)partialTicks, (float)this.prevBody, (float)this.bodyAnim.get());
    }

    public float getRenderYaw(float partialTicks) {
        return MathHelper.lerp((float)partialTicks, (float)this.prevYaw, (float)this.yawAnim.get());
    }

    public float getRenderPitch(float partialTicks) {
        return MathHelper.lerp((float)partialTicks, (float)this.prevPitch, (float)this.pitchAnim.get());
    }

    public float getRenderLimbSwing(float partialTicks) {
        return MathHelper.lerp((float)partialTicks, (float)this.prevLimbSwing, (float)this.limbSwing);
    }

    public float getRenderLimbSwingAmount(float partialTicks) {
        return MathHelper.lerp((float)partialTicks, (float)this.prevLimbSwingAmount, (float)this.limbSwingAmount);
    }

    public float getIdleAnimationTicks(float partialTicks) {
        return this.idleAnimating ? MathHelper.lerp((float)partialTicks, (float)this.prevIdleAnimationTicks, (float)this.idleAnimationTicks) : 0.0f;
    }

    public float getHypeAnimationTicks(float partialTicks) {
        if (!this.hypeAnimationActive) {
            return 0.0f;
        }
        return (float)(System.currentTimeMillis() - this.hypeAnimationStartMs) / 50.0f;
    }

    public float getHypeAnimationProgress(float partialTicks) {
        if (!this.hypeAnimationActive) {
            return 0.0f;
        }
        return MathHelper.clamp((float)((float)(System.currentTimeMillis() - this.hypeAnimationStartMs) / (float)this.hypeAnimationDurationMs), (float)0.0f, (float)1.0f);
    }

    public boolean playHypeAnimation(int mode) {
        if (this.hypeAnimationActive) {
            return false;
        }
        this.clickAnimationMode = mode;
        this.hypeAnimationDurationMs = switch (mode) {
            case 1 -> 900L;
            case 2 -> 3400L;
            default -> 2300L;
        };
        this.hypeAnimationStartMs = System.currentTimeMillis();
        this.hypeAnimationActive = true;
        if (this.isBurnoutMode(mode)) {
            this.motion = Vec3d.ZERO;
        }
        return true;
    }

    public boolean isBurnoutAnimationActive() {
        return this.hypeAnimationActive && this.isBurnoutMode(this.clickAnimationMode);
    }

    public int getClickAnimationMode() {
        return this.hypeAnimationActive ? this.clickAnimationMode : 0;
    }

    public float getBurnout2WarmupProgress(float partialTicks) {
        if (!this.hypeAnimationActive || this.clickAnimationMode != 2) {
            return 0.0f;
        }
        float progress = this.getHypeAnimationProgress(partialTicks);
        return MathHelper.clamp((float)(progress / 0.72f), (float)0.0f, (float)1.0f);
    }

    public float getBurnout2FlipProgress(float partialTicks) {
        if (!this.hypeAnimationActive || this.clickAnimationMode != 2) {
            return 0.0f;
        }
        float progress = this.getHypeAnimationProgress(partialTicks);
        return MathHelper.clamp((float)((progress - 0.72f) / 0.27999997f), (float)0.0f, (float)1.0f);
    }

    public Box getInteractionBox() {
        Vec3d position = this.getPos();
        return this.getCollisionBox(position.x, position.y, position.z).expand(0.14, 0.18, 0.14);
    }

    public Vec3d getPos() {
        return new Vec3d((double)this.x.get(), (double)this.y.get(), (double)this.z.get());
    }

    public boolean isLay() {
        return this.lay;
    }

    private boolean isBlockSolid(double x2, double y2, double z2) {
        if (DogBrain.mc.world == null) {
            return false;
        }
        BlockPos pos = BlockPos.ofFloored((double)x2, (double)y2, (double)z2);
        BlockState state = DogBrain.mc.world.getBlockState(pos);
        return !state.isAir() && state.isSolidBlock((BlockView)DogBrain.mc.world, pos);
    }

    private Vec3d moveWithCollision(Vec3d start, Vec3d velocity) {
        double x2 = start.x;
        double y2 = start.y;
        double z2 = start.z;
        double vx = velocity.x;
        double vy = velocity.y;
        double vz = velocity.z;
        if (vy != 0.0) {
            double nextY = y2 + vy;
            if (!this.collidesAt(x2, nextY, z2)) {
                y2 = nextY;
            } else {
                vy = 0.0;
            }
        }
        if (vx != 0.0) {
            double nextX = x2 + vx;
            if (!this.collidesAt(nextX, y2, z2)) {
                x2 = nextX;
            } else {
                vx = 0.0;
            }
        }
        if (vz != 0.0) {
            double nextZ = z2 + vz;
            if (!this.collidesAt(x2, y2, nextZ)) {
                z2 = nextZ;
            } else {
                vz = 0.0;
            }
        }
        this.motion = new Vec3d(vx, vy, vz);
        return new Vec3d(x2, y2, z2);
    }

    private boolean isOnGround(Vec3d position) {
        return this.collidesAt(position.x, position.y - 0.04, position.z);
    }

    private boolean canJumpOverBlock(Vec3d position, Vec3d velocity) {
        double length = Math.hypot(velocity.x, velocity.z);
        if (length < 0.001) {
            return false;
        }
        double stepX = velocity.x / length * 0.38;
        double targetX = position.x + stepX;
        double stepZ = velocity.z / length * 0.38;
        double targetZ = position.z + stepZ;
        return this.collidesAt(targetX, position.y, targetZ) && !this.collidesAt(targetX, position.y + 1.0, targetZ);
    }

    private boolean collidesAt(double x2, double y2, double z2) {
        return this.intersectsBlocks(this.getCollisionBox(x2, y2, z2));
    }

    private Box getCollisionBox(double x2, double y2, double z2) {
        return new Box(x2 - 0.32, y2, z2 - 0.32, x2 + 0.32, y2 + 0.82, z2 + 0.32);
    }

    private boolean intersectsBlocks(Box box) {
        if (DogBrain.mc.world == null) {
            return false;
        }
        int minX = MathHelper.floor((double)box.minX);
        int minY = MathHelper.floor((double)box.minY);
        int minZ = MathHelper.floor((double)box.minZ);
        int maxX = MathHelper.floor((double)box.maxX);
        int maxY = MathHelper.floor((double)box.maxY);
        int maxZ = MathHelper.floor((double)box.maxZ);
        for (int blockX = minX; blockX <= maxX; ++blockX) {
            for (int blockY = minY; blockY <= maxY; ++blockY) {
                for (int blockZ = minZ; blockZ <= maxZ; ++blockZ) {
                    VoxelShape shape;
                    BlockPos blockPos = new BlockPos(blockX, blockY, blockZ);
                    BlockState state = DogBrain.mc.world.getBlockState(blockPos);
                    if (state.isAir() || (shape = state.getCollisionShape((BlockView)DogBrain.mc.world, blockPos)).isEmpty()) continue;
                    for (Box blockBox : shape.getBoundingBoxes()) {
                        if (!blockBox.offset((double)blockX, (double)blockY, (double)blockZ).intersects(box)) continue;
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void updateStopAnimation(boolean moving) {
        if (moving) {
            this.movingTicks = Math.min(this.movingTicks + 1, 20);
            this.wasMoving = true;
            this.idleAnimating = false;
            this.idleAnimationTicks = 0;
            return;
        }
        if (this.wasMoving && this.movingTicks >= 4) {
            this.idleAnimating = true;
            this.idleAnimationTicks = 0;
        }
        this.wasMoving = false;
        this.movingTicks = 0;
        if (this.idleAnimating) {
            ++this.idleAnimationTicks;
        }
    }

    private void updateHypeAnimation() {
        if (this.hypeAnimationActive && System.currentTimeMillis() - this.hypeAnimationStartMs >= this.hypeAnimationDurationMs) {
            this.hypeAnimationActive = false;
            this.clickAnimationMode = 0;
            this.hypeAnimationDurationMs = 2300L;
        }
    }

    private boolean isBurnoutMode(int mode) {
        return mode == 1 || mode == 2;
    }

    private static final class SmoothFloat {
        private float value;
        private boolean initialized;

        private SmoothFloat() {
        }

        void set(float value) {
            this.value = value;
            this.initialized = true;
        }

        void animate(float target, float speed) {
            if (!this.initialized) {
                this.set(target);
                return;
            }
            if (speed <= 1.0f) {
                this.value = target;
                return;
            }
            float factor = MathHelper.clamp((float)(speed / 1200.0f), (float)0.02f, (float)0.35f);
            this.value += (target - this.value) * factor;
        }

        float get() {
            return this.value;
        }
    }
}