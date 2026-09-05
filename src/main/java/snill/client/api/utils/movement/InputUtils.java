package snill.client.api.utils.movement;

import lombok.experimental.UtilityClass;
import net.minecraft.client.option.KeyBinding;
import snill.client.api.QClient;

@UtilityClass
public class InputUtils implements QClient {

    private boolean movementLocked;

    public void lockMovement() {
        movementLocked = true;
        unpressMovementKeys();
    }

    public void unlockMovement() {
        movementLocked = false;
    }

    public boolean isMovementLocked() {
        return movementLocked;
    }

    private void unpressMovementKeys() {
        if (mc == null || mc.options == null) return;
        mc.options.forwardKey.setPressed(false);
        mc.options.backKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);
        mc.options.sneakKey.setPressed(false);
        mc.options.sprintKey.setPressed(false);
    }

    public void syncMovementKeys(KeyBinding[] bindings) {
        if (mc == null || mc.getWindow() == null || mc.options == null || movementLocked) return;
        for (KeyBinding binding : bindings) {
            binding.setPressed(net.minecraft.client.util.InputUtil.isKeyPressed(
                    mc.getWindow().getHandle(),
                    binding.getDefaultKey().getCode()
            ));
        }
    }
}
