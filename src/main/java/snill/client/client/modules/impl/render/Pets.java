package snill.client.client.modules.impl.render;

import com.mojang.blaze3d.systems.RenderSystem;
import snill.client.api.events.EventLink;
import snill.client.api.events.implement.Event3DRender;
import snill.client.api.events.implement.EventUpdate;
import snill.client.client.modules.Module;
import snill.client.client.modules.impl.render.pets.DeguModel;
import snill.client.client.modules.impl.render.pets.DogBrain;
import snill.client.client.modules.settings.implement.FloatSetting;
import snill.client.client.modules.settings.implement.ModeSetting;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.gl.ShaderProgramKey;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Position;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockRenderView;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;

public class Pets extends Module {
    public static Pets INSTANCE = new Pets();
    private static final Identifier SMOKE_TEXTURE = Identifier.of("snill", "textures/particle/bloom.png");
    private final FloatSetting scale = new FloatSetting("Размер", 0.55f, 0.25f, 1.25f, 0.05f);
    private final ModeSetting texture = new ModeSetting("Текстура", "Agouti", "Agouti", "Sand", "Blue", "Agouti Pied", "White", "Black");
    private final ModeSetting clickAnimation = new ModeSetting("Анимация при нажатии", "Танец", "Танец", "Бернаут", "Бернаут 2");
    private final DogBrain brain = new DogBrain();
    private final DeguModel model = new DeguModel(DeguModel.getTexturedModelData().createModel());
    private final List<SmokeParticle> smokeParticles = new ArrayList<>();
    private final Random smokeRandom = new Random();
    private boolean lastUsePressed;
    private long lastSmokeSpawnMs;

    public Pets() {
        super("Pet", "Питомец рядом с игроком", Module.ModuleCategory.RENDER);
        this.addSettings(this.scale, this.texture, this.clickAnimation);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        this.brain.reset();
    }

    @Override
    public void onDisable() {
        this.brain.reset();
        this.smokeParticles.clear();
        super.onDisable();
    }

    @EventLink
    public void onUpdate(EventUpdate event) {
        if (Pets.mc.player == null || Pets.mc.world == null) {
            return;
        }
        this.brain.setEntity((PlayerEntity) Pets.mc.player);
        this.brain.tick();
        this.handlePetInteraction();
        this.updateBurnoutSmoke();
    }

