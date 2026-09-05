package snill.client.api.utils.math;


import java.util.concurrent.ThreadLocalRandom;

import snill.client.api.utils.IMinecraft;
import lombok.Generated;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.MathHelper;
import org.joml.Vector3d;

public final class MathUtil
implements IMinecraft {
    public static double PI2 = Math.PI * 2;
    private static final int TABLE_SIZE = 65536;
    private static final double TWO_PI = Math.PI * 2;
    private static final double[] TRIG_TABLE = new double[65536];

    public static double sin(double radians) {
        int index = (int)(radians * 10430.378350470453) & 0xFFFF;
        return TRIG_TABLE[index];
    }

    public static double cos(double radians) {
        int index = (int)(radians * 10430.378350470453 + 16384.0) & 0xFFFF;
        return TRIG_TABLE[index];
    }

    public static float random(double min, double max) {
        return (float)(min + (max - min) * Math.random());
    }

    public static double cubicBezier(double t2, double p0, double p1, double p2, double p3) {
        return Math.pow(1.0 - t2, 3.0) * p0 + 3.0 * t2 * Math.pow(1.0 - t2, 2.0) * p1 + 3.0 * Math.pow(t2, 2.0) * (1.0 - t2) * p2 + Math.pow(t2, 3.0) * p3;
    }

    public static int levenshtein(String a2, String b2) {
        int n2 = a2.length();
        int m2 = b2.length();
        int[] dp = new int[m2 + 1];
        int i2 = 0;
        while (i2 <= m2) {
            dp[i2] = i2++;
        }
        for (i2 = 1; i2 <= n2; ++i2) {
            int prev = dp[0];
            dp[0] = i2;
            for (int j2 = 1; j2 <= m2; ++j2) {
                int tmp = dp[j2];
                int cost = a2.charAt(i2 - 1) == b2.charAt(j2 - 1) ? 0 : 1;
                dp[j2] = Math.min(Math.min(dp[j2] + 1, dp[j2 - 1] + 1), prev + cost);
                prev = tmp;
            }
        }
        return dp[m2];
    }

    public static float angleDifference(float angle1, float angle2) {
        float diff = (angle1 - angle2) % 360.0f;
        if (diff < -180.0f) {
            diff += 360.0f;
        } else if (diff > 180.0f) {
            diff -= 360.0f;
        }
        return diff;
    }

    public static boolean isHovered(double mouseX, double mouseY, double x2, double y2, double width, double height) {
        return mouseX >= x2 && mouseX <= x2 + width && mouseY >= y2 && mouseY <= y2 + height;
    }

    public static boolean isHoveredByCords(double mouseX, double mouseY, int x2, int y2, int xEnd, int yEnd) {
        return mouseX >= (double)x2 && mouseX <= (double)xEnd && mouseY >= (double)y2 && mouseY <= (double)yEnd;
    }

    public static float interpolate(double oldValue, double newValue, double interpolationValue) {
        return (float)(oldValue + (newValue - oldValue) * interpolationValue);
    }

    public static float goodSubtract(float value1, float value2) {
        return Math.abs(value1 - value2);
    }

    public static double getRandom(double min, double max) {
        if (min == max) {
            return min;
        }
        if (min > max) {
            double d2 = min;
            min = max;
            max = d2;
        }
        return ThreadLocalRandom.current().nextDouble() * (max - min) + min;
    }

    public static float round(float value) {
        return (float)Math.round(value * 10.0f) / 10.0f;
    }

    public static double round(double num, double increment) {
        double rounded = (double)Math.round(num / increment) * increment;
        return (double)Math.round(rounded * 100.0) / 100.0;
    }

    public static Vec3d cosSin(int i2, int size, double width) {
        int index = Math.min(i2, size);
        float cos = (float)(Math.cos((double)index * PI2 / (double)size) * width);
        float sin = (float)(-Math.sin((double)index * PI2 / (double)size) * width);
        return new Vec3d((double)cos, 0.0, (double)sin);
    }

    public static Vector3d interpolate(Vector3d prevPos, Vector3d pos) {
        return new Vector3d(MathUtil.interpolate(prevPos.x, pos.x), MathUtil.interpolate(prevPos.y, pos.y), MathUtil.interpolate(prevPos.z, pos.z));
    }

    public static Vec3d interpolate(Vec3d prevPos, Vec3d pos) {
        return new Vec3d(MathUtil.interpolate(prevPos.x, pos.x), MathUtil.interpolate(prevPos.y, pos.y), MathUtil.interpolate(prevPos.z, pos.z));
    }

    public static Vec3d interpolate(Entity entity) {
        return entity == null ? Vec3d.ZERO : new Vec3d(MathUtil.interpolate(entity.prevX, entity.getX()), MathUtil.interpolate(entity.prevY, entity.getY()), MathUtil.interpolate(entity.prevZ, entity.getZ()));
    }

    public static float interpolate(float prev, float orig) {
        return MathHelper.lerp((float)mc.getRenderTickCounter().getTickDelta(false), (float)prev, (float)orig);
    }

    public static double interpolate(double prev, double orig) {
        return MathHelper.lerp((double)mc.getRenderTickCounter().getTickDelta(false), (double)prev, (double)orig);
    }

    public static int interpolateSmooth(double smooth, int prev, int orig) {
        return (int)MathHelper.lerp((double)((double)mc.getRenderTickCounter().getLastDuration() / smooth), (double)prev, (double)orig);
    }

    public static float interpolateSmooth(double smooth, float prev, float orig) {
        return (float)MathHelper.lerp((double)((double)mc.getRenderTickCounter().getLastDuration() / smooth), (double)prev, (double)orig);
    }

    public static double interpolateSmooth(double smooth, double prev, double orig) {
        return MathHelper.lerp((double)((double)mc.getRenderTickCounter().getLastDuration() / smooth), (double)prev, (double)orig);
    }

    public static double getDistance(Vec3d pos1, Vec3d pos2) {
        double deltaX = pos1.getX() - pos2.getX();
        double deltaY = pos1.getY() - pos2.getY();
        double deltaZ = pos1.getZ() - pos2.getZ();
        return MathHelper.sqrt((float)((float)(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ)));
    }

    @Generated
    private MathUtil() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    static {
        for (int i2 = 0; i2 < 65536; ++i2) {
            MathUtil.TRIG_TABLE[i2] = Math.sin((double)i2 * (Math.PI * 2) / 65536.0);
        }
    }
}