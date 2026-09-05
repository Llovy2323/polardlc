package snill.client.api.utils.render.hands;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKey;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import snill.client.api.QClient;
import snill.client.api.storages.implement.helpertstorages.enumvar.ModuleClass;
import snill.client.api.utils.color.ColorUtils;
import snill.client.api.utils.render.ShaderUtils;
import snill.client.client.modules.impl.render.ShaderHands;
import snill.client.Snill;

import java.util.ArrayList;
import java.util.List;

/**
 * Minecraft 1.21 backend for Polar 1.3 Hands.
 *
 * <p>The original module binds its own framebuffer in EventHandsRender.Pre and
 * composites it in EventHandsRender.Post.  These two methods are called from
 * the matching HEAD/TAIL injections in HeldItemRendererMixin, so only the
 * first-person arms/items enter the effect buffer.</p>
 */
public final class ShaderHandsRenderer implements QClient {
    private static ShaderHandsRenderer instance;

    private Framebuffer handsBuffer;
    private Framebuffer processedBuffer;
    private Framebuffer sceneBuffer;
    private Framebuffer trailRead;
    private Framebuffer trailWrite;
    private final List<Framebuffer> bloomBuffers = new ArrayList<>();

    private int width = -1;
    private int height = -1;
    private boolean capturing;

    private long lastTrailTime;
    private float smoothDt = 1f / 60f;
    private float smoothTrailRise;
    private float smoothTrailSway;
    private float smoothBurst;
    private long lastSwingMs = -10000L;
    private boolean wasSwinging;

    public static ShaderHandsRenderer getInstance() {
        if (instance == null) instance = new ShaderHandsRenderer();
        return instance;
    }

    /** Equivalent of EventHandsRender.Pre in Polar 1.3. */
    public void captureBeforeHands() {
        ShaderHands module = getModule();
        if (module == null || !module.isEnable()) return;

        ensureBuffers();
        copyMainColor(sceneBuffer);
        clear(handsBuffer);
        handsBuffer.beginWrite(true);
        capturing = true;
    }

    /** Equivalent of EventHandsRender.Post in Polar 1.3. */
    public void captureAfterHands() {
        if (!capturing) return;
        capturing = false;
        mc.getFramebuffer().beginWrite(true);

        ShaderHands module = getModule();
        if (module == null || !module.isEnable()) return;

        beginFullscreenState();
        try {
            buildProcessedHands(module);
            renderEffects(module);
        } finally {
            endFullscreenState();
            mc.getFramebuffer().beginWrite(true);
        }
    }

    /**
     * RenderLayer.MAIN_TARGET calls mainFramebuffer.beginWrite() during
     * VertexConsumerProvider.draw(). Keep that scoped write in handsBuffer.
     */
    public boolean redirectMainWrite(boolean setViewport) {
        if (!capturing || handsBuffer == null) return false;
        handsBuffer.beginWrite(setViewport);
        return true;
    }

    private void buildProcessedHands(ShaderHands module) {
        clear(processedBuffer);
        processedBuffer.beginWrite(true);
        RenderSystem.disableDepthTest();
        RenderSystem.disableBlend();

        if (module.mode.is("Зеркало")) {
            ShaderProgram shader = program(ShaderUtils.shaderHandsMirror);
            if (shader == null) {
                copyTexture(handsBuffer.getColorAttachment(), 1f);
                return;
            }

            int blurredScene = runKawase(sceneBuffer.getColorAttachment(), 4);
            processedBuffer.beginWrite(true);
            RenderSystem.setShader(ShaderUtils.shaderHandsMirror);
            RenderSystem.setShaderTexture(0, handsBuffer.getColorAttachment());
            RenderSystem.setShaderTexture(1, blurredScene);
            int color = module.mirrorColorMode.is("Интерфейс")
                    ? themeColor()
                    : parseColor(module.mirrorColor.get(), 0xFF8A98FF);
            set(shader, "multiplier", ColorUtils.redf(color), ColorUtils.greenf(color), ColorUtils.bluef(color));
            set(shader, "mixFactor", module.mixFactor.get());
            drawFullscreenQuad();
            return;
        }

        ShaderProgram shader = program(ShaderUtils.shaderHandsOverlay);
        if (shader == null) {
            copyTexture(handsBuffer.getColorAttachment(), 1f);
            return;
        }

        RenderSystem.setShader(ShaderUtils.shaderHandsOverlay);
        RenderSystem.setShaderTexture(0, handsBuffer.getColorAttachment());
        int color = module.fillColorMode.is("Интерфейс")
                ? themeColor()
                : parseColor(module.fillColor.get(), 0xFFFF4444);
        set(shader, "color", ColorUtils.redf(color), ColorUtils.greenf(color), ColorUtils.bluef(color));
        set(shader, "fillAlpha", module.fillAlpha.get());
        set(shader, "shadingStrength", module.shadingStrength.get());
        set(shader, "keepShading", module.keepShading.isState() ? 1 : 0);
        set(shader, "rainbow", module.fillRainbow.isState() ? 1 : 0);
        set(shader, "rainbowTime", time());
        set(shader, "rainbowSpeed", module.rainbowSpeed.get());
        set(shader, "rainbowScale", module.rainbowScale.get());
        drawFullscreenQuad();
    }

