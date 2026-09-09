package snill.client.client.modules.impl.render;

import net.minecraft.client.option.Perspective;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.MathHelper;
import snill.client.api.events.EventLink;
import snill.client.api.events.implement.EventRender;
import snill.client.api.utils.color.ColorUtils;
import snill.client.api.utils.render.RenderUtils;
import snill.client.client.modules.Module;
import snill.client.client.modules.settings.implement.BooleanSetting;
import snill.client.client.modules.settings.implement.FloatSetting;
import snill.client.client.modules.settings.implement.ModeSetting;

public class CustomCrosshair extends Module {

    public static CustomCrosshair INSTANCE = new CustomCrosshair();

    private static final int TARGET_COLOR = ColorUtils.rgba(255, 65, 65, 255);
    private static final int SHADOW_COLOR = ColorUtils.rgba(0, 0, 0, 190);
    private static final int COOLDOWN_BACKGROUND = ColorUtils.rgba(0, 0, 0, 120);

    private final ModeSetting style = new ModeSetting(
            "Стиль",
            "Cross",
            "Dot",
            "Cross",
            "Circle",
            "Dynamic"
    );

    private final FloatSetting size = new FloatSetting("Размер", 5.0f, 1.0f, 20.0f, 0.5f);
    private final FloatSetting thickness = new FloatSetting("Толщина линий", 1.5f, 0.5f, 5.0f, 0.25f);
    private final FloatSetting gap = new FloatSetting("Зазор", 3.0f, 0.0f, 15.0f, 0.5f);

    private final BooleanSetting dynamic = new BooleanSetting("Динамика", true);
    private final BooleanSetting targetHighlight = new BooleanSetting("Подсветка цели", true);
    private final BooleanSetting attackCooldown = new BooleanSetting("Кулдаун атаки", true);

    private float animatedSpread;
    private float animatedCooldown = 1.0f;

    public CustomCrosshair() {
        super("CustomCrosshair", "Настраиваемый прицел", ModuleCategory.RENDER);
        addSettings(style, size, thickness, gap, dynamic, targetHighlight, attackCooldown);
    }

    @EventLink
    public void onRender(EventRender.Default event) {
        if (mc.player == null || mc.world == null || mc.options.hudHidden) {
            animatedSpread = 0.0f;
            animatedCooldown = 1.0f;
            return;
        }

        if (mc.options.getPerspective() != Perspective.FIRST_PERSON) {
            return;
        }

        int centerX = event.getContext().getScaledWindowWidth() / 2;
        int centerY = event.getContext().getScaledWindowHeight() / 2;

        float lineSize = size.get();
        float lineThickness = thickness.get();
        float baseGap = gap.get();

        boolean dynamicStyle = style.is("Dynamic");
        float targetSpread = dynamic.isState() || dynamicStyle ? calculateMovementSpread() : 0.0f;
        animatedSpread = MathHelper.lerp(0.22f, animatedSpread, targetSpread);

        float currentGap = baseGap + animatedSpread;
        int color = isLookingAtLivingEntity() ? TARGET_COLOR : ColorUtils.getThemeColor();

        if (style.is("Dot")) {
            drawDot(event, centerX, centerY, lineSize, color);
        } else if (style.is("Circle")) {
            drawCircle(event, centerX, centerY, lineSize, lineThickness, currentGap, color);
        } else {
            drawCross(event, centerX, centerY, lineSize, lineThickness, currentGap, color);

            if (dynamicStyle) {
                float dotSize = Math.max(1.5f, lineThickness);
                drawDot(event, centerX, centerY, dotSize, color);
            }
        }

        if (attackCooldown.isState()) {
            drawAttackCooldown(event, centerX, centerY, lineSize, lineThickness, currentGap, color);
        } else {
            animatedCooldown = 1.0f;
        }
    }

    private void drawDot(EventRender.Default event, float centerX, float centerY, float diameter, int color) {
        float dotSize = Math.max(1.0f, diameter);

        RenderUtils.drawRoundCircle(
                event.getContext().getMatrices(),
                centerX,
                centerY,
                dotSize + 1.5f,
                SHADOW_COLOR
        );
        RenderUtils.drawRoundCircle(
                event.getContext().getMatrices(),
                centerX,
                centerY,
                dotSize,
                color
        );
    }

