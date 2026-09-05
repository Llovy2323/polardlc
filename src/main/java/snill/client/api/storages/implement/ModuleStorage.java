package snill.client.api.storages.implement;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.Getter;
import lombok.Setter;
import snill.client.api.QClient;
import snill.client.api.storages.implement.helpertstorages.enumvar.ModuleClass;
import snill.client.client.modules.Module;
import snill.client.client.modules.impl.combat.*;
import snill.client.client.modules.impl.misc.*;
import snill.client.client.modules.impl.movement.*;
import snill.client.client.modules.impl.player.*;
import snill.client.client.modules.impl.render.*;
import java.util.Arrays;

@Getter
@Setter
public class ModuleStorage implements QClient {

    public ModuleStorage() {
        this.initModules();
    }

    private void initModules() {
        ModuleClass.INSTANCE.initialize();
    }
}