    /** Same order as Hands.renderEffects(): bloom/trail, model, outline. */
    private void renderEffects(ShaderHands module) {
        int bloomTexture = -1;
        boolean outerGlow = module.glowEnabled.isState() && module.outerGlow.isState();

        if (outerGlow) {
            bloomTexture = runKawase(processedBuffer.getColorAttachment(),
                    Math.max(1, Math.round(module.glowRadius.get())));
            if (module.trailEnabled.isState()) {
                renderTrail(module, bloomTexture);
            } else {
                renderGlow(module, bloomTexture);
            }
        } else if (module.outlineEnabled.isState() && module.autoColor.isState()) {
            bloomTexture = runKawase(processedBuffer.getColorAttachment(), 3);
        }

        mc.getFramebuffer().beginWrite(true);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        copyTexture(processedBuffer.getColorAttachment(), 1f);

        if (module.outlineEnabled.isState()) {
            renderOutline(module, bloomTexture);
        }
    }

    private void renderGlow(ShaderHands module, int bloomTexture) {
        ShaderProgram shader = program(ShaderUtils.shaderHandsGlow);
        if (shader == null) return;

        mc.getFramebuffer().beginWrite(true);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        RenderSystem.setShader(ShaderUtils.shaderHandsGlow);
        RenderSystem.setShaderTexture(0, bloomTexture);
        RenderSystem.setShaderTexture(1, processedBuffer.getColorAttachment());
        setEffectColors(module, shader);
        set(shader, "exposure", module.glowExposure.get());
        set(shader, "autoColor", module.autoColor.isState() ? 1 : 0);
        set(shader, "saturation", module.saturation.get());
        drawFullscreenQuad();
        RenderSystem.defaultBlendFunc();
    }

    private void renderOutline(ShaderHands module, int bloomTexture) {
        ShaderProgram shader = program(ShaderUtils.shaderHandsOutline);
        if (shader == null) return;

        mc.getFramebuffer().beginWrite(true);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(ShaderUtils.shaderHandsOutline);
        RenderSystem.setShaderTexture(0, processedBuffer.getColorAttachment());
        RenderSystem.setShaderTexture(1, bloomTexture == -1
                ? processedBuffer.getColorAttachment()
                : bloomTexture);

        int color = module.effectColorMode.is("Интерфейс")
                ? themeColor()
                : parseColor(module.outlineColor.get(), 0xFF8A98FF);
        set(shader, "solidColor", ColorUtils.redf(color), ColorUtils.greenf(color), ColorUtils.bluef(color));
        set(shader, "texelSize", 1f / Math.max(1, width), 1f / Math.max(1, height));
        set(shader, "width", module.outlineWidth.get());
        set(shader, "alpha", 1f);
        set(shader, "rainbowTime", time());
        set(shader, "rainbowSpeed", module.rainbowSpeed.get());
        set(shader, "rainbowScale", module.rainbowScale.get());
        set(shader, "saturation", module.saturation.get());
        int colorMode = module.mode.is("Заливка") && module.fillRainbow.isState()
                ? 1
                : module.autoColor.isState() && bloomTexture != -1 ? 2 : 0;
        set(shader, "colorMode", colorMode);
        drawFullscreenQuad();
    }