    private void drawCross(
            EventRender.Default event,
            float centerX,
            float centerY,
            float lineSize,
            float lineThickness,
            float currentGap,
            int color
    ) {
        float halfThickness = lineThickness * 0.5f;

        drawCrossPart(event, centerX - currentGap - lineSize, centerY - halfThickness,
                lineSize, lineThickness, color);
        drawCrossPart(event, centerX + currentGap, centerY - halfThickness,
                lineSize, lineThickness, color);
        drawCrossPart(event, centerX - halfThickness, centerY - currentGap - lineSize,
                lineThickness, lineSize, color);
        drawCrossPart(event, centerX - halfThickness, centerY + currentGap,
                lineThickness, lineSize, color);
    }

    private void drawCrossPart(
            EventRender.Default event,
            float x,
            float y,
            float width,
            float height,
            int color
    ) {
        float outline = 0.75f;
        float radius = Math.min(width, height) * 0.5f;

        RenderUtils.drawRoundedRect(
                event.getContext().getMatrices(),
                x - outline,
                y - outline,
                width + outline * 2.0f,
                height + outline * 2.0f,
                radius + outline,
                SHADOW_COLOR
        );
        RenderUtils.drawRoundedRect(
                event.getContext().getMatrices(),
                x,
                y,
                width,
                height,
                radius,
                color
        );
    }

    private void drawCircle(
            EventRender.Default event,
            float centerX,
            float centerY,
            float circleRadius,
            float lineThickness,
            float currentGap,
            int color
    ) {
        float diameter = Math.max(2.0f, (circleRadius + currentGap) * 2.0f);
        float x = centerX - diameter * 0.5f;
        float y = centerY - diameter * 0.5f;

        RenderUtils.drawRingArc(
                event.getContext().getMatrices(),
                x,
                y,
                diameter,
                lineThickness + 1.5f,
                -180.0f,
                180.0f,
                SHADOW_COLOR
        );
        RenderUtils.drawRingArc(
                event.getContext().getMatrices(),
                x,
                y,
                diameter,
                lineThickness,
                -180.0f,
                180.0f,
                color
        );
    }

    private void drawAttackCooldown(
            EventRender.Default event,
            float centerX,
            float centerY,
            float lineSize,
            float lineThickness,
            float currentGap,
            int color
    ) {
        float cooldown = MathHelper.clamp(mc.player.getAttackCooldownProgress(0.0f), 0.0f, 1.0f);
        animatedCooldown = MathHelper.lerp(0.28f, animatedCooldown, cooldown);

        float diameter = Math.max(16.0f, (lineSize + currentGap) * 2.0f + 8.0f);
        float ringThickness = Math.max(1.0f, lineThickness * 0.7f);
        float x = centerX - diameter * 0.5f;
        float y = centerY - diameter * 0.5f;

        RenderUtils.drawRingArc(
                event.getContext().getMatrices(),
                x,
                y,
                diameter,
                ringThickness,
                -90.0f,
                270.0f,
                COOLDOWN_BACKGROUND
        );

        if (animatedCooldown > 0.001f) {
            RenderUtils.drawRingArc(
                    event.getContext().getMatrices(),
                    x,
                    y,
                    diameter,
                    ringThickness,
                    -90.0f,
                    -90.0f + 360.0f * animatedCooldown,
                    color
            );
        }
    }

    private float calculateMovementSpread() {
        double horizontalSpeed = mc.player.getVelocity().horizontalLength();
        float spread = MathHelper.clamp((float) horizontalSpeed * 32.0f, 0.0f, 7.0f);

        if (mc.player.isSprinting()) {
            spread += 2.0f;
        }
        if (!mc.player.isOnGround()) {
            spread += 3.0f;
        }

        return MathHelper.clamp(spread, 0.0f, 10.0f);
    }

    private boolean isLookingAtLivingEntity() {
        if (!targetHighlight.isState()) {
            return false;
        }

        if (!(mc.crosshairTarget instanceof EntityHitResult entityHitResult)) {
            return false;
        }

        return entityHitResult.getEntity() instanceof LivingEntity livingEntity
                && livingEntity.isAlive();
    }
}
