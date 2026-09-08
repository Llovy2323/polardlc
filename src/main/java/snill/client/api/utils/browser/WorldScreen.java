package snill.client.api.utils.browser;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class WorldScreen {

    private Vec3d position;
    private float yaw;
    private float pitch;
    private float width = 5.2f;
    private float height = 2.925f; // 16:9

    private Vec3d normal = new Vec3d(0, 0, 1);
    private Vec3d right = new Vec3d(1, 0, 0);
    private Vec3d up = new Vec3d(0, 1, 0);

    private boolean isDragging = false;
    private double dragDistance = 3.5;

    public WorldScreen(Vec3d position, float yaw, float pitch, float width) {
        this.position = position;
        this.yaw = yaw;
        this.pitch = pitch;
        this.width = width;
        this.height = width * (9.0f / 16.0f);
        updateBasis();
    }

    public void updateBasis() {
        float yawRad = (float) Math.toRadians(yaw);
        float pitchRad = (float) Math.toRadians(pitch);

        float nx = -MathHelper.sin(yawRad) * MathHelper.cos(pitchRad);
        float ny = -MathHelper.sin(pitchRad);
        float nz = MathHelper.cos(yawRad) * MathHelper.cos(pitchRad);
        this.normal = new Vec3d(nx, ny, nz).normalize();

        float rx = MathHelper.cos(yawRad);
        float ry = 0;
        float rz = MathHelper.sin(yawRad);
        this.right = new Vec3d(rx, ry, rz).normalize();

        this.up = this.normal.crossProduct(this.right).normalize();
    }

    public void setDimensions(float width) {
        this.width = width;
        this.height = width * (9.0f / 16.0f);
    }

    public void spawnInFrontOf(Vec3d eyePos, float playerYaw, float playerPitch, double distance) {
        float radYaw = (float) Math.toRadians(playerYaw);
        float radPitch = (float) Math.toRadians(playerPitch);

        float lookX = -MathHelper.sin(radYaw) * MathHelper.cos(radPitch);
        float lookY = -MathHelper.sin(radPitch);
        float lookZ = MathHelper.cos(radYaw) * MathHelper.cos(radPitch);
        Vec3d lookDir = new Vec3d(lookX, lookY, lookZ).normalize();

        this.position = eyePos.add(lookDir.multiply(distance));
        this.yaw = playerYaw + 180.0f;
        this.pitch = -playerPitch;
        this.dragDistance = distance;
        updateBasis();
    }

    public void updateDrag(Vec3d eyePos, Vec3d lookDir, float playerYaw, float playerPitch) {
        if (!isDragging) return;
        this.position = eyePos.add(lookDir.multiply(dragDistance));
        this.yaw = playerYaw + 180.0f;
        this.pitch = -playerPitch;
        updateBasis();
    }

    public RayHit intersect(Vec3d rayOrigin, Vec3d rayDir) {
        double denom = rayDir.dotProduct(normal);
        if (Math.abs(denom) < 1e-4) {
            return null;
        }

        double numer = position.subtract(rayOrigin).dotProduct(normal);
        double t = numer / denom;
        if (t <= 0 || t > 45.0) {
            return null;
        }

        Vec3d hit = rayOrigin.add(rayDir.multiply(t));
        Vec3d diff = hit.subtract(position);

        double localX = diff.dotProduct(right);
        double localY = diff.dotProduct(up);

        float halfW = width * 0.5f;
        float halfH = height * 0.5f;

        boolean inside = Math.abs(localX) <= halfW && Math.abs(localY) <= halfH;
        if (!inside) {
            return null;
        }

        float u = (float) ((localX + halfW) / width);
        float v = (float) ((halfH - localY) / height);

        return new RayHit(hit, u, v, t);
    }

    public Vec3d getPosition() {
        return position;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }

    public Vec3d getNormal() {
        return normal;
    }

    public Vec3d getRight() {
        return right;
    }

    public Vec3d getUp() {
        return up;
    }

    public boolean isDragging() {
        return isDragging;
    }

    public void setDragging(boolean dragging) {
        isDragging = dragging;
    }

    public double getDragDistance() {
        return dragDistance;
    }

    public void setDragDistance(double dragDistance) {
        this.dragDistance = dragDistance;
    }

    public static class RayHit {
        public final Vec3d point;
        public final float u;
        public final float v;
        public final double distance;

        public RayHit(Vec3d point, float u, float v, double distance) {
            this.point = point;
            this.u = u;
            this.v = v;
            this.distance = distance;
        }
    }
}