    private void renderTrail(ShaderHands module, int bloomTexture) {
        ShaderProgram fadeShader = program(ShaderUtils.shaderHandsTrailFade);
        ShaderProgram colorShader = program(ShaderUtils.shaderHandsTrailColor);
        if (fadeShader == null || colorShader == null) {
            renderGlow(module, bloomTexture);
            return;
        }

        long now = System.currentTimeMillis();
        float rawDt = lastTrailTime > 0L ? (now - lastTrailTime) / 1000f : 1f / 60f;
        lastTrailTime = now;
        if (rawDt <= 0f || rawDt > 0.05f) rawDt = smoothDt;
        rawDt = Math.max(1f / 144f, Math.min(1f / 60f, rawDt));
        smoothDt += (rawDt - smoothDt) * 0.08f;
        float dt = smoothDt;
        float t = time();

        float riseDelta = module.trailRise.get() * dt;
        float swayDelta = (float) (Math.sin(t * 3.6f) - Math.sin((t - dt) * 3.6f))
                * module.trailSway.get();
        float smooth = 1f - (float) Math.exp(-dt * 8f);
        smoothTrailRise += (riseDelta - smoothTrailRise) * smooth;
        smoothTrailSway += (swayDelta - smoothTrailSway) * smooth;

        boolean swinging = mc.player != null && mc.player.getHandSwingProgress(1f) > 0.01f;
        if (module.trailBurst.isState() && swinging && !wasSwinging) lastSwingMs = now;
        wasSwinging = swinging;
        float burst = Math.max(0f, 1f - (now - lastSwingMs) / 450f);
        smoothBurst += (burst - smoothBurst) * (1f - (float) Math.exp(-dt * 6f));
        float fadeAdd = smoothBurst * module.trailBurstPower.get() * 0.012f;

        clear(trailWrite);
        trailWrite.beginWrite(true);
        RenderSystem.disableBlend();
        RenderSystem.setShader(ShaderUtils.shaderHandsTrailFade);
        RenderSystem.setShaderTexture(0, trailRead.getColorAttachment());
        set(fadeShader, "offset", smoothTrailSway, smoothTrailRise);
        set(fadeShader, "texSize", trailRead.textureWidth, trailRead.textureHeight);
        set(fadeShader, "fade", module.trailFade.get() + fadeAdd);
        set(fadeShader, "time", t);
        set(fadeShader, "dt", dt);
        set(fadeShader, "turb", module.trailTurb.get());
        set(fadeShader, "flickAmp", module.trailFlicker.get());
        drawFullscreenQuad();

        trailWrite.beginWrite(true);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        RenderSystem.setShader(ShaderUtils.shaderHandsTrailColor);
        RenderSystem.setShaderTexture(0, bloomTexture);
        setEffectColors(module, colorShader);
        set(colorShader, "exposure", module.glowExposure.get());
        set(colorShader, "autoColor", module.autoColor.isState() ? 1 : 0);
        set(colorShader, "saturation", module.saturation.get());
        drawFullscreenQuad();

        if (module.trailModel.isState()) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            copyTexture(processedBuffer.getColorAttachment(), module.trailModelAlpha.get());
        }

        mc.getFramebuffer().beginWrite(true);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        copyTexture(trailWrite.getColorAttachment(), 1f);
        RenderSystem.defaultBlendFunc();

