package snill.client.client.modules.impl.combat;

import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import snill.client.api.events.EventLink;
import snill.client.api.events.implement.EventAttackEntity;
import snill.client.api.events.implement.EventUpdate;
import snill.client.api.utils.player.InventoryUtils;
import snill.client.client.modules.Module;
import snill.client.client.modules.settings.implement.BooleanSetting;
import snill.client.client.modules.settings.implement.FloatSetting;
import snill.client.client.modules.settings.implement.ModeSetting;

public class MaceHelper extends Module {
    public static MaceHelper INSTANCE = new MaceHelper();

    public final ModeSetting server = new ModeSetting("Сервер", "FunTime", "FunTime", "HolyWorld", "ReallyWorld", "Универсальный");
    public final FloatSetting minFall = new FloatSetting("Мин. падение", 1.8f, 0.5f, 5.0f, 0.1f);
    public final BooleanSetting swapBack = new BooleanSetting("Возвращать оружие", true);
    public final BooleanSetting onlyAura = new BooleanSetting("Только с Aura", true);
    public final BooleanSetting preferDensity = new BooleanSetting("Приоритет плотности (Density)", true);

    private int previousSlot = -1;
    private boolean swappedByUs = false;

    public MaceHelper() {
        super("MaceHelper", "[FunTime / HolyWorld / 1.21 Анархия] Авто-переключение на булаву при падении", ModuleCategory.COMBAT);
        addSettings(server, minFall, swapBack, onlyAura, preferDensity);
    }

    @EventLink
    public void onUpdate(final EventUpdate event) {
        if (mc.player == null || mc.world == null) return;

        LivingEntity target = getTarget();
        if (onlyAura.isState() && (target == null || !target.isAlive())) {
            resetSwapIfNeeded();
            return;
        }

        boolean isFalling = mc.player.fallDistance >= minFall.get() && mc.player.getVelocity().y < -0.08;

        if (isFalling) {
            if (target != null && mc.player.distanceTo(target) > 4.5f) {
                return;
            }

            int maceSlot = findMaceSlot();
            if (maceSlot != -1) {
                if (mc.player.getInventory().selectedSlot != maceSlot && !swappedByUs) {
                    previousSlot = mc.player.getInventory().selectedSlot;
                    if (mc.player.networkHandler != null) {
                        mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(maceSlot));
                    }
                    mc.player.getInventory().selectedSlot = maceSlot;
                    swappedByUs = true;
                }
            }
        } else if (mc.player.isOnGround()) {
            resetSwapIfNeeded();
        }
    }

    @EventLink
    public void onAttack(final EventAttackEntity event) {
        if (mc.player == null) return;
        if (swappedByUs && swapBack.isState()) {
            resetSwapIfNeeded();
        }
    }

    private void resetSwapIfNeeded() {
        if (swappedByUs && previousSlot != -1 && mc.player != null) {
            if (swapBack.isState()) {
                if (mc.player.networkHandler != null) {
                    mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(previousSlot));
                }
                mc.player.getInventory().selectedSlot = previousSlot;
            }
            previousSlot = -1;
            swappedByUs = false;
        }
    }

    private int findMaceSlot() {
        if (mc.player == null) return -1;
        int bestSlot = -1;
        int bestDensity = -1;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() == Items.MACE) {
                if (!preferDensity.isState()) return i;
                int density = InventoryUtils.getEnchantmentLevel(stack, Enchantments.DENSITY);
                if (density > bestDensity) {
                    bestDensity = density;
                    bestSlot = i;
                }
            }
        }
        return bestSlot;
    }

    private LivingEntity getTarget() {
        if (Aura.INSTANCE != null && Aura.INSTANCE.isEnable()) {
            return Aura.INSTANCE.getTarget();
        }
        return null;
    }

    @Override
    public void onDisable() {
        resetSwapIfNeeded();
        super.onDisable();
    }
}
