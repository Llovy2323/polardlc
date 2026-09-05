package snill.client.client.modules.impl.render;

import snill.client.api.utils.render.hands.ShaderHandsRenderer;
import snill.client.client.modules.Module;
import snill.client.client.modules.settings.implement.BooleanSetting;
import snill.client.client.modules.settings.implement.FloatSetting;
import snill.client.client.modules.settings.implement.ModeSetting;
import snill.client.client.modules.settings.implement.TextSetting;

/** Port of Polar 1.3 Hands. Rendering lives in {@link ShaderHandsRenderer}. */
public class ShaderHands extends Module {

    public static final ShaderHands INSTANCE = new ShaderHands();
    private static final ShaderHandsRenderer RENDERER = ShaderHandsRenderer.getInstance();

    public final ModeSetting mode = new ModeSetting("Режим", "Заливка", "Зеркало", "Заливка");

    public final ModeSetting mirrorColorMode = new ModeSetting("Цвет зеркала", "Свой", "Интерфейс", "Свой")
            .visible(() -> mode.is("Зеркало"));
    public final TextSetting mirrorColor = new TextSetting("Цвет", "#8A98FF", 7)
            .visible(() -> mode.is("Зеркало") && mirrorColorMode.is("Свой"));
    public final FloatSetting mixFactor = new FloatSetting("Смешивание", 0f, 0f, 0.5f, 0.01f)
            .visible(() -> mode.is("Зеркало"));

    public final BooleanSetting fillRainbow = new BooleanSetting("Радужная", false)
            .visible(() -> mode.is("Заливка"));
    public final FloatSetting rainbowSpeed = new FloatSetting("Скорость радуги", 0.4f, 0f, 2f, 0.05f)
            .visible(() -> mode.is("Заливка") && fillRainbow.isState());
    public final FloatSetting rainbowScale = new FloatSetting("Масштаб радуги", 1f, 0.2f, 3f, 0.1f)
            .visible(() -> mode.is("Заливка") && fillRainbow.isState());
    public final ModeSetting fillColorMode = new ModeSetting("Цвет заливки", "Свой", "Интерфейс", "Свой")
            .visible(() -> mode.is("Заливка") && !fillRainbow.isState());
    public final TextSetting fillColor = new TextSetting("Цвет заливки (свой)", "#FF4444", 7)
            .visible(() -> mode.is("Заливка") && !fillRainbow.isState() && fillColorMode.is("Свой"));
    public final FloatSetting fillAlpha = new FloatSetting("Прозрачность заливки", 0.8f, 0f, 1f, 0.05f)
            .visible(() -> mode.is("Заливка"));
    public final BooleanSetting keepShading = new BooleanSetting("Сохранить тени", true)
            .visible(() -> mode.is("Заливка"));
    public final FloatSetting shadingStrength = new FloatSetting("Сила теней", 0.3f, 0f, 1f, 0.05f)
            .visible(() -> mode.is("Заливка") && keepShading.isState());

    public final BooleanSetting outlineEnabled = new BooleanSetting("Обводка", false);
    public final FloatSetting outlineWidth = new FloatSetting("Толщина обводки", 1f, 0.5f, 3f, 0.5f)
            .visible(outlineEnabled::isState);

    public final BooleanSetting glowEnabled = new BooleanSetting("Глов", true);
    public final FloatSetting glowRadius = new FloatSetting("Размытие", 4f, 1f, 6f, 1f)
            .visible(glowEnabled::isState);
    public final BooleanSetting outerGlow = new BooleanSetting("Внешний глов", true)
            .visible(glowEnabled::isState);
    public final FloatSetting glowExposure = new FloatSetting("Яркость", 2f, 0.5f, 5f, 0.1f)
            .visible(() -> glowEnabled.isState() && outerGlow.isState());

