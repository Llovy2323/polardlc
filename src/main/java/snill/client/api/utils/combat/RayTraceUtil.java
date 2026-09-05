package snill.client.api.utils.combat;

import lombok.experimental.UtilityClass;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Vector3f;
import snill.client.api.QClient;
import snill.client.client.modules.impl.combat.ElytraTarget;

import java.util.Objects;
import java.util.Optional;

@UtilityClass
public class RayTraceUtil implements QClient {
    public HitResult rayTrace(double rayTraceDistance,
                              float yaw,
                              float pitch,
                              Entity entity) {

        Vec3d startVec = mc.player.getEyePos();
        Vec3d directionVec = getVectorForRotation(pitch, yaw);

        Vec3d endVec = startVec.add(
                directionVec.x * rayTraceDistance,
                directionVec.y * rayTraceDistance,
                directionVec.z * rayTraceDistance
        );

        return mc.world.raycast(new RaycastContext(
                startVec,
                endVec,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                entity)
        );
    }

    public BlockHitResult raycast(Vec3d start, Vec3d end, RaycastContext.ShapeType shapeType) {
        return raycast(start, end, shapeType, mc.player);
    }

    public BlockHitResult raycast(Vec3d start, Vec3d end, RaycastContext.ShapeType shapeType, Entity entity) {
        return mc.world.raycast(new RaycastContext(start, end, shapeType, RaycastContext.FluidHandling.NONE, entity));
    }

    public boolean rayTrace(Vec3d clientVec, double range, Box box) {
        Vec3d cameraVec = Objects.requireNonNull(mc.player).getEyePos();
        return box.contains(cameraVec) || box.raycast(cameraVec, cameraVec.add(clientVec.multiply(range))).isPresent();
    }

    public boolean isViewEntity(LivingEntity target, float yaw, float pitch, float distance, boolean ignoreWalls) {
        if (target == null) {
            return false;
        }

        if (mc.player != null && (mc.player.isGliding() || target.isGliding())) {
            return rayTraceEntity(yaw, pitch, distance, target, ignoreWalls);
        }

        Entity entity = mc.getCameraEntity();
        if (entity == null || mc.world == null) {
            return false;
        }

        double reachDistanceSquared = distance * distance;
        Vec3d startVec = entity.getEyePos();
        Vector3f directionVec = calculateViewVector(yaw, pitch);
        directionVec.mul(distance, distance, distance);
        Vec3d endVec = startVec.add(directionVec.x, directionVec.y, directionVec.z);
        Box aabb = target.getBoundingBox();

        EntityHitResult result = ProjectileUtil.raycast(
                entity,
                startVec,
                endVec,
                aabb,
                entityIn -> !entityIn.isSpectator() && entityIn.isAlive() && entityIn == target,
                reachDistanceSquared
        );

        return result != null;
    }

    public boolean rayTraceEntity(float yaw, float pitch, double distance, Entity entity, boolean raytraceBlock) {
        if (mc.player == null || mc.world == null || entity == null) {
            return false;
        }

        Vec3d eyeVec = mc.player.getEyePos();
        Vec3d lookVec = getVectorForRotation(pitch, yaw).normalize();
        Vec3d endVec = eyeVec.add(lookVec.multiply(distance));

        RayHit hit = traceBox(getStrictBox(entity), eyeVec, endVec);
        if (!hit.hit && shouldUseElytraTrace(entity)) {
            hit = traceElytraBox(entity, eyeVec, endVec);
        }

        if (!hit.hit) {
            return false;
        }

        return !raytraceBlock || canSeeHitPoint(eyeVec, hit.point);
    }

    public Vector3f calculateViewVector(float yaw, float pitch) {
        float pitchRad = pitch * 0.017453292519943295F;
        float yawRad = -yaw * 0.017453292519943295F;
        float cosYaw = MathHelper.cos(yawRad);
        float sinYaw = MathHelper.sin(yawRad);
        float cosPitch = MathHelper.cos(pitchRad);
        float sinPitch = MathHelper.sin(pitchRad);

        return new Vector3f(sinYaw * cosPitch, -sinPitch, cosYaw * cosPitch);
    }

    public Vec3d getVectorForRotation(float pitch, float yaw) {
        float yawRadians = -yaw * ((float) Math.PI / 180) - (float) Math.PI;
        float pitchRadians = -pitch * ((float) Math.PI / 180);

        float cosYaw = MathHelper.cos(yawRadians);
        float sinYaw = MathHelper.sin(yawRadians);
        float cosPitch = -MathHelper.cos(pitchRadians);
        float sinPitch = MathHelper.sin(pitchRadians);

        return new Vec3d(sinYaw * cosPitch, sinPitch, cosYaw * cosPitch);
    }

