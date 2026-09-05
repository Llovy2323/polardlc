package snill.client.client.modules.impl.render;

import snill.client.client.modules.Module;
import snill.client.client.modules.settings.implement.BooleanSetting;
import snill.client.client.modules.settings.implement.ListSetting;

public class Removals extends Module {

    public static Removals INSTANCE = new Removals();

    private final ListSetting elements = new ListSetting("Элементы",
            new BooleanSetting("Огонь", false),
            new BooleanSetting("Плохие эффекты", false),
            new BooleanSetting("Оверлей в блоке", false),
            new BooleanSetting("Частицы", false),
            new BooleanSetting("Погода", false),
            new BooleanSetting("Облака", false),
            new BooleanSetting("Блок-сущности", false),
            new BooleanSetting("Тени", false),
            new BooleanSetting("Анимацию тотема", false)
    );

    public Removals() {
        super("Removals", "Убирает выбранные элементы рендера", ModuleCategory.RENDER);
        addSettings(elements);
    }

    public boolean isEnabled(String element) {
        return isEnable() && elements.is(element);
    }

    public boolean isTotemAnimationDisabled() {
        return isEnabled("Анимацию тотема");
    }
}
