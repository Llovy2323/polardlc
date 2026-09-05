package snill.client.client.modules.impl.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import snill.client.api.QClient;
import snill.client.api.events.EventLink;
import snill.client.api.events.Priority;
import snill.client.api.events.implement.Event3DRender;
import snill.client.api.events.implement.EventRender;
import snill.client.api.storages.implement.helpertstorages.enumvar.ModuleClass;
import snill.client.api.utils.animation.Easings;
import snill.client.api.utils.color.ColorUtils;
import snill.client.client.modules.Module;
import snill.client.client.modules.impl.combat.Aura;
import snill.client.client.modules.settings.implement.BooleanSetting;
import snill.client.client.modules.settings.implement.FloatSetting;
import snill.client.client.modules.settings.implement.ModeSetting;

import java.awt.Color;
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;

public class TargetESP extends Module {

    public static TargetESP INSTANCE = new TargetESP();
    private static final float GHOST_ALPHA_MULT = 0.6f;
    private static final float CELKA_SPEED_MULT = 1.2f;
    private static final float SCALE_FACTOR = 0.007f;
    static final long CUBE_ATTACH_LIFE_MS = 560L;
    static final long CUBE_FADE_LIFE_MS = 320L;
    static final int MAX_CUBE_PARTICLES = 72;
    static final byte[][] CUBE_EDGES = {
            {-1, -1, -1, 1, -1, -1}, {1, -1, -1, 1, -1, 1}, {1, -1, 1, -1, -1, 1}, {-1, -1, 1, -1, -1, -1},
            {-1, 1, -1, 1, 1, -1}, {1, 1, -1, 1, 1, 1}, {1, 1, 1, -1, 1, 1}, {-1, 1, 1, -1, 1, -1},
            {-1, -1, -1, -1, 1, -1}, {1, -1, -1, 1, 1, -1}, {1, -1, 1, 1, 1, 1}, {-1, -1, 1, -1, 1, 1}
    };

    private final ModeSetting mode = new ModeSetting("Режим", "Картинка 1", "Картинка 1", "Картинка 2", "Кольцо", "Кольцо 2", "Души", "Целка", "Цепи", "Кубы", "Кристаллы", "Кракен", "Таргет души");
    private final FloatSetting size = new FloatSetting("Размер", 1.15f, 0.6f, 2.5f, 0.05f);
    private final FloatSetting ringRadius = new FloatSetting("Радиус кольца", 0.5f, 0.3f, 1.5f, 0.05f);
    private final FloatSetting ringSpeed = new FloatSetting("Скорость кольца", 1.0f, 0.3f, 3.0f, 0.1f);
    private final FloatSetting rotateSpeed = new FloatSetting("Скорость вращения", 1.2f, 0.2f, 4.0f, 0.05f);
    private final BooleanSetting hurtColor = new BooleanSetting("Окрашивание при ударе", true);
    private final BooleanSetting saturationMode = new BooleanSetting("Режим насыщенности", true);
    private final FloatSetting bmwGhostCount = new FloatSetting("Кол-во призраков", 3.0f, 2.0f, 5.0f, 1.0f);
    private final FloatSetting bmwGhostLife = new FloatSetting("Время жизни (мс)", 350.0f, 150.0f, 500.0f, 25.0f);
    private final FloatSetting bmwStrengthXZ = new FloatSetting("Цикл XZ", 2000.0f, 1000.0f, 5000.0f, 100.0f);
    private final FloatSetting bmwStrengthY = new FloatSetting("Цикл Y", 1700.0f, 1000.0f, 5000.0f, 100.0f);
    private float appearValue = 0f;
    private float scaleValue = 0f;
    private float rotProgress = 0f;
    private float rotFrom = -280f;
    private float rotTo = 280f;
    private long lastRotateUpdate = System.currentTimeMillis();
    private LivingEntity lastTarget = null;
    private LivingEntity lastHandledTarget = null;
    private Vec3d lastTargetPos = null;
    private float lastTargetHeight = 1.8f;
    private float lastTargetWidth = 0.6f;
    private final CopyOnWriteArrayList<GlowPoint> bmwPoints = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<GlowPoint> followGhostPoints = new CopyOnWriteArrayList<>();
    private float crystalRotationAngle = 0f;
    private float crystalAnimation = 0f;
    private float spawnAccumulator = 0f;
    private long lastCubeTime = 0L;
    private final ArrayList<CubeParticle> cubeParticles = new ArrayList<>();
    private final ArrayList<CubeParticle> renderCubeParticles = new ArrayList<>();
    private static final float SPAWN_INTERVAL = 0.022f;
    private static final int PARTICLES_PER_SPAWN = 1;

    // === Ring 2 ===
    private float ring2Angle = 0f;
    private long ring2LastUpdateMs = 0;
    private static final int RING2_GLOW_MAX = 8192;
    private static final long RING2_GLOW_LIFE = 400L;
    private final float[] ring2GlowX = new float[RING2_GLOW_MAX];
    private final float[] ring2GlowY = new float[RING2_GLOW_MAX];
    private final float[] ring2GlowZ = new float[RING2_GLOW_MAX];
    private final long[] ring2GlowTime = new long[RING2_GLOW_MAX];
    private final int[] ring2GlowHue = new int[RING2_GLOW_MAX];
    private int ring2GlowHead = 0;
    private int ring2GlowCount = 0;

    // === Chains ===
    private float chainSpawnAnimation = 0f;
    private Vec3d chainLastRenderPosition = null;
    private LivingEntity chainLastTarget = null;

    public TargetESP() {
        super("TargetESP", "Подсветка и эффекты на цели", ModuleCategory.RENDER);
        mode.addMode("Души 2");
        mode.addMode("Точки");
        size.visible(this::isImageMode);
        rotateSpeed.visible(this::isImageMode);
        saturationMode.visible(() -> isImageMode() || mode.is("Кольцо 2") || mode.is("Цепи"));
        bmwGhostCount.visible(() -> mode.is("Райдер") || mode.is("Таргет души"));
        bmwGhostLife.visible(() -> mode.is("Райдер") || mode.is("Таргет души"));
        bmwStrengthXZ.visible(() -> mode.is("Райдер") || mode.is("Таргет души"));
        bmwStrengthY.visible(() -> mode.is("Райдер") || mode.is("Таргет души"));
        ringRadius.visible(() -> mode.is("Кольцо"));
        ringSpeed.visible(() -> mode.is("Кольцо"));
        addSettings(mode, size, rotateSpeed, hurtColor, saturationMode, ringRadius, ringSpeed, bmwGhostCount, bmwGhostLife, bmwStrengthXZ, bmwStrengthY);
    }

    @Override
    public void onDisable() {
        appearValue = 0f;
        scaleValue = 0f;
        lastTarget = null;
        lastHandledTarget = null;
        lastTargetPos = null;
        rotProgress = 0f;
        rotFrom = -280f;
        rotTo = 280f;
        bmwPoints.clear();
        followGhostPoints.clear();
        crystalRotationAngle = 0f;
        crystalAnimation = 0f;
        spawnAccumulator = 0f;
        lastCubeTime = 0L;
        cubeParticles.clear();
        renderCubeParticles.clear();

        // Reset Ring 2
        ring2Angle = 0f;
        ring2GlowCount = 0;
        ring2GlowHead = 0;
        ring2LastUpdateMs = 0;


        chainSpawnAnimation = 0f;
        chainLastRenderPosition = null;
        chainLastTarget = null;

        super.onDisable();
    }

    private boolean isImageMode() {
        return mode.is("Картинка 1") || mode.is("Картинка 2");
    }

    private Identifier getCaptureTexture() {
        if (mode.is("Картинка 2")) {
            return Identifier.of("snill", "textures/targetesp/targetesp_3.png");
        }
        return Identifier.of("snill", "textures/targetesp/targetesp_2.png");
    }

    private Identifier getBloomTexture() {
        return Identifier.of("snill", "textures/targetesp/bloom.png");
    }

    private int getESPColor() {
        int color = ColorUtils.getThemeColor();
        if (((color >> 24) & 0xFF) == 0) {
            color = color | 0xFF000000;
        }
        return color;
    }

    private float animateTo(float current, float target, float delta) {
        if (current < target) {
            current = Math.min(current + delta, target);
        } else if (current > target) {
            current = Math.max(current - delta, target);
        }
        return current;
    }

    private float getDistanceScale(Vec3d cameraPos, double worldX, double worldY, double worldZ) {
        double dx = worldX - cameraPos.x;
        double dy = worldY - cameraPos.y;
        double dz = worldZ - cameraPos.z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        return (float) Math.max(0.1, distance * SCALE_FACTOR);
    }

