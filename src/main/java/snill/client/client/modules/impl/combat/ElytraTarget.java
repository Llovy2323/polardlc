package snill.client.client.modules.impl.combat;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import snill.client.api.events.EventLink;
import snill.client.api.events.implement.Event3DRender;
import snill.client.api.storages.implement.helpertstorages.enumvar.ModuleClass;
import snill.client.api.utils.combat.PredictUtils;
import snill.client.api.utils.color.ColorUtils;
import snill.client.client.modules.Module;
import snill.client.client.modules.impl.render.BlockOverlay;
import snill.client.client.modules.settings.implement.BooleanSetting;
import snill.client.client.modules.settings.implement.FloatSetting;
import ru.virtuoz.convert.Convert;

@Convert(Convert.ConvertType.MUTATION)
public class ElytraTarget extends Module {
    public static ElytraTarget INSTANCE = new ElytraTarget();

    public final FloatSetting forward = new FloatSetting("Сила предикта", 3.0f, 1.0f, 6.0f, 1.0f);
    public final FloatSetting forwardValue = forward;
    public final BooleanSetting showIntercept = new BooleanSetting("Show Intercept", true);

    public ElytraTarget() {
        super("ElytraSample", "Таргетит игрока на элитрах", ModuleCategory.COMBAT);
        addSettings(forward, showIntercept);
    }

    public boolean isPredictionActive() {
        return mc.player != null && isEnable() && mc.player.isGliding();
    }

    public boolean isAuraActive() {
        return isPredictionActive();
    }

    public boolean isCakeWorldMode() {
        return false;
    }

    public int getForwardTicks() {
        return Math.max(0, Math.round(forward.getValue().floatValue()));
    }
    public boolean shouldSyncTargetFlight(LivingEntity target) {
        return isAuraActive()
                && target != null
                && target.isAlive()
                && target.isGliding()
                && mc.player != null
                && mc.player.squaredDistanceTo(target) <= MathHelper.square(10.0D);
    }
    public Vec3d getPredictedPoint(LivingEntity target, Vec3d point) {
        if (target == null) {
            return point;
        }

        if (!isPredictionActive()) {
            return point;
        }

        int ticks = getForwardTicks();
        Vec3d velocity = target.getVelocity();
        double targetHorizontalSpeed = Math.hypot(velocity.x, velocity.z);
        double playerHorizontalSpeed = Math.hypot(mc.player.getVelocity().x, mc.player.getVelocity().z);
        double distance = mc.player.getEyePos().distanceTo(point);
        Vec3d horizontalToTarget = new Vec3d(point.x - mc.player.getX(), 0.0D, point.z - mc.player.getZ());
        Vec3d playerHorizontalVelocity = new Vec3d(mc.player.getVelocity().x, 0.0D, mc.player.getVelocity().z);

        boolean hasPassedTarget = playerHorizontalVelocity.lengthSquared() > 0.0025D
                && playerHorizontalVelocity.normalize().dotProduct(horizontalToTarget) < -0.45D;

        // Lead aggressively only while the target is still out of hit range.
        // Keeping the long lead after contact makes us fly past the target.
        if (distance <= 4.25D) {
            ticks = Math.min(ticks, 1);
        } else if (targetHorizontalSpeed > playerHorizontalSpeed + 0.03D) {
            ticks += Math.min(4, (int) Math.ceil((targetHorizontalSpeed - playerHorizontalSpeed) * 10.0D));
        }

        // Once we have flown past the target, aim deeper along its *current*
        // vector.  This makes a target's reversal immediately become an
        // interception turn instead of a wide circle behind it.
        if (hasPassedTarget) {
            ticks = Math.max(ticks, getForwardTicks() + 2);
        }

        Vec3d predicted = PredictUtils.predict(target, point, ticks);
        // Vertical speed changes much more abruptly during an elytra dive.  Limit
        // its lead so the aim does not oscillate above and below the player.
        double maxVerticalLead = 1.25D + Math.min(1.25D, targetHorizontalSpeed * 2.0D);
        double limitedY = MathHelper.clamp(predicted.y, point.y - maxVerticalLead, point.y + maxVerticalLead);
        Vec3d intercept = new Vec3d(predicted.x, limitedY, predicted.z);
        BlockOverlay.setPredictionBox(new Box(intercept, intercept).expand(0.42D));
        return intercept;
    }
    public Vec3d getPredictedCenter(LivingEntity target) {
        if (target == null) {
            return null;
        }

        return getPredictedPoint(target, target.getBoundingBox().getCenter());
    }
    public Vec3d getAimPoint(LivingEntity target) {
        if (target == null) {
            return null;
        }

        Vec3d center = target.getBoundingBox().getCenter();
        Vec3d predicted = getPredictedPoint(target, center);
        return predicted != null ? predicted : center;
    }

