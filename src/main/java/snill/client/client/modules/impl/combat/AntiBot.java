package snill.client.client.modules.impl.combat;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.entity.effect.StatusEffects;
import snill.client.api.events.EventLink;
import snill.client.api.events.implement.EventUpdate;
import snill.client.client.modules.Module;
import snill.client.client.modules.settings.implement.ModeSetting;

public class AntiBot extends Module {

    public static AntiBot INSTANCE = new AntiBot();

    public static final List<Entity> isBot = new ArrayList<>();

    private static final String MODE_DEFAULT = "Default";
    private static final String MODE_CAKE_ARTY = "Cake/Arty";

    private final ModeSetting mode = new ModeSetting("Режим", MODE_DEFAULT, MODE_DEFAULT, MODE_CAKE_ARTY);

    public AntiBot() {
        super("AntiBot", "Удаляет ботов от античита", ModuleCategory.COMBAT);
        addSettings(mode);
    }

    @EventLink
    public void onUpdate(EventUpdate event) {
        this.newMatrix();
    }

    public void newMatrix() {
        if (mc.world == null) return;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (mc.player == player) {
                continue;
            }

            boolean shouldRemove = mode.is(MODE_CAKE_ARTY) ? isCakeArtyBot(player) : isLegacyBot(player);

            if (shouldRemove) {
                if (!isBot.contains(player)) {
                    isBot.add(player);
                }
            } else {
                isBot.remove(player);
            }
        }
    }

    private boolean isCakeArtyBot(PlayerEntity player) {
        if (player == null) {
            return false;
        }

        if (!isInvisibleTarget(player)) {
            return false;
        }

        return countArmorPieces(player) == 0;
    }

    private boolean isInvisibleTarget(PlayerEntity player) {
        if (player.isInvisible()) {
            return true;
        }

        if (player.hasStatusEffect(StatusEffects.INVISIBILITY)) {
            return true;
        }

        return mc.player != null && player.isInvisibleTo(mc.player);
    }

    private boolean isLegacyBot(PlayerEntity player) {
        return player.getInventory().armor.get(0).getItem() != Items.AIR
                && player.getInventory().armor.get(1).getItem() != Items.AIR
                && player.getInventory().armor.get(2).getItem() != Items.AIR
                && player.getInventory().armor.get(3).getItem() != Items.AIR
                && player.getInventory().armor.get(0).isEnchantable()
                && player.getInventory().armor.get(1).isEnchantable()
                && player.getInventory().armor.get(2).isEnchantable()
                && player.getInventory().armor.get(3).isEnchantable()
                && player.getOffHandStack().getItem() == Items.AIR
                && (player.getInventory().armor.get(0).getItem() == Items.LEATHER_BOOTS
                || player.getInventory().armor.get(1).getItem() == Items.LEATHER_LEGGINGS
                || player.getInventory().armor.get(2).getItem() == Items.LEATHER_CHESTPLATE
                || player.getInventory().armor.get(3).getItem() == Items.LEATHER_HELMET
                || player.getInventory().armor.get(0).getItem() == Items.IRON_BOOTS
                || player.getInventory().armor.get(1).getItem() == Items.IRON_LEGGINGS
                || player.getInventory().armor.get(2).getItem() == Items.IRON_CHESTPLATE
                || player.getInventory().armor.get(3).getItem() == Items.IRON_HELMET)
                && player.getMainHandStack().getItem() != Items.AIR
                && !player.getInventory().armor.get(0).isDamaged()
                && !player.getInventory().armor.get(1).isDamaged()
                && !player.getInventory().armor.get(2).isDamaged()
                && !player.getInventory().armor.get(3).isDamaged()
                && player.getHungerManager().getFoodLevel() == 20;
    }

    private int countArmorPieces(PlayerEntity player) {
        int armorCount = 0;
        for (ItemStack stack : player.getInventory().armor) {
            if (stack != null && !stack.isEmpty() && stack.getItem() != Items.AIR) {
                armorCount++;
            }
        }
        return armorCount;
    }

    public static boolean checkBot(LivingEntity entity) {
        return entity instanceof PlayerEntity && isBot.contains(entity);
    }

    @Override
    public void onDisable() {
        super.onDisable();
        isBot.clear();
    }
}