    @EventLink(priority = Priority.LOW)
    public void onRender3D(Event3DRender event) {
        if (mc == null || mc.player == null || mc.world == null) return;

        Aura aura = ModuleClass.aura;
        boolean auraEnabled = aura != null && aura.isEnable();
        LivingEntity target = auraEnabled ? aura.getTarget() : null;
        boolean hasTarget = target != null && target.isAlive();
        float speed = 0.05f;
        appearValue = animateTo(appearValue, hasTarget ? 1f : 0f, speed);
        scaleValue = animateTo(scaleValue, hasTarget ? 1f : 0.5f, speed);

        if (hasTarget) {
            this.lastTarget = target;
            this.lastHandledTarget = target;
        }
        if (isCrystalMode()) {
            float crystalSpeed = hasTarget ? 0.045f : 0.03f;
            crystalAnimation = animateTo(crystalAnimation, hasTarget ? 1f : 0f, crystalSpeed);
            if (hasTarget) {
                crystalRotationAngle += 0.35f;
            }
        }

        if (appearValue <= 0.001f && !hasTarget) {
            if (!isCrystalMode() || crystalAnimation <= 0.001f) {
                lastTarget = null;
                lastTargetPos = null;
                return;
            }
        }
        if (hasTarget && target != null) {
            float td = event.getTickDelta();
            lastTargetPos = new Vec3d(
                    MathHelper.lerp(td, target.lastRenderX, target.getX()),
                    MathHelper.lerp(td, target.lastRenderY, target.getY()),
                    MathHelper.lerp(td, target.lastRenderZ, target.getZ())
            );
            lastTargetHeight = target.getHeight();
            lastTargetWidth = target.getWidth();
        }
        if (lastTargetPos == null) return;

        if (mode.is("Райдер")) {
            if (hasTarget && target != null) {
                addBMWGhosts(target, event.getTickDelta(),
                        Math.max(1, Math.round(bmwGhostCount.getValue().floatValue())),
                        Math.max(1, Math.round(bmwGhostLife.getValue().floatValue())),
                        getESPColor());
            }
            bmwPoints.removeIf(GlowPoint::shouldRemove);
            drawBMW3D(event);
            return;
        }
        if (mode.is("Кристаллы")) {
            LivingEntity crystalTarget = hasTarget ? target : lastTarget;
            if ((crystalTarget != null || lastTargetPos != null) && crystalAnimation > 0.01f) {
                renderCrystals3D(event.getMatrices(), crystalTarget, event.getTickDelta());
            }
            return;
        }
        if (mode.is("Таргет души")) {
            renderFollowingGhosts(event, target, hasTarget);
            return;
        }
        if (isImageMode()) {
            renderMarker3D(event);
        }
        if (mode.is("Точки")) {
            drawTargetPoints3D(event);
        }
        if (mode.is("Души 2")) {
            drawSouls2_3D(event);
        }
        if (mode.is("Души")) {
            drawSouls3D(event);
        }
        if (mode.is("Призраки")) {
            drawCelka3D(event);
        }
        if (mode.is("Кольцо")) {
            drawRing3D(event);
        }
        if (mode.is("Кольцо 2")) {
            drawRing2_3D(event);
        }
        if (mode.is("Целка")) {
            drawCelka2_3D(event);
        }
        if (mode.is("Цепи")) {
            drawChains3D(event, target, hasTarget);
        }
        if (mode.is("Кубы")) {
            renderCubes(event, target, hasTarget);
        }
        if (mode.is("Кракен")) {
            renderKraken(event, target, hasTarget);
        }
    }

    private void renderKraken(Event3DRender event, LivingEntity target, boolean hasTarget) {
        if (appearValue <= 0.001f || lastTargetPos == null) return;

        LivingEntity renderTarget = hasTarget ? target : lastTarget;
        Vec3d targetPos;
        float targetHeight;
        if (renderTarget != null && renderTarget.isAlive()) {
            float tickDelta = event.getTickDelta();
            targetPos = new Vec3d(
                    MathHelper.lerp(tickDelta, renderTarget.lastRenderX, renderTarget.getX()),
                    MathHelper.lerp(tickDelta, renderTarget.lastRenderY, renderTarget.getY()),
                    MathHelper.lerp(tickDelta, renderTarget.lastRenderZ, renderTarget.getZ())
            );
            targetHeight = renderTarget.getHeight();
        } else {
            targetPos = lastTargetPos;
            targetHeight = lastTargetHeight;
        }

        Vec3d cam = mc.gameRenderer.getCamera().getPos();
        float hurtPC = getHurtPC(renderTarget);
        int colorA = overCol(multAlpha(ColorUtils.getThemeColor(0), appearValue), multAlpha(getSoftRedColor(), appearValue), hurtPC);
        int colorB = overCol(multAlpha(ColorUtils.getThemeColor(90), appearValue), multAlpha(getSoftRedColor(), appearValue), hurtPC);

        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, getBloomTexture());

        final int tentacles = 4;
        final int particlesPerTentacle = 70;
        final float maxHeight = targetHeight * 1.2f;
        final float baseRadius = 0.3f;
        final float baseParticleSize = 0.7f;
        final float staticBend = 0.4f;
        final long wobbleCycleMs = 600L;
        final float wobbleAmplitude = 0.45f;

