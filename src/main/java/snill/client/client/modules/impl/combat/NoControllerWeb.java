package snill.client.client.modules.impl.combat;
import net.minecraft.block.Blocks;
import snill.client.api.events.EventLink;
import snill.client.api.events.implement.EventBlockCollide;
import snill.client.client.modules.Module;

public class NoControllerWeb extends Module {

    public static NoControllerWeb INSTANCE = new NoControllerWeb();

    public NoControllerWeb() {
        super("NoControllerWeb", "Позволяет ломать и бить сквозь паутину", ModuleCategory.COMBAT);
    }
    @EventLink
    public void onBlockCollide(final EventBlockCollide e) {
        if (mc.world == null || e.getPos() == null) return;
        if (mc.world.getBlockState(e.getPos()).getBlock() == Blocks.COBWEB) {
            e.setCancelled(true);
        }
    }
}
