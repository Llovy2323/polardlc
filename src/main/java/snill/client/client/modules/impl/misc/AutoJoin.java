package snill.client.client.modules.impl.misc;

import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import snill.client.api.events.EventLink;
import snill.client.api.events.implement.EventPacket;
import snill.client.api.events.implement.EventUpdate;
import snill.client.api.utils.math.TimerUtils;
import snill.client.client.modules.Module;
import snill.client.client.modules.settings.implement.FloatSetting;

public final class AutoJoin extends Module {
    public static AutoJoin INSTANCE = new AutoJoin();

    private final FloatSetting griefSelection = new FloatSetting("Гриферский мир", 1.0f, 1.0f, 54.0f, 1.0f);
    private final FloatSetting delay = new FloatSetting("Задержка (мс)", 350.0f, 100.0f, 2000.0f, 50.0f);
    private final TimerUtils timerUtil = new TimerUtils();

    public AutoJoin() {
        super("AutoJoin", "Автоматически заходит на сервер", ModuleCategory.MISC);
        addSettings(griefSelection, delay);
    }

    @Override
    public void onEnable() {
        super.onEnable();
    }

    @EventLink
    public void onUpdate(EventUpdate event) {
        handleEventUpdate();
    }

    @EventLink
    public void onPacket(EventPacket eventPacket) {
        if (eventPacket.getType() != EventPacket.Type.RECEIVE) {
            return;
        }

        if (eventPacket.getPacket() instanceof GameJoinS2CPacket) {
            try {
                if (mc.inGameHud == null || mc.inGameHud.getPlayerListHud() == null) {
                    return;
                }
            } catch (Exception ignored) {
            }
        }

        if (eventPacket.getPacket() instanceof GameMessageS2CPacket packet) {
            String message = packet.content().getString();
            if (message.contains("К сожалению сервер переполнен")
                    || message.contains("Подождите 20 секунд!")
                    || message.contains("большой поток игроков")) {
            }
        }
    }

    private void handleEventUpdate() {
        if (mc.player == null || mc.currentScreen == null) {
            return;
        }

        if (mc.currentScreen instanceof GenericContainerScreen screen) {
            try {
                ScreenHandler container = screen.getScreenHandler();

                for (int i = 0; i < container.slots.size(); i++) {
                    String s = container.slots.get(i).getStack().getName().getString();
                    int numberGrief = griefSelection.getValue().intValue();

                    if (s.contains("ГРИФЕРСКОЕ ВЫЖИВАНИЕ") || s.contains("ГРИФ #" + numberGrief + " (1.16.5+)")) {
                        if (timerUtil.finished((long) delay.get())) {
                            mc.interactionManager.clickSlot(
                                    container.syncId,
                                    i,
                                    0,
                                    SlotActionType.PICKUP,
                                    mc.player
                            );
                            timerUtil.reset();
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }
}