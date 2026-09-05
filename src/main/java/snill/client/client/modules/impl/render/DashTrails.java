package snill.client.client.modules.impl.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import snill.client.api.events.EventLink;
import snill.client.api.events.implement.Event3DRender;
import snill.client.api.events.implement.EventUpdate;
import snill.client.api.utils.color.ColorUtils;
import snill.client.client.modules.Module;
import snill.client.client.modules.settings.implement.FloatSetting;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class DashTrails extends Module {
    public static final DashTrails INSTANCE = new DashTrails();
    private static final Identifier BLOOM = Identifier.of("snill", "textures/particle/bloom.png");
    private static final Identifier SPARK = Identifier.of("snill", "textures/particle/spark.png");
    private static final long FRAME_DURATION_MS = 45L;
    private static final int DASH_FRAME_COUNT = 21;
    private final FloatSetting lifetime = new FloatSetting("Lifetime", .9f, .35f, 2.5f, .05f);
    private final FloatSetting density = new FloatSetting("Density", 12f, 4f, 20f, .5f);
    private final FloatSetting scale = new FloatSetting("Scale", 1f, .55f, 1.8f, .05f);
    private final List<Sprite> sprites = new ArrayList<>();
    private final Random random = new Random();
    private Vec3d lastPosition;

    private DashTrails() {
        super("DashTrails", "Leaves a glowing trail while moving", ModuleCategory.RENDER);
        addSettings(lifetime, density, scale);
    }

    @Override public void onDisable() { sprites.clear(); lastPosition = null; super.onDisable(); }

    @EventLink
    public void onUpdate(EventUpdate event) {
        if (mc.player == null || mc.world == null) return;
        Vec3d current = mc.player.getPos();
        if (lastPosition != null) spawnBetween(lastPosition, current);
        lastPosition = current;
        for (Iterator<Sprite> it = sprites.iterator(); it.hasNext();) { if (it.next().tick()) it.remove(); }
    }

    @EventLink
    public void onRender(Event3DRender event) {
        if (sprites.isEmpty() || mc.options.getPerspective() == Perspective.FIRST_PERSON) return;
        Vec3d camera = event.getCamera().getPos();
        MatrixStack matrices = event.getMatrices();
        float delta = event.getTickDelta();
        Vec3d up = toVec3d(new Vector3f(0, 1, 0).rotate(event.getCamera().getRotation())).normalize();
        Vec3d left = toVec3d(new Vector3f(-1, 0, 0).rotate(event.getCamera().getRotation())).normalize();
        Vec3d forward = toVec3d(new Vector3f(0, 0, 1).rotate(event.getCamera().getRotation())).normalize();
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        long frame = System.currentTimeMillis() / FRAME_DURATION_MS;
        int tint = ColorUtils.getThemeColor(360);
        int red = (tint >> 16) & 255, green = (tint >> 8) & 255, blue = tint & 255;
        RenderSystem.enableBlend(); RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE); RenderSystem.enableDepthTest(); RenderSystem.depthMask(false); RenderSystem.disableCull(); RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        for (Sprite sprite : sprites) {
            float progress = sprite.progress(delta), alpha = sprite.alpha(delta);
            int alphaInt = MathHelper.clamp((int) (255 * alpha), 0, 255);
            if (alphaInt <= 1) continue;
            Vec3d center = sprite.previous.lerp(sprite.position, delta).subtract(camera);
            float rotation = (float) Math.toDegrees(Math.atan2(sprite.facing.dotProduct(up), sprite.facing.dotProduct(left))) + sprite.roll;
            double radians = Math.toRadians(rotation), cos = Math.cos(radians), sin = Math.sin(radians);
            Vec3d axisX = left.multiply(cos).add(up.multiply(-sin));
            Vec3d axisY = left.multiply(sin).add(up.multiply(cos));
            double width = sprite.width * (1F - progress * .18F);
            double height = sprite.height * (1.08F - progress * .15F);
            int frameIndex = (int) ((frame + sprite.frameOffset + progress * 6F) % DASH_FRAME_COUNT);
            int core = color(255, 255, 255, (int) (alphaInt * .95F));
            int glow = color(red, green, blue, (int) (alphaInt * .55F));
            int dark = color((int) (red * .45F), (int) (green * .45F), (int) (blue * .45F), (int) (alphaInt * .7F));
            int main = color(red, green, blue, alphaInt);
            billboard(matrix, BLOOM, center, axisX, axisY, forward, 0D, width * (1.85D - progress * .2D), width * (1.85D - progress * .2D), glow, glow, glow, glow);
            billboard(matrix, SPARK, center, axisX, axisY, forward, .002D + frameIndex * .000001D, width, height, dark, main, main, dark);
            billboard(matrix, SPARK, center, axisX, axisY, forward, .004D + frameIndex * .000001D, width * .78D, height * .78D, core, core, core, core);
        }
        RenderSystem.depthMask(true); RenderSystem.enableCull(); RenderSystem.defaultBlendFunc(); RenderSystem.disableBlend();
    }

    private static void billboard(Matrix4f matrix, Identifier texture, Vec3d center, Vec3d axisX, Vec3d axisY, Vec3d forward, double bias, double width, double height, int c0, int c1, int c2, int c3) {
        double halfW = width * .5D, halfH = height * .5D;
        Vec3d base = center.add(forward.multiply(bias));
        quad(matrix, texture, base.add(axisX.multiply(-halfW)).add(axisY.multiply(halfH)), base.add(axisX.multiply(halfW)).add(axisY.multiply(halfH)), base.add(axisX.multiply(halfW)).add(axisY.multiply(-halfH)), base.add(axisX.multiply(-halfW)).add(axisY.multiply(-halfH)), c0, c1, c2, c3);
    }

    private static void quad(Matrix4f matrix, Identifier texture, Vec3d a, Vec3d b, Vec3d c, Vec3d d, int c0, int c1, int c2, int c3) {
        RenderSystem.setShaderTexture(0, texture);
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        buffer.vertex(matrix, (float) a.x, (float) a.y, (float) a.z).texture(0, 0).color(c0); buffer.vertex(matrix, (float) b.x, (float) b.y, (float) b.z).texture(1, 0).color(c1); buffer.vertex(matrix, (float) c.x, (float) c.y, (float) c.z).texture(1, 1).color(c2); buffer.vertex(matrix, (float) d.x, (float) d.y, (float) d.z).texture(0, 1).color(c3);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    private static Vec3d toVec3d(Vector3f vector) { return new Vec3d(vector.x(), vector.y(), vector.z()); }
    private static int color(int red, int green, int blue, int alpha) { return (MathHelper.clamp(alpha, 0, 255) << 24) | (MathHelper.clamp(red, 0, 255) << 16) | (MathHelper.clamp(green, 0, 255) << 8) | MathHelper.clamp(blue, 0, 255); }

    private void spawnBetween(Vec3d from, Vec3d to) {
        Vec3d delta = to.subtract(from);
        double distanceMoved = delta.length();
        if (distanceMoved < .02D || mc.player.isRiding()) return;
        Vec3d direction = delta.multiply(1D / distanceMoved);
        Vec3d back = direction.multiply(-1D);
        Vec3d side = new Vec3d(-direction.z, 0D, direction.x);
        side = side.lengthSquared() < 1E-4D ? new Vec3d(1D, 0D, 0D) : side.normalize();
        float playerHeight = mc.player.getHeight() - (mc.player.isSneaking() ? .15F : 0F);
        int bursts = MathHelper.clamp((int) Math.ceil(distanceMoved * (density.get() * 1.35F)), 1, 5);
        int maxAge = Math.max(8, (int) (lifetime.get() * 20F));
        float distanceFactor = MathHelper.clamp((float) (distanceMoved * 6D), 0F, 1.3F);
        for (int burst = 0; burst < bursts; burst++) {
            double t = bursts == 1 ? 1D : (double) burst / (bursts - 1);
            Vec3d base = from.add(delta.multiply(t)).add(0D, playerHeight * .18D, 0D);
            int count = 3 + random.nextInt(2) + (distanceFactor > .75F ? 1 : 0);
            for (int i = 0; i < count; i++) {
                double vertical = (random.nextDouble() - .1D) * playerHeight * .85D;
                double sideways = (random.nextDouble() - .5D) * (.28D + distanceFactor * .15D);
                double backward = random.nextDouble() * (.2D + distanceFactor * .18D);
                Vec3d position = base.add(0D, vertical, 0D).add(side.multiply(sideways)).add(back.multiply(backward));
                Vec3d facing = back.add(side.multiply((random.nextDouble() - .5D) * .35D)).add(0D, (random.nextDouble() - .5D) * .18D, 0D);
                facing = facing.lengthSquared() < 1E-4D ? back : facing.normalize();
                Vec3d velocity = back.multiply(.012D + distanceFactor * .02D + random.nextDouble() * .01D)
                        .add(side.multiply((random.nextDouble() - .5D) * .008D))
                        .add(0D, (random.nextDouble() - .5D) * .008D + .002D, 0D);
                float width = scale.get() * (.34F + random.nextFloat() * .18F + distanceFactor * .08F);
                float height = width * (.24F + random.nextFloat() * .08F);
                sprites.add(new Sprite(position, velocity, facing, width, height,
                        (float) ((random.nextDouble() - .5D) * 18D), random.nextInt(DASH_FRAME_COUNT), maxAge));
            }
        }
    }

    private static final class Sprite {
        private Vec3d previous, position, velocity;
        private final Vec3d facing;
        private final float width, height, roll;
        private final int frameOffset, maxAge;
        private int age;
        private Sprite(Vec3d position, Vec3d velocity, Vec3d facing, float width, float height, float roll, int frameOffset, int maxAge) { this.previous = this.position = position; this.velocity = velocity; this.facing = facing; this.width = width; this.height = height; this.roll = roll; this.frameOffset = frameOffset; this.maxAge = maxAge; }
        private boolean tick() { previous = position; position = position.add(velocity); velocity = velocity.multiply(.9D).add(0D, .0015D, 0D); return ++age >= maxAge; }
        private float progress(float delta) { return MathHelper.clamp((age + delta) / (float) maxAge, 0F, 1F); }
        private float alpha(float delta) { float fade = 1F - progress(delta); return MathHelper.clamp((float) Math.pow(Math.sin(fade * Math.PI * .5D), .85D), 0F, 1F); }
    }
}