    public final BooleanSetting autoColor = new BooleanSetting("Авто цвет", false)
            .visible(() -> glowEnabled.isState() || outlineEnabled.isState());
    public final FloatSetting saturation = new FloatSetting("Насыщенность", 1.4f, 0.5f, 3f, 0.1f)
            .visible(() -> (glowEnabled.isState() || outlineEnabled.isState()) && autoColor.isState());
    public final ModeSetting effectColorMode = new ModeSetting("Цвет эффекта", "Свой", "Интерфейс", "Свой")
            .visible(() -> (glowEnabled.isState() || outlineEnabled.isState()) && !autoColor.isState());
    public final TextSetting outlineColor = new TextSetting("Цвет обводки", "#8A98FF", 7)
            .visible(() -> outlineEnabled.isState() && effectColorMode.is("Свой"));
    public final TextSetting glowColor1 = new TextSetting("Цвет глова 1", "#8A98FF", 7)
            .visible(() -> glowEnabled.isState() && !autoColor.isState() && effectColorMode.is("Свой"));
    public final TextSetting glowColor2 = new TextSetting("Цвет глова 2", "#FF6BAC", 7)
            .visible(() -> glowEnabled.isState() && !autoColor.isState() && effectColorMode.is("Свой"));

    public final BooleanSetting trailEnabled = new BooleanSetting("Шлейф", true)
            .visible(() -> glowEnabled.isState() && outerGlow.isState());
    public final FloatSetting trailFade = new FloatSetting("Скорость затухания", 0.009f, 0.002f, 0.2f, 0.002f)
            .visible(() -> glowEnabled.isState() && outerGlow.isState() && trailEnabled.isState());
    public final FloatSetting trailRise = new FloatSetting("Подъём", 0.14f, 0f, 1.5f, 0.05f)
            .visible(() -> glowEnabled.isState() && outerGlow.isState() && trailEnabled.isState());
    public final FloatSetting trailSway = new FloatSetting("Качание", 0.025f, 0f, 0.2f, 0.005f)
            .visible(() -> glowEnabled.isState() && outerGlow.isState() && trailEnabled.isState());
    public final FloatSetting trailTurb = new FloatSetting("Турбулентность", 0f, 0f, 0.6f, 0.01f)
            .visible(() -> glowEnabled.isState() && outerGlow.isState() && trailEnabled.isState());
    public final FloatSetting trailFlicker = new FloatSetting("Мерцание", 0f, 0f, 0.2f, 0.01f)
            .visible(() -> glowEnabled.isState() && outerGlow.isState() && trailEnabled.isState());
    public final BooleanSetting trailBurst = new BooleanSetting("Сдув при ударе", true)
            .visible(() -> glowEnabled.isState() && outerGlow.isState() && trailEnabled.isState());
    public final FloatSetting trailBurstPower = new FloatSetting("Сила сдува", 2.5f, 1f, 10f, 0.5f)
            .visible(() -> glowEnabled.isState() && outerGlow.isState() && trailEnabled.isState() && trailBurst.isState());
    public final BooleanSetting trailModel = new BooleanSetting("Шлейф модели", true)
            .visible(() -> glowEnabled.isState() && outerGlow.isState() && trailEnabled.isState());
    public final FloatSetting trailModelAlpha = new FloatSetting("Прозрачность модели", 0.4f, 0.1f, 1f, 0.05f)
            .visible(() -> glowEnabled.isState() && outerGlow.isState() && trailEnabled.isState() && trailModel.isState());

    public ShaderHands() {
        super("Hands", "Накладывает эффект на ваши руки", ModuleCategory.RENDER);
        addSettings(mode,
                mirrorColorMode, mirrorColor, mixFactor,
                fillRainbow, rainbowSpeed, rainbowScale, fillColorMode, fillColor, fillAlpha,
                keepShading, shadingStrength,
                glowEnabled, glowRadius, outerGlow, glowExposure,
                autoColor, saturation, effectColorMode, glowColor1, glowColor2,
                trailEnabled, trailFade, trailRise, trailSway, trailTurb, trailFlicker,
                trailBurst, trailBurstPower, trailModel, trailModelAlpha,
                outlineEnabled, outlineWidth, outlineColor);
    }

    @Override
    public void onDisable() {
        RENDERER.release();
        super.onDisable();
    }
}
