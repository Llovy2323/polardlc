package snill.client.api.storages.implement;

import lombok.Getter;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import snill.client.api.QClient;
import snill.client.api.events.EventInvoker;
import snill.client.api.events.EventLink;
import snill.client.api.events.implement.EventRender;
import snill.client.api.utils.animation.AnimationUtils;
import snill.client.api.utils.animation.Easings;
import snill.client.api.utils.cmd.waypoint.Waypoint;
import snill.client.api.utils.color.ColorUtils;
import snill.client.api.utils.math.MathUtils;
import snill.client.api.utils.render.RenderUtils;
import snill.client.api.utils.render.fonts.msdf.Font;
import snill.client.api.utils.render.fonts.msdf.Fonts;

public class WaypointStorage implements QClient {

    private static final Identifier ARROW_TEXTURE = Identifier.of("snill", "textures/arrows/arrow.png");

    private final AnimationUtils alphaAnimation = new AnimationUtils(0.0f, 8.5f, Easings.CUBIC_OUT);
    private float animatedYaw;

    public WaypointStorage() {
        EventInvoker.register(this);
    }

    @Getter
    private Waypoint activeWaypoint = null;

    public void set(Waypoint waypoint) {
        this.activeWaypoint = waypoint;
    }

    public void remove(Waypoint waypoint) {
        if (this.activeWaypoint != null && this.activeWaypoint.equals(waypoint)) {
            this.activeWaypoint = null;
        }
    }

    public void clear() {
        this.activeWaypoint = null;
    }

    public boolean isEmpty() {
        return activeWaypoint == null;
    }

    @EventLink
    public void onRender2D(EventRender.Default event) {
        if (mc.player == null || mc.world == null) {
            return;
        }

        alphaAnimation.update(activeWaypoint == null ? 0.0f : 1.0f);
        float alpha = MathHelper.clamp(alphaAnimation.getValue(), 0.0f, 1.0f);
        if (activeWaypoint == null || alpha <= 0.02f) {
            return;
        }

        float centerX = mc.getWindow().getScaledWidth() * 0.5f;
        float centerY = mc.getWindow().getScaledHeight() * 0.25f;
        float size = 20.0f;

        double deltaX = activeWaypoint.getX() - mc.player.getX();
        double deltaZ = activeWaypoint.getZ() - mc.player.getZ();
        int distance = (int) MathUtils.round(MathHelper.sqrt((float) (deltaX * deltaX + deltaZ * deltaZ)));

        float targetYaw = (float) -Math.toDegrees(Math.atan2(deltaX, deltaZ)) - mc.gameRenderer.getCamera().getYaw();
        animatedYaw = interpolateAngle(animatedYaw, targetYaw, 0.18f);

        int color = ColorUtils.applyAlpha(ColorUtils.getThemeColor(), alpha);

        Font font = Fonts.getFont("sf_regular", 12);
        if (font != null) {
            String distanceText = distance + "m.";
            font.draw(event.getContext().getMatrices(), distanceText,
                    (centerX - font.getWidth(distanceText) * 0.5f) + 1.5f,
                    centerY + 7.5f,
                    ColorUtils.applyAlpha(0xFFFFFFFF, alpha));
        }

        event.getContext().getMatrices().push();
        event.getContext().getMatrices().translate(centerX, centerY, 0.0f);
        event.getContext().getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(animatedYaw));
        event.getContext().getMatrices().translate(-centerX, -centerY, 0.0f);

        float drawX = centerX - size * 0.5f;
        float drawY = centerY - size * 0.5f;

        RenderUtils.drawImage(event.getContext().getMatrices(), ARROW_TEXTURE, drawX, drawY, size, size, color);
        event.getContext().getMatrices().pop();
    }

    private float interpolateAngle(float current, float target, float factor) {
        float delta = MathHelper.wrapDegrees(target - current);
        return current + delta * factor;
    }
}
