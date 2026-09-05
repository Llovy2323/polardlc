package snill.client.client.modules.impl.combat.components.interpolation;

import lombok.experimental.UtilityClass;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import snill.client.api.QClient;
import snill.client.api.utils.combat.RayTraceUtil;
import snill.client.api.utils.math.MathUtils;
import snill.client.api.utils.rotate.Rotation;
import snill.client.api.utils.rotate.RotationUtils;

import java.util.concurrent.ThreadLocalRandom;

@UtilityClass
public class BestPoint implements QClient {
    private static Vec3d rotationPoint = Vec3d.ZERO;
    private static Vec3d rotationMotion = Vec3d.ZERO;

    public Vec3d getRotationPoint() {
        return rotationPoint;
    }

    public Vec3d getNearestPoint(Entity entity) {
        Box box = entity.getBoundingBox();
        double step = 0.085;
        Vec3d bestVec = null;
        double closestDistance = Double.MAX_VALUE;

        for (double x = box.minX; x <= box.maxX; x += step) {
            for (double y = box.minY; y <= box.maxY; y += step) {
                for (double z = box.minZ; z <= box.maxZ; z += step) {
                    Vec3d sample = new Vec3d(x, y, z);
                    double dist = mc.player.getEyePos().distanceTo(sample);
                    if (dist < closestDistance) {
                        closestDistance = dist;
                        bestVec = sample;
                    }
                }
            }
        }
        return bestVec;
    }

    public Vec3d getPoint(Entity target) {
        Box box = target.getBoundingBox();
        double width = box.maxX - box.minX;
        double height = box.maxY - box.minY;
        double depth = box.maxZ - box.minZ;

        double baseX = box.minX + width / 2.0;
        double baseY = box.minY + height * 0.72;
        double baseZ = box.minZ + depth / 2.0;

        double time = System.currentTimeMillis() / 42.15;
        int id = target.getId();

        double noise = ThreadLocalRandom.current().nextDouble(-0.015, 0.015);
        double offsetX = Math.sin(time * 0.9 + id) * (width * 0.42) + noise;
        double offsetY = Math.cos(time * 0.75 + id) * (height * 0.12);
        double offsetZ = Math.cos(time * 1.15 + id) * (depth * 0.42) + noise;

        return new Vec3d(baseX + offsetX, baseY + offsetY, baseZ + offsetZ);
    }

    public Vec3d getPoint2(Entity target) {
        Box box = target.getBoundingBox();
        double width = box.maxX - box.minX;
        double height = box.maxY - box.minY;
        double depth = box.maxZ - box.minZ;

        double baseX = box.minX + width / 2.0;
        double baseY = box.minY + height * 0.62;
        double baseZ = box.minZ + depth / 2.0;

        double time = System.currentTimeMillis() / 58.4;
        int id = target.getId();

        double offsetX = Math.sin(time * 1.1 + id) * (width * 0.65);
        double offsetY = Math.cos(time * 0.95 + id) * (height * 0.35);
        double offsetZ = Math.cos(time * 1.35 + id) * (depth * 0.65);

        return new Vec3d(baseX + offsetX, baseY + offsetY, baseZ + offsetZ);
    }

    public Vec3d getNearestVisiblePoint(Entity target, Vec3d preferredPoint, double range) {
        if (preferredPoint == null || mc.player == null || mc.world == null) {
            return preferredPoint;
        }

        if (isPointVisible(target, preferredPoint, range)) {
            return preferredPoint;
        }

        Box box = target.getBoundingBox();
        double step = 0.11;
        Vec3d bestPoint = null;
        double bestDistance = Double.MAX_VALUE;

        for (double x = box.minX; x <= box.maxX; x += step) {
            for (double y = box.minY; y <= box.maxY; y += step) {
                for (double z = box.minZ; z <= box.maxZ; z += step) {
                    Vec3d sample = new Vec3d(x, y, z);
                    if (!isPointVisible(target, sample, range)) {
                        continue;
                    }

                    double distanceToCurrent = sample.squaredDistanceTo(preferredPoint);
                    if (distanceToCurrent < bestDistance) {
                        bestDistance = distanceToCurrent;
                        bestPoint = sample;
                    }
                }
            }
        }

        return bestPoint != null ? bestPoint : preferredPoint;
    }

