package snill.client.client.modules.impl.misc;

import net.minecraft.text.Text;
import snill.client.Snill;
import snill.client.client.modules.Module;
import snill.client.client.modules.settings.implement.BooleanSetting;

public final class NameProtect extends Module {

    public static final NameProtect INSTANCE = new NameProtect();
    private static final String PROTECTED_NAME = "polardlc.ru";

    private final BooleanSetting hideFriends = new BooleanSetting("Скрыть друзей", false);

    private NameProtect() {
        super("NameProtect", "Защищает имена игроков", ModuleCategory.MISC);
        addSettings(hideFriends);
    }

    public static String getCustomName() {
        if (INSTANCE.isEnable()) {
            return PROTECTED_NAME;
        }

        return mc != null && mc.player != null ? mc.player.getNameForScoreboard() : "";
    }

    public static String getCustomName(String originalName) {
        return INSTANCE.patch(originalName);
    }

    public String patch(String originalName) {
        if (originalName == null || !isEnable() || mc == null || mc.player == null) {
            return originalName;
        }

        String localName = mc.player.getNameForScoreboard();
        if (contains(originalName, localName)) {
            return originalName.replace(localName, PROTECTED_NAME);
        }

        if (hideFriends.isState() && Snill.INSTANCE != null && Snill.INSTANCE.friendStorage != null) {
            for (String friend : Snill.INSTANCE.friendStorage.getFriends()) {
                if (contains(originalName, friend)) {
                    return originalName.replace(friend, PROTECTED_NAME);
                }
            }
        }

        return originalName;
    }

    public String patchIncomingText(String text) {
        return patch(text);
    }

    public Text patchText(Text text) {
        if (text == null) {
            return null;
        }

        String patched = patch(text.getString());
        if (patched.equals(text.getString())) {
            return text;
        }
        return Text.literal(patched).setStyle(text.getStyle());
    }

    public boolean shouldHideGrief() {
        return false;
    }

    private boolean contains(String text, String name) {
        return name != null && !name.isEmpty() && text.contains(name);
    }
}
