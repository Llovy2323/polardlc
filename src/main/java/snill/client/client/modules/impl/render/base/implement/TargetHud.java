package snill.client.client.modules.impl.render.base.implement;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import snill.client.Snill;
import snill.client.api.events.implement.EventRender;
import snill.client.api.storages.implement.helpertstorages.enumvar.ModuleClass;
import snill.client.api.utils.animation.AnimationUtils;
import snill.client.api.utils.animation.Easings;
import snill.client.api.utils.color.ColorUtils;
import snill.client.api.utils.draggable.Draggable;
import snill.client.api.utils.render.RenderUtils;
import snill.client.api.utils.render.fonts.msdf.Font;
import snill.client.api.utils.render.fonts.msdf.Fonts;
import snill.client.client.modules.impl.combat.Aura;
import snill.client.client.modules.impl.misc.NameProtect;
import snill.client.client.modules.impl.misc.ScoreboardHP;
import snill.client.client.modules.impl.render.base.InterfaceProcessing;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class TargetHud extends InterfaceProcessing {

    private final AnimationUtils alphaAnimation = new AnimationUtils(0.0f, 9.0f, Easings.QUAD_OUT);
    private final AnimationUtils hpAnimation = new AnimationUtils(1.0f, 9.2f, Easings.QUAD_OUT);
    private final AnimationUtils hpTrailAnimation = new AnimationUtils(1.0f, 7.4f, Easings.QUAD_OUT);
    private final AnimationUtils hpValueAnimation = new AnimationUtils(20.0f, 7.0f, Easings.QUAD_OUT);
    private final AnimationUtils abValueAnimation = new AnimationUtils(0.0f, 7.0f, Easings.QUAD_OUT);
    private final AnimationUtils goldenHpAnimation = new AnimationUtils(0.0f, 9.2f, Easings.QUAD_OUT);
    private final AnimationUtils goldenAlphaAnimation = new AnimationUtils(0.0f, 9.0f, Easings.QUAD_OUT);
    private final List<HeadParticle> headParticles = new ObjectArrayList<>();

    private LivingEntity lastTarget;
    private float maxAbsorption = 20.0f;
    private boolean headParticlesEnabled = true;
    private boolean healthBarStyleEnabled = false;
    private long lastParticleUpdateNs = System.nanoTime();
    private LivingEntity particleTarget;
    private int lastTargetHurtTime = 0;
    private int cachedBarThemeColor = ColorUtils.rgba(124, 91, 242, 255);
    private final ItemStack[] armorScratch = new ItemStack[4];

    public TargetHud(Draggable draggable) {
        super(draggable);
    }

    private Font issue(int size) {
        return Fonts.getFont("suisse", size);
    }

    public boolean isHeadParticlesEnabled() {
        return headParticlesEnabled;
    }

    public void setHeadParticlesEnabled(boolean headParticlesEnabled) {
        this.headParticlesEnabled = headParticlesEnabled;
        if (!headParticlesEnabled) {
            headParticles.clear();
        }
    }

    public boolean isHealthBarStyleEnabled() {
        return healthBarStyleEnabled;
    }

    public void setHealthBarStyleEnabled(boolean healthBarStyleEnabled) {
        this.healthBarStyleEnabled = healthBarStyleEnabled;
    }

    private void drawBlurPanel(MatrixStack matrices, float x, float y, float width, float height, float radius) {
        RenderUtils.drawBlur(matrices, x, y, width, height, radius, 5f, ColorUtils.rgba(255, 255, 255, 255));
        RenderUtils.drawBlur(matrices, x, y, width, height, radius, 5f, ColorUtils.rgba(0, 0, 0, 180));
        RenderUtils.drawRoundedRect(matrices, x, y, width, height, radius, ColorUtils.rgba(20, 20, 20, 100));
    }

    private int getDurabilityColor(float progress) {
        if (progress > 0.6f) {
            return ColorUtils.rgba(80, 220, 120, 255);
        }
        if (progress > 0.3f) {
            return ColorUtils.rgba(230, 190, 50, 255);
        }
        return ColorUtils.rgba(220, 70, 70, 255);
    }

    private void drawDurabilityBar(MatrixStack matrices, float x, float y, float width, float height,
                                   float progress, float alpha) {
        int bg = ColorUtils.rgba(30, 32, 42, (int) (160 * alpha));
        RenderUtils.drawRoundedRect(matrices, x, y, width, height, 1f, bg);

        if (progress <= 0.01f) {
            return;
        }

        float fillW = Math.max(2f, width * progress);
        int color = getDurabilityColor(progress);
        int fillLeft = ColorUtils.applyAlpha(ColorUtils.darken(color, 0.45f), alpha);
        int fillRight = ColorUtils.applyAlpha(color, alpha);
        int glowColor = ColorUtils.applyAlpha(color, alpha * 0.28f);

        RenderUtils.drawRoundedRect(matrices, x, y - 0.35f, fillW, height + 0.7f, 1f, glowColor);
        RenderUtils.drawGradientRect(matrices, x, y, fillW, height, 1f, fillLeft, fillRight, true);
    }

    private float getItemDurabilityProgress(ItemStack stack) {
        if (stack.isEmpty() || !stack.isDamageable()) {
            return -1f;
        }
        int max = stack.getMaxDamage();
        if (max <= 0) {
            return -1f;
        }
        return MathHelper.clamp((max - stack.getDamage()) / (float) max, 0f, 1f);
    }

    private static final class HeadParticle {
        float x, y, vx, vy, size, age, maxAge;
    }

    private void updateAndRenderHeadParticles(MatrixStack matrices, LivingEntity target, float headX, float headY, float headSize, float alpha, int themeColor) {
        if (target == null || alpha <= 0.02f) {
            headParticles.clear();
            particleTarget = target;
            lastTargetHurtTime = 0;
            return;
        }

        long now = System.nanoTime();
        float deltaTicks = MathHelper.clamp((now - lastParticleUpdateNs) / 1_000_000_000.0f * 60.0f, 0.2f, 3.0f);
        lastParticleUpdateNs = now;

        if (particleTarget != target) {
            headParticles.clear();
            particleTarget = target;
            lastTargetHurtTime = Math.max(0, target.hurtTime);
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        float centerX = headX + headSize * 0.5f;
        float centerY = headY + headSize * 0.5f;
        int hurtTime = Math.max(0, target.hurtTime);
        boolean spawnBurst = hurtTime > 0 && (hurtTime > lastTargetHurtTime || hurtTime % 3 == 0);
        lastTargetHurtTime = hurtTime;

        if (spawnBurst) {
            int burstCount = 1 + random.nextInt(2);
            for (int n = 0; n < burstCount && headParticles.size() < 14; n++) {
                float angle = (float) (random.nextDouble() * Math.PI * 2.0);
                float radius = random.nextFloat() * headSize * 0.24f;
                float spreadAngle = (float) (random.nextDouble() * Math.PI * 2.0);
                float speed = 0.58f + random.nextFloat() * 0.9f;

                HeadParticle p = new HeadParticle();
                p.x = centerX + MathHelper.cos(angle) * radius;
                p.y = centerY + MathHelper.sin(angle) * radius;
                p.vx = MathHelper.cos(spreadAngle) * speed + (p.x - centerX) * 0.025f;
                p.vy = MathHelper.sin(spreadAngle) * speed + (p.y - centerY) * 0.025f;
                p.size = 3.8f + random.nextFloat() * 1.4f;
                p.age = 0.0f;
                p.maxAge = 74.0f + random.nextFloat() * 42.0f;
                headParticles.add(p);
            }
        }

        float velocityDrag = (float) Math.pow(0.975f, deltaTicks);
        for (int i = headParticles.size() - 1; i >= 0; i--) {
            HeadParticle p = headParticles.get(i);
            p.age += deltaTicks;
            if (p.age >= p.maxAge) {
                headParticles.remove(i);
                continue;
            }

            p.x += p.vx * deltaTicks;
            p.y += p.vy * deltaTicks;
            p.vx *= velocityDrag;
            p.vy *= velocityDrag;
            p.vy += 0.0012f * deltaTicks;

            float life = 1.0f - (p.age / p.maxAge);
            float smoothLife = life * life * (3.0f - 2.0f * life);
            float particleAlpha = alpha * smoothLife;
            if (particleAlpha <= 0.02f) {
                continue;
            }

            RenderUtils.drawRoundedRect(matrices, p.x - p.size * 0.5f, p.y - p.size * 0.5f, p.size, p.size, p.size * 0.45f,
                    ColorUtils.applyAlpha(themeColor, particleAlpha * 0.58f));
        }
    }

    private void drawTargetHudItem(EventRender.Default eventRender, MatrixStack matrices, ItemStack stack,
                                   float slotX, float slotY, float slotSize, float itemScale) {
        if (stack.isEmpty() || itemScale < 0.05f) return;

        float itemScreenSize = 16f * itemScale;
        float offsetX = slotX + (slotSize - itemScreenSize) / 2f;
        float offsetY = slotY + (slotSize - itemScreenSize) / 2f;

        matrices.push();
        matrices.translate(offsetX, offsetY, 0);
        matrices.scale(itemScale, itemScale, 1f);
        eventRender.getContext().drawItem(stack, 0, 0);
        matrices.pop();
    }

    private String getDisplayName(LivingEntity target) {
        String raw = target.getName().getString();
        if (raw == null || raw.isEmpty()) raw = "Unknown";
        NameProtect np = NameProtect.INSTANCE;
        if (np != null && np.isEnable()) {
            String patched = np.patch(raw);
            if (patched != null) return patched;
        }
        return raw;
    }

    private int getThemeColor() {
        if (!Snill.INSTANCE.themeStorage.getThemes().getTheme().getName().equals("Rainbow")) {
            return Snill.INSTANCE.themeStorage.getThemes().getTheme().color[0];
        }
        return ColorUtils.getThemeColor();
    }

    @Override
    public void onRender(EventRender.Default eventRender) {
        DefaultStyle(eventRender);
        super.onRender(eventRender);
    }

    public void DefaultStyle(EventRender.Default eventRender) {
        if (mc.player == null) {
            headParticles.clear();
            lastTargetHurtTime = 0;
            draggable.setWidth(0);
            draggable.setHeight(0);
            return;
        }

        Aura aura = ModuleClass.aura;
        boolean chatOpen = mc.currentScreen instanceof ChatScreen;
        LivingEntity auraTarget = aura != null ? aura.getTarget() : null;
        boolean showTargetHud = chatOpen || auraTarget != null;

        alphaAnimation.setSpeed(showTargetHud ? 9.0f : 5.0f);
        alphaAnimation.update(showTargetHud ? 1.0f : 0.0f);
        float alpha = MathHelper.clamp(alphaAnimation.getValue(), 0.0f, 1.0f);

        if (showTargetHud) {
            lastTarget = chatOpen ? mc.player : auraTarget;
        }

        LivingEntity target = showTargetHud ? (chatOpen ? mc.player : auraTarget) : lastTarget;
        if (target == null || alpha <= 0.01f) {
            headParticles.clear();
            lastTargetHurtTime = 0;
            draggable.setWidth(0);
            draggable.setHeight(0);
            goldenAlphaAnimation.setValue(0.0f);
            abValueAnimation.setValue(0.0f);
            goldenHpAnimation.setValue(0.0f);
            return;
        }

        float currentAbsorption = target.getAbsorptionAmount();
        if (currentAbsorption > maxAbsorption) {
            maxAbsorption = currentAbsorption;
        }

        float maxHealth = Math.max(1.0f, target.getMaxHealth());
        float targetHealthForAnim = showTargetHud ? ScoreboardHP.getHealth(target) : 0.0f;
        hpValueAnimation.update(targetHealthForAnim);
        float animatedHealthValue = MathHelper.clamp(hpValueAnimation.getValue(), 0.0f, maxHealth);

        float healthProgress = MathHelper.clamp(targetHealthForAnim / maxHealth, 0.0f, 1.0f);
        hpAnimation.update(healthProgress);
        float hpProgressAnimated = MathHelper.clamp(hpAnimation.getValue(), 0.0f, 1.0f);
        if (hpProgressAnimated > hpTrailAnimation.getValue()) {
            hpTrailAnimation.setValue(MathHelper.lerp(0.78f, hpTrailAnimation.getValue(), hpProgressAnimated));
        } else {
            hpTrailAnimation.update(hpProgressAnimated);
        }
        float hpTrailProgressAnimated = MathHelper.clamp(hpTrailAnimation.getValue(), 0.0f, 1.0f);
        boolean hidingHud = !showTargetHud;
        if (hidingHud) {
            hpTrailProgressAnimated = hpProgressAnimated;
        }

        int colorTheme = getThemeColor();
        if (showTargetHud) {
            cachedBarThemeColor = colorTheme;
        }
        int barColor = showTargetHud ? colorTheme : cachedBarThemeColor;

        String name = getDisplayName(target);
        String hpText = "HP: " + (int) animatedHealthValue;

        float x = draggable.getX();
        float y = draggable.getY();

        int armorCount = 0;
        for (ItemStack stack : target.getArmorItems()) {
            if (!stack.isEmpty() && armorCount < armorScratch.length) {
                armorScratch[armorCount++] = stack;
            }
        }

        ItemStack mainHand = target.getMainHandStack();
        ItemStack offHand = target.getOffHandStack();
        ItemStack[] handStacks = new ItemStack[2];
        int handCount = 0;
        if (!offHand.isEmpty()) {
            handStacks[handCount++] = offHand;
        }
        if (!mainHand.isEmpty()) {
            handStacks[handCount++] = mainHand;
        }

        boolean hasArmor = armorCount > 0;
        boolean hasHands = handCount > 0;

        float slotSize = 10f;
        float slotGap = 1.5f;
        float slotPad = 1.5f;
        float itemsGap = 2f;
        float handSideGap = 3f;
        float blurRadius = 4f;
        float durBarH = 2f;
        float durGap = 1f;
        float slotBlockH = slotSize + durGap + durBarH;

        float armorContW = hasArmor ? slotPad * 2 + armorCount * slotSize + (armorCount - 1) * slotGap : 0f;
        float armorContH = hasArmor ? slotPad * 2 + slotBlockH : 0f;
        float handsContW = hasHands ? slotPad * 2 + slotSize : 0f;
        float handsContH = hasHands ? slotPad * 2 + handCount * slotSize + Math.max(0, handCount - 1) * slotGap : 0f;
        float armorRowH = hasArmor ? armorContH + itemsGap : 0f;
        float panelY = y + armorRowH;

        float headSize = 20f;
        float padding = 4.5f;
        float gap = 7f;
        float rightPad = 7f;
        float height = headSize + padding * 2f;
        float textW = Math.max(issue(14).getWidth(name), issue(12).getWidth(hpText));
        float barH = 4f;
        float width = Math.max(90f, padding + headSize + gap + textW + rightPad);

        float headX = x + padding;
        float headY = panelY + padding;
        float textX = headX + headSize + gap - 2f;
        float nameY = headY + 1.5f;
        float hpTextY = nameY + 8f;
        float barY = headY + headSize - barH - 0.5f;
        float barW = width - (textX - x) - rightPad;

        int drawAlphaInt = (int) (255 * alpha);
        MatrixStack matrices = eventRender.getContext().getMatrices();

        matrices.push();

        float armorContX = x + (width - armorContW) * 0.5f;
        if (hasArmor) {
            drawBlurPanel(matrices, armorContX, y, armorContW, armorContH, blurRadius);
        }

        drawBlurPanel(matrices, x, panelY, width, height, 6f);

        if (headParticlesEnabled) {
            updateAndRenderHeadParticles(matrices, target, headX, headY, headSize, alpha, barColor);
        } else {
            headParticles.clear();
        }

        float hurtPercent = 0f;
        if (target.hurtTime > 0) {
            hurtPercent = MathHelper.clamp((target.hurtTime / 10.0f) * 0.55f, 0f, 0.55f);
        }

        if (target instanceof PlayerEntity playerEntity) {
            RenderUtils.drawPlayerHead(matrices, playerEntity.getUuid(), headX, headY, headSize, 5f, alpha, hurtPercent);
        } else {
            RenderUtils.drawTargetHudDefaultPlaceholder(matrices, headX, headY, alpha);
        }

        issue(14).drawString(matrices, name, textX, nameY, ColorUtils.rgba(255, 255, 255, drawAlphaInt));
        issue(12).drawString(matrices, hpText, textX, hpTextY, ColorUtils.rgba(160, 165, 175, drawAlphaInt));

        goldenAlphaAnimation.setSpeed(currentAbsorption > 0 ? 9.0f : 5.0f);
        goldenAlphaAnimation.update(currentAbsorption > 0 ? 1.0f : 0.0f);
        float goldenAlpha = MathHelper.clamp(goldenAlphaAnimation.getValue(), 0.0f, 1.0f);

        if (goldenAlpha > 0.01f) {
            String abText = "+" + (int) currentAbsorption + "AB";
            float abTW = issue(12).getWidth(abText);
            float abX = textX + barW - abTW;
            int goldenAlphaInt = (int) (255 * goldenAlpha * alpha);
            issue(12).drawGradientStringHorizontal(matrices, abText, abX, hpTextY,
                    ColorUtils.rgba(236, 183, 39, goldenAlphaInt),
                    ColorUtils.rgba(200, 140, 20, goldenAlphaInt));
        }

        int barBg = ColorUtils.rgba(40, 42, 55, (int) (180 * alpha));
        RenderUtils.drawRoundedRect(matrices, textX, barY, barW, barH, 1.5f, barBg);

        float goldenReservedW = 0f;
        if (goldenAlpha > 0.01f && currentAbsorption > 0) {
            abValueAnimation.update(showTargetHud ? currentAbsorption : 0f);
            float maxAB = Math.max(1.0f, maxAbsorption);
            goldenHpAnimation.update(MathHelper.clamp(currentAbsorption / maxAB, 0f, 1f));
            float goldenFill = MathHelper.clamp(goldenHpAnimation.getValue(), 0f, 1f);
            goldenReservedW = barW * goldenFill;
            if (goldenReservedW > 1f) {
                float goldenX = textX + barW - goldenReservedW;
                int goldenL = ColorUtils.applyAlpha(ColorUtils.rgba(147, 108, 16, 255), goldenAlpha * alpha);
                int goldenR = ColorUtils.applyAlpha(ColorUtils.rgba(236, 183, 39, 255), goldenAlpha * alpha);
                RenderUtils.drawGradientRect(matrices, goldenX, barY, goldenReservedW, barH, 1.5f, goldenL, goldenR, true);
            }
        }

        float hpZoneW = barW - goldenReservedW;
        float trailW = hpZoneW * hpTrailProgressAnimated;
        if (!hidingHud && trailW > 1f) {
            int trailBase = healthBarStyleEnabled ? ColorUtils.rgba(180, 60, 30, 255) : barColor;
            int trailColorL = ColorUtils.applyAlpha(ColorUtils.darken(trailBase, 0.8f), alpha * 0.5f);
            int trailColorR = ColorUtils.applyAlpha(ColorUtils.darken(trailBase, 0.5f), alpha * 0.5f);
            RenderUtils.drawGradientRect(matrices, textX, barY, trailW, barH, 1.5f, trailColorL, trailColorR, true);
        }

        float fillW = hpZoneW * hpProgressAnimated;
        if (fillW > 1f) {
            int fillColorL;
            int fillColorR;
            if (healthBarStyleEnabled) {
                int healthColor;
                if (hpProgressAnimated > 0.5f) {
                    float t = (hpProgressAnimated - 0.5f) / 0.5f;
                    healthColor = ColorUtils.rgba((int) (255 * (1f - t)), 200, 50, 255);
                } else {
                    float t = hpProgressAnimated / 0.5f;
                    healthColor = ColorUtils.rgba(220, (int) (180 * t), 30, 255);
                }
                fillColorL = ColorUtils.applyAlpha(ColorUtils.darken(healthColor, 0.6f), alpha);
                fillColorR = ColorUtils.applyAlpha(healthColor, alpha);
            } else {
                fillColorL = ColorUtils.applyAlpha(ColorUtils.darken(barColor, 0.5f), alpha);
                fillColorR = ColorUtils.applyAlpha(barColor, alpha);
            }
            RenderUtils.drawGradientRect(matrices, textX, barY, fillW, barH, 1.5f, fillColorL, fillColorR, true);
        }

        float itemScale = 0.52f * alpha;

        if (hasHands) {
            float handsContX = x + width + handSideGap;
            float handsContY = panelY + (height - handsContH) * 0.5f;
            drawBlurPanel(matrices, handsContX, handsContY, handsContW, handsContH, blurRadius);

            for (int i = 0; i < handCount; i++) {
                float sx = handsContX + slotPad;
                float sy = handsContY + slotPad + i * (slotSize + slotGap);
                drawTargetHudItem(eventRender, matrices, handStacks[i], sx, sy, slotSize, itemScale);
            }
        }

        if (hasArmor) {
            for (int i = 0; i < armorCount; i++) {
                float sx = armorContX + slotPad + i * (slotSize + slotGap);
                float sy = y + slotPad;
                ItemStack stack = armorScratch[i];
                drawTargetHudItem(eventRender, matrices, stack, sx, sy, slotSize, itemScale);
                float durProgress = getItemDurabilityProgress(stack);
                if (durProgress >= 0f) {
                    drawDurabilityBar(matrices, sx, sy + slotSize + durGap, slotSize, durBarH, durProgress, alpha);
                }
                armorScratch[i] = ItemStack.EMPTY;
            }
        }

        matrices.pop();

        float totalWidth = Math.max(width, armorContW) + (hasHands ? handSideGap + handsContW : 0f);
        draggable.setWidth(totalWidth);
        draggable.setHeight(armorRowH + height);
    }
}