    public boolean rayTraceSingleEntity(float yaw, float pitch, double distance, Entity entity) {
        Vec3d eyeVec = mc.player.getEyePos();
        Vec3d lookVec = mc.player.getRotationVector(pitch, yaw);
        Vec3d extendedVec = eyeVec.add(lookVec.multiply(distance));

        Box aabb = entity.getBoundingBox();
        return aabb.contains(eyeVec) || aabb.raycast(eyeVec, extendedVec).isPresent();
    }

    private Box getStrictBox(Entity entity) {
        return entity.getBoundingBox();
    }

    private boolean shouldUseElytraTrace(Entity entity) {
        return mc.player.isGliding() || entity instanceof LivingEntity livingEntity && livingEntity.isGliding();
    }

    private RayHit traceElytraBox(Entity entity, Vec3d eyeVec, Vec3d endVec) {
        Box baseBox = getStrictBox(entity);
        Box sweptBox = buildElytraSweptBox(entity, baseBox);
        RayHit boxHit = traceBox(sweptBox, eyeVec, endVec);
        if (boxHit.hit) {
            return boxHit;
        }

        return traceElytraCorridor(entity, baseBox, eyeVec, endVec);
    }

    private Box buildElytraSweptBox(Entity entity, Box baseBox) {
        Vec3d playerMotion = mc.player.getVelocity();
        Vec3d entityMotion = entity.getVelocity();
        Vec3d relativeMotion = entityMotion.subtract(playerMotion);

        double entityHorizontalSpeed = Math.hypot(entityMotion.x, entityMotion.z);
        double playerHorizontalSpeed = Math.hypot(playerMotion.x, playerMotion.z);
        double relativeHorizontalSpeed = Math.hypot(relativeMotion.x, relativeMotion.z);

        double predictTicks = MathHelper.clamp(
                1.25D + entityHorizontalSpeed * 1.15D + playerHorizontalSpeed * 0.35D,
                1.25D,
                4.25D
        );

        Box sweptBox = union(baseBox, baseBox.offset(entity.prevX - entity.getX(), entity.prevY - entity.getY(), entity.prevZ - entity.getZ()));

        for (int i = 1; i <= 4; i++) {
            double scale = predictTicks * i / 4.0D;
            sweptBox = union(
                    sweptBox,
                    baseBox.offset(entityMotion.x * scale, entityMotion.y * scale, entityMotion.z * scale)
            );
        }

        Vec3d forward = getEntityForward(entity);
        if (forward.lengthSquared() > 1.0E-4D) {
            double forwardPredict = getElytraForwardTraceDistance(
                    entity,
                    1.2D + entityHorizontalSpeed * 2.4D + playerHorizontalSpeed * 0.45D
            );
            sweptBox = union(sweptBox, baseBox.offset(forward.normalize().multiply(forwardPredict)));
        }

        double growXZ = MathHelper.clamp(
                0.12D + relativeHorizontalSpeed * 0.85D + (mc.player.isGliding() ? 0.22D : 0.0D),
                0.18D,
                1.35D
        );
        double growY = MathHelper.clamp(
                0.10D + Math.abs(relativeMotion.y) * 1.15D + (entity instanceof LivingEntity livingEntity && livingEntity.isGliding() ? 0.18D : 0.0D),
                0.14D,
                0.90D
        );

        return sweptBox.expand(growXZ, growY, growXZ);
    }

    private RayHit traceElytraCorridor(Entity entity, Box baseBox, Vec3d eyeVec, Vec3d endVec) {
        Vec3d forward = getEntityForward(entity);
        if (forward.lengthSquared() <= 1.0E-4D) {
            return RayHit.MISS;
        }

        Vec3d anchor = entity instanceof LivingEntity livingEntity ? livingEntity.getEyePos() : baseBox.getCenter();
        Vec3d corridorEnd = anchor.add(forward.normalize().multiply(getElytraForwardTraceDistance(entity, 6.0D)));
        SegmentHit segmentHit = closestSegmentHit(eyeVec, endVec, anchor, corridorEnd);

        double boxLengthX = baseBox.maxX - baseBox.minX;
        double boxLengthY = baseBox.maxY - baseBox.minY;
        double boxLengthZ = baseBox.maxZ - baseBox.minZ;
        double radius = MathHelper.clamp(Math.max(boxLengthX, boxLengthZ) * 0.5D + 0.75D, 0.95D, 1.65D);
        double verticalRadius = MathHelper.clamp(boxLengthY * 0.5D + 0.35D, 0.95D, 1.45D);

        if (segmentHit.distance <= radius && Math.abs(segmentHit.targetPoint.y - segmentHit.rayPoint.y) <= verticalRadius) {
            return new RayHit(true, segmentHit.rayPoint);
        }

        return RayHit.MISS;
    }