        long time = System.currentTimeMillis();
        double globalRotation = time * 0.0005;
        for (int i = 0; i < tentacles; i++) {
            long wobbleTimer = time + (i * wobbleCycleMs / 3);
            double wobbleAngle = (wobbleTimer % wobbleCycleMs) / (double) wobbleCycleMs * Math.PI * 2.0;
            double wobbleFactor = Math.sin(wobbleAngle);
            double baseAngle = ((double) i / tentacles * Math.PI * 2.0) + (Math.PI / 4.0) + globalRotation;
            Vec3d radialDir = new Vec3d(Math.cos(baseAngle), 0.0, Math.sin(baseAngle));
            Vec3d tangentialDir = new Vec3d(-radialDir.z, 0.0, radialDir.x);

            for (int j = 0; j < particlesPerTentacle; j++) {
                float progress = j / (float) (particlesPerTentacle - 1);
                double staticCurve = Math.sin(progress * Math.PI) * staticBend;
                double baseX = targetPos.x + radialDir.x * (baseRadius + staticCurve);
                double baseZ = targetPos.z + radialDir.z * (baseRadius + staticCurve);
                double y = targetPos.y + progress * maxHeight;
                double wobbleStrength = progress * progress;
                double offsetX = tangentialDir.x * wobbleFactor * wobbleAmplitude * wobbleStrength;
                double offsetZ = tangentialDir.z * wobbleFactor * wobbleAmplitude * wobbleStrength;
                float sharpness = (float) Math.pow(1.0f - progress, 1.5f);
                float particleSize = baseParticleSize * sharpness / 1.5f;
                if (particleSize < 0.05f) continue;

                float alpha = (1.0f - progress * 0.5f) * appearValue;
                int color = setAlpha(overCol(colorA, colorB, progress), alpha);
                drawStaticBillboard(event.getMatrices(), cam, baseX + offsetX, y, baseZ + offsetZ, particleSize, color, 0.0f);
            }
        }

        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
    }

    private void renderCubes(Event3DRender event, LivingEntity target, boolean hasTarget) {
        long now = System.currentTimeMillis();
        if (lastCubeTime == 0L) lastCubeTime = now;
        float dt = Math.min((now - lastCubeTime) / 1000.0f, 0.1f);
        lastCubeTime = now;
        if (!Float.isFinite(dt) || mc.gameRenderer == null || mc.gameRenderer.getCamera() == null) {
            return;
        }

        if (hasTarget && target != null) {
            lastTarget = target;
            spawnAccumulator += dt;
            while (spawnAccumulator >= SPAWN_INTERVAL) {
                spawnAccumulator -= SPAWN_INTERVAL;
                if (cubeParticles.size() >= MAX_CUBE_PARTICLES) {
                    break;
                }
                for (int i = 0; i < PARTICLES_PER_SPAWN; i++) {
                    double rand = Math.random() * 360.0;
                    double px = Math.cos(Math.toRadians(rand)) * 0.7;
                    double py = 0.02 + Math.random() * 0.10;
                    double pz = Math.sin(Math.toRadians(rand)) * 0.7;
                    cubeParticles.add(new CubeParticle(target, px, py, pz));
                }
            }
        } else {
            spawnAccumulator = 0f;
        }

        renderCubeParticles.clear();
        for (int i = cubeParticles.size() - 1; i >= 0; i--) {
            CubeParticle particle = cubeParticles.get(i);
            try {
                particle.update(dt, now, hasTarget ? target : null);
                if (particle.shouldRemove(now)) {
                    cubeParticles.remove(i);
                } else {
                    renderCubeParticles.add(particle);
                }
            } catch (Throwable ignored) {
                cubeParticles.remove(i);
            }
        }

        if (renderCubeParticles.isEmpty()) {
            return;
        }

        float partialTicks = event.getTickDelta();
        MatrixStack matrices = event.getMatrices();
        Vec3d camPos = mc.gameRenderer.getCamera().getPos();
        LivingEntity colorTarget = hasTarget ? target : lastTarget;
        float hurtPC = getHurtPC(colorTarget);
        int baseColor = getESPColor();
        int redColor = getSoftRedColor();

        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        BufferBuilder faceBuilder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        boolean hasFaces = false;
        for (int i = 0, size = renderCubeParticles.size(); i < size; i++) {
            CubeParticle particle = renderCubeParticles.get(i);
            try {
                int particleColor = particle.getRenderColor(baseColor, redColor, hurtPC, now);
                if (((particleColor >> 24) & 0xFF) <= 0) continue;
                if (particle.appendCubeFaces(faceBuilder, matrices, camPos, partialTicks, particleColor)) {
                    hasFaces = true;
                }
            } catch (Throwable ignored) {
            }
        }
        if (hasFaces) BufferRenderer.drawWithGlobalProgram(faceBuilder.end());

        BufferBuilder lineBuilder = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        boolean hasLines = false;
        for (int i = 0, size = renderCubeParticles.size(); i < size; i++) {
            CubeParticle particle = renderCubeParticles.get(i);
            try {
                int particleColor = particle.getRenderColor(baseColor, redColor, hurtPC, now);
                if (((particleColor >> 24) & 0xFF) <= 0) continue;
                if (particle.appendCubeLines(lineBuilder, matrices, camPos, partialTicks, particleColor)) {
                    hasLines = true;
                }
            } catch (Throwable ignored) {
            }
        }
        if (hasLines) BufferRenderer.drawWithGlobalProgram(lineBuilder.end());

        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, getBloomTexture());
        BufferBuilder bloomBuilder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        boolean hasBloom = false;
        float camYaw = mc.gameRenderer.getCamera().getYaw();
        float camPitch = mc.gameRenderer.getCamera().getPitch();
        for (int i = 0, size = renderCubeParticles.size(); i < size; i++) {
            CubeParticle particle = renderCubeParticles.get(i);
            try {
                int particleColor = particle.getRenderColor(baseColor, redColor, hurtPC, now);
                if (particle.appendBloom(bloomBuilder, matrices, camPos, camYaw, camPitch, partialTicks, particleColor, now)) {
                    hasBloom = true;
                }
            } catch (Throwable ignored) {
            }
        }
        if (hasBloom) BufferRenderer.drawWithGlobalProgram(bloomBuilder.end());

        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
    }

    private void drawRing3D(Event3DRender event) {
        if (appearValue <= 0.001f || lastTargetPos == null) return;

        float partialTicks = mc.getRenderTickCounter().getTickDelta(true);
        Vec3d vec;
        float entityHeight;
        LivingEntity target = lastTarget;

        if (target != null && target.isAlive()) {
            vec = new Vec3d(
                    MathHelper.lerp(partialTicks, target.lastRenderX, target.getX()),
                    MathHelper.lerp(partialTicks, target.lastRenderY, target.getY()),
                    MathHelper.lerp(partialTicks, target.lastRenderZ, target.getZ())
            );
            entityHeight = target.getHeight();
        } else {
            vec = lastTargetPos;
            entityHeight = lastTargetHeight;
        }

        Vec3d cam = mc.gameRenderer.getCamera().getPos();
        double x = vec.x - cam.x;
        double y = vec.y - cam.y;
        double z = vec.z - cam.z;

        double duration = 2000.0 / ringSpeed.get();
        double elapsed = (double) (System.currentTimeMillis() % (long) duration);
        boolean side = elapsed > duration / 2.0;
        double progress = elapsed / (duration / 2.0);

        if (side) {
            progress -= 1.0;
        } else {
            progress = 1.0 - progress;
        }

        progress = progress < 0.5 ? 2.0 * progress * progress : 1.0 - Math.pow(-2.0 * progress + 2.0, 2.0) / 2.0;
        double eased = (double) entityHeight / 1.2 * (progress > 0.5 ? 1.0 - progress : progress) * (double) (side ? -1 : 1);

        int baseCol = getESPColor();
        float hurtPC = getHurtPC(target);
        int redCol = getSoftRedColor();
        int mainColor = overCol(baseCol, redCol, hurtPC);

        int colorWithAlpha = setAlpha(mainColor, 225.0f / 255.0f * appearValue);
        int colorTransparent = setAlpha(mainColor, 1.0f / 255.0f * appearValue);
        int colorFull = setAlpha(mainColor, appearValue);
        double radius = ringRadius.get();

        MatrixStack matrices = event.getMatrices();
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        RenderSystem.disableCull();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
        for (int i = 0; i <= 360; i++) {
            double rad = Math.toRadians(i);
            float px = (float) (x + Math.cos(rad) * radius);
            float pz = (float) (z + Math.sin(rad) * radius);
            float py1 = (float) (y + entityHeight * progress);
            float py2 = (float) (y + entityHeight * progress + eased);

            buffer.vertex(matrix, px, py1, pz).color(colorWithAlpha);
            buffer.vertex(matrix, px, py2, pz).color(colorTransparent);
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.lineWidth(1.5f);
        BufferBuilder lineBuffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINE_STRIP, VertexFormats.POSITION_COLOR);
        for (int i = 0; i <= 360; i++) {
            double rad = Math.toRadians(i);
            float px = (float) (x + Math.cos(rad) * radius);
            float pz = (float) (z + Math.sin(rad) * radius);
            float py = (float) (y + entityHeight * progress);

            lineBuffer.vertex(matrix, px, py, pz).color(colorFull);
        }
        BufferRenderer.drawWithGlobalProgram(lineBuffer.end());

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    private int setAlpha(int color, float alpha) {
        alpha = Math.max(0.0f, Math.min(1.0f, alpha));
        return (color & 0x00FFFFFF) | ((int) (alpha * 255) << 24);
    }


    @EventLink(priority = Priority.LOW)
    public void onRender2D(EventRender.Default event) {
        if (!mode.is("Кристаллы") || crystalAnimation <= 0.001f || lastTargetPos == null) return;
        LivingEntity crystalTarget = lastTarget != null && lastTarget.isAlive() ? lastTarget : null;
        drawCrystalGlow2D(event.getContext().getMatrices(), crystalTarget);
    }

    private int multAlpha(int color, float mult) {
        int a = (int) (((color >> 24) & 0xFF) * mult);
        a = Math.max(0, Math.min(255, a));
        return (a << 24) | (color & 0x00FFFFFF);
    }

    private int replAlpha(int color, int alpha) {
        alpha = Math.max(0, Math.min(255, alpha));
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    int overCol(int color1, int color2, float factor) {
        factor = Math.max(0f, Math.min(1f, factor));
        int r1 = (color1 >> 16) & 0xFF, g1 = (color1 >> 8) & 0xFF, b1 = color1 & 0xFF, a1 = (color1 >> 24) & 0xFF;
        int r2 = (color2 >> 16) & 0xFF, g2 = (color2 >> 8) & 0xFF, b2 = color2 & 0xFF, a2 = (color2 >> 24) & 0xFF;
        int r = (int) (r1 + (r2 - r1) * factor);
        int g = (int) (g1 + (g2 - g1) * factor);
        int b = (int) (b1 + (b2 - b1) * factor);
        int a = (int) (a1 + (a2 - a1) * factor);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private int getSoftRedColor() {
        // Нежный красный с белым оттенком - светло-красный (255, 180, 180)
        return ColorUtils.rgb(255, 180, 180);
    }

    private float getHurtPC(LivingEntity target) {
        if (!hurtColor.isState() || target == null) return 0f;
        float partialTicks = mc != null ? mc.getRenderTickCounter().getTickDelta(true) : 0f;
        float hurtTicks = MathHelper.clamp(target.hurtTime - partialTicks, 0.0f, 10.0f);
        float progress = hurtTicks / 10.0f;
        return progress * progress * (3.0f - 2.0f * progress);
    }

    private void drawBillboard(MatrixStack matrices, Vec3d cameraPos, double worldX, double worldY, double worldZ, float baseScreenSize, int color, float rotation) {
        float distScale = getDistanceScale(cameraPos, worldX, worldY, worldZ);
        float half = baseScreenSize * distScale * 0.5f;
        drawBillboardInternal(matrices, cameraPos, worldX, worldY, worldZ, half, color, rotation);
    }

    private void drawStaticBillboard(MatrixStack matrices, Vec3d cameraPos, double worldX, double worldY, double worldZ, float worldSize, int color, float rotation) {
        float half = worldSize * 0.5f;
        drawBillboardInternal(matrices, cameraPos, worldX, worldY, worldZ, half, color, rotation);
    }

    private void drawBillboardInternal(MatrixStack matrices, Vec3d cameraPos, double worldX, double worldY, double worldZ, float half, int color, float rotation) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = (color >> 24) & 0xFF;
        if (a <= 0) return;

        matrices.push();
        matrices.translate(worldX - cameraPos.x, worldY - cameraPos.y, worldZ - cameraPos.z);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-mc.gameRenderer.getCamera().getYaw()));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(mc.gameRenderer.getCamera().getPitch()));
        if (rotation != 0) {
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotation));
        }

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        buffer.vertex(matrix, -half, -half, 0).texture(0, 1).color(r, g, b, a);
        buffer.vertex(matrix, -half, half, 0).texture(0, 0).color(r, g, b, a);
        buffer.vertex(matrix, half, half, 0).texture(1, 0).color(r, g, b, a);
        buffer.vertex(matrix, half, -half, 0).texture(1, 1).color(r, g, b, a);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        matrices.pop();
    }

    private void renderMarker3D(Event3DRender event) {
        if (lastTargetPos == null || appearValue <= 0.001f) return;

        Vec3d cam = mc.gameRenderer.getCamera().getPos();
        double worldX = lastTargetPos.x;
        double worldY = lastTargetPos.y + ((lastTargetHeight + 0.4f) * 0.5f);
        double worldZ = lastTargetPos.z;

        float baseSize = size.getValue().floatValue() * 12f;
        float renderSize = baseSize * scaleValue;

        long now = System.currentTimeMillis();
        float dt = Math.max(0.001f, (now - lastRotateUpdate) / 1000f);
        lastRotateUpdate = now;
        float cycleDuration = Math.max(0.35f, 2.2f / rotateSpeed.getValue().floatValue());
        rotProgress += dt / cycleDuration;
        while (rotProgress >= 1f) {
            rotProgress -= 1f;
            rotFrom = rotTo;
            rotTo = rotTo > 0f ? -280f : 280f;
        }

        float accel = (float) Easings.SINE_IN_OUT.ease(rotProgress);
        float rotation = MathHelper.lerp(accel, rotFrom, rotTo);

        float hurtPC = getHurtPC(lastTarget);
        int baseColor = multAlpha(getESPColor(), appearValue);
        int redColor = multAlpha(getSoftRedColor(), appearValue);
        int color = overCol(baseColor, redColor, hurtPC);

        RenderSystem.enableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, getCaptureTexture());

        // Рисуем основной слой
        drawBillboard(event.getMatrices(), cam, worldX, worldY, worldZ, renderSize, color, rotation);

        // Если режим насыщенности включен, рисуем второй слой
        if (saturationMode.isState()) {
            // Второй слой с той же непрозрачностью для полной насыщенности
            drawBillboard(event.getMatrices(), cam, worldX, worldY, worldZ, renderSize, color, rotation);
        }

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private void drawSouls3D(Event3DRender event) {
        if (appearValue <= 0.001f || lastTargetPos == null) return;

        float partialTicks = mc.getRenderTickCounter().getTickDelta(true);
        Vec3d vec;
        float height;
        LivingEntity target = lastTarget;
        if (target != null && target.isAlive()) {
            vec = new Vec3d(
                    MathHelper.lerp(partialTicks, target.lastRenderX, target.getX()),
                    MathHelper.lerp(partialTicks, target.lastRenderY, target.getY()),
                    MathHelper.lerp(partialTicks, target.lastRenderZ, target.getZ())
            );
            height = target.getHeight();
        } else {
            vec = lastTargetPos;
            height = lastTargetHeight;
        }

        Vec3d cam = mc.gameRenderer.getCamera().getPos();
        double baseX = vec.x;
        double baseY = vec.y + (height / 2.0f);
        double baseZ = vec.z;
        double radius = 0.7;
        float fixedSize = 4.0f;
        long time = System.currentTimeMillis();
        float hurtPC = getHurtPC(target);
        int baseCol = getESPColor();
        int redCol = getSoftRedColor();

        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, getBloomTexture());

        MatrixStack matrices = event.getMatrices();

        for (int i = 0; i < 20; i++) {
            float trailFactor = 1.0f - (float) i / 20.0f * 0.7f;
            double angle = 0.15 * (time - i * 10.0) / 25.0;
            double s = Math.sin(angle) * radius;
            double c = Math.cos(angle) * radius;
            double worldX = baseX + s;
            double worldY = baseY + c;
            double worldZ = baseZ - c;

            float sz = fixedSize * trailFactor;
            float alphaTrail = appearValue * GHOST_ALPHA_MULT;
            int col = multAlpha(baseCol, alphaTrail * appearValue);
            int red = multAlpha(redCol, alphaTrail * appearValue);
            int color = overCol(col, red, hurtPC);

            drawStaticBillboard(matrices, cam, worldX, worldY, worldZ, sz * 0.12f, color, 0);
            int glowColor = multAlpha(color, 0.45f);
            drawStaticBillboard(matrices, cam, worldX, worldY, worldZ, sz * 0.21f, glowColor, 0);
        }

        for (int i = 0; i < 20; i++) {
            float trailFactor = 1.0f - (float) i / 20.0f * 0.7f;
            double angle = 0.15 * (time - i * 10.0) / 25.0;
            double s = Math.sin(angle) * radius;
            double c = Math.cos(angle) * radius;
            double worldX = baseX - s;
            double worldY = baseY + s;
            double worldZ = baseZ - c;

            float sz = fixedSize * trailFactor;
            float alphaTrail = appearValue * GHOST_ALPHA_MULT;
            int col = multAlpha(baseCol, alphaTrail * appearValue);
            int red = multAlpha(getSoftRedColor(), alphaTrail * appearValue);
            int color = overCol(col, red, hurtPC);

            drawStaticBillboard(matrices, cam, worldX, worldY, worldZ, sz * 0.12f, color, 0);
            int glowColor = multAlpha(color, 0.45f);
            drawStaticBillboard(matrices, cam, worldX, worldY, worldZ, sz * 0.21f, glowColor, 0);
        }

        for (int i = 0; i < 20; i++) {
            float trailFactor = 1.0f - (float) i / 20.0f * 0.7f;
            double angle = 0.15 * (time - i * 10.0) / 25.0;
            double s = Math.sin(angle) * radius;
            double c = Math.cos(angle) * radius;
            double worldX = baseX - s;
            double worldY = baseY - s;
            double worldZ = baseZ + c;

            float sz = fixedSize * trailFactor;
            float alphaTrail = appearValue * GHOST_ALPHA_MULT;
            int col = multAlpha(baseCol, alphaTrail * appearValue);
            int red = multAlpha(redCol, alphaTrail * appearValue);
            int color = overCol(col, red, hurtPC);

            drawStaticBillboard(matrices, cam, worldX, worldY, worldZ, sz * 0.12f, color, 0);
            int glowColor = multAlpha(color, 0.45f);
            drawStaticBillboard(matrices, cam, worldX, worldY, worldZ, sz * 0.21f, glowColor, 0);
        }

        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
    }

    private void drawTargetPoints3D(Event3DRender event) {
        if (appearValue <= 0.001f || lastTargetPos == null) return;

        LivingEntity target = lastTarget;
        Vec3d position = lastTargetPos;
        float height = lastTargetHeight;
        if (target != null && target.isAlive()) {
            float delta = event.getTickDelta();
            position = new Vec3d(
                    MathHelper.lerp(delta, target.lastRenderX, target.getX()),
                    MathHelper.lerp(delta, target.lastRenderY, target.getY()),
                    MathHelper.lerp(delta, target.lastRenderZ, target.getZ())
            );
            height = target.getHeight();
        }

        float smooth = appearValue * appearValue * (3f - 2f * appearValue);
        int theme = getESPColor();
        int hit = getSoftRedColor();
        int color = overCol(multAlpha(theme, smooth), multAlpha(hit, smooth), getHurtPC(target));
        Vec3d camera = event.getCamera().getPos();

        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, getBloomTexture());

        double x = position.x;
        double z = position.z;
        double[] heights = {height * .87D, height * .52D, height * .16D};
        for (double yOffset : heights) {
            double y = position.y + yOffset;
            drawStaticBillboard(event.getMatrices(), camera, x, y, z, .82f * smooth, multAlpha(color, .30f), 0f);
            drawStaticBillboard(event.getMatrices(), camera, x, y, z, .32f * smooth, color, 0f);
        }

        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
    }

    /** Port of Nuclear's thin ghosts effect, exposed here as "Души 2". */
    private void drawSouls2_3D(Event3DRender event) {
        if (appearValue <= 0.001f || lastTargetPos == null) return;

        LivingEntity target = lastTarget;
        Vec3d position = lastTargetPos;
        float height = lastTargetHeight;
        if (target != null && target.isAlive()) {
            float delta = event.getTickDelta();
            position = new Vec3d(
                    MathHelper.lerp(delta, target.lastRenderX, target.getX()),
                    MathHelper.lerp(delta, target.lastRenderY, target.getY()),
                    MathHelper.lerp(delta, target.lastRenderZ, target.getZ())
            );
            height = target.getHeight();
        }

        Vec3d camera = event.getCamera().getPos();
        float spin = System.currentTimeMillis() * 45f / 4800f;
        float hurt = getHurtPC(target);

        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, getBloomTexture());

        int sprites = 17;
        for (int layer = 0; layer < 3; layer++) {
            int layerIndex = layer * 3;
            int phaseOffset = layerIndex * layerIndex;
            for (int index = 0; index < sprites; index++) {
                float phase = spin + index * 0.1f;
                double x = position.x + .8f * MathHelper.sin(phase + phaseOffset);
                double y = position.y + 0.5f + 0.3f * MathHelper.sin(spin + index * 0.2f) + 0.2f * layerIndex;
                double z = position.z + .8f * MathHelper.cos(phase - phaseOffset);
                float falloff = Math.max(0f, 1f - index / (float) sprites * 14f / 20f);
                int alpha = Math.round(appearValue * 255f * falloff);
                int color = overCol(ColorUtils.setAlphaColor(getESPColor(), alpha), ColorUtils.setAlphaColor(ColorUtils.rgb(255, 50, 50), alpha), hurt);
                float fullSize = .15f * 0.5f + index / 18000f;
                drawStaticBillboard(event.getMatrices(), camera, x, y, z, fullSize, color, 0f);
            }
        }

        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
    }

    private void addBMWGhosts(LivingEntity entity, float partialTicks, int cornersCount, int maxTime, int colorBase) {
        float xzRange = 0.7f;
        float yRange = entity.getHeight();
        int delayXZ = (int) bmwStrengthXZ.getValue().floatValue();
        int delayY = (int) bmwStrengthY.getValue().floatValue();
        long time = System.currentTimeMillis();
        float rotateProgress = (float) (time % delayXZ) / (float) delayXZ;
        float xzRotate = rotateProgress * 360.0f;
        float yProgress = (float) (time % delayY) / (float) delayY;
        float yLrpPC = 0.5f - 0.5f * MathHelper.cos(yProgress * (float) (Math.PI * 2.0));

        for (int corner = 0; corner < cornersCount; corner++) {
            float cornersPC = (float) corner / (float) cornersCount;
            double yawRad = Math.toRadians(MathHelper.wrapDegrees(cornersPC * 360.0f + xzRotate));
            float offsetX = -(float) Math.sin(yawRad) * xzRange;
            float offsetY = yRange * yLrpPC;
            float offsetZ = (float) Math.cos(yawRad) * xzRange;
            bmwPoints.add(new GlowPoint(offsetX, offsetY, offsetZ, maxTime, colorBase));
        }
    }

    private void drawBMW3D(Event3DRender event) {
        if (bmwPoints.isEmpty() || appearValue <= 0.001f) return;

        LivingEntity renderTarget = lastTarget != null ? lastTarget : lastHandledTarget;
        if (renderTarget == null && lastTargetPos == null) return;

        float partialTicks = mc.getRenderTickCounter().getTickDelta(true);
        Vec3d basePos;
        if (renderTarget != null && renderTarget.isAlive()) {
            basePos = new Vec3d(
                    MathHelper.lerp(partialTicks, renderTarget.lastRenderX, renderTarget.getX()),
                    MathHelper.lerp(partialTicks, renderTarget.lastRenderY, renderTarget.getY()),
                    MathHelper.lerp(partialTicks, renderTarget.lastRenderZ, renderTarget.getZ())
            );
        } else {
            basePos = lastTargetPos;
        }

        if (basePos == null) return;

        Vec3d cam = mc.gameRenderer.getCamera().getPos();
        float hurtPC = getHurtPC(renderTarget);
        float fixedScreenSize = 6f;

        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, getBloomTexture());

        MatrixStack matrices = event.getMatrices();

        for (GlowPoint point : bmwPoints) {
            float timePC = point.getTimeProgress();
            float trailFactor = 1.0f - timePC * 0.6f;

            double worldX = basePos.x + point.x;
            double worldY = basePos.y + point.y;
            double worldZ = basePos.z + point.z;

            float sz = fixedScreenSize * trailFactor;
            int alpha = (int) (255 * appearValue * trailFactor * 0.8f);
            alpha = Math.max(0, Math.min(255, alpha));
            int col = replAlpha(point.baseColor, alpha);
            int red = replAlpha(getSoftRedColor(), alpha);
            int finalColor = overCol(col, red, hurtPC);

            drawBillboard(matrices, cam, worldX, worldY, worldZ, sz, finalColor, 0);
        }

        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
    }

    private void drawCelka3D(Event3DRender event) {
        if (appearValue <= 0.001f || lastTargetPos == null) return;

        float partialTicks = mc.getRenderTickCounter().getTickDelta(true);
        Vec3d vec;
        float entityHeight;
        LivingEntity target = lastTarget;
        if (target != null && target.isAlive()) {
            vec = new Vec3d(
                    MathHelper.lerp(partialTicks, target.lastRenderX, target.getX()),
                    MathHelper.lerp(partialTicks, target.lastRenderY, target.getY()),
                    MathHelper.lerp(partialTicks, target.lastRenderZ, target.getZ())
            );
            entityHeight = target.getHeight();
        } else {
            vec = lastTargetPos;
            entityHeight = lastTargetHeight;
        }

        Vec3d cam = mc.gameRenderer.getCamera().getPos();
        double bx = vec.x;
        double by = vec.y;
        double bz = vec.z;
        double t = (System.currentTimeMillis() / 384.61539872299335) * CELKA_SPEED_MULT;
        double tv = (System.currentTimeMillis() / 666.6666666666666) * CELKA_SPEED_MULT;
        int baseCol = getESPColor();
        float fixedSize = 4.0f;

        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, getBloomTexture());

        MatrixStack matrices = event.getMatrices();

        float radius = 0.65f;
        for (int k = 0; k < 4; k++) {
            for (int j = 0; j < 20; j++) {
                float kf = (float) j / 20.0f;
                float sizeFactor = 1.0f - kf * 0.55f;

                double tj = t - j * 0.05;
                double tvj = tv - j * 0.05;
                double cyc = (Math.sin(tvj) + 1.0) * 0.5;

                double baseAngle = Math.toRadians(k * 90.0 + (tj * 50.0) % 360.0);
                double offX = Math.cos(baseAngle) * radius;
                double offZ = Math.sin(baseAngle) * radius;
                double offY = k % 2 == 0
                        ? 0.1 + 1.7 * cyc
                        : 1.8 - 1.7 * cyc;

                double worldX = bx + offX;
                double worldY = by + offY;
                double worldZ = bz + offZ;

                float sz = fixedSize * sizeFactor;
                int finalAlpha = (int) (255.0f * appearValue * GHOST_ALPHA_MULT);
                int color = replAlpha(baseCol, finalAlpha);

                drawBillboard(matrices, cam, worldX, worldY, worldZ, sz, color, 0);
                int glowColor = multAlpha(color, 0.45f);
                drawBillboard(matrices, cam, worldX, worldY, worldZ, sz * 1.75f, glowColor, 0);
            }
            radius *= -1.0f;
        }

        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
    }


    private void renderCrystals3D(MatrixStack ms, LivingEntity target, float partialTicks) {
        if (lastTargetPos == null || crystalAnimation <= 0.01f) return;

        Vec3d cameraPos = mc.gameRenderer.getCamera().getPos();
        int baseColor = ColorUtils.getThemeColor();
        int color = multAlpha(baseColor, crystalAnimation);
        int glowColor = multAlpha(baseColor, crystalAnimation * 0.28f);
        float hurtPC = getHurtPC(target);
        if (hurtPC > 0.0f) {
            int hurtColor = multAlpha(getSoftRedColor(), crystalAnimation);
            color = overCol(color, hurtColor, hurtPC);
            glowColor = overCol(glowColor, multAlpha(hurtColor, 0.65f), hurtPC);
        }

        float entityWidth = target != null ? target.getWidth() : lastTargetWidth;
        float entityHeight = target != null ? target.getHeight() : lastTargetHeight;
        float width = entityWidth * 1.5f;

        Vec3d renderPos;
        if (target != null && target.isAlive()) {
            renderPos = new Vec3d(
                    MathHelper.lerp(partialTicks, target.lastRenderX, target.getX()),
                    MathHelper.lerp(partialTicks, target.lastRenderY, target.getY()),
                    MathHelper.lerp(partialTicks, target.lastRenderZ, target.getZ())
            );
        } else {
            renderPos = lastTargetPos;
        }

        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();

        float orbitScale = 1.2f - 0.5f * crystalAnimation;
        ms.push();
        ms.translate(renderPos.x - cameraPos.x, renderPos.y - cameraPos.y, renderPos.z - cameraPos.z);

        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        for (int i = 0; i < 360; i += 20) {
            float angleRad = (float) Math.toRadians(i + crystalRotationAngle);
            float sin = (float) (Math.sin(angleRad) * width * orbitScale);
            float cos = (float) (Math.cos(angleRad) * width * orbitScale);
            float crystalSize = 0.1f;
            float yOffset = 0.1f + entityHeight * Math.abs(MathHelper.sin(i));

            float offsetX = sin;
            float offsetY = yOffset;
            float offsetZ = cos;
            float targetCenterY = entityHeight / 2.0f;
            float dirX = -offsetX;
            float dirY = targetCenterY - offsetY;
            float dirZ = -offsetZ;

            float length = (float) Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
            if (length < 0.001f) continue;

            dirX /= length;
            dirY /= length;
            dirZ /= length;
            ms.push();
            ms.translate(offsetX, offsetY, offsetZ);
            Vector3f initial = new Vector3f(0, 1, 0);
            Vector3f dir = new Vector3f(dirX, dirY, dirZ);
            Vector3f axis = new Vector3f();
            initial.cross(dir, axis);
            float axisLen = axis.length();
            if (axisLen >= 0.001f) {
                axis.div(axisLen);
                float dot = Math.max(-1f, Math.min(1f, initial.dot(dir)));
                float angle = (float) Math.acos(dot);
                ms.multiply(new Quaternionf().setAngleAxis(angle, axis.x, axis.y, axis.z));
            }
            renderCrystalShape(buffer, ms.peek().getPositionMatrix(), crystalSize, color);

            ms.pop();
        }

        BufferRenderer.drawWithGlobalProgram(buffer.end());

        ms.pop();

        float glowBaseSize = 4.5f + entityWidth * 3.0f;
        float outerGlowSize = glowBaseSize * 1.28f;
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, getBloomTexture());

        for (int i = 0; i < 360; i += 20) {
            float angleRad = (float) Math.toRadians(i + crystalRotationAngle);
            float sin = (float) (Math.sin(angleRad) * width * orbitScale);
            float cos = (float) (Math.cos(angleRad) * width * orbitScale);
            float yOffset = 0.1f + entityHeight * Math.abs(MathHelper.sin(i));

            double worldX = renderPos.x + sin;
            double worldY = renderPos.y + yOffset;
            double worldZ = renderPos.z + cos;
            drawBillboard(ms, cameraPos, worldX, worldY, worldZ, outerGlowSize, multAlpha(glowColor, 0.24f), crystalRotationAngle + i);
            drawBillboard(ms, cameraPos, worldX, worldY, worldZ, glowBaseSize, glowColor, -(crystalRotationAngle + i * 0.5f));
        }

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
    }

    private void renderCrystalShape(BufferBuilder buffer, Matrix4f matrix, float size, int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = (color >> 24) & 0xFF;

        float w = 0.34f * size / 0.1f;
        float h = 1.15f * size / 0.1f;
        w = 0.06f;
        h = 0.2f;
        tri(buffer, matrix, 0, h, 0, w, 0, 0, 0, 0, w, r, g, b, a);
        tri(buffer, matrix, 0, h, 0, 0, 0, w, -w, 0, 0, r, g, b, a);
        tri(buffer, matrix, 0, h, 0, -w, 0, 0, 0, 0, -w, r, g, b, a);
        tri(buffer, matrix, 0, h, 0, 0, 0, -w, w, 0, 0, r, g, b, a);
        tri(buffer, matrix, 0, -h, 0, w, 0, 0, 0, 0, w, r, g, b, a);
        tri(buffer, matrix, 0, -h, 0, 0, 0, w, -w, 0, 0, r, g, b, a);
        tri(buffer, matrix, 0, -h, 0, -w, 0, 0, 0, 0, -w, r, g, b, a);
        tri(buffer, matrix, 0, -h, 0, 0, 0, -w, w, 0, 0, r, g, b, a);
    }

    private boolean isCrystalMode() {
        return mode.is("Кристаллы");
    }

    private void renderFollowingGhosts(Event3DRender event, LivingEntity target, boolean hasTarget) {
        if (hasTarget && target != null && target.isAlive()) {
            addFollowingGhostPoints(target, event.getTickDelta(),
                    Math.max(1, Math.round(bmwGhostCount.getValue().floatValue())),
                    Math.max(50, Math.round(bmwGhostLife.getValue().floatValue())),
                    getESPColor(),
                    bmwStrengthXZ.getValue().floatValue(),
                    bmwStrengthY.getValue().floatValue());
        }

        followGhostPoints.removeIf(GlowPoint::shouldRemove);
        if (followGhostPoints.isEmpty()) return;

        Vec3d cam = mc.gameRenderer.getCamera().getPos();
        MatrixStack matrices = event.getMatrices();

        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, getBloomTexture());

        for (GlowPoint point : followGhostPoints) {
            float timePC = point.getTimeProgress();
            float scale = 0.5f * (1.0f - timePC);
            int color = point.getColor(timePC);
            drawStaticBillboard(matrices, cam, point.x, point.y, point.z, scale, color, 0);
        }

        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
    }

    private void addFollowingGhostPoints(LivingEntity entity, float partialTicks, int count, int maxTime, int colorBase, float strengthXZ, float strengthY) {
        float x = (float) MathHelper.lerp(partialTicks, entity.lastRenderX, entity.getX());
        float y = (float) MathHelper.lerp(partialTicks, entity.lastRenderY, entity.getY());
        float z = (float) MathHelper.lerp(partialTicks, entity.lastRenderZ, entity.getZ());
        float xzRange = Math.max(0.35f, entity.getWidth());
        float yRange = entity.getHeight();
        int delayXZ = Math.max(1, (int) strengthXZ);
        int delayY = Math.max(1, (int) strengthY);
        long time = System.currentTimeMillis();

        for (int corner = 0; corner < count; corner++) {
            float cornerProgress = corner / (float) count;
            float xzRotate = ((time + (int) (delayXZ * cornerProgress)) % delayXZ) / (float) delayXZ * 360.0f;
            float yProgress = ((time + (int) (delayY * cornerProgress)) % delayY) / (float) delayY;
            yProgress = (yProgress > 0.5f ? 1.0f - yProgress : yProgress) * 2.0f;
            yProgress *= yProgress;
            double yawRad = Math.toRadians(MathHelper.wrapDegrees(cornerProgress * 360.0f + xzRotate));
            float pointX = x - (float) Math.sin(yawRad) * xzRange;
            float pointY = y + yRange * yProgress;
            float pointZ = z + (float) Math.cos(yawRad) * xzRange;
            followGhostPoints.add(new GlowPoint(pointX, pointY, pointZ, maxTime, colorBase));
        }
    }

    private float[] project2D(double worldX, double worldY, double worldZ) {
        return null;
    }

    private double getScale(double worldX, double worldY, double worldZ) {
        Vec3d cam = mc.gameRenderer.getCamera().getPos();
        double dx = worldX - cam.x;
        double dy = worldY - cam.y;
        double dz = worldZ - cam.z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        return Math.max(0.5, 8.0 / Math.max(0.1, distance));
    }

    private void drawTexturedRect2D(MatrixStack matrix, float x, float y, float width, float height, int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = (color >> 24) & 0xFF;
        if (a <= 0) return;
        Matrix4f mat = matrix.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        buffer.vertex(mat, x, y, 0).texture(0, 0).color(r, g, b, a);
        buffer.vertex(mat, x, y + height, 0).texture(0, 1).color(r, g, b, a);
        buffer.vertex(mat, x + width, y + height, 0).texture(1, 1).color(r, g, b, a);
        buffer.vertex(mat, x + width, y, 0).texture(1, 0).color(r, g, b, a);
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    private void drawCrystalGlow2D(MatrixStack matrix, LivingEntity target) {
        return;
    }

    // ========== КОЛЬЦО 2 ==========
    private void drawRing2_3D(Event3DRender event) {
        if (appearValue <= 0.001f || lastTargetPos == null) {
            ring2Angle = 0f;
            ring2GlowCount = 0;
            ring2GlowHead = 0;
            return;
        }

        float partialTicks = mc.getRenderTickCounter().getTickDelta(true);
        Vec3d vec;
        float height;
        LivingEntity target = lastTarget;

        if (target != null && target.isAlive()) {
            vec = new Vec3d(
                    MathHelper.lerp(partialTicks, target.lastRenderX, target.getX()),
                    MathHelper.lerp(partialTicks, target.lastRenderY, target.getY()),
                    MathHelper.lerp(partialTicks, target.lastRenderZ, target.getZ())
            );
            height = target.getHeight();
        } else {
            vec = lastTargetPos;
            height = lastTargetHeight;
        }

        Vec3d cam = mc.gameRenderer.getCamera().getPos();
        double duration = 1800;
        double elapsed = System.currentTimeMillis() % duration;
        boolean side = elapsed > duration / 2.0;
        double progress = elapsed / (duration / 2.0);
        progress = side ? (progress - 1.0) : 1.0 - progress;
        progress = progress < 0.5 ? 2.0 * progress * progress : 1.0 - Math.pow(-2.0 * progress + 2.0, 2.0) / 2.0;

        double baseX = vec.x;
        double baseY = vec.y;
        double baseZ = vec.z;

        // Обновление угла
        long nowMs = System.currentTimeMillis();
        float dt = ring2LastUpdateMs == 0 ? 0.016f : Math.min((nowMs - ring2LastUpdateMs) / 1000f, 0.1f);
        ring2LastUpdateMs = nowMs;
        ring2Angle += 360.0f * dt;

        float ringH = (float) (height * progress);
        float radius = lastTargetWidth * 0.7f;

        // Спавним glow точки по окружности
        final int SPAWN = 180;
        for (int s = 0; s < SPAWN; s++) {
            float spawnAngle = (360f / SPAWN) * s;
            double rad = Math.toRadians(spawnAngle);
            int slot = ring2GlowHead & (RING2_GLOW_MAX - 1);
            ring2GlowX[slot] = (float) (baseX + Math.cos(rad) * radius);
            ring2GlowY[slot] = (float) (baseY + ringH);
            ring2GlowZ[slot] = (float) (baseZ + Math.sin(rad) * radius);
            ring2GlowTime[slot] = nowMs;
            ring2GlowHue[slot] = (int) (spawnAngle * 8);
            ring2GlowHead++;
            if (ring2GlowCount < RING2_GLOW_MAX) ring2GlowCount++;
        }

        if (ring2GlowCount == 0) return;

        int baseCol = getESPColor();
        float hurtPC = getHurtPC(target);
        int redCol = getSoftRedColor();

        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, getBloomTexture());

        MatrixStack matrices = event.getMatrices();
        long trailLifeMs = 160L;

        int total = Math.min(ring2GlowCount, RING2_GLOW_MAX);
        for (int i = 0; i < total; i++) {
            int slot = (ring2GlowHead - 1 - i) & (RING2_GLOW_MAX - 1);
            long age = nowMs - ring2GlowTime[slot];
            if (age >= trailLifeMs) continue;

            float life = 1f - (float) age / trailLifeMs;
            float a = appearValue * life * life;
            if (a <= 0.01f) continue;

            double wx = ring2GlowX[slot] - cam.x;
            double wy = ring2GlowY[slot] - cam.y;
            double wz = ring2GlowZ[slot] - cam.z;

            int hue = ring2GlowHue[slot];
            // Создаем цвет на основе hue напрямую
            int color = ColorUtils.getThemeColor(hue);
            int blended = overCol(multAlpha(color, a), multAlpha(redCol, a), hurtPC);
            int finalColor = replAlpha(blended, MathHelper.clamp((int) (255 * a), 0, 255));

            // Рисуем основной billboard с текстурой bloom
            drawStaticBillboard(matrices, cam, wx + cam.x, wy + cam.y, wz + cam.z, 0.1f, finalColor, 0);

            // Если режим насыщенности включен, рисуем второй слой
            if (saturationMode.isState()) {
                // Второй слой с той же непрозрачностью для полной насыщенности
                drawStaticBillboard(matrices, cam, wx + cam.x, wy + cam.y, wz + cam.z, 0.1f, finalColor, 0);
            }
        }

        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    // ========== ЦЕЛКА (SPIRITS) ==========
    private void drawCelka2_3D(Event3DRender event) {
        if (appearValue <= 0.001f || lastTargetPos == null) return;

        float partialTicks = mc.getRenderTickCounter().getTickDelta(true);
        Vec3d vec;
        float height;
        LivingEntity target = lastTarget;

        if (target != null && target.isAlive()) {
            vec = new Vec3d(
                    MathHelper.lerp(partialTicks, target.lastRenderX, target.getX()),
                    MathHelper.lerp(partialTicks, target.lastRenderY, target.getY()),
                    MathHelper.lerp(partialTicks, target.lastRenderZ, target.getZ())
            );
            height = target.getHeight();
        } else {
            vec = lastTargetPos;
            height = lastTargetHeight;
        }

        Vec3d cam = mc.gameRenderer.getCamera().getPos();
        double bx = vec.x;
        double by = vec.y;
        double bz = vec.z;

        final float speed = 1.3F;
        final int trailLength = 20;
        final float radiusConst = 0.7F;
        final float upperPosition = 1.8F;
        final float lowerPosition = 0.1F;

        long time = System.currentTimeMillis();
        double t = time / (500.0 / speed);
        double tv = time / (1000.0 / 1.5F);

        int baseCol = getESPColor();
        float hurtPC = getHurtPC(target);
        int redCol = ColorUtils.rgb(190, 100, 100);

        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, Identifier.of("snill", "textures/trajectories/glow.png"));

        MatrixStack matrices = event.getMatrices();
        float radius = radiusConst;

        for (int k = 0; k < 4; k++) {
            for (int j = 0; j < trailLength; j++) {
                float kf = j / (float) trailLength;
                double tj = t - j * 0.05;
                double tvj = tv - j * 0.05;
                double cyc = (Math.sin(tvj) + 1.0) * 0.5;

                double baseAngle = Math.toRadians(k * 90.0 + (tj * 50.0 % 360.0));
                double offX = Math.cos(baseAngle) * radius;
                double offZ = Math.sin(baseAngle) * radius;
                double offY = (k % 2 == 0)
                        ? lowerPosition + (upperPosition - lowerPosition) * cyc
                        : upperPosition - (upperPosition - lowerPosition) * cyc;

                float sizeFactor = 1.0f - kf * 0.6f;
                float dynSize = 0.45F * sizeFactor;

                int dynAlpha = MathHelper.clamp((int) (255 * appearValue), 0, 255);
                int color = replAlpha(overCol(multAlpha(baseCol, appearValue), multAlpha(redCol, appearValue), hurtPC), dynAlpha);

                double worldX = bx + offX;
                double worldY = by + offY;
                double worldZ = bz + offZ;

                drawStaticBillboard(matrices, cam, worldX, worldY, worldZ, dynSize, color, 0);
            }
            radius *= -1.0f;
        }

        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    // ========== ЦЕПИ (CHAINS) ==========
    private void drawChains3D(Event3DRender event, LivingEntity target, boolean hasTarget) {
        boolean isChainMode = mode.is("Цепи");
        LivingEntity chainTarget = isChainMode && hasTarget ? target : null;
        boolean chainTargetAlive = chainTarget != null && chainTarget.isAlive();
        boolean chainTargetPresent = chainTarget != null;

        if (chainTargetAlive && chainSpawnAnimation < 1.0f) {
            chainSpawnAnimation += 0.04f;
        } else if (!chainTargetPresent && chainSpawnAnimation > 0.0f) {
            chainSpawnAnimation -= 0.04f;
        }
        chainSpawnAnimation = MathHelper.clamp(chainSpawnAnimation, 0f, 1f);

        if (chainTarget != null) chainLastTarget = chainTarget;

        if (chainSpawnAnimation > 0f && chainLastTarget != null) {
            float hurtPC = getHurtPC(chainLastTarget);
            renderChains(chainLastTarget, event.getMatrices(), event.getTickDelta(), chainSpawnAnimation, hurtPC);
        } else if (chainSpawnAnimation <= 0f) {
            chainLastRenderPosition = null;
            chainLastTarget = null;
        }
    }

    private void renderChains(LivingEntity currentTarget, MatrixStack ms, float partialTicks, float alphaPC, float hurtPC) {
        if (currentTarget == null) return;

        ms.push();
        Vec3d cam = mc.gameRenderer.getCamera().getPos();

        Vec3d interpolated = new Vec3d(
                MathHelper.lerp(partialTicks, currentTarget.lastRenderX, currentTarget.getX()),
                MathHelper.lerp(partialTicks, currentTarget.lastRenderY, currentTarget.getY()),
                MathHelper.lerp(partialTicks, currentTarget.lastRenderZ, currentTarget.getZ())
        );

        double entX = interpolated.x - cam.x;
        double entY = interpolated.y - cam.y - 0.5f;
        double entZ = interpolated.z - cam.z;

        float movingValue = (System.currentTimeMillis() % 3600000) / 10f;
        float gradusX = (float) (20 * Math.min(1 + Math.sin(Math.toRadians(movingValue)), 1));
        float gradusZ = (float) (20 * (Math.min(1 + Math.sin(Math.toRadians(movingValue)), 2) - 1));

        float radiusAnim = 1.25f - 0.5f * alphaPC;
        float width = currentTarget.getWidth() * 1.8f * radiusAnim;

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);

        int baseColor = getESPColor();
        int redColor = getSoftRedColor();
        int blendedColor = overCol(baseColor, redColor, hurtPC);

        Tessellator tess = Tessellator.getInstance();
        GL11.glDisable(GL11.GL_CULL_FACE);
        RenderSystem.depthMask(true);

        // Используем правильную текстуру для цепей
        Identifier chainTexture = Identifier.of("snill", "textures/targetesp/chain.png");
        RenderSystem.setShaderTexture(0, chainTexture);

        int linksStep = 18;
        int totalAngle = 360 * 2;
        float chainSize = 4f;
        float down = 1f;

        for (int chain = 0; chain < 2; chain++) {
            float val = 1.2f - 0.5f * (chain == 0 ? 1.0f : 0.9f);

            // Рисуем основной слой с текстурой
            ms.push();
            ms.translate(entX, entY + currentTarget.getHeight() / 2.0f, entZ);

            float x = 0, y = 0, z = 0;
            Matrix4f matrix = ms.peek().getPositionMatrix();

            ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(chain == 0 ? gradusX : -gradusX));
            ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(chain == 0 ? gradusZ : -gradusZ));

            int alphaVal = MathHelper.clamp((int) (alphaPC * 255), 0, 255);
            int color = replAlpha(blendedColor, alphaVal);

            RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
            BufferBuilder buffer = tess.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

            int modif = linksStep / 2;
            for (int i = 0; i < totalAngle; i += modif) {
                float prevSin = (float) (x + (chain == 0 ? gradusX : -gradusX) / 100F + Math.sin(Math.toRadians(i - modif + movingValue * 0.5f)) * width * val);
                float prevCos = (float) (z + (chain == 0 ? -gradusZ : gradusZ) / 100F + Math.cos(Math.toRadians(i - modif + movingValue * 0.5f)) * width * val);
                float sin = (float) (x + (chain == 0 ? gradusX : -gradusX) / 100F + Math.sin(Math.toRadians(i + movingValue * 0.5f)) * width * val);
                float cos = (float) (z + (chain == 0 ? -gradusZ : gradusZ) / 100F + Math.cos(Math.toRadians(i + movingValue * 0.5f)) * width * val);

                int r = (color >> 16) & 0xFF;
                int g = (color >> 8) & 0xFF;
                int b = color & 0xFF;
                int a = (color >> 24) & 0xFF;

                buffer.vertex(matrix, prevSin, y, prevCos).texture(1f / 360f * (float) (i - modif) * chainSize, 0).color(r, g, b, a);
                buffer.vertex(matrix, sin, y, cos).texture(1f / 360f * (float) (i) * chainSize, 0).color(r, g, b, a);
                buffer.vertex(matrix, sin, y + down, cos).texture(1f / 360f * (float) (i) * chainSize, 1 - 0.01f).color(r, g, b, a);
                buffer.vertex(matrix, prevSin, y + down, prevCos).texture(1f / 360f * (float) (i - modif) * chainSize, 1 - 0.01f).color(r, g, b, a);
            }

            BufferRenderer.drawWithGlobalProgram(buffer.end());
            ms.pop();

            // Если режим насыщенности включен, рисуем второй слой
            if (saturationMode.isState()) {
                ms.push();
                ms.translate(entX, entY + currentTarget.getHeight() / 2.0f, entZ);

                matrix = ms.peek().getPositionMatrix();

                ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(chain == 0 ? gradusX : -gradusX));
                ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(chain == 0 ? gradusZ : -gradusZ));

                // Второй слой с той же непрозрачностью для полной насыщенности
                int saturatedColor = color; // 100% непрозрачность

                BufferBuilder satBuffer = tess.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

                for (int i = 0; i < totalAngle; i += modif) {
                    float prevSin = (float) (x + (chain == 0 ? gradusX : -gradusX) / 100F + Math.sin(Math.toRadians(i - modif + movingValue * 0.5f)) * width * val);
                    float prevCos = (float) (z + (chain == 0 ? -gradusZ : gradusZ) / 100F + Math.cos(Math.toRadians(i - modif + movingValue * 0.5f)) * width * val);
                    float sin = (float) (x + (chain == 0 ? gradusX : -gradusX) / 100F + Math.sin(Math.toRadians(i + movingValue * 0.5f)) * width * val);
                    float cos = (float) (z + (chain == 0 ? -gradusZ : gradusZ) / 100F + Math.cos(Math.toRadians(i + movingValue * 0.5f)) * width * val);

                    int r2 = (saturatedColor >> 16) & 0xFF;
                    int g2 = (saturatedColor >> 8) & 0xFF;
                    int b2 = saturatedColor & 0xFF;
                    int a2 = (saturatedColor >> 24) & 0xFF;

                    satBuffer.vertex(matrix, prevSin, y, prevCos).texture(1f / 360f * (float) (i - modif) * chainSize, 0).color(r2, g2, b2, a2);
                    satBuffer.vertex(matrix, sin, y, cos).texture(1f / 360f * (float) (i) * chainSize, 0).color(r2, g2, b2, a2);
                    satBuffer.vertex(matrix, sin, y + down, cos).texture(1f / 360f * (float) (i) * chainSize, 1 - 0.01f).color(r2, g2, b2, a2);
                    satBuffer.vertex(matrix, prevSin, y + down, prevCos).texture(1f / 360f * (float) (i - modif) * chainSize, 1 - 0.01f).color(r2, g2, b2, a2);
                }

                BufferRenderer.drawWithGlobalProgram(satBuffer.end());
                ms.pop();
            }
        }

        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        GL11.glEnable(GL11.GL_CULL_FACE);
        ms.pop();
    }

    private void tri(BufferBuilder buffer, Matrix4f matrix,
                     float x1, float y1, float z1,
                     float x2, float y2, float z2,
                     float x3, float y3, float z3,
                     int r, int g, int b, int a) {
        buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a);
        buffer.vertex(matrix, x2, y2, z2).color(r, g, b, a);
        buffer.vertex(matrix, x3, y3, z3).color(r, g, b, a);
    }

    private static class GlowPoint {
        final float x, y, z;
        final long startTime;
        final int maxLife;
        final int baseColor;

        GlowPoint(float x, float y, float z, int maxLife, int baseColor) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.startTime = System.currentTimeMillis();
            this.maxLife = maxLife;
            this.baseColor = baseColor;
        }

        boolean shouldRemove() {
            return System.currentTimeMillis() - startTime >= maxLife;
        }

        float getTimeProgress() {
            return MathHelper.clamp((System.currentTimeMillis() - startTime) / (float) maxLife, 0f, 1f);
        }

        int getColor(float timePC) {
            int a = (int) (((baseColor >> 24) & 0xFF) * (1.0f - timePC));
            a = Math.max(0, Math.min(255, a));
            return (a << 24) | (baseColor & 0x00FFFFFF);
        }
    }
}

