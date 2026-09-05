package snill.client.api.storages.implement;

import com.google.gson.*;
import snill.client.Snill;
import snill.client.api.storages.implement.helpertstorages.enumvar.ModuleClass;
import snill.client.api.utils.cmd.macro.Macro;
import snill.client.api.utils.draggable.Draggable;
import snill.client.api.utils.namespaced.FileUtils;
import snill.client.client.modules.Module;
import snill.client.client.modules.impl.render.Interface;
import snill.client.client.modules.impl.render.base.InterfaceProcessing;
import snill.client.client.modules.impl.render.base.implement.TargetHud;
import snill.client.client.modules.impl.render.base.implement.WaterMark;
import snill.client.client.modules.settings.Setting;
import snill.client.client.modules.settings.implement.*;
import ru.virtuoz.convert.Convert;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

public class ConfigStorage {

    public String currentConfig = "default";
    private final String extension = ".snill";

    public ConfigStorage() {
        loadAll();
        Runtime.getRuntime().addShutdownHook(new Thread(this::saveAll));
    }

    private void loadAll() {
        try {
            loadGlobals();
            loadConfig(currentConfig);
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
    }

    @Convert(Convert.ConvertType.ULTRA)
    public synchronized void saveAll() {
        try {
            saveConfig(currentConfig);
            saveGlobals();
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
    }

    @Convert(Convert.ConvertType.ULTRA)
    public void saveConfig(String config) throws Exception {
        ensureConfigName(config);
        if (!Snill.INSTANCE.configsDir.exists() && !Snill.INSTANCE.configsDir.mkdirs()) {
            throw new IOException("Unable to create configs directory");
        }
        File file = new File(Snill.INSTANCE.configsDir, config + extension);

        JsonObject object = new JsonObject();
        object.add("config", new JsonPrimitive(config));
        object.add("theme", new JsonPrimitive(Snill.INSTANCE.themeStorage.getThemes().name()));
        object.add("language", new JsonPrimitive(Snill.INSTANCE.localizationStorage.getLanguage().name()));
        object.add("modules", serializeModules());
        object.add("draggables", serializeDraggables());
        object.add("hud", serializeHudState());

        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file, false), StandardCharsets.UTF_8)) {
            writer.write(new GsonBuilder().setPrettyPrinting().create().toJson(object));
        }

