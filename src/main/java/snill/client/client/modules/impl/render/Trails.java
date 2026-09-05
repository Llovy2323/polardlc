package snill.client.client.modules.impl.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import snill.client.api.events.EventLink;
import snill.client.api.events.implement.Event3DRender;
import snill.client.api.utils.color.ColorUtils;
import snill.client.client.modules.Module;
import snill.client.client.modules.settings.implement.FloatSetting;

import java.util.ArrayList;
import java.util.List;

public class Trails extends Module {

    public static Trails INSTANCE = new Trails();

    private static final float BLINK_SPEED = 0.002f;

    private final FloatSetting trailLength = new FloatSetting("Длина", 2f, 2f, 4f, 0.5f);

    private final List<Point> points = new ArrayList<>();

    public Trails() {
        super("Trails", "Создаёт плавную линию ходьбы", ModuleCategory.RENDER);
        addSettings(trailLength);
    }

    @Override
    public void onDisable() {
        points.clear();
        super.onDisable();
    }

    @EventLink
    public void onRender(Event3DRender event) {
        if (mc.options.getPerspective() == Perspective.FIRST_PERSON) {
            return;
        }

        if (mc.player == null || mc.world == null) return;

        long currentTime = System.currentTimeMillis();

        points.removeIf(p -> (currentTime - p.time) > trailLength.get() * 100f);

        Vec3d playerPos = interpolatePlayerPosition(event.getTickDelta());

        points.add(new Point(playerPos));

        render3DPoints(event.getMatrices());
    }

    private Vec3d interpolatePlayerPosition(float partialTicks) {
        return new Vec3d(
                MathHelper.lerp(partialTicks, mc.player.prevX, mc.player.getX()),
                MathHelper.lerp(partialTicks, mc.player.prevY, mc.player.getY()),
                MathHelper.lerp(partialTicks, mc.player.prevZ, mc.player.getZ())
        );
    }

    private void render3DPoints(MatrixStack matrixStack) {
        if (points.size() < 2) return;

        startRendering();

        matrixStack.push();

        Vec3d view = mc.gameRenderer.getCamera().getPos();
        matrixStack.translate(-view.x, -view.y, -view.z);

        Matrix4f matrix = matrixStack.peek().getPositionMatrix();

        float blinkFactor = (float) (Math.sin(System.currentTimeMillis() * BLINK_SPEED) * 0.5 + 0.5);
        int themeColor = ColorUtils.getThemeColor();
        float red = applyBlink(ColorUtils.redf(themeColor), blinkFactor);
        float green = applyBlink(ColorUtils.greenf(themeColor), blinkFactor);
        float blue = applyBlink(ColorUtils.bluef(themeColor), blinkFactor);

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);

        int index = 0;
        for (Point p : points) {
            float alpha = (float) index / (float) points.size() * 0.7f;
            int alphaInt = (int) (alpha * 255);

            buffer.vertex(matrix, (float) p.pos.x, (float) (p.pos.y + mc.player.getHeight()), (float) p.pos.z)
                    .color((int) (red * 255), (int) (green * 255), (int) (blue * 255), alphaInt);
            buffer.vertex(matrix, (float) p.pos.x, (float) p.pos.y, (float) p.pos.z)
                    .color((int) (red * 255), (int) (green * 255), (int) (blue * 255), alphaInt);
            index++;
        }

        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.lineWidth(2);

        renderLineStrip(matrix, points, true, red, green, blue);
        renderLineStrip(matrix, points, false, red, green, blue);

        matrixStack.pop();
        stopRendering();
    }

    private float applyBlink(float channel, float blinkFactor) {
        return MathHelper.lerp(blinkFactor, channel, Math.min(channel + 0.3f, 1.0f));
    }

    private void renderLineStrip(Matrix4f matrix, List<Point> points, boolean withHeight, float red, float green, float blue) {
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINE_STRIP, VertexFormats.POSITION_COLOR);

        int index = 0;
        for (Point p : points) {
            float alpha = Math.min((float) index / (float) points.size() * 1.5f, 1f);
            int alphaInt = (int) (alpha * 255);

            float y = withHeight ? (float) (p.pos.y + mc.player.getHeight()) : (float) p.pos.y;

            buffer.vertex(matrix, (float) p.pos.x, y, (float) p.pos.z)
                    .color((int) (red * 255), (int) (green * 255), (int) (blue * 255), alphaInt);
            index++;
        }

        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    private void startRendering() {
        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
    }

    private void stopRendering() {
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static class Point {
        public Vec3d pos;
        public long time;

        public Point(Vec3d pos) {
            this.pos = pos;
            this.time = System.currentTimeMillis();
        }
    }
}
