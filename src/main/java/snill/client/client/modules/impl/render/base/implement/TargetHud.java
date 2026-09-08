package snill.client.client.modules.impl.render.base.implement;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.lwjgl.opengl.GL11;
import net.minecraft.util.hit.EntityHitResult;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;
import snill.client.api.events.implement.EventRender;
import snill.client.api.storages.implement.helpertstorages.enumvar.ModuleClass;
import snill.client.api.utils.animation.AnimationUtils;
import snill.client.api.utils.animation.Easings;
import snill.client.api.utils.color.ColorUtils;
import snill.client.api.utils.draggable.Draggable;
import snill.client.api.utils.render.RenderUtils;
import snill.client.api.utils.render.fonts.msdf.Font;
import snill.client.api.utils.render.fonts.msdf.Fonts;
import snill.client.client.modules.impl.misc.NameProtect;
import snill.client.client.modules.impl.misc.ScoreboardHP;
import snill.client.api.utils.math.HoveringUtils;
import snill.client.client.modules.impl.render.base.InterfaceProcessing;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Minecraft 1.21.4 / Yarn 1.21.4+build.8 / Java 21.
 * All colors are ARGB. Coordinates and sizes are GUI-scaled pixels.
 *
 * Render-thread only. Call close() when removing this HUD from Interface.
 * The class owns no textures, shaders, mixins or network requests.
 * The optional framebuffer is allocated only for fading native item/model passes.
 */
public class TargetHud extends InterfaceProcessing implements AutoCloseable {
    public enum Mode {
        LLOVY12XC("Llovy12XC", 110.0f, 46.0f);

        private final String title;
        private final float width;
        private final float height;

        Mode(String title, float width, float height) {
            this.title = title;
            this.width = width;
            this.height = height;
        }

        public String getTitle() { return title; }
        public float getWidth() { return width; }
        public float getHeight() { return height; }
        @Override public String toString() { return title; }
    }

