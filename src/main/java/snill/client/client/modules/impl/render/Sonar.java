package snill.client.client.modules.impl.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import snill.client.api.events.EventLink;
import snill.client.api.events.implement.EventChunkReload;
import snill.client.api.events.implement.EventUpdate;
import snill.client.api.utils.animation.Easings;
import snill.client.api.utils.color.ColorUtils;
import snill.client.api.utils.render.ShaderUtils;
import snill.client.client.modules.Module;
import snill.client.client.modules.settings.implement.BooleanSetting;
import snill.client.client.modules.settings.implement.FloatSetting;
import snill.client.client.modules.settings.implement.ModeSetting;

public class Sonar extends Module {

    public static Sonar INSTANCE = new Sonar();

    private final ModeSetting mode = new ModeSetting("Режим", "3D Сетка", "3D Сетка", "Киберпанк", "Волна", "Топография");
    private final FloatSetting maxDistance = new FloatSetting("Дистанция", 64.0f, 20.0f, 160.0f, 5.0f);
    private final FloatSetting interval = new FloatSetting("Интервал", 3.5f, 0.5f, 15.0f, 0.5f);
    private final FloatSetting duration = new FloatSetting("Длительность", 2.2f, 0.5f, 8.0f, 0.1f);
    private final FloatSetting alpha = new FloatSetting("Яркость", 1.0f, 0.2f, 1.5f, 0.05f);
    private final FloatSetting widthMul = new FloatSetting("Ширина волны", 1.25f, 0.4f, 3.0f, 0.05f);
    private final FloatSetting sharpness = new FloatSetting("Резкость", 32f, 4f, 80f, 1f);
    private final BooleanSetting sound = new BooleanSetting("Звук сонара", true);
    private final BooleanSetting onlyJump = new BooleanSetting("Только при прыжке", false);

    private Framebuffer depthCopyBuffer;
    private int lastFbWidth = -1;
    private int lastFbHeight = -1;

    private long currentStart;
    private long lastPingTime;
    private Vec3d center = Vec3d.ZERO;
    private boolean wasOnGround = true;

    public Sonar() {
        super("Sonar", "Периодическое сканирование местности сонаром", ModuleCategory.RENDER);
        interval.visible(() -> !onlyJump.isState());
        addSettings(mode, maxDistance, interval, duration, alpha, widthMul, sharpness, sound, onlyJump);
    }

    @Override
    public void onEnable() {
        lastPingTime = System.currentTimeMillis();
        if (mc.player != null && !onlyJump.isState()) {
            ping(mc.player.getPos());
        }
        super.onEnable();
    }

    @Override
    public void onDisable() {
        currentStart = 0L;
        lastPingTime = 0L;
        deleteDepthCopyFramebuffer();
        super.onDisable();
    }

    @EventLink
    public void onUpdate(EventUpdate event) {
        if (mc.player == null) {
            return;
        }

        long now = System.currentTimeMillis();

        if (onlyJump.isState()) {
            boolean onGround = mc.player.isOnGround();
            boolean jumped = wasOnGround && !onGround && mc.player.getVelocity().y > 0.12;
            wasOnGround = onGround;
            if (jumped && now - lastPingTime >= 400L) {
                ping(mc.player.getPos());
                lastPingTime = now;
            }
        } else {
            wasOnGround = mc.player.isOnGround();
            long intervalMs = (long) (interval.get() * 1000f);
            if (now - lastPingTime >= intervalMs) {
                ping(mc.player.getPos());
                lastPingTime = now;
            }
        }
    }

    @EventLink
    public void onChunkReload(EventChunkReload event) {
        if (mc.player != null) {
            ping(mc.player.getPos());
        }
    }

    public void renderFromMixin(Matrix4f positionMatrix, Matrix4f projectionMatrix, Vec3d camPos) {
        if (mc.player == null || mc.world == null || currentStart <= 0L) {
            return;
        }

        float durationMs = duration.get() * 1000f;
        float elapsed = System.currentTimeMillis() - currentStart;
        if (elapsed >= durationMs) {
            currentStart = 0L;
            return;
        }

        Framebuffer framebuffer = mc.getFramebuffer();
        ensureDepthCopyFramebuffer(framebuffer.textureWidth, framebuffer.textureHeight);
        if (depthCopyBuffer == null) {
            return;
        }
        depthCopyBuffer.copyDepthFrom(framebuffer);

        Matrix4f invView = new Matrix4f(positionMatrix).invert();
        Matrix4f invProj = new Matrix4f(projectionMatrix).invert();

        float maxR = maxDistance.get();
        float t = MathHelper.clamp(elapsed / durationMs, 0f, 1f);
        // Smooth deceleration easing: fast pulse out, smooth deceleration at perimeter
        float ease = (float) Easings.QUART_OUT.ease(t);
        float baseRadius = MathHelper.lerp(ease, 1.5f, maxR);

        // High visibility envelope: fast fade in, full 100% brightness across travel, smooth fade at boundary
        float alphaFactor = 1.0f;
        if (t < 0.10f) {
            alphaFactor = t / 0.10f;
        } else if (t > 0.78f) {
            alphaFactor = (1.0f - t) / 0.22f;
        }
        float baseAlpha = MathHelper.clamp(alpha.get() * alphaFactor, 0f, 1f);

        int c1 = ColorUtils.getThemeColor(0);
        int c2 = ColorUtils.getThemeColor(90);
        int c3 = ColorUtils.getThemeColor(180);
        int c4 = ColorUtils.getThemeColor(270);

        float baseWidth = MathHelper.clamp((5.5f + baseRadius * 0.16f) * widthMul.get(), 3.5f, maxR * 0.5f);
        float baseSharp = sharpness.get();

        int modeIndex = switch (mode.getCurrent()) {
            case "3D Сетка" -> 0;
            case "Киберпанк" -> 1;
            case "Волна" -> 2;
            case "Топография" -> 3;
            default -> 0;
        };

        renderPass(invView, invProj, camPos, framebuffer,
                baseRadius,
                baseWidth,
                baseSharp,
                modeIndex,
                applyAlpha(c1, baseAlpha),
                applyAlpha(c2, baseAlpha),
                applyAlpha(c3, baseAlpha),
                applyAlpha(c4, baseAlpha));

        RenderSystem.defaultBlendFunc();
    }