    private double getElytraForwardTraceDistance(Entity entity, double fallback) {
        double maxDistance = 14.0D;
        double distance = MathHelper.clamp(fallback, 1.2D, maxDistance);

        ElytraTarget elytraTarget = ElytraTarget.INSTANCE;
        if (elytraTarget != null && elytraTarget.isAuraActive() && entity instanceof LivingEntity) {
            distance = Math.max(
                    distance,
                    MathHelper.clamp(elytraTarget.forwardValue.getValue().doubleValue() + 3.0D, 3.0D, maxDistance)
            );
        }

        return distance;
    }

    private Vec3d getEntityForward(Entity entity) {
        if (entity instanceof LivingEntity livingEntity) {
            PredictUtils.PositionData data = PredictUtils.getData(livingEntity);
            if (data != null) {
                Vec3d resolvedForward = data.getResolvedForward();
                if (resolvedForward.lengthSquared() > 1.0E-4D) {
                    return resolvedForward;
                }
            }
        }

        Vec3d motion = entity.getVelocity();
        Vec3d horizontalMotion = new Vec3d(motion.x, 0.0D, motion.z);
        if (horizontalMotion.lengthSquared() > 1.0E-4D) {
            return horizontalMotion;
        }

        return entity.getRotationVector();
    }

    private RayHit traceBox(Box box, Vec3d eyeVec, Vec3d endVec) {
        if (box.contains(eyeVec)) {
            return new RayHit(true, eyeVec);
        }

        Optional<Vec3d> hit = box.raycast(eyeVec, endVec);
        return hit.map(vec3d -> new RayHit(true, vec3d)).orElse(RayHit.MISS);
    }

    private boolean canSeeHitPoint(Vec3d eyeVec, Vec3d hitPoint) {
        RaycastContext context = new RaycastContext(
                eyeVec,
                hitPoint,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                mc.player
        );
        return mc.world.raycast(context).getType() == HitResult.Type.MISS;
    }

    private SegmentHit closestSegmentHit(Vec3d a0, Vec3d a1, Vec3d b0, Vec3d b1) {
        Vec3d u = a1.subtract(a0);
        Vec3d v = b1.subtract(b0);
        Vec3d w = a0.subtract(b0);

        double a = u.dotProduct(u);
        double b = u.dotProduct(v);
        double c = v.dotProduct(v);
        double d = u.dotProduct(w);
        double e = v.dotProduct(w);
        double denominator = a * c - b * b;

        double sc;
        double tc;

        if (denominator < 1.0E-7D) {
            sc = 0.0D;
            tc = c > 1.0E-7D ? MathHelper.clamp(e / c, 0.0D, 1.0D) : 0.0D;
        } else {
            sc = MathHelper.clamp((b * e - c * d) / denominator, 0.0D, 1.0D);
            tc = c > 1.0E-7D ? MathHelper.clamp((a * e - b * d) / denominator, 0.0D, 1.0D) : 0.0D;
        }

        Vec3d rayPoint = a0.add(u.multiply(sc));
        Vec3d targetPoint = b0.add(v.multiply(tc));
        return new SegmentHit(rayPoint.distanceTo(targetPoint), rayPoint, targetPoint);
    }

    private Box union(Box first, Box second) {
        return new Box(
                Math.min(first.minX, second.minX),
                Math.min(first.minY, second.minY),
                Math.min(first.minZ, second.minZ),
                Math.max(first.maxX, second.maxX),
                Math.max(first.maxY, second.maxY),
                Math.max(first.maxZ, second.maxZ)
        );
    }

    private static class RayHit {
        private static final RayHit MISS = new RayHit(false, Vec3d.ZERO);

        private final boolean hit;
        private final Vec3d point;

        private RayHit(boolean hit, Vec3d point) {
            this.hit = hit;
            this.point = point;
        }
    }

    private static class SegmentHit {
        private final double distance;
        private final Vec3d rayPoint;
        private final Vec3d targetPoint;

        private SegmentHit(double distance, Vec3d rayPoint, Vec3d targetPoint) {
            this.distance = distance;
            this.rayPoint = rayPoint;
            this.targetPoint = targetPoint;
        }
    }
}