    private static final int TEXT = 0xFFF1F3F8;
    private static final int MUTED = 0xFF9CA5B7;
    private static final int SURFACE = 0xEF121620;
    private static final int TRACK = 0xFF272E3C;
    private static final int GREEN = 0xFF72DCA6;
    private static final int YELLOW = 0xFFF0C96A;
    private static final int RED = 0xFFF07885;
    private static final int GOLD = 0xFFFFCE68;
    private static final int FULL_BRIGHT = 0x00F000F0;
    private static final float FADE_EPSILON = 0.015f;
    private static final float TRAIL_DELAY = 0.24f;
    private static final int MAX_PARTICLES = 64;
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST,
            EquipmentSlot.LEGS, EquipmentSlot.FEET
    };
    private static final String[] SLOT_LABELS = {"H", "C", "L", "B", "M", "O"};

    private final MinecraftClient client = MinecraftClient.getInstance();
    private final AnimationUtils alphaAnimation =
            new AnimationUtils(0.0f, 16.0f, Easings.QUAD_OUT);
    private final List<HeadParticle> headParticles = new ArrayList<>(MAX_PARTICLES);
    private final ItemStack[] equipment = {
            ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY,
            ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY
    };

    private Mode mode = Mode.LLOVY12XC;
    private boolean headParticlesEnabled = true;
    private boolean healthBarStyleEnabled;
    private LivingEntity displayedTarget;
    private ClientWorld lastWorld;
    private long lastFrameNs;
    private float rawHp;
    private float rawAbsorption;
    private float effectiveMaxHp = 20.0f;
    private float smoothHp;
    private float smoothAbsorption;
    private float trailHp;
    private float trailDelay;
    private float hurtFlash;
    private int lastHurtTime;
    private float dollYaw;
    private float dollTilt;
    private String displayName = "";
    private String distanceText = "";
    private int latency = -1;
    private int themeColor = 0xFF8B7AF0;
    private Advantage advantage = Advantage.EQUAL;
    private SimpleFramebuffer nativeFramebuffer;
    public static boolean renderingDoll = false;

    public TargetHud(Draggable draggable) {
        super(draggable);
        updateDimensions();
    }

    public boolean isHeadParticlesEnabled() { return headParticlesEnabled; }

    public void setHeadParticlesEnabled(boolean enabled) {
        headParticlesEnabled = enabled;
        if (!enabled) headParticles.clear();
    }

    /** False = continuous HP bar; true = ten subtle divisions. */
    public boolean isHealthBarStyleEnabled() { return healthBarStyleEnabled; }
    public void setHealthBarStyleEnabled(boolean enabled) { healthBarStyleEnabled = enabled; }

    /** String API is convenient for the existing context menu and config storage. */
    public String getMode() { return mode.title; }
    public Mode getModeValue() { return mode; }
    public int getModeIndex() { return mode.ordinal(); }

    public void setMode(Mode mode) {
        Mode next = Objects.requireNonNull(mode, "mode");
        if (this.mode == next) return;
        this.mode = next;
        headParticles.clear();
        updateDimensions();
    }

    public void setMode(int index) {
        Mode[] modes = Mode.values();
        setMode(modes[Math.floorMod(index, modes.length)]);
    }

    public void setMode(String value) {
        this.mode = Mode.LLOVY12XC;
        updateDimensions();
    }

    public void nextMode() { setMode(mode.ordinal() + 1); }
    public void previousMode() { setMode(mode.ordinal() - 1); }

    public void drawModeSelector(DrawContext context, float x, float y, float width, double mouseX, double mouseY) {
        MatrixStack m = context.getMatrices();
        float height = 13.0f;
        boolean hovered = HoveringUtils.isHovered(mouseX, mouseY, x, y, width, height);

        int bg = hovered ? 0xB0283042 : 0x701C2230;
        RenderUtils.drawRoundedRect(m, x, y, width, height, 2.5f, bg);
        int theme = ColorUtils.getThemeColor() | 0xFF000000;
        RenderUtils.drawRoundedRectOutline(m, x, y, width, height, 2.5f, 0.5f,
                hovered ? theme : 0x408B7AF0);

        Font font = bold(9);
        String label = mode.title;
        float labelW = font.getWidth(label);

        // Font.draw() internally subtracts 1.5f; adding 1.8f centers the glyphs vertically in a 13px box.
        float textY = y + (height - font.getHeight()) * 0.5f + 1.8f;
        font.draw(m, label, x + (width - labelW) * 0.5f, textY, TEXT);

        Font arrowFont = regular(8);
        float arrowY = y + (height - arrowFont.getHeight()) * 0.5f + 1.8f;
        boolean leftHover = HoveringUtils.isHovered(mouseX, mouseY, x, y, 16.0f, height);
        boolean rightHover = HoveringUtils.isHovered(mouseX, mouseY, x + width - 16.0f, y, 16.0f, height);
        arrowFont.draw(m, "<", x + 5.0f, arrowY, leftHover ? 0xFFFFFFFF : MUTED);
        arrowFont.draw(m, ">", x + width - 5.0f - arrowFont.getWidth(">"), arrowY, rightHover ? 0xFFFFFFFF : MUTED);
    }

    public boolean clickModeSelector(double mouseX, double mouseY, int button, float x, float y, float width) {
        float height = 13.0f;
        if (HoveringUtils.isHovered(mouseX, mouseY, x, y, width, height)) {
            if (mouseX < x + 18.0f || button == 1) {
                previousMode();
                return true;
            } else {
                nextMode();
                return true;
            }
        }
        return false;
    }

    private void updateDimensions() {
        draggable.setWidth(mode.width);
        draggable.setHeight(mode.height);
    }

    @Override
    public void onRender(EventRender.Default eventRender) {
        updateDimensions();
        long now = System.nanoTime();
        float dt = lastFrameNs == 0L ? 1.0f / 60.0f
                : MathHelper.clamp((now - lastFrameNs) / 1_000_000_000.0f, 0.0f, 0.1f);
        lastFrameNs = now;

        if (client.world != lastWorld) {
            resetTarget();
            releaseFramebuffer();
            lastWorld = client.world;
        }
        if (client.player == null || client.world == null) {
            resetTarget();
            releaseFramebuffer();
            return;
        }

        LivingEntity wanted = resolveTarget();
        // Keep the old target until it has faded out. Never interpolate A's HP into B's.
        if (displayedTarget == null && wanted != null) adoptTarget(wanted);
        boolean show = wanted != null && wanted == displayedTarget;
        alphaAnimation.update(show ? 1.0f : 0.0f);
        float alpha = unit(alphaAnimation.getValue());

        if (!show && alpha <= FADE_EPSILON) {
            resetTarget();
            if (wanted == null) {
                releaseFramebuffer();
                return;
            }
            adoptTarget(wanted);
            return; // Start the incoming target next frame at zero alpha.
        }
        if (displayedTarget == null) return;
        if (show && alpha >= 1.0f - FADE_EPSILON) {
            alpha = 1.0f;
            alphaAnimation.setValue(1.0f);
            releaseFramebuffer();
        }

        updateTarget(dt);
        updateParticles(dt);
        if (alpha <= 0.001f) return;

        DrawContext context = eventRender.getContext();
        MatrixStack matrices = context.getMatrices();
        float x = draggable.getX();
        float y = draggable.getY();
        float tickDelta = MathHelper.clamp(eventRender.getPartialTicks(), 0.0f, 1.0f);
        themeColor = ColorUtils.getThemeColor() | 0xFF000000;

        // Flush vanilla's deferred GUI vertices before interleaving immediate MSDF/blur.
        context.draw();
        try (GlState ignored = new GlState()) {
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.disableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            drawPanel(matrices, x, y, mode.width, mode.height, alpha);
            renderLlovy12XC(context, x, y, alpha, tickDelta);
            renderParticles(matrices, x, y, alpha);
            context.draw();
        }
    }

    private LivingEntity lastManualTarget;
    private long lastManualTargetTime;

    private LivingEntity resolveTarget() {
        var aura = ModuleClass.aura;
        LivingEntity target = aura == null ? null : aura.getTarget();
        if (validTarget(target)) return target;

        if (client.crosshairTarget instanceof EntityHitResult eHit && eHit.getEntity() instanceof LivingEntity living && validTarget(living)) {
            lastManualTarget = living;
            lastManualTargetTime = System.currentTimeMillis();
            return living;
        }
        if (client.targetedEntity instanceof LivingEntity living && validTarget(living)) {
            lastManualTarget = living;
            lastManualTargetTime = System.currentTimeMillis();
            return living;
        }

        if (lastManualTarget != null && System.currentTimeMillis() - lastManualTargetTime < 250L && validTarget(lastManualTarget)) {
            return lastManualTarget;
        }

        return client.currentScreen instanceof ChatScreen ? client.player : null;
    }

    private boolean validTarget(LivingEntity target) {
        return target != null && target.isAlive() && !target.isRemoved()
                && target.getWorld() == client.world;
    }

    private void adoptTarget(LivingEntity target) {
        displayedTarget = target;
        rawHp = health(target);
        rawAbsorption = nonNegative(target.getAbsorptionAmount());
        effectiveMaxHp = Math.max(1.0f, Math.max(nonNegative(target.getMaxHealth()), rawHp));
        smoothHp = trailHp = rawHp;
        smoothAbsorption = rawAbsorption;
        trailDelay = 0.0f;
        lastHurtTime = target.hurtTime;
        hurtFlash = unit(target.hurtTime / 10.0f);
        dollYaw = 0.0f;
        dollTilt = 0.0f;
        headParticles.clear();
        updateTarget(0.0f);
    }

    private void resetTarget() {
        displayedTarget = null;
        alphaAnimation.setValue(0.0f);
        headParticles.clear();
        for (int i = 0; i < equipment.length; i++) equipment[i] = ItemStack.EMPTY;
        trailDelay = 0.0f;
        lastHurtTime = 0;
        hurtFlash = 0.0f;
    }

    private void updateTarget(float dt) {
        LivingEntity target = displayedTarget;
        float hp = health(target);
        float absorption = nonNegative(target.getAbsorptionAmount());
        boolean healthDropped = hp < rawHp - 0.01f;
        boolean absorptionDropped = absorption < rawAbsorption - 0.01f;
        boolean hurtStarted = target.hurtTime > lastHurtTime;

        if (healthDropped) {
            trailHp = Math.max(trailHp, Math.max(rawHp, smoothHp));
            trailDelay = TRAIL_DELAY;
        }
        // Avoid spawning twice if the scoreboard updates one frame after hurtTime.
        if ((healthDropped || absorptionDropped || hurtStarted) && hurtFlash < 0.72f) {
            hurtFlash = 1.0f;
            if (headParticlesEnabled) spawnParticles();
        }
        rawHp = hp;
        rawAbsorption = absorption;
        lastHurtTime = target.hurtTime;
        effectiveMaxHp = Math.max(effectiveMaxHp,
                Math.max(Math.max(1.0f, nonNegative(target.getMaxHealth())), hp));
        smoothHp = damp(smoothHp, hp, 12.0f, dt);
        smoothAbsorption = damp(smoothAbsorption, absorption, 12.0f, dt);
        trailDelay = Math.max(0.0f, trailDelay - dt);
        if (hp >= trailHp) trailHp = hp; // Healing must not leave a backwards damage trail.
        else if (trailDelay == 0.0f) trailHp = damp(trailHp, hp, 3.7f, dt);
        trailHp = Math.max(trailHp, smoothHp);
        hurtFlash = damp(hurtFlash, 0.0f, 5.0f, dt);

        String rawName = target.getName().getString();
        displayName = NameProtect.INSTANCE != null && NameProtect.INSTANCE.isEnable()
                ? NameProtect.INSTANCE.patch(rawName) : rawName;
        if (displayName == null) displayName = "";
        displayName = displayName.replaceAll("\\u00A7.", "")
                .replace('\n', ' ').replace('\r', ' ');
        distanceText = decimal(client.player.distanceTo(target)) + "m";
        latency = -1;
        if (target instanceof PlayerEntity && client.getNetworkHandler() != null) {
            var entry = client.getNetworkHandler().getPlayerListEntry(target.getUuid());
            if (entry != null) latency = entry.getLatency();
        }
        advantage = computeAdvantage(target);
        for (int i = 0; i < ARMOR_SLOTS.length; i++) {
            equipment[i] = target.getEquippedStack(ARMOR_SLOTS[i]);
        }
        equipment[4] = target.getMainHandStack();
        equipment[5] = target.getOffHandStack();

        float targetYaw;
        float targetTilt;
        if (client.currentScreen != null) {
            double mx = client.mouse.getX() * client.getWindow().getScaledWidth()
                    / Math.max(1, client.getWindow().getWidth());
            double my = client.mouse.getY() * client.getWindow().getScaledHeight()
                    / Math.max(1, client.getWindow().getHeight());
            targetYaw = (float) MathHelper.clamp((mx - draggable.getX() - 38.0) * 0.18, -28.0, 28.0);
            targetTilt = (float) MathHelper.clamp((my - draggable.getY() - 54.0) * 0.08, -9.0, 9.0);
        } else {
            targetYaw = MathHelper.clamp(MathHelper.wrapDegrees(
                    client.player.getYaw() - target.getYaw()) * 0.10f, -16.0f, 16.0f);
            targetTilt = 0.0f;
        }
        dollYaw = damp(dollYaw, targetYaw, 9.0f, dt);
        dollTilt = damp(dollTilt, targetTilt, 9.0f, dt);
    }

    private static float health(LivingEntity entity) {
        if (!entity.isAlive()) return 0.0f;
        float scoreboard = ScoreboardHP.getHealth(entity);
        return Float.isFinite(scoreboard) && scoreboard >= 0.0f
                ? scoreboard : nonNegative(entity.getHealth());
    }

    private enum Advantage {
        WINNING("WINNING", GREEN), EQUAL("EQUAL", YELLOW), RISKY("RISKY", RED);
        final String title;
        final int color;
        Advantage(String title, int color) { this.title = title; this.color = color; }
    }

    /** Heuristic, not a damage simulator: effective health for a nominal 8-damage hit. */
    private Advantage computeAdvantage(LivingEntity target) {
        if (target == client.player) return Advantage.EQUAL;
        float own = combatScore(client.player);
        float enemy = combatScore(target);
        float relative = (own - enemy) / Math.max(1.0f, Math.max(own, enemy));
        return relative > 0.13f ? Advantage.WINNING
                : relative < -0.13f ? Advantage.RISKY : Advantage.EQUAL;
    }

    private static float combatScore(LivingEntity entity) {
        float armor = MathHelper.clamp(entity.getArmor(), 0.0f, 30.0f);
        float effectiveArmor = MathHelper.clamp(armor - 4.0f, armor * 0.2f, 20.0f);
        float damageTaken = 1.0f - effectiveArmor / 25.0f;
        return (health(entity) + nonNegative(entity.getAbsorptionAmount())) / damageTaken;
    }

    private void drawPanel(MatrixStack m, float x, float y, float w, float h, float alpha) {
        float radius = 5.0f;
        for (int i = 3; i >= 1; i--) {
            float spread = i * 0.85f;
            round(m, x - spread, y - spread + 1.0f, w + spread * 2.0f,
                    h + spread * 2.0f, radius + spread, 0x08000000, alpha);
        }
        if (isUnusualRectType()) {
            // Delegates the blur algorithm to the project's existing blur pipeline.
            RenderUtils.drawBlur(m, x, y, w, h, radius, 4.0f, fade(0xFFFFFFFF, alpha * 0.9f));
        }
        round(m, x, y, w, h, radius, isUnusualRectType() ? SURFACE : 0xFF121620, alpha);
        int accent = mix(themeColor, 0xFFE5F0FF, 0.26f);
        RenderUtils.drawRoundedRectOutline(m, x, y, w, h, radius, 0.55f,
                fade(accent, alpha * 0.65f), fade(themeColor, alpha * 0.40f),
                fade(0xFF475166, alpha * 0.22f), fade(themeColor, alpha * 0.17f));
        RenderUtils.drawGradientRect(m, x + 6, y + 1, w - 12, 0.6f, 0.3f,
                fade(accent, alpha * 0.50f), fade(themeColor, alpha * 0.02f), true);
    }

    private void renderLlovy12XC(DrawContext context, float x, float y, float alpha, float tickDelta) {
        MatrixStack m = context.getMatrices();
        round(m, x + 4.0f, y + 4.0f, 22.0f, 38.0f, 3.5f, 0x70242A3D, alpha);
        RenderUtils.drawGradientRect(m, x + 4.5f, y + 28.0f, 21.0f, 13.5f, 3.0f,
                fade(themeColor, 0.02f * alpha), fade(themeColor, 0.15f * alpha), false);
        round(m, x + 7.0f, y + 38.5f, 16.0f, 1.2f, 0.6f, themeColor, alpha * 0.25f);

        float left = x + 30.0f;
        float right = x + 106.0f;
        text(m, bold(8), displayName, left, y + 4.0f, 66.0f, 8.0f, TEXT, alpha);
        drawPingDot(m, right - 2.5f, y + 8.0f, alpha);
        text(m, regular(8), distanceText, left, y + 12.5f, 36.0f, 7.5f, MUTED, alpha);
        rightText(m, regular(8), latency < 0 ? "PING N/A" : latency + " ms",
                right, y + 12.5f, 38.0f, 7.5f, MUTED, alpha);

        text(m, bold(8), decimal(smoothHp) + " HP", left, y + 20.5f, 38.0f, 8.0f, TEXT, alpha);
        float hpWidth = bold(8).getWidth(decimal(smoothHp) + " HP");
        if (smoothAbsorption > 0.05f) {
            text(m, bold(8), "+" + decimal(smoothAbsorption), left + hpWidth + 2.0f,
                    y + 20.5f, 18.0f, 8.0f, GOLD, alpha);
        }
        float badgeWidth = Math.min(34.0f, bold(8).getWidth(advantage.title) + 5.0f);
        float badgeX = right - badgeWidth;
        round(m, badgeX, y + 20.5f, badgeWidth, 8.0f, 2.5f, advantage.color, alpha * 0.18f);
        centerText(m, bold(8), advantage.title, badgeX + badgeWidth * 0.5f, y + 20.5f,
                badgeWidth, 8.0f, advantage.color, alpha);

        drawHealthBar(m, left, y + 29.5f, right - left, 2.5f, alpha);
        float slotW = 11.2f;
        float slotGap = 1.7f;
        for (int i = 0; i < 6; i++) {
            drawSlotBackground(m, i, left + i * (slotW + slotGap), y + 33.5f, slotW, 8.5f, false, alpha);
        }
        drawNative(context, alpha, () -> {
            drawLiveDoll(context, x + 4.0f, y + 4.0f, 22.0f, 38.0f, tickDelta);
            for (int i = 0; i < 6; i++) {
                drawItem(context, equipment[i], left + i * (slotW + slotGap) + 1.85f, y + 34.0f, 7.5f);
            }
        });
    }

    private void drawPingDot(MatrixStack m, float cx, float cy, float alpha) {
        int color = latency < 0 ? MUTED : latency < 80 ? GREEN : latency < 160 ? YELLOW : RED;
        round(m, cx - 2.0f, cy - 2.0f, 4.0f, 4.0f, 2.0f, color, alpha * 0.10f);
        round(m, cx - 1.0f, cy - 1.0f, 2.0f, 2.0f, 1.0f, color, alpha);
    }

    private void drawHealthBar(MatrixStack m, float x, float y, float w, float h, float alpha) {
        round(m, x, y, w, h, h * 0.5f, TRACK, alpha);
        float ab = Math.max(0.0f, smoothAbsorption);
        float denominator = Math.max(1.0f, effectiveMaxHp + ab);
        float hpWidth = w * unit(smoothHp / denominator);
        float trailWidth = w * unit(trailHp / denominator);
        float normalWidth = w * unit(effectiveMaxHp / denominator);
        round(m, x, y, trailWidth, h, h * 0.5f, mix(RED, themeColor, 0.28f), alpha * 0.65f);
        gradient(m, x, y, hpWidth, h, mix(themeColor, 0xFFFFFFFF, 0.18f), themeColor, alpha);
        float goldWidth = Math.max(0.0f, w - normalWidth);
        if (goldWidth > 0.1f) {
            gradient(m, x + normalWidth, y, goldWidth, h, 0xFFFFE7A5, GOLD, alpha);
        }
        if (healthBarStyleEnabled) {
            for (int i = 1; i < 10; i++) {
                round(m, x + w * i / 10.0f - 0.35f, y, 0.7f, h, 0, SURFACE, alpha * 0.8f);
            }
        }
    }

    private void drawSlotBackground(MatrixStack m, int index, float x, float y,
                                    float w, float h, boolean percent, float alpha) {
        ItemStack stack = equipment[index];
        round(m, x, y, w, h, 2.0f, 0xA0252C3A, alpha);
        if (stack.isEmpty()) {
            Font font = regular(8);
            centerText(m, font, SLOT_LABELS[index], x + w * 0.5f, y, w, h, MUTED, alpha * 0.40f);
            return;
        }
        if (!stack.isDamageable() || stack.getDamage() <= 0 || stack.getMaxDamage() <= 0) return;
        float durability = unit(1.0f - stack.getDamage() / (float) stack.getMaxDamage());
        int color = durability > 0.50f ? GREEN : durability > 0.20f ? YELLOW : RED;
        round(m, x + 1.5f, y + h - 1.5f, w - 3.0f, 1.0f, 0.5f, TRACK, alpha);
        round(m, x + 1.5f, y + h - 1.5f, (w - 3.0f) * durability, 1.0f, 0.5f, color, alpha);
        if (percent) {
            String value = Math.round(durability * 100.0f) + "%";
            Font font = regular(8);
            centerText(m, font, value, x + w * 0.5f, y + 4.0f, w, 8.0f, color, alpha);
        }
    }

    private static void drawItem(DrawContext context, ItemStack stack, float x, float y, float size) {
        if (stack.isEmpty()) return;
        MatrixStack m = context.getMatrices();
        m.push();
        try {
            m.translate(x, y, 0);
            m.scale(size / 16.0f, size / 16.0f, 1.0f);
            context.drawItem(stack, 0, 0);
            context.draw(); // Flush while this transform and the native framebuffer are active.
        } finally {
            m.pop();
        }
    }

    /**
     * 1.21.4 render-state path: no writes to entity yaw, pitch, hurtTime or pose.
     * Renderer.render() omits the dispatcher's world shadows/fire/hitbox pass.
     */
    private void drawLiveDoll(DrawContext context, float x, float y, float w, float h, float tickDelta) {
        context.draw();
        MatrixStack m = context.getMatrices();
        float entityHeight = Math.max(0.8f, displayedTarget.getHeight());
        float entityWidth = Math.max(0.6f, displayedTarget.getWidth());
        // Reserve room for swinging arms, armor and wide non-player targets.
        float size = Math.min((h - 6.0f) / entityHeight, (w - 6.0f) / (entityWidth * 1.9f));
        context.enableScissor((int) Math.floor(x), (int) Math.floor(y),
                (int) Math.ceil(x + w), (int) Math.ceil(y + h));
        m.push();
        try {
            m.translate(x + w * 0.5f, y + h * 0.5f, 50.0f);
            m.scale(size, size, -size);
            m.translate(0.0f, entityHeight * 0.5f, 0.0f);
            m.multiply(new Quaternionf().rotationZ((float) Math.PI)
                    .rotateX((float) Math.toRadians(dollTilt)));
            DiffuseLighting.enableGuiDepthLighting();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            VertexConsumerProvider.Immediate consumers = client.getBufferBuilders().getEntityVertexConsumers();
            try {
                renderingDoll = true;
                renderDollState(client.getEntityRenderDispatcher().getRenderer(displayedTarget),
                        displayedTarget, tickDelta, m, consumers);
            } finally {
                renderingDoll = false;
                consumers.draw();
            }
        } finally {
            DiffuseLighting.disableGuiDepthLighting();
            m.pop();
            context.disableScissor();
            RenderSystem.disableDepthTest();
        }
    }

    private <S extends EntityRenderState> void renderDollState(
            EntityRenderer<? super LivingEntity, S> renderer, LivingEntity target,
            float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers) {
        S state = renderer.getAndUpdateRenderState(target, tickDelta);
        var savedName = state.displayName;
        var savedLeash = state.leashData;
        LivingEntityRenderState living = state instanceof LivingEntityRenderState l ? l : null;
        float savedBodyYaw = living == null ? 0.0f : living.bodyYaw;
        try {
            state.displayName = null;
            state.leashData = null;
            // Rotate only the preview body. Relative head yaw, pitch, limb swing,
            // swimming/crouching and held-item poses remain the target's real pose.
            if (living != null) living.bodyYaw = 180.0f + dollYaw;
            // LivingEntityRenderState.hurt is populated by vanilla from hurtTime/deathTime.
            renderer.render(state, matrices, consumers, FULL_BRIGHT);
        } finally {
            state.displayName = savedName;
            state.leashData = savedLeash;
            if (living != null) living.bodyYaw = savedBodyYaw;
        }
    }

    /**
     * Native models/items do not reliably honor setShaderColor(alpha) in 1.21.4.
     * Fade their completed pixels instead. The framebuffer uses the current viewport
     * size and projection, so scissor rectangles and GUI scaling remain consistent.
     * At alpha=1 the pass renders directly, without framebuffer work.
     */
    private void drawNative(DrawContext context, float alpha, Runnable render) {
        context.draw();
        if (alpha >= 1.0f) {
            try (GlState ignored = new GlState()) {
                RenderSystem.setShaderColor(1, 1, 1, 1);
                render.run();
                context.draw();
            }
            return;
        }
        try (GlState before = new GlState()) {
            int width = Math.max(1, client.getWindow().getFramebufferWidth());
            int height = Math.max(1, client.getWindow().getFramebufferHeight());
            if (nativeFramebuffer == null) {
                nativeFramebuffer = new SimpleFramebuffer(width, height, true);
                nativeFramebuffer.setClearColor(0, 0, 0, 0);
            } else if (nativeFramebuffer.textureWidth != width || nativeFramebuffer.textureHeight != height) {
                nativeFramebuffer.resize(width, height);
            }
            // Framebuffer.clear must not inherit a parent GUI scissor.
            RenderSystem.disableScissor();
            RenderSystem.depthMask(true);
            nativeFramebuffer.clear();
            nativeFramebuffer.beginWrite(true);
            before.restoreScissor();
            RenderSystem.setShaderColor(1, 1, 1, 1);
            render.run();
            context.draw();
            before.restoreFramebuffer();
            before.restoreScissor();
            compositeNative(context.getMatrices(), width, height, alpha);
        }
    }

    private void compositeNative(MatrixStack matrices, int width, int height, float alpha) {
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        // Transparent-black target stores premultiplied RGB. Do not multiply by
        // texture alpha a second time; doing so gives dark fringes around skins.
        RenderSystem.blendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        RenderSystem.setShader(ShaderProgramKeys.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, nativeFramebuffer.getColorAttachment());
        RenderSystem.setShaderColor(1, 1, 1, 1);
        int a = Math.round(unit(alpha) * 255.0f);
        int color = a << 24 | a << 16 | a << 8 | a;
        float w = (float) (width / client.getWindow().getScaleFactor());
        float h = (float) (height / client.getWindow().getScaleFactor());
        // Use identity here: the source already includes the incoming GUI matrix.
        // Applying the context matrix again would double-translate the HUD.
        Matrix4f matrix = new Matrix4f();
        BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        b.vertex(matrix, 0, 0, 0).texture(0, 1).color(color);
        b.vertex(matrix, 0, h, 0).texture(0, 0).color(color);
        b.vertex(matrix, w, h, 0).texture(1, 0).color(color);
        b.vertex(matrix, w, 0, 0).texture(1, 1).color(color);
        BufferRenderer.drawWithGlobalProgram(b.end());
    }


    private void spawnParticles() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < 9 && headParticles.size() < MAX_PARTICLES; i++) {
            double angle = random.nextDouble(0.0, Math.PI * 2.0);
            float velocity = (float) random.nextDouble(8.0, 20.0);
            headParticles.add(new HeadParticle(
                    (float) Math.cos(angle) * 3.0f, (float) Math.sin(angle) * 3.0f,
                    (float) Math.cos(angle) * velocity, (float) Math.sin(angle) * velocity - 4.0f,
                    (float) random.nextDouble(0.35, 0.72), (float) random.nextDouble(0.8, 1.6)));
        }
    }

    private void updateParticles(float dt) {
        for (int i = headParticles.size() - 1; i >= 0; i--) {
            HeadParticle p = headParticles.get(i);
            p.age += dt;
            if (p.age >= p.life) {
                headParticles.remove(i);
                continue;
            }
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            float drag = (float) Math.exp(-2.2f * dt);
            p.vx *= drag;
            p.vy = p.vy * drag + 18.0f * dt;
        }
    }

    private void renderParticles(MatrixStack m, float x, float y, float alpha) {
        if (!headParticlesEnabled) return;
        float cx = x + 15.0f;
        float cy = y + 12.0f;
        for (HeadParticle p : headParticles) {
            float life = 1.0f - p.age / p.life;
            float opacity = alpha * life * life;
            float size = p.size * (0.5f + life * 0.5f);
            round(m, cx + p.x - size, cy + p.y - size, size * 2, size * 2,
                    size, themeColor, opacity * 0.13f);
            round(m, cx + p.x - size * 0.5f, cy + p.y - size * 0.5f, size, size,
                    size * 0.5f, mix(themeColor, 0xFFFFFFFF, 0.5f), opacity);
        }
    }

    private static final class HeadParticle {
        float x, y, vx, vy, age;
        final float life, size;
        HeadParticle(float x, float y, float vx, float vy, float life, float size) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy;
            this.life = life; this.size = size;
        }
    }

    private static Font regular(int size) { return Fonts.getFont("suisse", size); }
    private static Font bold(int size) { return Fonts.getFont("semibold", size); }

    private static void text(MatrixStack m, Font font, String value, float x, float y,
                             float maxWidth, float rowHeight, int color, float alpha) {
        String clipped = ellipsize(font, value, Math.max(0.0f, maxWidth));
        if (clipped.isEmpty()) return;
        float drawY = y + (rowHeight - font.getHeight()) * 0.5f + 1.5f;
        font.draw(m, clipped, x, drawY, fade(color, alpha));
    }

    private static void rightText(MatrixStack m, Font font, String value, float right, float y,
                                  float maxWidth, float rowHeight, int color, float alpha) {
        String clipped = ellipsize(font, value, Math.max(0.0f, maxWidth));
        if (clipped.isEmpty()) return;
        float drawY = y + (rowHeight - font.getHeight()) * 0.5f + 1.5f;
        font.draw(m, clipped, right - font.getWidth(clipped), drawY, fade(color, alpha));
    }

    private static void centerText(MatrixStack m, Font font, String value, float cx, float y,
                                   float maxWidth, float rowHeight, int color, float alpha) {
        String clipped = ellipsize(font, value, Math.max(0.0f, maxWidth));
        if (clipped.isEmpty()) return;
        float drawY = y + (rowHeight - font.getHeight()) * 0.5f + 1.5f;
        font.draw(m, clipped, cx - font.getWidth(clipped) * 0.5f, drawY, fade(color, alpha));
    }

    /** Unicode-safe truncation; does not split surrogate pairs in nicknames. */
    private static String ellipsize(Font font, String value, float maxWidth) {
        if (value == null || maxWidth <= 0.0f) return "";
        if (font.getWidth(value) <= maxWidth) return value;
        String suffix = "...";
        if (font.getWidth(suffix) > maxWidth) return "";
        int low = 0, high = value.codePointCount(0, value.length());
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            int end = value.offsetByCodePoints(0, middle);
            if (font.getWidth(value.substring(0, end) + suffix) <= maxWidth) low = middle;
            else high = middle - 1;
        }
        return value.substring(0, value.offsetByCodePoints(0, low)) + suffix;
    }

    private static void round(MatrixStack m, float x, float y, float w, float h,
                              float radius, int color, float alpha) {
        if (w <= 0.05f || h <= 0.05f || alpha <= 0.0f) return;
        RenderUtils.drawRoundedRect(m, x, y, w, h,
                Math.max(0.0f, Math.min(radius, Math.min(w, h) * 0.5f)), fade(color, alpha));
    }

    private static void gradient(MatrixStack m, float x, float y, float w, float h,
                                 int left, int right, float alpha) {
        if (w <= 0.05f || h <= 0.05f) return;
        RenderUtils.drawGradientRect(m, x, y, w, h, Math.min(w, h) * 0.5f,
                fade(left, alpha), fade(right, alpha), true);
    }

    private static int fade(int argb, float alpha) {
        int a = Math.round(((argb >>> 24) & 255) * unit(alpha));
        return argb & 0x00FFFFFF | a << 24;
    }

    private static int mix(int a, int b, float factor) {
        float t = unit(factor);
        int aa = Math.round(((a >>> 24) & 255) * (1 - t) + ((b >>> 24) & 255) * t);
        int r = Math.round(((a >>> 16) & 255) * (1 - t) + ((b >>> 16) & 255) * t);
        int g = Math.round(((a >>> 8) & 255) * (1 - t) + ((b >>> 8) & 255) * t);
        int blue = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return aa << 24 | r << 16 | g << 8 | blue;
    }

    private static float damp(float current, float target, float speed, float dt) {
        float result = current + (target - current) * (1.0f - (float) Math.exp(-speed * dt));
        return Math.abs(result - target) < 0.005f ? target : result;
    }

    private static float nonNegative(float value) { return Float.isFinite(value) ? Math.max(0, value) : 0; }
    private static float unit(float value) { return MathHelper.clamp(nonNegative(value), 0, 1); }
    private static String decimal(float value) { return String.format(Locale.ROOT, "%.1f", nonNegative(value)); }

    private void releaseFramebuffer() {
        if (nativeFramebuffer != null) {
            nativeFramebuffer.delete();
            nativeFramebuffer = null;
        }
    }

    /** Call from Interface's disable/removal path on the render thread. Safe to call repeatedly. */
    @Override
    public void close() {
        resetTarget();
        releaseFramebuffer();
        lastFrameNs = 0L;
        lastWorld = null;
    }

    /** Restores the GL state changed by native HUD rendering and composition. */
    private static final class GlState implements AutoCloseable {
        final boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        final boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        final boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        final boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        final boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        final int depthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        final int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        final int dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        final int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        final int dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        final int drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        final int readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        final int[] viewport = new int[4];
        final int[] scissorBox = new int[4];
        final float[] clearColor = new float[4];
        final float[] shaderColor = RenderSystem.getShaderColor().clone();
        final ShaderProgram shader = RenderSystem.getShader();
        final int texture = RenderSystem.getShaderTexture(0);

        GlState() {
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissorBox);
            GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, clearColor);
        }

        void restoreFramebuffer() {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        }

        void restoreScissor() {
            if (scissor) RenderSystem.enableScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);
            else RenderSystem.disableScissor();
        }

        @Override
        public void close() {
            restoreFramebuffer();
            restoreScissor();
            RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            RenderSystem.depthMask(depthMask);
            RenderSystem.depthFunc(depthFunction);
            RenderSystem.clearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3]);
            RenderSystem.setShaderColor(shaderColor[0], shaderColor[1], shaderColor[2], shaderColor[3]);
            RenderSystem.setShaderTexture(0, texture);
            if (shader != null) RenderSystem.setShader(shader);
        }
    }
}