class CubeParticle implements QClient {
    double x, y, z;
    double worldX, worldY, worldZ;
    long time;
    LivingEntity entity;
    boolean fading;
    long fadeStartTime;
    float vx, vy, vz;
    float rotX, rotY, rotZ;
    float rotSpeedX, rotSpeedY, rotSpeedZ;

    public CubeParticle(LivingEntity entity, double x, double y, double z) {
        this.entity = entity;
        this.x = x;
        this.y = y;
        this.z = z;
        this.time = System.currentTimeMillis();
        this.rotX = (float) (Math.random() * 360.0);
        this.rotY = (float) (Math.random() * 360.0);
        this.rotZ = (float) (Math.random() * 360.0);
        this.rotSpeedX = 1.4f + (float) Math.random() * 3.4f;
        this.rotSpeedY = 1.4f + (float) Math.random() * 3.4f;
        this.rotSpeedZ = 1.4f + (float) Math.random() * 3.4f;
        this.vx = (float) ((Math.random() - 0.5) * 0.0022);
        this.vy = 0.031f + (float) Math.random() * 0.020f;
        this.vz = (float) ((Math.random() - 0.5) * 0.0022);
    }

    public void update(float dt, long now, LivingEntity currentTarget) {
        float step = dt * 60.0f;
        rotX += rotSpeedX * step;
        rotY += rotSpeedY * step;
        rotZ += rotSpeedZ * step;

        if (!fading) {
            x += vx * step;
            y += vy * step;
            z += vz * step;
            vx *= 0.992f;
            vz *= 0.992f;
            vy *= 0.989f;

            if (entity != null) {
                double shoulderHeight = Math.max(2.2, entity.getHeight() * 1.85);
                if (y >= shoulderHeight) {
                    y = shoulderHeight;
                    beginFade(now);
                    return;
                }
            }

            boolean targetLost = currentTarget == null || entity == null || !entity.isAlive() || entity != currentTarget;
            if (targetLost || now - time >= TargetESP.CUBE_ATTACH_LIFE_MS) {
                beginFade(now);
            }
            return;
        }
    }

