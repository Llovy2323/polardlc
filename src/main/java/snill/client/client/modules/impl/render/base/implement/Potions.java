package snill.client.client.modules.impl.render.base.implement;

import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.texture.Sprite;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.MathHelper;
import static snill.client.Snill.INSTANCE;
import snill.client.api.events.implement.EventRender;
import snill.client.api.utils.animation.AnimationUtils;
import snill.client.api.utils.animation.Easings;
import snill.client.api.utils.color.ColorUtils;
import snill.client.api.utils.draggable.Draggable;
import snill.client.api.utils.render.RenderUtils;
import snill.client.api.utils.render.fonts.msdf.Font;
import snill.client.api.utils.render.fonts.msdf.Fonts;
import snill.client.api.utils.render.fonts.ttf.MCFontRenderer;
import snill.client.api.utils.scissor.ScissorUtils;
import snill.client.client.modules.impl.render.base.InterfaceProcessing;

import java.util.*;

public class Potions extends InterfaceProcessing {

    private static final float BASE_MIN_WIDTH = 64f;
    private static final float EXTRA_WIDTH = 0f;
    private static final float ROW_HEIGHT = 10.5f;
    private static final float HEADER_HEIGHT = 16f;
    private static final float HEADER_GAP = 0.1f;
    private static final float CONTENT_PAD_TOP = 2.5f;
    private static final float CONTENT_PAD_BOTTOM = 0.8f;
    private static final int EXPIRING_TICKS = 5 * 20;
    private static final float PULSE_SPEED_EXPIRING = 3.8f;
    private static final float PULSE_SPEED_BAD = 1.6f;

    private static final class PotionSnapshot {
        RegistryEntry<StatusEffect> entry;
        String baseName;
        int amplifier, duration;
        boolean infinite;
    }

    private Font issue(int size) { return Fonts.getFont("suisse", size); }

    private final Map<StatusEffect, AnimationUtils> animations = new LinkedHashMap<>();
    private final Map<StatusEffect, PotionSnapshot> snapshots = new HashMap<>();
    private final Map<StatusEffect, Integer> maxDurations = new HashMap<>();
    private final Set<StatusEffect> renderOrderSeen = new HashSet<>();
    private final AnimationUtils widthAnimation = new AnimationUtils(60, 10.5f, Easings.QUAD_OUT);
    private final AnimationUtils heightAnimation = new AnimationUtils(16, 10.5f, Easings.QUAD_OUT);

    public Potions(Draggable draggable) { super(draggable); }

    private MCFontRenderer myfont(int size) {
        return snill.client.api.utils.render.fonts.ttf.Fonts.getFont("myfont.ttf", size);
    }

    private AnimationUtils getAnimation(StatusEffect effect) {
        return animations.computeIfAbsent(effect, e -> new AnimationUtils(0, 10.5f, Easings.QUAD_OUT));
    }

