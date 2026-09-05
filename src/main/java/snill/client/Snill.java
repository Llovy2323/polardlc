package snill.client;
import lombok.Getter;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import org.lwjgl.glfw.GLFW;
import snill.client.api.QClient;
import snill.client.api.storages.InitializeStorage;
import snill.client.api.storages.implement.*;
import snill.client.api.events.EventInvoker;
import snill.client.api.utils.client.UserInfo;
import snill.client.api.utils.draggable.Draggable;
import snill.client.api.utils.rpc.DiscordManager;
import snill.client.api.utils.tps.TPSCalc;
import snill.client.client.modules.Module;
import ru.virtuoz.convert.Convert;

import java.io.File;

public class Snill implements ModInitializer, QClient {

    public static Snill INSTANCE;

    public Snill() {
        INSTANCE = this;
    }


    public boolean isServer;
    private static double prevTime = 0.0;
    public static double deltaTime = 0.0;

    public InitializeStorage initializer;
    public ModuleStorage moduleStorage;
    public ThemeStorage themeStorage;
    public TPSCalc tpsCalc;
    public ServerStorage serverStorage;
    public RotationStorage rotationStorage;
    public FreeLookStorage freeLookStorage;
    public CommandStorage commandStorage;
    public LocalizationStorage localizationStorage;
    public ConfigStorage configStorage;
    public FriendStorage friendStorage;
    public MacroStorage macroStorage;
    public StaffStorage staffStorage;
    public WaypointStorage waypointStorage;
    public snill.client.api.storages.implement.alt.AltStorage altStorage;
    public DiscordManager discordManager;
    @Getter public UserInfo userInfo = UserInfo.empty();

    public File globalsDir;
    public File configsDir;
    public File abItemsDir;
    @Override
    @Convert(Convert.ConvertType.ULTRA)
    public void onInitialize() {
        this.initStorage();
        WorldRenderEvents.START.register(client -> {
            double currentTime = GLFW.glfwGetTime();
            deltaTime = currentTime - prevTime;
            prevTime = currentTime;
            deltaTime = mc.isPaused() ? 0.0 : Math.min(0.05, deltaTime);
        });
    }
    @Convert(Convert.ConvertType.ULTRA)
    private void initStorage() {
        this.globalsDir = new File("C:\\snill", "snill");
        this.configsDir = new File(globalsDir, "configs");
        this.abItemsDir = new File(globalsDir, "abitems");

        EventInvoker.register(this);
        createDirs(globalsDir, configsDir, abItemsDir);
        this.initializer = new InitializeStorage();
        this.initializer.onInitialize();
        this.discordManager = new DiscordManager().start();
    }

    private void createDirs(File... file) {
        for (File f : file) f.mkdirs();
    }

    public void closeMinecraft() {
        try {
            if (configStorage != null) {
                configStorage.saveAll();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (discordManager != null) {
            discordManager.stopRPC();
        }
    }

    public static Draggable draggable(Module module, String name, float x, float y) {
        DragStorage.draggables.put(name, new Draggable(module, name, x, y));
        return DragStorage.draggables.get(name);
    }

    public void setUserInfo(UserInfo userInfo) {
        this.userInfo = userInfo == null ? UserInfo.empty() : userInfo;
    }
}