    public boolean shouldRemove(long now) {
        return fading && now - fadeStartTime >= TargetESP.CUBE_FADE_LIFE_MS;
    }

    public int getRenderColor(int baseColor, int redColor, float hurtPC, long now) {
        float alpha = getAlpha(now);
        if (alpha <= 0.001f) {
            return 0;
        }
        int color = ColorUtils.replAlpha(baseColor, (int) (alpha * 255.0f));
        int hurt = ColorUtils.replAlpha(redColor, (int) (alpha * 255.0f));
        return TargetESP.INSTANCE.overCol(color, hurt, hurtPC);
    }

    public boolean appendCubeFaces(BufferBuilder faceBuilder, MatrixStack ms, Vec3d cam,
                                   float partialTicks, int color) {
        float alpha = ((color >> 24) & 0xFF) / 255.0f;
        if (alpha <= 0.001f) return false;

        Vec3d renderPos = getRenderPos(partialTicks);
        if (renderPos == null) return false;

        float fadeScale = fading
                ? MathHelper.lerp(MathHelper.clamp((System.currentTimeMillis() - fadeStartTime) / (float) TargetESP.CUBE_FADE_LIFE_MS, 0.0f, 1.0f), 1.0f, 0.45f)
                : 1.0f;
        float scale = 0.12f * fadeScale;

        ms.push();
        ms.translate(renderPos.x - cam.x, renderPos.y - cam.y, renderPos.z - cam.z);
        ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rotX));
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotY));
        ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotZ));
        ms.scale(scale, scale, scale);
        Matrix4f m = ms.peek().getPositionMatrix();

        appendFaces(faceBuilder, m, color);
        ms.pop();
        return true;
    }

    public boolean appendCubeLines(BufferBuilder lineBuilder, MatrixStack ms, Vec3d cam,
                                   float partialTicks, int color) {
        float alpha = ((color >> 24) & 0xFF) / 255.0f;
        if (alpha <= 0.001f) return false;

        Vec3d renderPos = getRenderPos(partialTicks);
        if (renderPos == null) return false;

        float fadeScale = fading
                ? MathHelper.lerp(MathHelper.clamp((System.currentTimeMillis() - fadeStartTime) / (float) TargetESP.CUBE_FADE_LIFE_MS, 0.0f, 1.0f), 1.0f, 0.45f)
                : 1.0f;
        float scale = 0.12f * fadeScale;

        ms.push();
        ms.translate(renderPos.x - cam.x, renderPos.y - cam.y, renderPos.z - cam.z);
        ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rotX));
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotY));
        ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotZ));
        ms.scale(scale, scale, scale);
        Matrix4f m = ms.peek().getPositionMatrix();

        appendEdges(lineBuilder, m, ColorUtils.replAlpha(color, Math.max(1, (int) (((color >> 24) & 0xFF) * 0.7f))));
        ms.pop();
        return true;
    }

    public boolean appendBloom(BufferBuilder builder, MatrixStack ms, Vec3d camPos, float camYaw, float camPitch,
                               float partialTicks, int colorInt, long now) {
        float alpha = getAlpha(now);
        if (alpha <= 0.001f) return false;

        Vec3d renderPos = getRenderPos(partialTicks);
        if (renderPos == null) return false;

        float fadeScale = fading
                ? MathHelper.lerp(MathHelper.clamp((now - fadeStartTime) / (float) TargetESP.CUBE_FADE_LIFE_MS, 0.0f, 1.0f), 1.0f, 0.55f)
                : 1.0f;
        float glowScale = 0.95f * fadeScale;
        int ai = (int) (alpha * 0.15f * 255.0f);
        if (ai <= 0) return false;

        int r = (colorInt >> 16) & 0xFF;
        int g = (colorInt >> 8) & 0xFF;
        int b = colorInt & 0xFF;

        ms.push();
        ms.translate(renderPos.x - camPos.x, renderPos.y - camPos.y, renderPos.z - camPos.z);
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camYaw));
        ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camPitch));
        ms.scale(glowScale, glowScale, glowScale);
        Matrix4f m = ms.peek().getPositionMatrix();

        builder.vertex(m, -0.5f, 0.5f, 0).texture(0f, 1f).color(r, g, b, ai);
        builder.vertex(m, 0.5f, 0.5f, 0).texture(1f, 1f).color(r, g, b, ai);
        builder.vertex(m, 0.5f, -0.5f, 0).texture(1f, 0f).color(r, g, b, ai);
        builder.vertex(m, -0.5f, -0.5f, 0).texture(0f, 0f).color(r, g, b, ai);
        ms.pop();
        return true;
    }

    private void beginFade(long now) {
        if (fading) return;
        Vec3d renderPos = getRenderPos(1.0f);
        if (renderPos != null) {
            worldX = renderPos.x;
            worldY = renderPos.y;
            worldZ = renderPos.z;
        }
        fadeStartTime = now;
        fading = true;
        entity = null;
    }

    private float getAlpha(long now) {
        if (!fading) {
            float fadeIn = MathHelper.clamp((now - time) / 140.0f, 0.0f, 1.0f);
            float preFade = 1.0f - MathHelper.clamp((now - time - (TargetESP.CUBE_ATTACH_LIFE_MS - 120L)) / 120.0f, 0.0f, 0.35f);
            return fadeIn * preFade;
        }
        return 1.0f - MathHelper.clamp((now - fadeStartTime) / (float) TargetESP.CUBE_FADE_LIFE_MS, 0.0f, 1.0f);
    }

    private Vec3d getRenderPos(float partialTicks) {
        if (fading || entity == null) {
            return new Vec3d(worldX, worldY, worldZ);
        }
        return new Vec3d(
                MathHelper.lerp(partialTicks, entity.lastRenderX, entity.getX()) + x,
                MathHelper.lerp(partialTicks, entity.lastRenderY, entity.getY()) + y,
                MathHelper.lerp(partialTicks, entity.lastRenderZ, entity.getZ()) + z
        );
    }

    private void appendFaces(BufferBuilder fb, Matrix4f m, int color) {
        float min = -0.5f, max = 0.5f;
        int fillColor = ColorUtils.replAlpha(color, Math.max(1, (int) (((color >> 24) & 0xFF) * 0.16f)));
        addFace(fb, m, min, min, min, max, max, max, fillColor);
    }

    private void appendEdges(BufferBuilder buf, Matrix4f m, int color) {
        for (byte[] edge : TargetESP.CUBE_EDGES) {
            buf.vertex(m, edge[0] * 0.5f, edge[1] * 0.5f, edge[2] * 0.5f).color(color);
            buf.vertex(m, edge[3] * 0.5f, edge[4] * 0.5f, edge[5] * 0.5f).color(color);
        }
    }

    private void addFace(BufferBuilder buf, Matrix4f m,
                         float x1, float y1, float z1, float x2, float y2, float z2,
                         int color) {
        buf.vertex(m, x1, y1, z1).color(color);
        buf.vertex(m, x2, y1, z1).color(color);
        buf.vertex(m, x2, y1, z2).color(color);
        buf.vertex(m, x1, y1, z2).color(color);

        buf.vertex(m, x1, y2, z1).color(color);
        buf.vertex(m, x1, y2, z2).color(color);
        buf.vertex(m, x2, y2, z2).color(color);
        buf.vertex(m, x2, y2, z1).color(color);

        buf.vertex(m, x1, y1, z1).color(color);
        buf.vertex(m, x1, y2, z1).color(color);
        buf.vertex(m, x2, y2, z1).color(color);
        buf.vertex(m, x2, y1, z1).color(color);

        buf.vertex(m, x1, y1, z2).color(color);
        buf.vertex(m, x2, y1, z2).color(color);
        buf.vertex(m, x2, y2, z2).color(color);
        buf.vertex(m, x1, y2, z2).color(color);

        buf.vertex(m, x1, y1, z1).color(color);
        buf.vertex(m, x1, y1, z2).color(color);
        buf.vertex(m, x1, y2, z2).color(color);
        buf.vertex(m, x1, y2, z1).color(color);

        buf.vertex(m, x2, y1, z1).color(color);
        buf.vertex(m, x2, y2, z1).color(color);
        buf.vertex(m, x2, y2, z2).color(color);
        buf.vertex(m, x2, y1, z2).color(color);
    }
}