        this.currentConfig = config;
        saveGlobals();
    }

    public void loadConfig(String config) throws Exception {
        ensureConfigName(config);
        File file = findConfigFile(config);
        if (file == null) return;
        JsonObject object;
        try (InputStream stream = Files.newInputStream(file.toPath());
             Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            object = JsonParser.parseReader(reader).getAsJsonObject();
        }

        if (object.has("theme")) {
            String themeName = object.get("theme").getAsString();
            for (ThemeStorage.Themes theme : ThemeStorage.Themes.values()) {
                if (theme.name().equals(themeName)) {
                    Snill.INSTANCE.themeStorage.setThemes(theme);
                    break;
                }
            }
        }

        if (object.has("language")) {
            try {
                Snill.INSTANCE.localizationStorage.setLanguage(LocalizationStorage.Language.valueOf(object.get("language").getAsString()));
            } catch (Exception ignored) {
            }
        }

        if (object.has("modules")) {
            deserializeModules(object.get("modules").getAsJsonObject());
        }

        if (object.has("draggables")) {
            deserializeDraggables(object.get("draggables").getAsJsonObject());
        }

        if (object.has("hud")) {
            deserializeHudState(object.get("hud").getAsJsonObject());
        }

        this.currentConfig = config;
        saveGlobals();
    }

    public File[] listConfigFiles() {
        File[] files = Snill.INSTANCE.configsDir.listFiles((dir, name) -> {
            String lower = name.toLowerCase(java.util.Locale.ROOT);
            return lower.endsWith(".snill") || lower.endsWith(".polar") || lower.endsWith(".cfg");
        });
        return files == null ? new File[0] : files;
    }

    private File findConfigFile(String config) {
        File snillFile = new File(Snill.INSTANCE.configsDir, config + extension);
        if (snillFile.isFile()) return snillFile;
        File polarFile = new File(Snill.INSTANCE.configsDir, config + ".polar");
        if (polarFile.isFile()) return polarFile;
        File cfgFile = new File(Snill.INSTANCE.configsDir, config + ".cfg");
        return cfgFile.isFile() ? cfgFile : null;
    }

    private static void ensureConfigName(String config) throws IOException {
        if (config == null || config.isBlank() || config.contains("..") || config.indexOf('/') >= 0 || config.indexOf('\\') >= 0) {
            throw new IOException("Invalid config name");
        }
    }

    @Convert(Convert.ConvertType.ULTRA)
    public void saveGlobals() throws Exception {
        File file = new File(Snill.INSTANCE.globalsDir, "globals" + extension);
        JsonObject object = new JsonObject();
        object.add("config", new JsonPrimitive(currentConfig));
        object.add("theme", new JsonPrimitive(Snill.INSTANCE.themeStorage.getThemes().name()));
        object.add("language", new JsonPrimitive(Snill.INSTANCE.localizationStorage.getLanguage().name()));

        JsonArray friendsArray = new JsonArray();
        Snill.INSTANCE.friendStorage.getFriends().forEach(friendsArray::add);
        object.add("friends", friendsArray);

        JsonArray staffsArray = new JsonArray();
        Snill.INSTANCE.staffStorage.getStaffs().forEach(staffsArray::add);
        object.add("staffs", staffsArray);

        JsonArray macrosArray = new JsonArray();
        Snill.INSTANCE.macroStorage.getMacros().forEach(macro -> {
            JsonObject macroObject = new JsonObject();
            macroObject.addProperty("name", macro.getName());
            macroObject.addProperty("command", macro.getCommand());
            macroObject.addProperty("key", macro.getBind().getKey());
            macrosArray.add(macroObject);
        });
        object.add("macros", macrosArray);

        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file, false), StandardCharsets.UTF_8)) {
            writer.write(new GsonBuilder().setPrettyPrinting().create().toJson(object));
        }
    }

    public void loadGlobals() throws Exception {
        File snillGlobals = new File(Snill.INSTANCE.globalsDir, "globals" + extension);
        File polarGlobals = new File(Snill.INSTANCE.globalsDir, "globals.polar");
        File fileToLoad = snillGlobals.exists() ? snillGlobals : (polarGlobals.exists() ? polarGlobals : null);
        if (fileToLoad == null) return;

        JsonObject object;
        try (InputStream stream = Files.newInputStream(fileToLoad.toPath());
             Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            object = JsonParser.parseReader(reader).getAsJsonObject();
        }

        if (object.has("config")) currentConfig = object.get("config").getAsString();

        if (object.has("theme")) {
            String themeName = object.get("theme").getAsString();
            for (ThemeStorage.Themes theme : ThemeStorage.Themes.values()) {
                if (theme.name().equals(themeName)) {
                    Snill.INSTANCE.themeStorage.setThemes(theme);
                    break;
                }
            }
        }

        if (object.has("language")) {
            try {
                Snill.INSTANCE.localizationStorage.setLanguage(LocalizationStorage.Language.valueOf(object.get("language").getAsString()));
            } catch (Exception ignored) {
            }
        }

        if (object.has("friends")) {
            for (JsonElement element : object.get("friends").getAsJsonArray()) {
                if (Snill.INSTANCE.friendStorage.isFriend(element.getAsString())) continue;
                Snill.INSTANCE.friendStorage.add(element.getAsString());
            }
        }

        if (object.has("staffs")) {
            for (JsonElement element : object.get("staffs").getAsJsonArray()) {
                if (Snill.INSTANCE.staffStorage.isStaff(element.getAsString())) continue;
                Snill.INSTANCE.staffStorage.add(element.getAsString());
            }
        }

        if (object.has("macros")) {
            for (JsonElement element : object.get("macros").getAsJsonArray()) {
                try {
                    String name;
                    String command;
                    int key;

                    if (element.isJsonObject()) {
                        JsonObject macroObject = element.getAsJsonObject();
                        name = macroObject.has("name") ? macroObject.get("name").getAsString() : "";
                        command = macroObject.has("command") ? macroObject.get("command").getAsString() : "";
                        key = macroObject.has("key") ? macroObject.get("key").getAsInt() : -1;
                    } else {
                        String[] split = element.getAsString().split(":", 3);
                        if (split.length < 3) continue;
                        name = split[0];
                        command = split[1];
                        key = Integer.parseInt(split[2]);
                    }

                    if (name.isBlank() || Snill.INSTANCE.macroStorage.getMacro(name) != null) {
                        continue;
                    }

                    Snill.INSTANCE.macroStorage.add(new Macro(name, command, new BindSetting("bind", key)));
                } catch (Exception ignored) {
                }
            }
        }
    }

    private JsonObject serializeModules() {
        JsonObject modules = new JsonObject();
        for (Module module : ModuleClass.INSTANCE.getObject()) {
            try {
                JsonObject object = new JsonObject();
                object.add("toggled", new JsonPrimitive(module.isEnable()));
                object.add("bind", new JsonPrimitive(module.getKey()));

                JsonObject settings = new JsonObject();
                for (Setting s : module.getSettings()) {
                    try {
                        if (s instanceof BooleanSetting bool) {
                            settings.add(s.name(), new JsonPrimitive(bool.isState()));
                        } else if (s instanceof FloatSetting num) {
                            settings.add(s.name(), new JsonPrimitive(num.getValue().floatValue()));
                        } else if (s instanceof ModeSetting mode) {
                            settings.add(s.name(), new JsonPrimitive(mode.getCurrent()));
                        } else if (s instanceof TextSetting text) {
                            settings.add(s.name(), new JsonPrimitive(text.get()));
                        } else if (s instanceof BindSetting bind) {
                            settings.add(s.name(), new JsonPrimitive(bind.getKey()));
                        } else if (s instanceof ListSetting list) {
                            JsonObject listObj = new JsonObject();
                            for (BooleanSetting setting : list.getSettings()) {
                                listObj.add(setting.name(), new JsonPrimitive(setting.isState()));
                            }
                            settings.add(list.name(), listObj);
                        }
                    } catch (Exception ignored) {
                    }
                }

                object.add("settings", settings);
                object.add("settingsV2", serializeSettingsV2(module));
                modules.add(module.getName(), object);
            } catch (Exception ignored) {
            }
        }
        return modules;
    }

    private JsonArray serializeSettingsV2(Module module) {
        JsonArray settings = new JsonArray();
        for (int index = 0; index < module.getSettings().size(); index++) {
            Setting setting = module.getSettings().get(index);
            JsonObject entry = new JsonObject();
            entry.addProperty("index", index);
            if (setting instanceof BooleanSetting value) {
                entry.addProperty("type", "boolean");
                entry.addProperty("value", value.isState());
            } else if (setting instanceof FloatSetting value) {
                entry.addProperty("type", "float");
                entry.addProperty("value", value.get());
            } else if (setting instanceof ModeSetting value) {
                entry.addProperty("type", "mode");
                entry.addProperty("value", value.getCurrent());
            } else if (setting instanceof TextSetting value) {
                entry.addProperty("type", "text");
                entry.addProperty("value", value.get());
            } else if (setting instanceof BindSetting value) {
                entry.addProperty("type", "bind");
                entry.addProperty("value", value.getKey());
            } else if (setting instanceof ListSetting value) {
                entry.addProperty("type", "list");
                JsonObject values = new JsonObject();
                for (BooleanSetting option : value.getSettings()) values.addProperty(option.name(), option.isState());
                entry.add("value", values);
            } else {
                continue;
            }
            settings.add(entry);
        }
        return settings;
    }

    private void deserializeSettingsV2(Module module, JsonArray entries) {
        for (JsonElement element : entries) {
            try {
                JsonObject entry = element.getAsJsonObject();
                int index = entry.get("index").getAsInt();
                if (index < 0 || index >= module.getSettings().size() || !entry.has("value")) continue;
                Setting setting = module.getSettings().get(index);
                JsonElement value = entry.get("value");
                if (setting instanceof BooleanSetting target) target.setState(value.getAsBoolean());
                else if (setting instanceof FloatSetting target) target.setValue(value.getAsFloat());
                else if (setting instanceof ModeSetting target) target.set(value.getAsString());
                else if (setting instanceof TextSetting target) target.setText(value.getAsString());
                else if (setting instanceof BindSetting target) target.setKey(value.getAsInt());
                else if (setting instanceof ListSetting target) {
                    JsonObject values = value.getAsJsonObject();
                    for (BooleanSetting option : target.getSettings()) {
                        if (values.has(option.name())) option.setState(values.get(option.name()).getAsBoolean());
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }

    private void deserializeModules(JsonObject modules) {
        Map<Module, Boolean> targetStates = new LinkedHashMap<>();

        for (Module module : ModuleClass.INSTANCE.getObject()) {
            try {
                JsonObject object = modules.has(module.getName())
                        ? modules.get(module.getName()).getAsJsonObject()
                        : null;

                boolean toggled = object != null
                        && object.has("toggled")
                        && object.get("toggled").getAsBoolean();

                targetStates.put(module, toggled);

                if (module.isEnable()) {
                    module.setEnabled(false);
                }
            } catch (Exception ignored) {
                targetStates.put(module, false);
            }
        }

        for (Module module : ModuleClass.INSTANCE.getObject()) {
            try {
                if (!modules.has(module.getName())) continue;

                JsonObject object = modules.get(module.getName()).getAsJsonObject();

                if (object.has("bind")) {
                    module.setKey(object.get("bind").getAsInt());
                }

                if (object.has("settings")) {
                    if (object.has("settingsV2") && object.get("settingsV2").isJsonArray()) {
                        deserializeSettingsV2(module, object.getAsJsonArray("settingsV2"));
                        continue;
                    }
                    JsonObject settings = object.get("settings").getAsJsonObject();

                    for (Setting s : module.getSettings()) {
                        try {
                            if (!settings.has(s.name())) continue;

                            JsonElement element = settings.get(s.name());

                            if (s instanceof BooleanSetting bool) {
                                bool.setState(element.getAsBoolean());
                            } else if (s instanceof FloatSetting num) {
                                num.setValue(element.getAsFloat());
                            } else if (s instanceof ModeSetting mode) {
                                mode.set(element.getAsString());
                            } else if (s instanceof TextSetting text) {
                                text.setText(element.getAsString());
                            } else if (s instanceof BindSetting bind) {
                                bind.setKey(element.getAsInt());
                            } else if (s instanceof ListSetting list) {
                                JsonObject listObj = element.getAsJsonObject();
                                for (BooleanSetting setting : list.getSettings()) {
                                    if (listObj.has(setting.name())) {
                                        setting.setState(listObj.get(setting.name()).getAsBoolean());
                                    }
                                }
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }

            } catch (Exception ignored) {
            }
        }

        for (Map.Entry<Module, Boolean> entry : targetStates.entrySet()) {
            try {
                entry.getKey().setEnabled(entry.getValue());
            } catch (Exception ignored) {
            }
        }
    }

    private JsonObject serializeHudState() {
        JsonObject hud = new JsonObject();
        Interface interfaceModule = ModuleClass.interfaceModule;
        if (interfaceModule == null) return hud;

        for (Map.Entry<String, InterfaceProcessing> entry : interfaceModule.getConfigurableHudElements().entrySet()) {
            InterfaceProcessing element = entry.getValue();
            if (element == null) continue;

            JsonObject object = new JsonObject();
            object.add("unusualRectType", new JsonPrimitive(element.isUnusualRectType()));

            if (element instanceof WaterMark waterMark) {
                object.add("showFps", new JsonPrimitive(waterMark.isShowFps()));
                object.add("showMs", new JsonPrimitive(waterMark.isShowMs()));
            }

            hud.add(entry.getKey(), object);
        }

        return hud;
    }

    private void deserializeHudState(JsonObject hud) {
        Interface interfaceModule = ModuleClass.interfaceModule;
        if (interfaceModule == null) return;

        for (Map.Entry<String, InterfaceProcessing> entry : interfaceModule.getConfigurableHudElements().entrySet()) {
            if (!hud.has(entry.getKey())) continue;

            try {
                JsonObject object = hud.get(entry.getKey()).getAsJsonObject();
                InterfaceProcessing element = entry.getValue();

                if (object.has("unusualRectType")) {
                    element.setUnusualRectType(object.get("unusualRectType").getAsBoolean());
                }

                if (element instanceof WaterMark waterMark) {
                    if (object.has("showFps")) waterMark.setShowFps(object.get("showFps").getAsBoolean());
                    if (object.has("showMs")) waterMark.setShowMs(object.get("showMs").getAsBoolean());
                }
            } catch (Exception ignored) {
            }
        }
    }

    private JsonObject serializeDraggables() {
        JsonObject draggables = new JsonObject();
        for (Draggable drag : DragStorage.draggables.values()) {
            JsonObject object = new JsonObject();
            object.add("x", new JsonPrimitive(drag.getX()));
            object.add("y", new JsonPrimitive(drag.getY()));
            draggables.add(drag.getName(), object);
        }
        return draggables;
    }

    private void deserializeDraggables(JsonObject draggables) {
        for (String name : draggables.keySet()) {
            Draggable drag = DragStorage.draggables.get(name);
            if (drag == null) continue;

            JsonObject object = draggables.get(name).getAsJsonObject();
            if (object.has("x")) drag.setX(object.get("x").getAsFloat());
            if (object.has("y")) drag.setY(object.get("y").getAsFloat());
        }
    }
}
