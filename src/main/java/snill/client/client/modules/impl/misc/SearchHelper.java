package snill.client.client.modules.impl.misc;

import net.minecraft.item.ItemStack;
import snill.client.api.utils.chat.ChatUtils;
import snill.client.client.modules.Module;
import snill.client.client.modules.settings.implement.BindSetting;

import java.util.Locale;

public class SearchHelper extends Module {

    public static SearchHelper INSTANCE = new SearchHelper();

    public final BindSetting bind = new BindSetting("Бинд", -1);

    public SearchHelper() {
        super("SearchHelper", "Ищет в АХ предмет из руки по бинду", ModuleCategory.MISC);
        addSettings(bind);
    }

    public void onBindPressed() {
        if (mc.player == null || mc.getNetworkHandler() == null || mc.currentScreen != null) {
            return;
        }

        if (bind.getKey() == -1) {
            return;
        }

        ItemStack stack = mc.player.getMainHandStack();
        if (stack.isEmpty()) {
            ChatUtils.sendMessage("§cВозьми предмет в руку!");
            return;
        }

        String itemName = cleanItemName(stack.getName().getString());
        if (itemName.isBlank()) {
            return;
        }
        mc.getNetworkHandler().sendChatCommand("ah search " + itemName);
    }

    private String cleanItemName(String name) {
        if (name == null) {
            return "";
        }

        String cleaned = name
                .replaceAll("\\[[^\\]]*\\]", " ")
                .replaceAll("[^\\p{L}\\p{N} _.-]+", " ")
                .replaceAll("\\s+", " ")
                .trim();

        return cleaned.toLowerCase(Locale.ROOT);
    }
}