    public boolean shouldRenderWave() {
        return currentStart > 0L;
    }

    public void manualPing(Vec3d pos) {
        ping(pos);
    }

    private void renderPass(Matrix4f invView, Matrix4f invProj, Vec3d camPos,
                            Framebuffer framebuffer,
                            float radius, float width, float sharp, int modeIndex,
                            int outerColor, int midColor, int innerColor, int scanlineColor) {
        if (radius <= 0.001f || width <= 0.001f) {
            return;
        }

        ShaderProgram shader = mc.getShaderLoader().getOrCreateProgram(ShaderUtils.scanEffect);

        GlUniform invViewUniform = shader.getUniform("invViewMat");
        GlUniform invProjUniform = shader.getUniform("invProjMat");
        GlUniform posUniform = shader.getUniform("pos");
        GlUniform centerUniform = shader.getUniform("center");
        GlUniform radiusUniform = shader.getUniform("radius");
        GlUniform widthUniform = shader.getUniform("width");
        GlUniform sharpnessUniform = shader.getUniform("sharpness");
        GlUniform outerColorUniform = shader.getUniform("outerColor");
        GlUniform midColorUniform = shader.getUniform("midColor");
        GlUniform innerColorUniform = shader.getUniform("innerColor");
        GlUniform scanlineColorUniform = shader.getUniform("scanlineColor");
        GlUniform debugModeUniform = shader.getUniform("DebugMode");

        if (invViewUniform != null) invViewUniform.set(invView);
        if (invProjUniform != null) invProjUniform.set(invProj);
        if (posUniform != null) posUniform.set((float) camPos.x, (float) camPos.y, (float) camPos.z);
        if (centerUniform != null) centerUniform.set((float) center.x, (float) center.y, (float) center.z);
        if (radiusUniform != null) radiusUniform.set(radius);
        if (widthUniform != null) widthUniform.set(width);
        if (sharpnessUniform != null) sharpnessUniform.set(sharp);
        if (outerColorUniform != null) setColor(outerColorUniform, outerColor);
        if (midColorUniform != null) setColor(midColorUniform, midColor);
        if (innerColorUniform != null) setColor(innerColorUniform, innerColor);
        if (scanlineColorUniform != null) setColor(scanlineColorUniform, scanlineColor);
        if (debugModeUniform != null) debugModeUniform.set(modeIndex);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);

        int depthTex = depthCopyBuffer.getDepthAttachment();
        if (depthTex == 0) {
            depthTex = mc.getFramebuffer().getDepthAttachment();
        }
        RenderSystem.bindTexture(depthTex);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_MODE, GL11.GL_NONE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);

        framebuffer.beginWrite(false);
        RenderSystem.setShaderTexture(0, depthTex);
        RenderSystem.setShader(ShaderUtils.scanEffect);
        drawFullscreenQuad();

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private void drawFullscreenQuad() {
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
        buffer.vertex(-1f, -1f, 0f).texture(0f, 0f);
        buffer.vertex(-1f, 1f, 0f).texture(0f, 1f);
        buffer.vertex(1f, 1f, 0f).texture(1f, 1f);
        buffer.vertex(1f, -1f, 0f).texture(1f, 0f);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    private void ensureDepthCopyFramebuffer(int width, int height) {
        if (depthCopyBuffer == null || lastFbWidth != width || lastFbHeight != height) {
            deleteDepthCopyFramebuffer();
            depthCopyBuffer = new SimpleFramebuffer(width, height, true);
            lastFbWidth = width;
            lastFbHeight = height;
        }
    }

    private void deleteDepthCopyFramebuffer() {
        if (depthCopyBuffer != null) {
            depthCopyBuffer.delete();
            depthCopyBuffer = null;
        }
        lastFbWidth = -1;
        lastFbHeight = -1;
    }

    private void ping(Vec3d pos) {
        currentStart = System.currentTimeMillis();
        center = pos;
        if (sound.isState() && mc.player != null) {
            mc.getSoundManager().play(PositionedSoundInstance.master(
                    SoundEvents.BLOCK_BEACON_ACTIVATE, 1.85f, 0.7f));
        }
    }

    private void setColor(GlUniform uniform, int color) {
        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        if (a == 0) a = 255;
        uniform.set(r / 255f, g / 255f, b / 255f, a / 255f);
    }

    private int applyAlpha(int color, float alphaMul) {
        int a = (color >> 24) & 0xFF;
        if (a == 0) a = 255;
        a = (int) (a * MathHelper.clamp(alphaMul, 0f, 1f));
        return (color & 0x00FFFFFF) | (a << 24);
    }
}
