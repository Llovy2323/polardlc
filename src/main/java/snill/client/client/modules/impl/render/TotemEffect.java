package snill.client.client.modules.impl.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import snill.client.api.events.EventLink;
import snill.client.api.events.implement.Event3DRender;
import snill.client.api.events.implement.EventPacket;
import snill.client.api.storages.implement.helpertstorages.enumvar.ModuleClass;
import snill.client.api.utils.color.ColorUtils;
import snill.client.client.modules.Module;
import snill.client.client.modules.settings.implement.FloatSetting;
import snill.client.client.modules.settings.implement.ModeSetting;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

public class TotemEffect extends Module {

    public static TotemEffect INSTANCE = new TotemEffect();

    private final ModeSetting mode = new ModeSetting("Режим", "Партиклы", "Партиклы", "Кубики", "Sonar");
    private final FloatSetting duration = new FloatSetting("Длительность", 3f, 0.2f, 6.0f, 0.1f);
    private static final Identifier SPARKLE_TEXTURE = Identifier.of("snill", "textures/particle/sparkle.png");
    private static final byte[][] CUBE_EDGES = {
            {-1, -1, -1, 1, -1, -1}, {1, -1, -1, 1, -1, 1}, {1, -1, 1, -1, -1, 1}, {-1, -1, 1, -1, -1, -1},
            {-1, 1, -1, 1, 1, -1}, {1, 1, -1, 1, 1, 1}, {1, 1, 1, -1, 1, 1}, {-1, 1, 1, -1, 1, -1},
            {-1, -1, -1, -1, 1, -1}, {1, -1, -1, 1, 1, -1}, {1, -1, 1, 1, 1, 1}, {-1, -1, 1, -1, 1, 1}
    };

    private final List<TotemSphereEffect> sphereEffects = new CopyOnWriteArrayList<>();
    private final List<TotemCubeParticle> cubeEffects = new CopyOnWriteArrayList<>();
    private final Map<Integer, Long> recentSphereSpawns = new ConcurrentHashMap<>();

    public TotemEffect() {
        super("TotemEffect", "Частицы при срабатывании тотема", ModuleCategory.RENDER);
        addSettings(mode, duration);
    }

    @Override
    public void onDisable() {
        sphereEffects.clear();
        cubeEffects.clear();
        recentSphereSpawns.clear();
        super.onDisable();
    }

    @EventLink
    public void onPacket(EventPacket event) {
        if (mc.world == null || mc.player == null || event.getType() != EventPacket.Type.RECEIVE) return;

        if (event.getPacket() instanceof EntityStatusS2CPacket packet && packet.getStatus() == 35) {
            mc.execute(() -> {
                Entity entity = packet.getEntity(mc.world);
                if (entity instanceof AbstractClientPlayerEntity player) {
                    if (mode.is("Sonar")) {
                        Sonar sonar = ModuleClass.INSTANCE != null ? ModuleClass.INSTANCE.sonar : null;
                        if (sonar != null) {
                            sonar.manualPing(player.getPos().add(0.0, player.getHeight() * 0.5, 0.0));
                        }
                    } else if (mode.is("Кубики")) {
                        spawnCubeEffect(player);
                    } else {
                        TotemEffect(player);
                    }
                }
            });
        }
    }

    @EventLink
    public void onRender3D(Event3DRender event) {
        if (mc.world == null || mc.player == null) return;
        Vec3d cameraPos = mc.gameRenderer.getCamera().getPos();
        if (!sphereEffects.isEmpty()) {
            renderSphereEffects(event.getMatrices(), cameraPos);
        }
        if (!cubeEffects.isEmpty()) {
            renderCubeEffects(event.getMatrices(), cameraPos, event.getTickDelta());
        }
    }

