package snill.client.client.modules.impl.misc;

import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import snill.client.api.events.EventLink;
import snill.client.api.events.implement.EventBinding;
import snill.client.api.events.implement.EventUpdate;
import snill.client.api.utils.input.KeyBoardUtils;
import snill.client.api.utils.player.InventoryUtils;
import snill.client.client.modules.Module;
import snill.client.client.modules.settings.implement.BindSetting;
import snill.client.client.modules.settings.implement.BooleanSetting;
import snill.client.client.modules.settings.implement.ModeSetting;

public class ClickPearl extends Module {

    public static ClickPearl INSTANCE = new ClickPearl();

    private final ModeSetting server = new ModeSetting("Сервер", "FunTime", "FunTime", "HolyWorld", "ReallyWorld", "Универсальный");
    private final BindSetting keyToPearl = new BindSetting("Кнопка", -1);
    private final BooleanSetting middleClick = new BooleanSetting("По колесику (СКМ)", true);
    private final BooleanSetting bypass = new BooleanSetting("Обход", true);

    private boolean use;

    public ClickPearl() {
        super("ClickPearl", "[Все серверы / FunTime / HolyWorld] Кидает перку по бинду или колесику мыши", ModuleCategory.MISC);
        addSettings(server, keyToPearl, middleClick, bypass);
    }

    @Override
    public void onEnable() {
        this.use = false;
        super.onEnable();
    }

    @EventLink
    public void onEvent(final EventBinding event) {
        if (mc.currentScreen != null) return;

        if (middleClick.isState() && event.getKey() == KeyBoardUtils.MOUSE_BUTTON_OFFSET + 2) {
            this.use = true;
        } else if (keyToPearl.getKey() != -1 && event.getKey() == keyToPearl.getKey()) {
            this.use = true;
        }
    }

    @EventLink
    public void onEvent(final EventUpdate event) {
        if (!this.use) return;
        if (mc.player == null || mc.world == null) {
            this.use = false;
            return;
        }

        int oldSlot = mc.player.getInventory().selectedSlot;
        int pearlSlot = InventoryUtils.find(Items.ENDER_PEARL, 0, 36);

        if (pearlSlot > 9 && this.use) {
            mc.player.setSprinting(false);
        }

        if (pearlSlot == -1) {
            this.use = false;
            return;
        }

        if (pearlSlot < 9) {
            if (pearlSlot != oldSlot && mc.player.networkHandler != null) {
                mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(pearlSlot));
                mc.player.getInventory().selectedSlot = pearlSlot;
            }
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            if (pearlSlot != oldSlot && mc.player.networkHandler != null) {
                mc.player.getInventory().selectedSlot = oldSlot;
                mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(oldSlot));
            }
        } else {
            InventoryUtils.swapAndUseHvH(Items.ENDER_PEARL);
        }

        this.use = false;
    }
}