package snill.client.client.modules.impl.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import snill.client.api.events.EventLink;
import snill.client.api.events.implement.Event3DRender;
import snill.client.api.events.implement.EventAttackEntity;
import snill.client.api.events.implement.EventUpdate;
import snill.client.api.utils.color.ColorUtils;
import snill.client.client.modules.Module;
import snill.client.client.modules.settings.implement.BooleanSetting;
import snill.client.client.modules.settings.implement.FloatSetting;
import snill.client.client.modules.settings.implement.ModeSetting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class Particles extends Module {
    public static final Particles INSTANCE = new Particles();
    private static final Identifier GLOW = Identifier.of("snill", "textures/particle/bloom.png");

    private static final Map<String, Identifier> TEXTURES = new HashMap<>();
    static {
        for (String name : new String[]{"Crown", "Dollar", "Firefly", "Heart", "Lightning", "Line", "Point", "Rhombus", "Snowflake", "Spark", "Star"}) {
            TEXTURES.put(name, Identifier.of("snill", "textures/particle/" + name.toLowerCase() + ".png"));
        }
    }
    private final BooleanSetting hitParticles = new BooleanSetting("Hit particles", true);
    private final BooleanSetting worldParticles = new BooleanSetting("World particles", false);
    private final ModeSetting texture = new ModeSetting("Texture", "Star", "Crown", "Dollar", "Firefly", "Heart", "Lightning", "Line", "Point", "Rhombus", "Snowflake", "Spark", "Star");
    private final FloatSetting amount = new FloatSetting("Amount", 16f, 1f, 50f, 1f);
    private final FloatSetting lifetime = new FloatSetting("Lifetime", 1.2f, 0.2f, 5f, 0.1f);
    private final FloatSetting size = new FloatSetting("Size", 0.22f, 0.05f, 1f, 0.01f);
    private final FloatSetting spread = new FloatSetting("Spread", 0.18f, 0.02f, 0.6f, 0.01f);
    private final List<Particle> particles = new ArrayList<>();
    private final Random random = new Random();
    private long nextWorldSpawn;

    private Particles() {
        super("Particles", "Particles on hit and around the player", ModuleCategory.RENDER);
        addSettings(hitParticles, worldParticles, texture, amount, lifetime, size, spread);
    }

    @Override
    public void onDisable() {
        particles.clear();
        super.onDisable();
    }

    @EventLink
    public void onAttack(EventAttackEntity event) {
        if (!hitParticles.isState() || mc.world == null) return;
        Entity target = event.getTarget();
        if (target == null) return;
        spawn(target.getPos().add(0, target.getHeight() * 0.55, 0), Math.round(amount.get()));
    }

    @EventLink
    public void onUpdate(EventUpdate event) {
        if (mc.player == null || mc.world == null) return;
        for (Iterator<Particle> iterator = particles.iterator(); iterator.hasNext();) {
            if (iterator.next().tick()) iterator.remove();
        }
        long now = System.currentTimeMillis();
        if (worldParticles.isState() && now >= nextWorldSpawn) {
            nextWorldSpawn = now + 90L;
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = 3.0 + random.nextDouble() * 9.0;
            spawn(mc.player.getPos().add(Math.cos(angle) * distance, 1.0 + random.nextDouble() * 5.0, Math.sin(angle) * distance), 1);
        }
    }

    @EventLink
    public void onRender(Event3DRender event) {
        if (particles.isEmpty()) return;
        Vec3d camera = event.getCamera().getPos();
        MatrixStack matrices = event.getMatrices();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        for (Particle particle : particles) particle.render(matrices, camera, event.getCamera(), event.getTickDelta());
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private void spawn(Vec3d center, int count) {
        long maxAge = (long) (lifetime.get() * 1000f);
        for (int i = 0; i < count; i++) {
            Vec3d velocity = new Vec3d((random.nextDouble() - .5) * spread.get() * 2, random.nextDouble() * spread.get(), (random.nextDouble() - .5) * spread.get() * 2);
            particles.add(new Particle(center, velocity, maxAge, random.nextFloat() * 360f));
        }
    }

    private Identifier getTexture() {
        return TEXTURES.getOrDefault(texture.getCurrent(), TEXTURES.get("Star"));
    }

    private final class Particle {
        private Vec3d previous;
        private Vec3d position;
        private Vec3d velocity;
        private final long born = System.currentTimeMillis();
        private final long maxAge;
        private final float roll;

        private Particle(Vec3d position, Vec3d velocity, long maxAge, float roll) {
            this.previous = this.position = position;
            this.velocity = velocity;
            this.maxAge = maxAge;
            this.roll = roll;
        }

        private boolean tick() {
            previous = position;
            position = position.add(velocity);
            velocity = velocity.multiply(.92).add(0, -.0025, 0);
            return System.currentTimeMillis() - born >= maxAge;
        }

        private void render(MatrixStack matrices, Vec3d cameraPos, net.minecraft.client.render.Camera camera, float tickDelta) {
            float age = MathHelper.clamp((System.currentTimeMillis() - born) / (float) maxAge, 0f, 1f);
            int color = ColorUtils.setAlphaColor(ColorUtils.getThemeColor((int) (roll + age * 180)), (int) (255 * (1f - age)));
            Vec3d point = previous.lerp(position, tickDelta).subtract(cameraPos);
            matrices.push();
            matrices.translate(point.x, point.y, point.z);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(roll + age * 180f));
            Matrix4f matrix = matrices.peek().getPositionMatrix();
            float half = size.get() * (1f - age * .25f);
            int glow = ColorUtils.setAlphaColor(ColorUtils.getThemeColor((int) (roll + age * 180)), (int) (90 * (1f - age)));
            RenderSystem.setShaderTexture(0, GLOW);
            drawQuad(matrix, half * 2.15f, glow);
            RenderSystem.setShaderTexture(0, getTexture());
            drawQuad(matrix, half, color);
            matrices.pop();
        }

        private static void drawQuad(Matrix4f matrix, float half, int color) {
            BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            buffer.vertex(matrix, -half, -half, 0).texture(0, 1).color(color);
            buffer.vertex(matrix, -half, half, 0).texture(0, 0).color(color);
            buffer.vertex(matrix, half, half, 0).texture(1, 0).color(color);
            buffer.vertex(matrix, half, -half, 0).texture(1, 1).color(color);
            BufferRenderer.drawWithGlobalProgram(buffer.end());
        }
    }
}
