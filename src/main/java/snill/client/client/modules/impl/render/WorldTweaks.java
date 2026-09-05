package snill.client.client.modules.impl.render;

import snill.client.api.events.EventLink;
import snill.client.api.events.implement.EventUpdate;
import snill.client.api.utils.color.ColorUtils;
import snill.client.client.modules.Module;
import snill.client.client.modules.settings.implement.BooleanSetting;
import snill.client.client.modules.settings.implement.FloatSetting;
import snill.client.client.modules.settings.implement.ListSetting;
import snill.client.client.modules.settings.implement.ModeSetting;
import snill.client.Snill;

public class WorldTweaks extends Module {

    public static WorldTweaks INSTANCE = new WorldTweaks();

    private final ListSetting worldSettings = new ListSetting("World Settings",
            new BooleanSetting("Time", true),
            new BooleanSetting("Fog", true),
            new BooleanSetting("SkyShader", false));

    private final FloatSetting timeSetting = new FloatSetting("Time", 12f, 0f, 24f, 1f)
            .visible(() -> worldSettings.is("Time"));
    private final FloatSetting fogDistanceSetting = new FloatSetting("Fog Distance", 100f, 20f, 200f, 1f)
            .visible(() -> worldSettings.is("Fog"));
    private final ModeSetting skyMode = new ModeSetting("Sky", "Обычное",
            "Обычное", "Космос", "Плазма", "Balatro", "Лето", "Сакура", "Эфир", "Метель")
            .visible(() -> worldSettings.is("SkyShader"));
    private final ModeSetting skySummerVariant = new ModeSetting("Summer Variant", "Обычное", "Обычное", "Ночное")
            .visible(() -> worldSettings.is("SkyShader") && skyMode.is("Лето"));
    private final FloatSetting skyScale = new FloatSetting("Sky Scale", 1f, 0.2f, 3f, 0.05f)
            .visible(() -> worldSettings.is("SkyShader") && shouldUseCustomSky());
    private final FloatSetting skySpeed = new FloatSetting("Sky Speed", 1f, 0f, 3f, 0.05f)
            .visible(() -> worldSettings.is("SkyShader") && shouldUseCustomSky());

    public WorldTweaks() {
        super("CustomWorld", "Настройка времени, тумана и неба", ModuleCategory.RENDER);
        addSettings(worldSettings, timeSetting, fogDistanceSetting, skyMode, skySummerVariant, skyScale, skySpeed);
    }

    public boolean isTimeEnabled() {
        return isEnable() && worldSettings.is("Time");
    }

    public boolean isFogEnabled() {
        return isEnable() && worldSettings.is("Fog");
    }

    public long getForcedTime() {
        return (long) (timeSetting.get() * 1000.0f);
    }

    public float getFogDistance() {
        return fogDistanceSetting.get();
    }

    public int getFogColor() {
        return getThemeBaseColor();
    }

    public boolean shouldUseCustomSky() {
        return isEnable() && worldSettings.is("SkyShader") && !skyMode.is("Обычное");
    }

    public boolean isSummerNightSky() {
        return skyMode.is("Лето") && skySummerVariant.is("Ночное");
    }

    public String getSkyMode() {
        return skyMode.getCurrent();
    }

    public float getSkyScale() {
        return skyScale.get();
    }

    public float getSkySpeed() {
        return skySpeed.get();
    }

    public float getSkyModeIndex() {
        if (skyMode.is("Эфир") || skyMode.is("Метель") || skyMode.is("Сакура")) {
            return 1f;
        }
        if (skyMode.is("Balatro")) {
            return 2f;
        }
        if (skyMode.is("Космос")) {
            return 3f;
        }
        return 0f;
    }

    @EventLink
    public void onUpdate(EventUpdate event) {
        if (!isTimeEnabled() || mc.world == null) {
            return;
        }
        mc.world.getLevelProperties().setTimeOfDay(getForcedTime());
    }

    private int getThemeBaseColor() {
        if (Snill.INSTANCE == null
                || Snill.INSTANCE.themeStorage == null
                || Snill.INSTANCE.themeStorage.getThemes() == null
                || Snill.INSTANCE.themeStorage.getThemes().getTheme() == null) {
            return ColorUtils.getThemeColor();
        }

        var theme = Snill.INSTANCE.themeStorage.getThemes().getTheme();
        if (!"Rainbow".equals(theme.getName()) && theme.color != null && theme.color.length > 0) {
            return theme.color[0];
        }
        return ColorUtils.getThemeColor();
    }
}