        Framebuffer swap = trailRead;
        trailRead = trailWrite;
        trailWrite = swap;
    }

    private int runKawase(int sourceTexture, int iterations) {
        ensureBloomBuffers(iterations);
        ShaderProgram down = program(ShaderUtils.shaderHandsKawaseDown);
        ShaderProgram up = program(ShaderUtils.shaderHandsKawaseUp);
        if (down == null || up == null || bloomBuffers.isEmpty()) return sourceTexture;

        int current = sourceTexture;
        RenderSystem.disableBlend();
        for (int i = 0; i < iterations; i++) {
            Framebuffer target = bloomBuffers.get(i);
            clear(target);
            target.beginWrite(true);
            RenderSystem.setShader(ShaderUtils.shaderHandsKawaseDown);
            RenderSystem.setShaderTexture(0, current);
            setKawase(down, target, 1f + i);
            drawFullscreenQuad();
            current = target.getColorAttachment();
        }

        for (int i = iterations - 1; i >= 1; i--) {
            Framebuffer target = bloomBuffers.get(i - 1);
            clear(target);
            target.beginWrite(true);
            RenderSystem.setShader(ShaderUtils.shaderHandsKawaseUp);
            RenderSystem.setShaderTexture(0, current);
            setKawase(up, target, 1f + i);
            set(up, "color", 1f, 1f, 1f);
            drawFullscreenQuad();
            current = target.getColorAttachment();
        }
        return current;
    }

    private void ensureBuffers() {
        int newWidth = Math.max(2, mc.getWindow().getFramebufferWidth());
        int newHeight = Math.max(2, mc.getWindow().getFramebufferHeight());
        if (newWidth == width && newHeight == height && handsBuffer != null) return;

        deleteBuffers();
        width = newWidth;
        height = newHeight;
        handsBuffer = new SimpleFramebuffer(width, height, true);
        processedBuffer = new SimpleFramebuffer(width, height, false);
        sceneBuffer = new SimpleFramebuffer(width, height, false);
        trailRead = new SimpleFramebuffer(Math.max(2, width / 2), Math.max(2, height / 2), false);
        trailWrite = new SimpleFramebuffer(Math.max(2, width / 2), Math.max(2, height / 2), false);
        linear(handsBuffer);
        linear(processedBuffer);
        linear(sceneBuffer);
        linear(trailRead);
        linear(trailWrite);
        clear(trailRead);
        clear(trailWrite);
    }

    private void ensureBloomBuffers(int count) {
        while (bloomBuffers.size() > count) {
            bloomBuffers.remove(bloomBuffers.size() - 1).delete();
        }
        for (int i = 0; i < count; i++) {
            int bufferWidth = Math.max(2, width >> (i + 1));
            int bufferHeight = Math.max(2, height >> (i + 1));
            if (i >= bloomBuffers.size()) {
                Framebuffer framebuffer = new SimpleFramebuffer(bufferWidth, bufferHeight, false);
                linear(framebuffer);
                bloomBuffers.add(framebuffer);
            } else {
                Framebuffer framebuffer = bloomBuffers.get(i);
                if (framebuffer.textureWidth != bufferWidth || framebuffer.textureHeight != bufferHeight) {
                    framebuffer.delete();
                    framebuffer = new SimpleFramebuffer(bufferWidth, bufferHeight, false);
                    linear(framebuffer);
                    bloomBuffers.set(i, framebuffer);
                }
            }
        }
    }

    private void copyMainColor(Framebuffer target) {
        int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, mc.getFramebuffer().fbo);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, target.fbo);
        GL30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height,
                GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
        mc.getFramebuffer().beginWrite(true);
    }

    private void beginFullscreenState() {
        RenderSystem.backupProjectionMatrix();
        float scaledWidth = Math.max(1, mc.getWindow().getScaledWidth());
        float scaledHeight = Math.max(1, mc.getWindow().getScaledHeight());
        Matrix4f ortho = new Matrix4f().setOrtho(0f, scaledWidth, scaledHeight, 0f, -1000f, 1000f);
        RenderSystem.setProjectionMatrix(ortho, ProjectionType.ORTHOGRAPHIC);
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.identity();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
    }

    private void endFullscreenState() {
        RenderSystem.getModelViewStack().popMatrix();
        RenderSystem.restoreProjectionMatrix();
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.setShaderTexture(0, 0);
        RenderSystem.setShaderTexture(1, 0);
    }

    private void copyTexture(int texture, float alpha) {
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, texture);
        drawFullscreenQuad(alpha);
    }

    private void setEffectColors(ShaderHands module, ShaderProgram shader) {
        int first;
        int second;
        if (module.effectColorMode.is("Интерфейс")) {
            first = themeColor();
            second = first;
        } else {
            first = parseColor(module.glowColor1.get(), 0xFF8A98FF);
            second = parseColor(module.glowColor2.get(), 0xFFFF6BAC);
        }
        set(shader, "color", ColorUtils.redf(first), ColorUtils.greenf(first), ColorUtils.bluef(first));
        set(shader, "color2", ColorUtils.redf(second), ColorUtils.greenf(second), ColorUtils.bluef(second));
    }

    public void release() {
        boolean wasCapturing = capturing;
        capturing = false;
        if (wasCapturing) mc.getFramebuffer().beginWrite(true);
        deleteBuffers();
        width = height = -1;
        lastTrailTime = 0L;
        smoothDt = 1f / 60f;
        smoothTrailRise = 0f;
        smoothTrailSway = 0f;
        smoothBurst = 0f;
        wasSwinging = false;
    }

    private void deleteBuffers() {
        if (handsBuffer != null) handsBuffer.delete();
        if (processedBuffer != null) processedBuffer.delete();
        if (sceneBuffer != null) sceneBuffer.delete();
        if (trailRead != null) trailRead.delete();
        if (trailWrite != null) trailWrite.delete();
        handsBuffer = processedBuffer = sceneBuffer = trailRead = trailWrite = null;
        bloomBuffers.forEach(Framebuffer::delete);
        bloomBuffers.clear();
    }

    private ShaderHands getModule() {
        if (Snill.INSTANCE == null || ModuleClass.INSTANCE == null) return null;
        return ModuleClass.INSTANCE.shaderHands;
    }

    private ShaderProgram program(ShaderProgramKey key) {
        return mc.getShaderLoader().getOrCreateProgram(key);
    }

    private int themeColor() {
        return ColorUtils.getThemeColor();
    }

    private int parseColor(String text, int fallback) {
        try {
            String value = text == null ? "" : text.trim();
            if (value.startsWith("#")) value = value.substring(1);
            if (value.length() != 6) return fallback;
            return 0xFF000000 | Integer.parseInt(value, 16);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private float time() {
        return (System.currentTimeMillis() % 100000L) / 1000f;
    }

    private void clear(Framebuffer framebuffer) {
        framebuffer.setClearColor(0f, 0f, 0f, 0f);
        framebuffer.clear();
    }

    private void linear(Framebuffer framebuffer) {
        RenderSystem.bindTexture(framebuffer.getColorAttachment());
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        RenderSystem.bindTexture(0);
    }

    private void setKawase(ShaderProgram shader, Framebuffer target, float offset) {
        set(shader, "uSize", target.textureWidth, target.textureHeight);
        set(shader, "uOffset", offset, offset);
        set(shader, "uHalfPixel", 0.5f / Math.max(1, target.textureWidth),
                0.5f / Math.max(1, target.textureHeight));
    }

    private void set(ShaderProgram shader, String name, float value) {
        GlUniform uniform = shader.getUniform(name);
        if (uniform != null) uniform.set(value);
    }

    private void set(ShaderProgram shader, String name, int value) {
        GlUniform uniform = shader.getUniform(name);
        if (uniform != null) uniform.set(value);
    }

    private void set(ShaderProgram shader, String name, float x, float y) {
        GlUniform uniform = shader.getUniform(name);
        if (uniform != null) uniform.set(x, y);
    }

    private void set(ShaderProgram shader, String name, float x, float y, float z) {
        GlUniform uniform = shader.getUniform(name);
        if (uniform != null) uniform.set(x, y, z);
    }

    private void drawFullscreenQuad() {
        drawFullscreenQuad(1f);
    }

    private void drawFullscreenQuad(float alpha) {
        float scaledWidth = Math.max(1, mc.getWindow().getScaledWidth());
        float scaledHeight = Math.max(1, mc.getWindow().getScaledHeight());
        BufferBuilder buffer = Tessellator.getInstance().begin(
                VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        buffer.vertex(0, 0, 0).texture(0, 1).color(1f, 1f, 1f, alpha);
        buffer.vertex(0, scaledHeight, 0).texture(0, 0).color(1f, 1f, 1f, alpha);
        buffer.vertex(scaledWidth, scaledHeight, 0).texture(1, 0).color(1f, 1f, 1f, alpha);
        buffer.vertex(scaledWidth, 0, 0).texture(1, 1).color(1f, 1f, 1f, alpha);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }
}