    private static String getLevelSuffix(int level) {
        int n = Math.max(1, level);
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> "X".repeat(n / 10) + getLevelSuffix(n % 10 == 0 ? 10 : n % 10);
        };
    }

    private static String formatDuration(int duration, boolean infinite) {
        if (infinite) return "inf";
        int seconds = Math.max(0, duration / 20);
        int secs = seconds % 60;
        return (seconds / 60) + ":" + (secs < 10 ? "0" + secs : String.valueOf(secs));
    }

    private void updateSnapshot(StatusEffectInstance effect) {
        StatusEffect type = effect.getEffectType().value();
        PotionSnapshot s = snapshots.computeIfAbsent(type, e -> new PotionSnapshot());
        s.entry = effect.getEffectType();
        s.baseName = I18n.translate(effect.getTranslationKey());
        s.amplifier = effect.getAmplifier() + 1;
        s.duration = effect.getDuration();
        s.infinite = effect.isInfinite();
    }

    private List<StatusEffect> buildRenderOrder(Collection<StatusEffectInstance> effects, Set<StatusEffect> active) {
        List<StatusEffect> order = new ArrayList<>();
        renderOrderSeen.clear();
        for (StatusEffectInstance effect : effects) {
            StatusEffect type = effect.getEffectType().value();
            if (renderOrderSeen.add(type)) order.add(type);
        }
        for (StatusEffect type : animations.keySet()) if (!active.contains(type)) order.add(type);
        return order;
    }

    @Override
    public void onRender(EventRender.Default eventRender) {
        DefaultStyle(eventRender);
        super.onRender(eventRender);
    }

    public void DefaultStyle(EventRender.Default eventRender) {

        float baseX = draggable.getX(), y = draggable.getY();
        int colorTheme = getStableThemeColor();
        float targetWidth = BASE_MIN_WIDTH;
        int visibleCount = 0;

        Collection<StatusEffectInstance> effects = mc != null && mc.player != null
                ? mc.player.getStatusEffects() : List.of();

        Set<StatusEffect> active = new HashSet<>();
        for (StatusEffectInstance effect : effects) {
            StatusEffect type = effect.getEffectType().value();
            active.add(type);
            getAnimation(type).update(1);
            updateSnapshot(effect);
            int duration = effect.getDuration();
            Integer prevMax = maxDurations.get(type);
            if (prevMax == null || duration > prevMax) maxDurations.put(type, duration);
        }
        for (Map.Entry<StatusEffect, AnimationUtils> e : animations.entrySet())
            if (!active.contains(e.getKey())) e.getValue().update(0);

        List<StatusEffect> renderOrder = buildRenderOrder(effects, active);

        // Check if any effects are actually visible
        boolean hasVisibleEffects = false;
        for (StatusEffect type : renderOrder) {
            float animValue = getAnimation(type).getValue();
            PotionSnapshot snapshot = snapshots.get(type);
            if (animValue > 0.01f && snapshot != null) {
                hasVisibleEffects = true;
                break;
            }
        }

        // Hide HUD element if no active potions
        if (!hasVisibleEffects) {
            draggable.setWidth(0);
            draggable.setHeight(0);
            return;
        }

        for (StatusEffect type : renderOrder) {
            float animValue = getAnimation(type).getValue();
            PotionSnapshot snapshot = snapshots.get(type);
            if (animValue > 0.01f && snapshot != null) {
                visibleCount++;
                String baseName = snapshot.baseName;
                String levelSuffix = getLevelSuffix(snapshot.amplifier);
                String time = formatDuration(snapshot.duration, snapshot.infinite);

                float textWidth = issue(12).getWidth(baseName);
                if (!levelSuffix.isEmpty()) textWidth += issue(12).getWidth(" " + levelSuffix);
                float timeBoxWidth = Math.max(issue(10).getStringWidth(time) + 4, 9f);

                // Точный подсчет ширины:
                // 4 (слева) + 8 (иконка) + 3 (отступ текста) + текст + 4 (зазор по центру) + 6 (кольцо) + 3 (отступ таймера) + таймер + 4 (справа) = 32f + текст + таймер
                float rowWidth = textWidth + timeBoxWidth + 32f;
                if (rowWidth > targetWidth) targetWidth = rowWidth;
            }
        }

        float targetHeight = HEADER_HEIGHT + HEADER_GAP + CONTENT_PAD_TOP + visibleCount * ROW_HEIGHT + CONTENT_PAD_BOTTOM;
        widthAnimation.update(targetWidth);
        heightAnimation.update(targetHeight);

        float width = widthAnimation.getValue() + EXTRA_WIDTH;
        float height = heightAnimation.getValue();
        float rightEdge = baseX + width;
        float x = baseX;

        RenderUtils.drawDefaultHudElementRects(eventRender.getContext().getMatrices(), x, y, width, height, colorTheme, isUnusualRectType());
        issue(14).draw(eventRender.getContext().getMatrices(), "Potions", x + 5.2f, y + 6f, -1);
        myfont(15).drawString("e", rightEdge - 12f, y + 7f, colorTheme);

        float offsetY = HEADER_HEIGHT + HEADER_GAP + CONTENT_PAD_TOP;
        int effectIndex = 0;

        for (StatusEffect type : renderOrder) {

            float animValue = getAnimation(type).getValue();
            PotionSnapshot snapshot = snapshots.get(type);
            if (animValue <= 0.01f || snapshot == null) {
                effectIndex++;
                continue;
            }

            ScissorUtils.push();
            ScissorUtils.setFromComponentCoordinates(x, y, width, height);

            int alpha = (int) (255 * animValue);

            float iconSize = 8f;
            float iconX = x + 4; // Прижимаем иконку ближе к левому краю (было 5)
            float iconY = y + offsetY - 2;

            if (snapshot.entry != null) {
                Sprite sprite = mc.getStatusEffectSpriteManager().getSprite(snapshot.entry);
                RenderUtils.drawSprite(eventRender.getContext().getMatrices(), sprite, iconX, iconY, (int) iconSize, ColorUtils.rgba(255,255,255,alpha));
            }

            String baseName = snapshot.baseName;
            String levelSuffix = getLevelSuffix(snapshot.amplifier);
            float textX = iconX + iconSize + 3;
            float textY = y + 1 + offsetY;

            issue(12).draw(eventRender.getContext().getMatrices(), baseName, textX, textY, ColorUtils.rgba(255,255,255,alpha));

            if (!levelSuffix.isEmpty()) {
                float baseWidth = issue(12).getWidth(baseName);
                issue(12).draw(eventRender.getContext().getMatrices(), " " + levelSuffix, textX + baseWidth, textY + 0.2f, ColorUtils.rgba(255,255,255,alpha));
            }

            String time = formatDuration(snapshot.duration, snapshot.infinite);
            float timeBoxWidth = Math.max(issue(10).getStringWidth(time) + 4, 9f);
            float ringSize = 6f;
            float ringGap = 3f;

            // Таймер будет ровно в 4px от правого края
            float timeBoxX = rightEdge - timeBoxWidth - 4;
            float ringX = timeBoxX - ringGap - ringSize; // Кольцо идет ровно перед таймером

            float boxY = y + offsetY - 2f;
            float blurStartX = ringX - 3f;
            float blurWidth = (timeBoxX + timeBoxWidth) - blurStartX + 1f;

            RenderUtils.drawBlur(eventRender.getContext().getMatrices(),
                    blurStartX,
                    boxY,
                    blurWidth,
                    9f,
                    1.5f, 5f,
                    ColorUtils.rgba(255, 255, 255, 255));

            RenderUtils.drawBlur(eventRender.getContext().getMatrices(),
                    blurStartX,
                    boxY,
                    blurWidth,
                    9f,
                    1.5f, 5f,
                    ColorUtils.rgba(0, 0, 0, 180));

            issue(12).drawCenteredString(eventRender.getContext().getMatrices(),
                    time,
                    timeBoxX + timeBoxWidth / 2,
                    y + offsetY + 1.3f,
                    ColorUtils.rgba(255,255,255,alpha));

            float progress = 1f;
            if (!snapshot.infinite) {
                int currentDuration = snapshot.duration;
                int maxDuration = maxDurations.getOrDefault(type, currentDuration);
                if (maxDuration > 0)
                    progress = MathHelper.clamp((float) currentDuration / (float) maxDuration, 0f, 1f);
                else progress = 0f;
            }

            int grayColor = ColorUtils.rgba(55, 55, 55, alpha);
            int ringColor = ColorUtils.setAlphaColor(colorTheme, alpha);
            float thickness = 1.75f;
            float ringY = y + offsetY - 0.7f;

            RenderUtils.drawRingArc(eventRender.getContext().getMatrices(), ringX, ringY, ringSize, thickness, -90f, 270f, grayColor);

            if (progress > 0f) {
                float endAngle = -90f + 360f * progress;
                RenderUtils.drawRingArc(eventRender.getContext().getMatrices(), ringX, ringY, ringSize, thickness, -90f, endAngle, ringColor);
            }

            offsetY += ROW_HEIGHT * animValue;
            effectIndex++;
            ScissorUtils.pop();
            ScissorUtils.unset();
        }
// ЕЩКЕРЕ ЖИРНЫЙ ПИДОРАС
        draggable.setWidth(width);
        draggable.setHeight(height);
    }

    private int getStableThemeColor() {
        if (!INSTANCE.themeStorage.getThemes().getTheme().getName().equals("Rainbow")) {
            return INSTANCE.themeStorage.getThemes().getTheme().color[0];
        }
        return ColorUtils.getThemeColor();
    }
}   
