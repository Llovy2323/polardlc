package snill.client.api.utils.render.world;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKey;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Fog;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import snill.client.api.QClient;
import snill.client.api.storages.implement.helpertstorages.enumvar.ModuleClass;
import snill.client.api.utils.color.ColorUtils;
import snill.client.api.utils.render.ShaderUtils;
import snill.client.client.modules.impl.render.WorldTweaks;

public class WorldShaderRenderer implements QClient {

    private static final float SKY_RADIUS = 100.0F;
    private static final int STACKS = 28;
    private static final int SLICES = 56;

    private static WorldShaderRenderer instance;

    public static WorldShaderRenderer getInstance() {
        if (instance == null) {
            instance = new WorldShaderRenderer();
        }
        return instance;
    }

    public boolean shouldRender() {
        WorldTweaks tweaks = ModuleClass.worldTweaks;
        return tweaks != null && tweaks.shouldUseCustomSky() && mc.world != null;
    }

    public void render(Camera camera, float tickDelta, Fog fog) {
        WorldTweaks tweaks = ModuleClass.worldTweaks;
        if (tweaks == null || !tweaks.shouldUseCustomSky() || mc.world == null) {
            return;
        }

        ShaderProgramKey shaderKey = getShaderKey(tweaks);
        ShaderProgram shader = mc.getShaderLoader().getOrCreateProgram(shaderKey);
        setUniforms(shader, tweaks, camera, tickDelta, fog);

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(shaderKey);
        drawSkySphere(camera);
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private ShaderProgramKey getShaderKey(WorldTweaks tweaks) {
        if (tweaks.getSkyMode().equals("Лето")) {
            return ShaderUtils.skySummer;
        }
        if (tweaks.getSkyMode().equals("Плазма")) {
            return ShaderUtils.skyPlasma;
        }
        return ShaderUtils.skyShader;
    }

    private void setUniforms(ShaderProgram shader, WorldTweaks tweaks, Camera camera, float tickDelta, Fog fog) {
        float time = ((float) mc.world.getTime() + tickDelta) * tweaks.getSkySpeed() * 0.08f;
        int primary = getThemeColor(0);
        int secondary = getThemeColor(140);
        int accent = getAccentColor(tweaks, fog);

        setFloat(shader, "time", time);
        setFloat(shader, "scale", tweaks.getSkyScale());
        setFloat(shader, "mode", tweaks.getSkyModeIndex());
        setFloat(shader, "alpha", 1f);
        setColor(shader, "primaryColor", primary);
        setColor(shader, "secondaryColor", secondary);
        setColor(shader, "accentColor", accent);

        setColor(shader, "u_Color", primary);
        setColor(shader, "u_Color2", secondary);
        setFloat(shader, "u_Scale", tweaks.getSkyScale());
        setFloat(shader, "u_Time", time);
        setFloat(shader, "u_Night", tweaks.isSummerNightSky() || tweaks.getSkyMode().equals("Метель") ? 1f : 0f);
        setVec2(shader, "u_CameraDir",
                (float) Math.toRadians(camera.getYaw()),
                (float) Math.toRadians(camera.getPitch()));
        setFloat(shader, "u_Fov", mc.options.getFov().getValue().floatValue());
        setVec2(shader, "u_Resolution",
                mc.getWindow().getFramebufferWidth(),
                mc.getWindow().getFramebufferHeight());

        setFloat(shader, "uTime", time);
        setFloat(shader, "uAlpha", 1f);
        setFloat(shader, "uSpeed", Math.max(0.001f, tweaks.getSkySpeed()));
        setFloat(shader, "uScale", 4.5f * tweaks.getSkyScale());
        setFloat(shader, "uIntensity", tweaks.isSummerNightSky() ? 0.55f : 0.8f);
        setVec2(shader, "uCameraDir",
                (float) Math.toRadians(camera.getYaw()),
                (float) Math.toRadians(camera.getPitch()));
        setFloat(shader, "uFov", mc.options.getFov().getValue().floatValue());
        setVec2(shader, "uResolution",
                mc.getWindow().getFramebufferWidth(),
                mc.getWindow().getFramebufferHeight());
        setVec3(shader, "uColor", primary);
    }

    private int getAccentColor(WorldTweaks tweaks, Fog fog) {
        if (tweaks.getSkyMode().equals("Сакура")) {
            return ColorUtils.rgba(255, 150, 210, 255);
        }
        if (tweaks.getSkyMode().equals("Метель")) {
            return ColorUtils.rgba(195, 225, 255, 255);
        }
        if (tweaks.getSkyMode().equals("Эфир")) {
            return ColorUtils.rgba(165, 235, 255, 255);
        }
        if (fog != null) {
            return ColorUtils.rgba((int) (fog.red() * 255f), (int) (fog.green() * 255f), (int) (fog.blue() * 255f), 255);
        }
        return getThemeColor(260);
    }

    private int getThemeColor(int offset) {
        try {
            return ColorUtils.getThemeColor(offset);
        } catch (Exception ignored) {
            return ColorUtils.getColor(255, 255, 255, 255);
        }
    }

    private void drawSkySphere(Camera camera) {
        Matrix4f matrix = new Matrix4f().rotation(camera.getRotation());
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION);
        for (int i = 0; i < STACKS; i++) {
            float phi0 = (float) (Math.PI * i / STACKS);
            float phi1 = (float) (Math.PI * (i + 1) / STACKS);
            for (int j = 0; j < SLICES; j++) {
                float theta0 = (float) (2.0 * Math.PI * j / SLICES);
                float theta1 = (float) (2.0 * Math.PI * (j + 1) / SLICES);
                vertex(buffer, matrix, theta0, phi0);
                vertex(buffer, matrix, theta1, phi0);
                vertex(buffer, matrix, theta1, phi1);
                vertex(buffer, matrix, theta0, phi1);
            }
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    private void vertex(BufferBuilder buffer, Matrix4f matrix, float theta, float phi) {
        float sinPhi = MathHelper.sin(phi);
        float x = SKY_RADIUS * MathHelper.cos(theta) * sinPhi;
        float y = SKY_RADIUS * MathHelper.cos(phi);
        float z = SKY_RADIUS * MathHelper.sin(theta) * sinPhi;
        buffer.vertex(matrix, x, y, z);
    }

    private void setColor(ShaderProgram shader, String name, int color) {
        GlUniform uniform = shader.getUniform(name);
        if (uniform != null) {
            int alpha = ColorUtils.alpha(color);
            if (alpha == 0) {
                alpha = 255;
            }
            uniform.set(ColorUtils.redf(color), ColorUtils.greenf(color), ColorUtils.bluef(color), alpha / 255f);
        }
    }

    private void setFloat(ShaderProgram shader, String name, float value) {
        GlUniform uniform = shader.getUniform(name);
        if (uniform != null) {
            uniform.set(value);
        }
    }

    private void setVec2(ShaderProgram shader, String name, float x, float y) {
        GlUniform uniform = shader.getUniform(name);
        if (uniform != null) {
            uniform.set(x, y);
        }
    }

    private void setVec3(ShaderProgram shader, String name, int color) {
        GlUniform uniform = shader.getUniform(name);
        if (uniform != null) {
            uniform.set(ColorUtils.redf(color), ColorUtils.greenf(color), ColorUtils.bluef(color));
        }
    }
}