    public Vec3d getAimVector(LivingEntity target) {
        if (mc.player == null) {
            return Vec3d.ZERO;
        }

        Vec3d aimPoint = getAimPoint(target);
        return aimPoint == null ? Vec3d.ZERO : aimPoint.subtract(mc.player.getEyePos());
    }

    public boolean shouldTarget(LivingEntity livingEntity) {
        return isPredictionActive() && livingEntity != null && livingEntity.isGliding();
    }

    public boolean canCriticalDuringChase() {
        // Elytra flight does not reliably update fallDistance client-side.  Using
        // it as an attack gate drops valid hit ticks and therefore loses crits.
        return mc.player != null
                && mc.player.isGliding()
                && !mc.player.isTouchingWater()
                && !mc.player.isSubmergedInWater()
                && !mc.player.isInLava()
                && !mc.player.hasVehicle()
                && !mc.player.hasStatusEffect(StatusEffects.LEVITATION);
    }

    public boolean isReverseActive() {
        return false;
    }

    public boolean hasChasePosition() {
        return false;
    }

    public double getPredictedDistance() {
        return 0.0D;
    }

    public void resetChase() {
        BlockOverlay.setPredictionBox(null);
    }
    public void updateChase(LivingEntity target, boolean shouldPredict) {
    }
    public void syncTargetFlightSpeed(LivingEntity target) {
    }

    public static void resetPredictState() {
    }

    @Override
    public void onDisable() {
        BlockOverlay.setPredictionBox(null);
        super.onDisable();
    }

    @EventLink
    @Convert(Convert.ConvertType.EXCEPTION)
    public void onRender3D(Event3DRender event) {
        if (mc.player == null || mc.world == null) {
            return;
        }

        if (!showIntercept.isState()) {
            return;
        }

        Aura aura = ModuleClass.aura;
        LivingEntity target = aura != null && aura.isEnable() ? aura.getTarget() : null;
        if (target == null || !target.isGliding()) {
            return;
        }

        Vec3d predictedCenter = getAimPoint(target);
        if (predictedCenter == null) {
            return;
        }

        Vec3d cam = event.getCamera().getPos();
        Box box = new Box(
                predictedCenter.x - 0.35D - cam.x,
                predictedCenter.y - 0.35D - cam.y,
                predictedCenter.z - 0.35D - cam.z,
                predictedCenter.x + 0.35D - cam.x,
                predictedCenter.y + 0.35D - cam.y,
                predictedCenter.z + 0.35D - cam.z
        );

        int baseColor = ColorUtils.getThemeColor();
        int fillColor = ColorUtils.setAlphaColor(baseColor, 40);
        int lineColor = ColorUtils.setAlphaColor(baseColor, 255);

        renderBox(event, box, fillColor, lineColor);
    }

    private void renderBox(Event3DRender event, Box box, int fillColor, int lineColor) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        RenderSystem.lineWidth(2.0f);

