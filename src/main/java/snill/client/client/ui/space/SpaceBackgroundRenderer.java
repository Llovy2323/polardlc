package snill.client.client.ui.space;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import snill.client.api.QClient;
import snill.client.api.utils.color.ColorUtils;
import snill.client.api.utils.render.RenderUtils;
import snill.client.api.utils.render.ShaderUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class SpaceBackgroundRenderer implements QClient {

    private static final int STAR_COUNT = 240;
    private final List<Star> stars = new ArrayList<>();
    private final List<ShootingStar> shootingStars = new ArrayList<>();
    private final Random random = new Random();
    private long lastShootingStarSpawn = 0L;

    private float smoothMouseX = 0f;
    private float smoothMouseY = 0f;

    public SpaceBackgroundRenderer() {
        initStars();
    }

    private void initStars() {
        stars.clear();
        for (int i = 0; i < STAR_COUNT; i++) {
            float x = random.nextFloat();
            float y = random.nextFloat();
            float depth = 0.1f + random.nextFloat() * 0.9f;
            float size = 0.8f + random.nextFloat() * 1.8f;
            float baseAlpha = 0.25f + random.nextFloat() * 0.75f;
            float twinkleSpeed = 0.0015f + random.nextFloat() * 0.0035f;
            float twinklePhase = random.nextFloat() * ((float) Math.PI * 2f);

            int color;
            float colorRand = random.nextFloat();
            if (colorRand < 0.50f) {
                color = 0xFFFFFFFF; // Pure white
            } else if (colorRand < 0.75f) {
                color = 0xFF99D8FF; // Ice blue
            } else if (colorRand < 0.90f) {
                color = 0xFFD899FF; // Neon purple
            } else {
                color = 0xFFFFD199; // Golden star
            }

            stars.add(new Star(x, y, depth, size, baseAlpha, twinkleSpeed, twinklePhase, color));
        }
    }

    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int width = mc.getWindow().getScaledWidth();
        int height = mc.getWindow().getScaledHeight();
        MatrixStack matrices = context.getMatrices();

        smoothMouseX = MathHelper.lerp(0.06f, smoothMouseX, (float) mouseX);
        smoothMouseY = MathHelper.lerp(0.06f, smoothMouseY, (float) mouseY);

        long time = System.currentTimeMillis();

        // 1. Deep Cosmic Space Gradient (No huge circles, clean cosmic background)
        int bgTop = 0xFF080614;
        int bgMid = 0xFF0A0818;
        int bgBottom = 0xFF04030A;
        RenderUtils.drawGradientRect(matrices, 0, 0, width, height, 0, bgTop, bgMid, bgBottom, bgBottom);

        // 2. Stars & Constellations with 3D Parallax
        drawConstellationsAndStars(matrices, width, height, time);

        // 3. Shooting Stars
        updateAndDrawShootingStars(matrices, width, height, time);
    }

    private void drawConstellationsAndStars(MatrixStack matrices, int width, int height, long time) {
        Matrix4f posMatrix = matrices.peek().getPositionMatrix();
        float mouseOffsetX = (smoothMouseX - width * 0.5f);
        float mouseOffsetY = (smoothMouseY - height * 0.5f);

        // Precalculate screen coordinates for stars
        float[] posX = new float[stars.size()];
        float[] posY = new float[stars.size()];
        float[] starAlpha = new float[stars.size()];

        for (int i = 0; i < stars.size(); i++) {
            Star star = stars.get(i);
            float screenX = star.x * width + mouseOffsetX * star.depth * 0.035f;
            float screenY = star.y * height + mouseOffsetY * star.depth * 0.035f;

            // Wrap around screen edges
            screenX = (screenX % width + width) % width;
            screenY = (screenY % height + height) % height;

            posX[i] = screenX;
            posY[i] = screenY;

            float twinkle = 0.45f + 0.55f * (float) Math.sin(time * star.twinkleSpeed + star.twinklePhase);
            starAlpha[i] = MathHelper.clamp(star.baseAlpha * twinkle, 0.05f, 1.0f);
        }

        // Draw Constellation Lines between close stars
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        RenderSystem.setShader(ShaderUtils.sonar);

        BufferBuilder lineBuffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        float maxDist = 60.0f;
        float maxDistSq = maxDist * maxDist;

        for (int i = 0; i < stars.size(); i++) {
            for (int j = i + 1; j < stars.size(); j++) {
                if (Math.abs(stars.get(i).depth - stars.get(j).depth) > 0.35f) continue;

                float dx = posX[i] - posX[j];
                float dy = posY[i] - posY[j];
                float distSq = dx * dx + dy * dy;

                if (distSq < maxDistSq) {
                    float dist = (float) Math.sqrt(distSq);
                    float lineAlpha = (1.0f - (dist / maxDist)) * 0.16f * Math.min(starAlpha[i], starAlpha[j]);
                    int alphaByte = (int) (lineAlpha * 255);
                    if (alphaByte > 2) {
                        lineBuffer.vertex(posMatrix, posX[i], posY[i], 0f).color(130, 160, 255, alphaByte);
                        lineBuffer.vertex(posMatrix, posX[j], posY[j], 0f).color(130, 160, 255, alphaByte);
                    }
                }
            }
        }
        BufferRenderer.drawWithGlobalProgram(lineBuffer.end());

        // Draw Star Points with subtle glow
        for (int i = 0; i < stars.size(); i++) {
            Star star = stars.get(i);
            float alpha = starAlpha[i];
            int color = ColorUtils.applyAlpha(star.color, alpha);

            if (star.size > 1.3f) {
                RenderUtils.drawRoundCircle(matrices, posX[i], posY[i], star.size * 1.8f, ColorUtils.applyAlpha(star.color, alpha * 0.2f));
            }
            RenderUtils.drawRoundCircle(matrices, posX[i], posY[i], star.size, color);
        }

        RenderSystem.defaultBlendFunc();
    }

    private void updateAndDrawShootingStars(MatrixStack matrices, int width, int height, long time) {
        if (time - lastShootingStarSpawn > 3500 + random.nextInt(3000)) {
            lastShootingStarSpawn = time;
            float startX = random.nextFloat() * (width * 0.8f);
            float startY = random.nextFloat() * (height * 0.35f);
            float length = 110f + random.nextFloat() * 80f;
            float angle = (float) (Math.PI / 4.0 + (random.nextFloat() - 0.5f) * 0.25f);
            float speed = 8.0f + random.nextFloat() * 4.0f;
            shootingStars.add(new ShootingStar(startX, startY, length, angle, speed, time));
        }

        shootingStars.removeIf(s -> s.isDead(time));

        for (ShootingStar star : shootingStars) {
            star.render(matrices, time);
        }
    }

    private static class Star {
        final float x, y, depth, size, baseAlpha, twinkleSpeed, twinklePhase;
        final int color;

        Star(float x, float y, float depth, float size, float baseAlpha, float twinkleSpeed, float twinklePhase, int color) {
            this.x = x;
            this.y = y;
            this.depth = depth;
            this.size = size;
            this.baseAlpha = baseAlpha;
            this.twinkleSpeed = twinkleSpeed;
            this.twinklePhase = twinklePhase;
            this.color = color;
        }
    }

    private static class ShootingStar {
        float x, y;
        final float length, angle, speed;
        final long spawnTime;
        final long lifetime = 1300L;

        ShootingStar(float x, float y, float length, float angle, float speed, long spawnTime) {
            this.x = x;
            this.y = y;
            this.length = length;
            this.angle = angle;
            this.speed = speed;
            this.spawnTime = spawnTime;
        }

        boolean isDead(long currentTime) {
            return currentTime - spawnTime > lifetime;
        }

        void render(MatrixStack matrices, long currentTime) {
            float progress = (float) (currentTime - spawnTime) / (float) lifetime;
            float currentX = x + (float) Math.cos(angle) * (speed * (currentTime - spawnTime) * 0.08f);
            float currentY = y + (float) Math.sin(angle) * (speed * (currentTime - spawnTime) * 0.08f);

            float tailX = currentX - (float) Math.cos(angle) * length;
            float tailY = currentY - (float) Math.sin(angle) * length;

            float alpha = (float) Math.sin(progress * Math.PI);

            RenderUtils.drawRoundCircle(matrices, currentX, currentY, 2.0f, ColorUtils.applyAlpha(0xFFFFFFFF, alpha));

            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            RenderSystem.setShader(ShaderUtils.sonar);
            BufferBuilder lineBuffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
            Matrix4f posMatrix = matrices.peek().getPositionMatrix();
            int c1 = ColorUtils.applyAlpha(0x00A78BFA, 0f);
            int c2 = ColorUtils.applyAlpha(0xFFFFFFFF, alpha * 0.9f);
            lineBuffer.vertex(posMatrix, tailX, tailY, 0f).color(c1);
            lineBuffer.vertex(posMatrix, currentX, currentY, 0f).color(c2);
            BufferRenderer.drawWithGlobalProgram(lineBuffer.end());
            RenderSystem.defaultBlendFunc();
        }
    }
}
