package snill.client.api.storages;


import snill.client.Snill;
import snill.client.api.QClient;
import snill.client.api.events.EventInvoker;
import snill.client.api.storages.implement.*;
import snill.client.api.storages.implement.helpertstorages.enumvar.ModuleClass;
import snill.client.api.utils.tps.TPSCalc;
import snill.client.mods.maseffects.MaseffectsParticleTypes;
import snill.client.mods.particular.ParticularParticleTypes;
import snill.client.client.modules.impl.render.TotemAngel;
import ru.virtuoz.convert.Convert;

public class InitializeStorage implements QClient {

    public void onInitialize() {
        EventInvoker.register(this);
        this.initStorages();
    }

    @Convert(Convert.ConvertType.ULTRA)
    public void initStorages() {
        MaseffectsParticleTypes.register();
        ParticularParticleTypes.register();
        Snill.INSTANCE.moduleStorage = new ModuleStorage();
        Snill.INSTANCE.themeStorage = new ThemeStorage();
        Snill.INSTANCE.tpsCalc = new TPSCalc();
        EventInvoker.register(Snill.INSTANCE.tpsCalc);
        Snill.INSTANCE.localizationStorage = new LocalizationStorage();
        Snill.INSTANCE.freeLookStorage = new FreeLookStorage();
        Snill.INSTANCE.rotationStorage = new RotationStorage();
        Snill.INSTANCE.serverStorage = new ServerStorage();
        Snill.INSTANCE.serverStorage.ServerManager();
        Snill.INSTANCE.friendStorage = new FriendStorage();
        Snill.INSTANCE.macroStorage = new MacroStorage();
        Snill.INSTANCE.staffStorage = new StaffStorage();
        Snill.INSTANCE.waypointStorage = new WaypointStorage();
        Snill.INSTANCE.commandStorage = new CommandStorage();
        Snill.INSTANCE.altStorage = new snill.client.api.storages.implement.alt.AltStorage();
        Snill.INSTANCE.configStorage = new ConfigStorage();
    }
}