        Tessellator tessellator = Tessellator.getInstance();
        drawFilledBox(tessellator, event, box, fillColor);
        drawBoxOutline(tessellator, event, box, lineColor);

        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
    }
    @Convert(Convert.ConvertType.EXCEPTION)
    private void drawFilledBox(Tessellator tessellator, Event3DRender event, Box box, int color) {
        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        float minX = (float) box.minX;
        float minY = (float) box.minY;
        float minZ = (float) box.minZ;
        float maxX = (float) box.maxX;
        float maxY = (float) box.maxY;
        float maxZ = (float) box.maxZ;

        buffer.vertex(event.getMatrices().peek().getPositionMatrix(), minX, minY, minZ).color(color);
        buffer.vertex(event.getMatrices().peek().getPositionMatrix(), minX, minY, maxZ).color(color);
        buffer.vertex(event.getMatrices().peek().getPositionMatrix(), maxX, minY, maxZ).color(color);
        buffer.vertex(event.getMatrices().peek().getPositionMatrix(), maxX, minY, minZ).color(color);

        buffer.vertex(event.getMatrices().peek().getPositionMatrix(), minX, maxY, minZ).color(color);
        buffer.vertex(event.getMatrices().peek().getPositionMatrix(), maxX, maxY, minZ).color(color);
        buffer.vertex(event.getMatrices().peek().getPositionMatrix(), maxX, maxY, maxZ).color(color);
        buffer.vertex(event.getMatrices().peek().getPositionMatrix(), minX, maxY, maxZ).color(color);

        buffer.vertex(event.getMatrices().peek().getPositionMatrix(), minX, minY, minZ).color(color);
        buffer.vertex(event.getMatrices().peek().getPositionMatrix(), minX, maxY, minZ).color(color);
        buffer.vertex(event.getMatrices().peek().getPositionMatrix(), maxX, maxY, minZ).color(color);
        buffer.vertex(event.getMatrices().peek().getPositionMatrix(), maxX, minY, minZ).color(color);

        buffer.vertex(event.getMatrices().peek().getPositionMatrix(), minX, minY, maxZ).color(color);
        buffer.vertex(event.getMatrices().peek().getPositionMatrix(), maxX, minY, maxZ).color(color);
        buffer.vertex(event.getMatrices().peek().getPositionMatrix(), maxX, maxY, maxZ).color(color);
        buffer.vertex(event.getMatrices().peek().getPositionMatrix(), minX, maxY, maxZ).color(color);

        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    private void drawBoxOutline(Tessellator tessellator, Event3DRender event, Box box, int color) {
        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        float minX = (float) box.minX;
        float minY = (float) box.minY;
        float minZ = (float) box.minZ;
        float maxX = (float) box.maxX;
        float maxY = (float) box.maxY;
        float maxZ = (float) box.maxZ;

        buffer.vertex(event.getMatrices().peek().getPositionMatrix(), minX, minY, minZ).color(color);
        buffer.vertex(event.getMatrices().peek().getPositionMatrix(), maxX, minY, minZ).color(color);
        buffer.vertex(event.getMatrices().peek().getPositionMatrix(), maxX, minY, minZ).color(color);
        buffer.vertex(event.getMatrices().peek().getPositionMatrix(), maxX, minY, maxZ).color(color);
        buffer.vertex(event.getMatrices().peek().getPositionMatrix(), maxX, minY, maxZ).color(color);
        buffer.vertex(event.getMatrices().peek().getPositionMatrix(), minX, minY, maxZ).color(color);
        buffer.vertex(event.getMatrices().peek().getPositionMatrix(), minX, minY, maxZ).color(color);
        buffer.vertex(event.getMatrices().peek().getPositionMatrix(), minX, minY, minZ).color(color);

        buffer.vertex(event.getMatrices().peek().getPositionMatrix(), minX, maxY, minZ).color(color);
        buffer.vertex(event.getMatrices().peek().getPositionMatrix(), maxX, maxY, minZ).color(color);
        buffer.vertex(event.getMatrices().peek().getPositionMatrix(), maxX, maxY, minZ).color(color);
        buffer.vertex(event.getMatrices().peek().getPositionMatrix(), maxX, maxY, maxZ).color(color);
        buffer.vertex(event.getMatrices().peek().getPositionMatrix(), maxX, maxY, maxZ).color(color);
        buffer.vertex(event.getMatrices().peek().getPositionMatrix(), minX, maxY, maxZ).color(color);
        buffer.vertex(event.getMatrices().peek().getPositionMatrix(), minX, maxY, maxZ).color(color);
        buffer.vertex(event.getMatrices().peek().getPositionMatrix(), minX, maxY, minZ).color(color);

        buffer.vertex(event.getMatrices().peek().getPositionMatrix(), minX, minY, minZ).color(color);
        buffer.vertex(event.getMatrices().peek().getPositionMatrix(), minX, maxY, minZ).color(color);
        buffer.vertex(event.getMatrices().peek().getPositionMatrix(), maxX, minY, minZ).color(color);
        buffer.vertex(event.getMatrices().peek().getPositionMatrix(), maxX, maxY, minZ).color(color);
        buffer.vertex(event.getMatrices().peek().getPositionMatrix(), maxX, minY, maxZ).color(color);
        buffer.vertex(event.getMatrices().peek().getPositionMatrix(), maxX, maxY, maxZ).color(color);
        buffer.vertex(event.getMatrices().peek().getPositionMatrix(), minX, minY, maxZ).color(color);
        buffer.vertex(event.getMatrices().peek().getPositionMatrix(), minX, maxY, maxZ).color(color);

        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }
}