    private boolean isPointVisible(Entity target, Vec3d point, double range) {
        Vec3d eyePos = mc.player.getEyePos();
        double distance = eyePos.distanceTo(point);
        if (distance > range) {
            return false;
        }

        Vec3d direction = point.subtract(eyePos).normalize();
        if (!RayTraceUtil.rayTrace(direction, distance + 0.15, target.getBoundingBox())) {
            return false;
        }

        var blockHit = RayTraceUtil.raycast(eyePos, point, RaycastContext.ShapeType.COLLIDER, mc.player);
        return blockHit.getType() == HitResult.Type.MISS || eyePos.squaredDistanceTo(blockHit.getPos()) >= eyePos.squaredDistanceTo(point) - 1e-4;
    }

    public static Vec3d getMultipoint(Entity target, double distance) {
        float minMotionXZ = 0.0062f;
        float maxMotionXZ = 0.0185f;

        float minMotionY = 0.0022f;
        float maxMotionY = 0.0185f;

        double lengthX = target.getBoundingBox().getLengthX();
        double lengthY = target.getBoundingBox().getLengthY();
        double lengthZ = target.getBoundingBox().getLengthZ();

        if (rotationMotion.equals(Vec3d.ZERO))
            rotationMotion = new Vec3d(MathUtils.randomBest(-0.025f, 0.025f), MathUtils.randomBest(-0.025f, 0.025f), MathUtils.randomBest(-0.025f, 0.025f));

        if (rotationPoint.equals(Vec3d.ZERO))
            rotationPoint = new Vec3d(0, lengthY * 0.52, 0);

        rotationPoint = rotationPoint.add(rotationMotion);

        double safeX = (lengthX - 0.08) / 2f;
        double safeZ = (lengthZ - 0.08) / 2f;

        if (Math.abs(rotationPoint.x) >= safeX)
            rotationMotion = new Vec3d((rotationPoint.x > 0 ? -1 : 1) * MathUtils.randomBest(minMotionXZ, maxMotionXZ), rotationMotion.getY(), rotationMotion.getZ());

        if (rotationPoint.y >= lengthY * 0.82)
            rotationMotion = new Vec3d(rotationMotion.getX(), -MathUtils.randomBest(minMotionY, maxMotionY), rotationMotion.getZ());
        else if (rotationPoint.y <= lengthY * 0.22)
            rotationMotion = new Vec3d(rotationMotion.getX(), MathUtils.randomBest(minMotionY, maxMotionY), rotationMotion.getZ());

        if (Math.abs(rotationPoint.z) >= safeZ)
            rotationMotion = new Vec3d(rotationMotion.getX(), rotationMotion.getY(), (rotationPoint.z > 0 ? -1 : 1) * MathUtils.randomBest(minMotionXZ, maxMotionXZ));

        rotationPoint = rotationPoint.add(MathUtils.randomBest(-0.035f, 0.035f), 0f, MathUtils.randomBest(-0.035f, 0.035f));

        Rotation rotation;

        if (!RayTraceUtil.rayTrace(mc.player.getRotationVector(), distance, target.getBoundingBox())) {
            float halfBox = (float) (lengthX / 2f) * 0.85f;

            for (float x1 = -halfBox; x1 <= halfBox; x1 += 0.09f) {
                for (float z1 = -halfBox; z1 <= halfBox; z1 += 0.09f) {
                    for (float y1 = (float) (lengthY * 0.88); y1 >= lengthY * 0.25; y1 -= 0.09f) {
                        Vec3d v1 = new Vec3d(target.getX() + x1, target.getY() + y1, target.getZ() + z1);
                        rotation = RotationUtils.fromVec3d(v1);
                        if (RayTraceUtil.rayTrace(rotation.toVector(), distance, target.getBoundingBox())) {
                            rotationPoint = new Vec3d(x1, y1, z1);
                            return target.getPos().add(rotationPoint);
                        }
                    }
                }
            }
        }
        return target.getPos().add(rotationPoint);
    }
}