    private void TotemEffect(AbstractClientPlayerEntity player) {
        if (player == null || player == mc.player) return;

        long now = System.currentTimeMillis();
        recentSphereSpawns.entrySet().removeIf(entry -> now - entry.getValue() > 1000L);

        Long lastSpawn = recentSphereSpawns.get(player.getId());
        if (lastSpawn != null && now - lastSpawn < 120L) return;
        recentSphereSpawns.put(player.getId(), now);

        double centerY = player.getY() + player.getHeight() * 0.62;
        List<SphereParticle> particles = new ArrayList<>(96);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int themeColor = withFullAlpha(ColorUtils.getThemeColor());

        for (int i = 0; i < 96; i++) {
            double yaw = random.nextDouble(0.0, Math.PI * 2.0);
            double pitch = random.nextDouble(-0.8, 0.8);
            Vec3d direction = new Vec3d(
                    Math.cos(yaw) * Math.cos(pitch),
                    Math.sin(pitch) * 0.62 + random.nextDouble(-0.12, 0.24),
                    Math.sin(yaw) * Math.cos(pitch)
            ).normalize();

            particles.add(new SphereParticle(
                    direction,
                    random.nextFloat(0.36f, 1.28f),
                    random.nextFloat(1.05f, 1.85f),
                    random.nextFloat(1.25f, 2.15f),
                    random.nextFloat(1.02f, 1.58f),
                    random.nextFloat(0.0f, 1.0f),
                    themeColor
            ));
        }

        sphereEffects.add(new TotemSphereEffect(
                new Vec3d(player.getX(), centerY, player.getZ()),
                now,
                random.nextFloat(0f, 360f),
                particles,
                createSphereOrbitLines(themeColor)
        ));
    }

    private void spawnCubeEffect(AbstractClientPlayerEntity player) {
        if (player == null || player == mc.player) return;

        ThreadLocalRandom random = ThreadLocalRandom.current();
        Vec3d origin = new Vec3d(player.getX(), player.getY() + player.getHeight() * 0.55, player.getZ());
        int color = withFullAlpha(ColorUtils.getThemeColor());
        long now = System.currentTimeMillis();

        for (int i = 0; i < 54; i++) {
            double yaw = random.nextDouble(0.0, Math.PI * 2.0);
            double lift = random.nextDouble(0.24, 0.92);
            double speed = random.nextDouble(1.45, 3.85);
            Vec3d velocity = new Vec3d(
                    Math.cos(yaw) * speed,
                    lift * speed,
                    Math.sin(yaw) * speed
            );
            Vec3d offset = new Vec3d(
                    random.nextDouble(-0.22, 0.22),
                    random.nextDouble(-0.10, 0.22),
                    random.nextDouble(-0.22, 0.22)
            );
            cubeEffects.add(new TotemCubeParticle(
                    origin.add(offset),
                    velocity,
                    player.getY() + 0.02,
                    random.nextFloat(0.085f, 0.16f),
                    now,
                    color,
                    random.nextFloat(0f, 360f),
                    random.nextFloat(0f, 360f),
                    random.nextFloat(0f, 360f),
                    random.nextFloat(-250f, 250f),
                    random.nextFloat(-250f, 250f),
                    random.nextFloat(-250f, 250f)
            ));
        }
    }