    @EventLink
    public void onRender3D(Event3DRender event) {
        if (Pets.mc.player == null || Pets.mc.world == null) {
            return;
        }

        float tickDelta = event.getTickDelta();
        Vec3d pos = this.brain.getRenderPos(tickDelta);
        Vec3d camera = event.getCamera().getPos();
        MatrixStack matrices = event.getMatrices();
        float renderScale = this.scale.get();
        int light = WorldRenderer.getLightmapCoordinates((BlockRenderView) Pets.mc.world, BlockPos.ofFloored((Position) pos));
        float hypeProgress = this.brain.getHypeAnimationProgress(tickDelta);
        float hypeAmount = MathHelper.sin(hypeProgress * (float) Math.PI);
        float hypeTicks = this.brain.getHypeAnimationTicks(tickDelta);
        int activeAnimation = this.brain.getClickAnimationMode();
        boolean burnout = activeAnimation == 1;
        boolean burnout2 = activeAnimation == 2;
        float burnout2Warmup = this.brain.getBurnout2WarmupProgress(tickDelta);
        float burnout2Flip = this.brain.getBurnout2FlipProgress(tickDelta);
        float burnout2FlipAmount = MathHelper.sin(burnout2Flip * (float) Math.PI);
        float hypeBounce = this.getClickBounce(hypeAmount, hypeTicks, burnout, burnout2, burnout2Warmup, burnout2FlipAmount);
        float hypeScale = 1.0f + (burnout2 ? burnout2FlipAmount : hypeAmount) * (burnout || burnout2 ? 0.04f : 0.08f);
        float flipAngle = burnout ? this.getBurnoutFlipAngle(hypeProgress) : (burnout2 ? this.getBurnoutFlipAngle(burnout2Flip) : 0.0f);

        matrices.push();
        matrices.translate(pos.x - camera.x, pos.y - camera.y + hypeBounce, pos.z - camera.z);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f - this.brain.getRenderBody(tickDelta)));
        if (burnout || burnout2) {
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(flipAngle));
        }
        matrices.scale(-renderScale * hypeScale, -renderScale * hypeScale, renderScale * hypeScale);
        matrices.translate(0.0f, -1.501f, 0.0f);
        this.model.setAngles(Pets.mc.player.age + tickDelta, tickDelta, this.brain, activeAnimation);
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.defaultBlendFunc();
        VertexConsumerProvider.Immediate vertices = mc.getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer consumer = vertices.getBuffer(this.model.getLayer(this.getTexture()));
        this.model.render(matrices, consumer, light, OverlayTexture.DEFAULT_UV, -1);
        vertices.draw();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        matrices.pop();
        this.renderSmoke(event);
    }

    private Identifier getTexture() {
        return switch (this.texture.getCurrent()) {
            case "Sand" -> Identifier.of("snill", "textures/pets/degu/sand.png");
            case "Blue" -> Identifier.of("snill", "textures/pets/degu/blue.png");
            case "Agouti Pied" -> Identifier.of("snill", "textures/pets/degu/agoutipied.png");
            case "White" -> Identifier.of("snill", "textures/pets/degu/white.png");
            case "Black" -> Identifier.of("snill", "textures/pets/degu/black.png");
            default -> Identifier.of("snill", "textures/pets/degu/agouti.png");
        };
    }

    private void handlePetInteraction() {
        boolean usePressed = Pets.mc.options.useKey.isPressed();
        if (usePressed && !this.lastUsePressed && this.isLookingAtPet()) {
            this.brain.playHypeAnimation(this.getSelectedClickAnimationMode());
        }
        this.lastUsePressed = usePressed;
    }

    private int getSelectedClickAnimationMode() {
        if (this.clickAnimation.is("Бернаут")) {
            return 1;
        }
        if (this.clickAnimation.is("Бернаут 2")) {
            return 2;
        }
        return 0;
    }

    private boolean isLookingAtPet() {
        if (Pets.mc.player == null) {
            return false;
        }
        Vec3d start = Pets.mc.player.getCameraPosVec(1.0f);
        Vec3d end = start.add(Pets.mc.player.getRotationVec(1.0f).multiply(4.5));
        Box box = this.brain.getInteractionBox();
        return box.raycast(start, end).isPresent();
    }

    private float getBurnoutFlipAngle(float progress) {
        if (progress <= 0.12f) {
            return 0.0f;
        }
        if (progress >= 0.88f) {
            return 360.0f;
        }
        float flipProgress = (progress - 0.12f) / 0.76f;
        flipProgress = flipProgress * flipProgress * (3.0f - 2.0f * flipProgress);
        return flipProgress * 360.0f;
    }

    private float getClickBounce(float hypeAmount, float hypeTicks, boolean burnout, boolean burnout2, float burnout2Warmup, float burnout2FlipAmount) {
        if (burnout) {
            return hypeAmount * 0.82f;
        }
        if (burnout2) {
            float warmupBob = MathHelper.sin(burnout2Warmup * (float) Math.PI * 10.0f) * 0.035f * (1.0f - burnout2FlipAmount);
            return warmupBob + burnout2FlipAmount * 3.28f;
        }
        return hypeAmount * MathHelper.sin(hypeTicks * 0.55f) * 0.12f;
    }

    private void updateBurnoutSmoke() {
        long now = System.currentTimeMillis();
        Iterator<SmokeParticle> iterator = this.smokeParticles.iterator();
        while (iterator.hasNext()) {
            SmokeParticle particle = iterator.next();
            if (now - particle.spawnTime > 720L) {
                iterator.remove();
                continue;
            }
            particle.position = particle.position.add(particle.velocity);
            particle.velocity = particle.velocity.multiply(0.96, 0.92, 0.96).add(0.0, 0.0025, 0.0);
        }

        boolean burnout2Warmup = this.brain.getClickAnimationMode() == 2 && this.brain.getBurnout2WarmupProgress(1.0f) > 0.0f && this.brain.getBurnout2FlipProgress(1.0f) <= 0.0f;
        if (!burnout2Warmup || now - this.lastSmokeSpawnMs < 35L) {
            return;
        }
        this.spawnBurnoutSmoke(now);
        this.lastSmokeSpawnMs = now;
    }

    private void spawnBurnoutSmoke(long now) {
        Vec3d pos = this.brain.getRenderPos(1.0f);
        float yaw = this.brain.getRenderBody(1.0f) * ((float) Math.PI / 180);
        Vec3d forward = new Vec3d(-MathHelper.sin(yaw), 0.0, MathHelper.cos(yaw));
        Vec3d right = new Vec3d(forward.z, 0.0, -forward.x);
        Vec3d back = forward.multiply(-0.34);
        Vec3d backDirection = back.normalize();
        for (int side = -1; side <= 1; side += 2) {
            Vec3d foot = pos.add(back).add(right.multiply((double) side * 0.18)).add(0.0, 0.08, 0.0);
            for (int i2 = 0; i2 < 2; ++i2) {
                double jitterSide = (this.smokeRandom.nextDouble() - 0.5) * 0.08;
                double jitterBack = this.smokeRandom.nextDouble() * 0.1;
                Vec3d spawn = foot.add(right.multiply(jitterSide)).add(backDirection.multiply(jitterBack));
                Vec3d velocity = backDirection.multiply(0.018 + this.smokeRandom.nextDouble() * 0.018).add(right.multiply((this.smokeRandom.nextDouble() - 0.5) * 0.018)).add(0.0, 0.012 + this.smokeRandom.nextDouble() * 0.014, 0.0);
                this.smokeParticles.add(new SmokeParticle(spawn, velocity, 0.11f + this.smokeRandom.nextFloat() * 0.06f, now));
            }
        }
        while (this.smokeParticles.size() > 80) {
            this.smokeParticles.remove(0);
        }
    }

    private void renderSmoke(Event3DRender event) {
        if (this.smokeParticles.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        Vec3d cameraPos = event.getCamera().getPos();
        MatrixStack matrices = event.getMatrices();
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader((ShaderProgramKey) ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, SMOKE_TEXTURE);
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        for (SmokeParticle particle : this.smokeParticles) {
            float age = MathHelper.clamp((now - particle.spawnTime) / 720.0f, 0.0f, 1.0f);
            float alpha = (1.0f - age) * 0.46f;
            float size = particle.size * (0.85f + age * 1.9f);
            int color = 128 + (int) (age * 34.0f);
            int alphaInt = MathHelper.clamp((int) (alpha * 255.0f), 0, 255);
            matrices.push();
            matrices.translate(particle.position.x - cameraPos.x, particle.position.y - cameraPos.y, particle.position.z - cameraPos.z);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-event.getCamera().getYaw()));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(event.getCamera().getPitch()));
            Matrix4f matrix = matrices.peek().getPositionMatrix();
            float half = size * 0.5f;
            buffer.vertex(matrix, -half, -half, 0.0f).texture(0.0f, 1.0f).color(color, color, color, alphaInt);
            buffer.vertex(matrix, -half, half, 0.0f).texture(0.0f, 0.0f).color(color, color, color, alphaInt);
            buffer.vertex(matrix, half, half, 0.0f).texture(1.0f, 0.0f).color(color, color, color, alphaInt);
            buffer.vertex(matrix, half, -half, 0.0f).texture(1.0f, 1.0f).color(color, color, color, alphaInt);
            matrices.pop();
        }
        BufferRenderer.drawWithGlobalProgram((BuiltBuffer) buffer.end());
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static final class SmokeParticle {
        private Vec3d position;
        private Vec3d velocity;
        private final float size;
        private final long spawnTime;

        private SmokeParticle(Vec3d position, Vec3d velocity, float size, long spawnTime) {
            this.position = position;
            this.velocity = velocity;
            this.size = size;
            this.spawnTime = spawnTime;
        }
    }
}
