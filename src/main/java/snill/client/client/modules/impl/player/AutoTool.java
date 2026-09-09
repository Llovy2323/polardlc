package snill.client.client.modules.impl.player;

import net.minecraft.block.BlockState;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import snill.client.api.events.EventLink;
import snill.client.api.events.implement.EventUpdate;
import snill.client.api.utils.player.HotbarUtil;
import snill.client.api.utils.player.InventoryUtils;
import snill.client.api.utils.player.SlotSearchResult;
import snill.client.client.modules.Module;
import snill.client.client.modules.settings.implement.BooleanSetting;
import snill.client.client.modules.settings.implement.FloatSetting;

public class AutoTool extends Module {

    public static AutoTool INSTANCE = new AutoTool();

    private final BooleanSetting switchBack = new BooleanSetting("Возвращать предмет", true);
    private final BooleanSetting autoWeapon = new BooleanSetting("Оружие на атаку", true);
    private final BooleanSetting saveDurability = new BooleanSetting("Сохранять прочность", true);
    private final FloatSetting swapDelay = new FloatSetting("Задержка возврата", 2.0F, 0.0F, 10.0F, 1.0F);

    private int previousSlot = -1;
    private BlockPos lastMinedPos = null;
    private int ticksSinceMining = 0;

    public AutoTool() {
        super("AutoTool", "При копании берет лучший предмет", ModuleCategory.PLAYER);
        addSettings(switchBack, autoWeapon, saveDurability, swapDelay);
    }

    @EventLink
    public void onEvent(final EventUpdate event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            reset();
            return;
        }

        if (mc.player.isCreative() || mc.player.isSpectator()) {
            reset();
            return;
        }

        // Case 1: AutoWeapon - player attacks an entity
        if (autoWeapon.isState() && isAttackingEntity()) {
            int weaponSlot = findOptimalWeapon();
            if (weaponSlot != -1) {
                if (weaponSlot != mc.player.getInventory().selectedSlot && previousSlot == -1) {
                    previousSlot = mc.player.getInventory().selectedSlot;
                }
                HotbarUtil.switchTo(weaponSlot);
                ticksSinceMining = 0;
                return;
            }
        }

        // Case 2: Mining a block
        BlockPos targetPos = getTargetBlockPos();
        boolean mining = targetPos != null && isMiningActive();

        if (mining) {
            BlockState state = mc.world.getBlockState(targetPos);
            int toolSlot = findOptimalTool(targetPos, state);

            if (toolSlot != -1) {
                if (toolSlot != mc.player.getInventory().selectedSlot && previousSlot == -1) {
                    previousSlot = mc.player.getInventory().selectedSlot;
                }
                HotbarUtil.switchTo(toolSlot);
            }
            ticksSinceMining = 0;
        } else {
            // Case 3: Idle / not mining
            ticksSinceMining++;

            if (switchBack.isState() && previousSlot != -1) {
                int delay = (int) swapDelay.get();
                if (ticksSinceMining >= delay) {
                    HotbarUtil.switchTo(previousSlot);
                    previousSlot = -1;
                }
            } else if (!switchBack.isState()) {
                previousSlot = -1;
            }
        }
    }

    private boolean isAttackingEntity() {
        if (!mc.options.attackKey.isPressed()) return false;
        if (mc.targetedEntity != null && mc.targetedEntity.isAlive()) return true;
        if (mc.crosshairTarget instanceof EntityHitResult ehr && ehr.getEntity() != null && ehr.getEntity().isAlive()) {
            return true;
        }
        return false;
    }

    private int findOptimalWeapon() {
        SlotSearchResult sword = HotbarUtil.getSwordHotBar();
        SlotSearchResult axe = HotbarUtil.getAxeHotBar();

        if (sword.found() && axe.found()) {
            float swordDmg = HotbarUtil.getHitDamage(sword.stack(), mc.player);
            float axeDmg = HotbarUtil.getHitDamage(axe.stack(), mc.player);
            return swordDmg >= axeDmg ? sword.slot() : axe.slot();
        } else if (sword.found()) {
            return sword.slot();
        } else if (axe.found()) {
            return axe.slot();
        }
        return -1;
    }

    private boolean isMiningActive() {
        return mc.interactionManager.isBreakingBlock() || mc.options.attackKey.isPressed();
    }

    private BlockPos getTargetBlockPos() {
        if (mc.crosshairTarget instanceof BlockHitResult blockHit && blockHit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = blockHit.getBlockPos();
            BlockState state = mc.world.getBlockState(pos);
            if (!state.isAir() && state.getHardness(mc.world, pos) >= 0.0F) {
                lastMinedPos = pos;
                return pos;
            }
        }

        // If crosshair momentarily flicked off block while interactionManager is still breaking
        if (mc.interactionManager.isBreakingBlock() && lastMinedPos != null) {
            BlockState state = mc.world.getBlockState(lastMinedPos);
            if (!state.isAir() && state.getHardness(mc.world, lastMinedPos) >= 0.0F) {
                return lastMinedPos;
            }
        }

        return null;
    }

    private int findOptimalTool(BlockPos pos, BlockState blockState) {
        if (blockState == null || blockState.isAir()) {
            return -1;
        }
        if (pos != null && blockState.getHardness(mc.world, pos) < 0.0F) {
            return -1;
        }

        int bestSlot = -1;
        float bestSpeed = 1.0F;
        boolean toolRequired = blockState.isToolRequired();
        boolean bestSuitable = false;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;

            // Preserve tool if durability is critical (<= 2)
            if (saveDurability.isState() && stack.isDamageable()) {
                if (stack.getMaxDamage() - stack.getDamage() <= 2) {
                    continue;
                }
            }

            boolean suitable = stack.isSuitableFor(blockState);
            float speed = getToolSpeed(stack, blockState);

            if (toolRequired) {
                if (suitable && !bestSuitable) {
                    bestSuitable = true;
                    bestSpeed = speed;
                    bestSlot = i;
                    continue;
                } else if (!suitable && bestSuitable) {
                    continue; // Never choose an unsuitable tool over a suitable one
                }
            }

            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot = i;
                if (suitable) {
                    bestSuitable = true;
                }
            }
        }

        // If the current slot is already one of the best tools, don't swap unnecessarily
        int currentSlot = mc.player.getInventory().selectedSlot;
        if (bestSlot != -1 && currentSlot != bestSlot) {
            ItemStack currentStack = mc.player.getInventory().getStack(currentSlot);
            if (!currentStack.isEmpty()) {
                boolean currentSuitable = currentStack.isSuitableFor(blockState);
                if (!toolRequired || (currentSuitable == bestSuitable)) {
                    float currentSpeed = getToolSpeed(currentStack, blockState);
                    if (currentSpeed >= bestSpeed) {
                        return currentSlot;
                    }
                }
            }
        }

        return bestSlot;
    }

    private float getToolSpeed(ItemStack stack, BlockState state) {
        float speed = stack.getMiningSpeedMultiplier(state);
        if (speed > 1.0F) {
            int efficiency = InventoryUtils.getEnchantmentLevel(stack, Enchantments.EFFICIENCY);
            if (efficiency > 0) {
                speed += (float) (efficiency * efficiency + 1);
            }
        }
        return speed;
    }

    @Override
    public void onDisable() {
        if (mc.player != null && previousSlot != -1 && switchBack.isState()) {
            HotbarUtil.switchTo(previousSlot);
        }
        reset();
        super.onDisable();
    }

    private void reset() {
        this.previousSlot = -1;
        this.lastMinedPos = null;
        this.ticksSinceMining = 0;
    }
}