    private void renderSphereEffects(MatrixStack matrices, Vec3d cameraPos) {
        long now = System.currentTimeMillis();
        float durationMs = duration.get() * 1000.0f;

        sphereEffects.removeIf(effect -> now - effect.startTime >= durationMs);
        if (sphereEffects.isEmpty()) return;

        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        renderSphereParticles(matrices, cameraPos, now, durationMs);
        renderSphereArcs(matrices, cameraPos, now, durationMs);

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private void renderSphereParticles(MatrixStack matrices, Vec3d cameraPos, long now, float durationMs) {
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        RenderSystem.setShaderTexture(0, SPARKLE_TEXTURE);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);

        float cameraYaw = mc.gameRenderer.getCamera().getYaw();
        float cameraPitch = mc.gameRenderer.getCamera().getPitch();

        for (TotemSphereEffect effect : sphereEffects) {
            float age = (now - effect.startTime) / durationMs;
            float appear = MathHelper.clamp(1.0f - age, 0.0f, 1.0f);
            float burstProgress = easeOutQuad(Math.min(1.0f, age * 1.12f));

            for (SphereParticle particle : effect.particles) {
                float localProgress = MathHelper.clamp((age * particle.timeScale) + particle.progressOffset * 0.1f, 0.0f, 1.0f);
                float launchProgress = easeOutQuad(localProgress);
                float radial = (0.34f + launchProgress * (1.20f + particle.spread * 1.05f) + burstProgress * 0.32f) * 1.34f;
                float orbit = (now * 0.0012f * particle.rotationScale) + particle.progressOffset * 5.4f;
                double swirlScale = (1.0f - localProgress) * 0.18f;

                Vec3d worldPos = effect.origin
                        .add(particle.direction.multiply(radial))
                        .add(
                                Math.cos(orbit) * swirlScale * particle.swirlAmount,
                                Math.sin(orbit * 1.3f) * swirlScale * 0.75f * particle.swirlAmount + localProgress * 0.08f - localProgress * localProgress * 0.14f,
                                Math.sin(orbit) * swirlScale * particle.swirlAmount
                        );

                int color = setAlpha(particle.color, (int) (255 * appear * (0.66f + 0.54f * (1.0f - localProgress))));
                float drawSize = 0.34f * (0.76f + particle.spread * 0.42f) * (0.82f + appear * 0.62f);

                matrices.push();
                matrices.translate(worldPos.x - cameraPos.x, worldPos.y - cameraPos.y, worldPos.z - cameraPos.z);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-cameraYaw));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(cameraPitch));
                drawBillboard(matrices.peek().getPositionMatrix(), drawSize, color);
                matrices.pop();
            }
        }
    }

    private void renderSphereArcs(MatrixStack matrices, Vec3d cameraPos, long now, float durationMs) {
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        RenderSystem.lineWidth(1.65f);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);

        for (TotemSphereEffect effect : sphereEffects) {
            float age = (now - effect.startTime) / durationMs;
            float appear = MathHelper.clamp(1.0f - age, 0.0f, 1.0f);
            float grow = easeOutQuad(Math.min(1.0f, age * 1.25f));
            float elapsedSec = (now - effect.startTime) / 1000.0f;
            float scale = 1.32f * (0.82f + grow * 0.16f);

            for (OrbitLine line : effect.orbitLines) {
                matrices.push();
                matrices.translate(effect.origin.x - cameraPos.x, effect.origin.y - cameraPos.y, effect.origin.z - cameraPos.z);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(effect.baseRotation + line.baseYaw + elapsedSec * line.speedDeg));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(line.tiltX));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(line.tiltZ));
                drawOrbitArc(matrices, line.radiusX * scale, line.radiusZ * scale, line.yOffset,
                        line.startDeg, line.arcDeg, appear * line.alphaMul, line.startColor, line.endColor);
                matrices.pop();
            }
        }

        GL11.glDisable(GL11.GL_LINE_SMOOTH);
    }

    private void renderCubeEffects(MatrixStack matrices, Vec3d cameraPos, float tickDelta) {
        long now = System.currentTimeMillis();
        float durationMs = duration.get() * 1000.0f;
        float dt = Math.min(0.05f, Math.max(0.001f, tickDelta / 20.0f));

        cubeEffects.removeIf(particle -> {
            particle.update(dt);
            return now - particle.startTime >= durationMs || particle.alpha(now, durationMs) <= 0.01f;
        });
        if (cubeEffects.isEmpty()) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        BufferBuilder faces = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        for (TotemCubeParticle particle : cubeEffects) {
            particle.appendFaces(faces, matrices, cameraPos, now, durationMs);
        }
        BufferRenderer.drawWithGlobalProgram(faces.end());

        BufferBuilder lines = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        for (TotemCubeParticle particle : cubeEffects) {
            particle.appendLines(lines, matrices, cameraPos, now, durationMs);
        }
        BufferRenderer.drawWithGlobalProgram(lines.end());

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private void drawOrbitArc(MatrixStack matrices, float radiusX, float radiusZ, float y, float startDeg, float arcDeg,
                              float alphaMul, int startColor, int endColor) {
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINE_STRIP, VertexFormats.POSITION_COLOR);

        int segments = 28;
        float from = (float) Math.toRadians(startDeg);
        float to = (float) Math.toRadians(startDeg + arcDeg);
        for (int i = 0; i <= segments; i++) {
            float progress = i / (float) segments;
            float angle = MathHelper.lerp(progress, from, to);
            float edgeFade = MathHelper.clamp(1.0f - Math.abs(progress - 0.5f) * 2.0f, 0.0f, 1.0f);
            buffer.vertex(matrix, MathHelper.cos(angle) * radiusX, y + MathHelper.sin(angle * 1.35f) * 0.010f, MathHelper.sin(angle) * radiusZ)
                    .color(fadeLerp(startColor, endColor, progress, alphaMul * (0.22f + 0.78f * edgeFade)));
        }

        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    private List<OrbitLine> createSphereOrbitLines(int themeColor) {
        List<OrbitLine> lines = new ArrayList<>(5);
        lines.add(new OrbitLine(1.02f, 0.66f, 0.20f, 196f, 156f, 14f, -12f, 54f, 0.46f, themeColor, themeColor));
        lines.add(new OrbitLine(0.92f, 0.60f, 0.16f, 188f, 148f, 14f, -12f, 54f, 0.22f, themeColor, themeColor));
        lines.add(new OrbitLine(0.86f, 0.54f, -0.12f, 122f, 112f, 78f, 4f, -68f, 0.65f, themeColor, themeColor));
        lines.add(new OrbitLine(0.74f, 0.46f, -0.02f, 314f, 88f, 62f, -18f, 76f, 0.58f, themeColor, themeColor));
        lines.add(new OrbitLine(0.68f, 0.34f, 0.00f, 202f, 44f, 8f, 52f, -44f, 0.18f, themeColor, themeColor));
        return lines;
    }

    private void drawBillboard(Matrix4f matrix, float size, int color) {
        float half = size * 0.5f;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = (color >> 24) & 0xFF;

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        buffer.vertex(matrix, -half, -half, 0).texture(0, 1).color(r, g, b, a);
        buffer.vertex(matrix, -half, half, 0).texture(0, 0).color(r, g, b, a);
        buffer.vertex(matrix, half, half, 0).texture(1, 0).color(r, g, b, a);
        buffer.vertex(matrix, half, -half, 0).texture(1, 1).color(r, g, b, a);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    private int fadeLerp(int start, int end, float progress, float alphaMul) {
        int r = (int) MathHelper.lerp(progress, (start >> 16) & 0xFF, (end >> 16) & 0xFF);
        int g = (int) MathHelper.lerp(progress, (start >> 8) & 0xFF, (end >> 8) & 0xFF);
        int b = (int) MathHelper.lerp(progress, start & 0xFF, end & 0xFF);
        int a = MathHelper.clamp((int) (255 * alphaMul), 0, 255);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private int setAlpha(int color, int alpha) {
        return (MathHelper.clamp(alpha, 0, 255) << 24) | (color & 0x00FFFFFF);
    }

    private int withFullAlpha(int color) {
        return 0xFF000000 | (color & 0x00FFFFFF);
    }

    private float easeOutQuad(float value) {
        float inv = 1.0f - value;
        return 1.0f - inv * inv;
    }

    private static class TotemSphereEffect {
        private final Vec3d origin;
        private final long startTime;
        private final float baseRotation;
        private final List<SphereParticle> particles;
        private final List<OrbitLine> orbitLines;

        private TotemSphereEffect(Vec3d origin, long startTime, float baseRotation, List<SphereParticle> particles, List<OrbitLine> orbitLines) {
            this.origin = origin;
            this.startTime = startTime;
            this.baseRotation = baseRotation;
            this.particles = particles;
            this.orbitLines = orbitLines;
        }
    }

    private static class OrbitLine {
        private final float radiusX;
        private final float radiusZ;
        private final float yOffset;
        private final float startDeg;
        private final float arcDeg;
        private final float tiltX;
        private final float tiltZ;
        private final float speedDeg;
        private final float alphaMul;
        private final int startColor;
        private final int endColor;
        private final float baseYaw;

        private OrbitLine(float radiusX, float radiusZ, float yOffset, float startDeg, float arcDeg, float tiltX, float tiltZ,
                          float speedDeg, float alphaMul, int startColor, int endColor) {
            this.radiusX = radiusX;
            this.radiusZ = radiusZ;
            this.yOffset = yOffset;
            this.startDeg = startDeg;
            this.arcDeg = arcDeg;
            this.tiltX = tiltX;
            this.tiltZ = tiltZ;
            this.speedDeg = speedDeg;
            this.alphaMul = alphaMul;
            this.startColor = startColor;
            this.endColor = endColor;
            this.baseYaw = startDeg * 0.35f;
        }
    }

    private static class SphereParticle {
        private final Vec3d direction;
        private final float spread;
        private final float swirlAmount;
        private final float rotationScale;
        private final float timeScale;
        private final float progressOffset;
        private final int color;

        private SphereParticle(Vec3d direction, float spread, float swirlAmount, float rotationScale, float timeScale, float progressOffset, int color) {
            this.direction = direction;
            this.spread = spread;
            this.swirlAmount = swirlAmount;
            this.rotationScale = rotationScale;
            this.timeScale = timeScale;
            this.progressOffset = progressOffset;
            this.color = color;
        }
    }

    private static class TotemCubeParticle {
        private Vec3d position;
        private Vec3d prevPosition;
        private Vec3d velocity;
        private final double floorY;
        private final float size;
        private final long startTime;
        private final int color;
        private float rotX;
        private float rotY;
        private float rotZ;
        private final float rotSpeedX;
        private final float rotSpeedY;
        private final float rotSpeedZ;

        private TotemCubeParticle(Vec3d position, Vec3d velocity, double floorY, float size, long startTime, int color,
                                  float rotX, float rotY, float rotZ, float rotSpeedX, float rotSpeedY, float rotSpeedZ) {
            this.position = position;
            this.prevPosition = position;
            this.velocity = velocity;
            this.floorY = floorY;
            this.size = size;
            this.startTime = startTime;
            this.color = color;
            this.rotX = rotX;
            this.rotY = rotY;
            this.rotZ = rotZ;
            this.rotSpeedX = rotSpeedX;
            this.rotSpeedY = rotSpeedY;
            this.rotSpeedZ = rotSpeedZ;
        }

        private void update(float dt) {
            prevPosition = position;
            velocity = velocity.add(0.0, -5.8 * dt, 0.0);
            position = position.add(velocity.multiply(dt));

            if (position.y - size < floorY) {
                position = new Vec3d(position.x, floorY + size, position.z);
                if (velocity.y < 0.0) {
                    velocity = new Vec3d(velocity.x * 0.72, -velocity.y * 0.46, velocity.z * 0.72);
                }
            } else {
                velocity = new Vec3d(velocity.x * 0.985, velocity.y, velocity.z * 0.985);
            }

            rotX += rotSpeedX * dt;
            rotY += rotSpeedY * dt;
            rotZ += rotSpeedZ * dt;
        }

        private float alpha(long now, float durationMs) {
            float age = MathHelper.clamp((now - startTime) / durationMs, 0.0f, 1.0f);
            float fadeIn = MathHelper.clamp((now - startTime) / 120.0f, 0.0f, 1.0f);
            return fadeIn * (1.0f - age * age);
        }

        private int renderColor(long now, float durationMs, float alphaMul) {
            int alpha = MathHelper.clamp((int) (255 * alpha(now, durationMs) * alphaMul), 0, 255);
            return (alpha << 24) | (color & 0x00FFFFFF);
        }

        private void transform(MatrixStack matrices, Vec3d cameraPos) {
            matrices.translate(position.x - cameraPos.x, position.y - cameraPos.y, position.z - cameraPos.z);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rotX));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotY));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotZ));
            matrices.scale(size, size, size);
        }

        private void appendFaces(BufferBuilder buffer, MatrixStack matrices, Vec3d cameraPos, long now, float durationMs) {
            int c = renderColor(now, durationMs, 0.34f);
            matrices.push();
            transform(matrices, cameraPos);
            Matrix4f m = matrices.peek().getPositionMatrix();
            addFace(buffer, m, c, -0.5f, -0.5f, -0.5f, 0.5f, -0.5f, -0.5f, 0.5f, 0.5f, -0.5f, -0.5f, 0.5f, -0.5f);
            addFace(buffer, m, c, -0.5f, -0.5f, 0.5f, -0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, -0.5f, 0.5f);
            addFace(buffer, m, c, -0.5f, -0.5f, -0.5f, -0.5f, 0.5f, -0.5f, -0.5f, 0.5f, 0.5f, -0.5f, -0.5f, 0.5f);
            addFace(buffer, m, c, 0.5f, -0.5f, -0.5f, 0.5f, -0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, -0.5f);
            addFace(buffer, m, c, -0.5f, 0.5f, -0.5f, 0.5f, 0.5f, -0.5f, 0.5f, 0.5f, 0.5f, -0.5f, 0.5f, 0.5f);
            addFace(buffer, m, c, -0.5f, -0.5f, -0.5f, -0.5f, -0.5f, 0.5f, 0.5f, -0.5f, 0.5f, 0.5f, -0.5f, -0.5f);
            matrices.pop();
        }

        private void appendLines(BufferBuilder buffer, MatrixStack matrices, Vec3d cameraPos, long now, float durationMs) {
            int c = renderColor(now, durationMs, 1.0f);
            matrices.push();
            transform(matrices, cameraPos);
            Matrix4f m = matrices.peek().getPositionMatrix();
            for (byte[] edge : CUBE_EDGES) {
                buffer.vertex(m, edge[0] * 0.5f, edge[1] * 0.5f, edge[2] * 0.5f).color(c);
                buffer.vertex(m, edge[3] * 0.5f, edge[4] * 0.5f, edge[5] * 0.5f).color(c);
            }
            matrices.pop();
        }

        private void addFace(BufferBuilder buffer, Matrix4f m, int color,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4) {
            buffer.vertex(m, x1, y1, z1).color(color);
            buffer.vertex(m, x2, y2, z2).color(color);
            buffer.vertex(m, x3, y3, z3).color(color);
            buffer.vertex(m, x4, y4, z4).color(color);
        }
    }
}
