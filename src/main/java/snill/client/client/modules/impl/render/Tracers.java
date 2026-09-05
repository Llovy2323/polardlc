package snill.client.client.modules.impl.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import snill.client.Snill;
import snill.client.api.events.EventLink;
import snill.client.api.events.implement.Event3DRender;
import snill.client.api.utils.color.ColorUtils;
import snill.client.api.utils.math.MathUtils;
import snill.client.client.modules.Module;
import snill.client.client.modules.impl.combat.AntiBot;
import snill.client.client.modules.settings.implement.BooleanSetting;
import snill.client.client.modules.settings.implement.FloatSetting;
import snill.client.client.modules.settings.implement.ListSetting;
import snill.client.client.modules.settings.implement.ModeSetting;

public class Tracers extends Module {
    public static Tracers INSTANCE = new Tracers();

    private final ListSetting targets = new ListSetting("Отображать",
            new BooleanSetting("Игроки", true),
            new BooleanSetting("Друзья", true),
            new BooleanSetting("Предметы", false),
            new BooleanSetting("Мобы", false)
    );
    private final FloatSetting lineWidth = new FloatSetting("Толщина", 1.0f, 0.5f, 5.0f, 0.1f);
    private final ModeSetting colorMode = new ModeSetting("Цвет", "По типу",
            "По типу", "Тема", "Белый", "Красный", "Зелёный", "Синий", "Голубой", "Жёлтый", "Свой"
    );
    private final FloatSetting customRed = new FloatSetting("Красный", 255.0f, 0.0f, 255.0f, 1.0f).visible(() -> colorMode.is("Свой"));
    private final FloatSetting customGreen = new FloatSetting("Зелёный", 255.0f, 0.0f, 255.0f, 1.0f).visible(() -> colorMode.is("Свой"));
    private final FloatSetting customBlue = new FloatSetting("Синий", 255.0f, 0.0f, 255.0f, 1.0f).visible(() -> colorMode.is("Свой"));

    public Tracers() {
        super("Tracers", "Рисует линии до сущностей", ModuleCategory.RENDER);
        addSettings(targets, lineWidth, colorMode, customRed, customGreen, customBlue);
    }

    @EventLink
    public void onRender3D(Event3DRender event) {
        if (mc.player == null || mc.world == null) return;

        Camera camera = event.getCamera();
        Vec3d cameraPos = camera.getPos();
        Vec3d start = cameraPos.add(Vec3d.fromPolar(camera.getPitch(), camera.getYaw()).multiply(150.0));

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        RenderSystem.lineWidth(lineWidth.get());

        MatrixStack matrices = event.getMatrices();
        matrices.push();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        float tickDelta = event.getTickDelta();
        boolean hasLines = false;
        for (Entity entity : mc.world.getEntities()) {
            if (!shouldRender(entity)) continue;

            Vec3d pos = MathUtils.interpolate(entity, tickDelta).add(0.0, entity.getHeight() * 0.5, 0.0);
            int color = getEntityColor(entity);
            int a = Math.max((color >> 24) & 0xFF, 255);
            int r = (color >> 16) & 0xFF;
            int g = (color >> 8) & 0xFF;
            int b = color & 0xFF;

            buffer.vertex(matrix, (float) start.x, (float) start.y, (float) start.z).color(r, g, b, a);
            buffer.vertex(matrix, (float) pos.x, (float) pos.y, (float) pos.z).color(r, g, b, a);
            hasLines = true;
        }

        if (hasLines) {
            BufferRenderer.drawWithGlobalProgram(buffer.end());
        }

        matrices.pop();

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.lineWidth(1.0f);
    }

    private boolean shouldRender(Entity entity) {
        if (entity == null || entity == mc.player) return false;
        if (entity instanceof LivingEntity living && !living.isAlive()) return false;

        if (entity instanceof PlayerEntity player) {
            if (AntiBot.checkBot(player)) return false;
            if (player.isSpectator()) return false;
            boolean friend = isFriend(player);
            return friend ? targets.is("Друзья") : targets.is("Игроки");
        }

        if (entity instanceof ItemEntity) {
            return targets.is("Предметы");
        }

        return (entity instanceof AnimalEntity || entity instanceof MobEntity) && targets.is("Мобы");
    }

    private int getEntityColor(Entity entity) {
        if (!colorMode.is("По типу")) {
            return getSelectedColor();
        }

        if (entity instanceof PlayerEntity player) {
            return isFriend(player) ? ColorUtils.rgba(0, 255, 255, 255) : ColorUtils.getThemeColor();
        }
        if (entity instanceof ItemEntity) {
            return ColorUtils.rgba(0, 255, 0, 255);
        }
        return ColorUtils.rgba(255, 0, 0, 255);
    }

    private int getSelectedColor() {
        if (colorMode.is("Тема")) return ColorUtils.getThemeColor();
        if (colorMode.is("Белый")) return ColorUtils.rgba(255, 255, 255, 255);
        if (colorMode.is("Красный")) return ColorUtils.rgba(255, 0, 0, 255);
        if (colorMode.is("Зелёный")) return ColorUtils.rgba(0, 255, 0, 255);
        if (colorMode.is("Синий")) return ColorUtils.rgba(0, 90, 255, 255);
        if (colorMode.is("Голубой")) return ColorUtils.rgba(0, 255, 255, 255);
        if (colorMode.is("Жёлтый")) return ColorUtils.rgba(255, 230, 0, 255);
        return ColorUtils.rgba(customRed.getValue().intValue(), customGreen.getValue().intValue(), customBlue.getValue().intValue(), 255);
    }

    private boolean isFriend(PlayerEntity player) {
        return Snill.INSTANCE.friendStorage.isFriend(player.getGameProfile().getName());
    }
}



