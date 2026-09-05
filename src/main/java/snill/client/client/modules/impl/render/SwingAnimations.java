package snill.client.client.modules.impl.render;

import snill.client.client.modules.Module;
import snill.client.client.modules.settings.implement.BooleanSetting;
import snill.client.client.modules.settings.implement.FloatSetting;
import snill.client.client.modules.settings.implement.ModeSetting;

public class SwingAnimations extends Module {

    public static SwingAnimations INSTANCE = new SwingAnimations();

    public boolean swimmingAnimation = true;
    public boolean climbAndCrawl = true;
    public boolean mb3DCompat = false;

    public final BooleanSetting swingEnabled = new BooleanSetting("Анимация свинга", true);

    public final ModeSetting swingType = new ModeSetting(
            "Тип свинга",
            "Smooth",
            "Smooth", "Static", "Down", "DropDown", "Poke", "SelfBack",
            "Feast", "ToBack", "Block", "Akrien", "Break", "Pander", "Slant"
    ).visible(swingEnabled::isState);

    public final FloatSetting swingStrength = new FloatSetting("Сила анимации", 1f, 0.1f, 3f, 0.01f)
            .visible(() -> swingEnabled.isState() && !swingType.is("Pander"));

    public final FloatSetting corner = new FloatSetting("Угол DropDown", 12f, 1f, 360f, 1f)
            .visible(() -> swingEnabled.isState() && swingType.is("DropDown"));

    public final FloatSetting slant = new FloatSetting("Наклон DropDown", 12f, 1f, 360f, 1f)
            .visible(() -> swingEnabled.isState() && swingType.is("DropDown"));

    public final BooleanSetting smoothEnabled = new BooleanSetting("Плавная анимация", false);

    public final FloatSetting slowAnimationSpeed = new FloatSetting("Скорость анимации", 12f, 1f, 50f, 1f)
            .visible(smoothEnabled::isState);

    public final BooleanSetting auraTargetOnly = new BooleanSetting("Только при Aura", false);
    public final BooleanSetting swapHands = new BooleanSetting("Свап рук", false);
    public final BooleanSetting eatAnim = new BooleanSetting("Анимация еды", false);

    public SwingAnimations() {
        super("SwingAnimations", "Кастомная анимация аттаки", ModuleCategory.RENDER);
        addSettings(
                swingEnabled, swingType, swingStrength, corner, slant,
                smoothEnabled, slowAnimationSpeed,
                auraTargetOnly, swapHands,
                eatAnim
        );
    }
}
