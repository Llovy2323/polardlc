package snill.client.api.commands.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;
import org.lwjgl.glfw.GLFW;

import snill.client.Snill;
import snill.client.api.commands.Command;
import snill.client.api.storages.implement.helpertstorages.enumvar.ModuleClass;
import snill.client.api.utils.chat.ChatUtils;
import snill.client.client.modules.Module;

import java.lang.reflect.Field;
import java.util.Optional;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;
import static com.mojang.brigadier.arguments.StringArgumentType.word;

public class BindCommand extends Command {
    public BindCommand() {
        super("bind");
    }

    @Override
    public void execute(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(literal("add")
                .then(arg("module", word())
                        .suggests((context, suggestionsBuilder) -> {
                            String remaining = suggestionsBuilder.getRemaining().toLowerCase();
                            ModuleClass.INSTANCE.getObject().stream()
                                    .map(Module::getName)
                                    .filter(name -> name.toLowerCase().startsWith(remaining))
                                    .forEach(suggestionsBuilder::suggest);
                            return suggestionsBuilder.buildFuture();
                        })
                        .then(arg("key", word())
                                .suggests((context, suggestionsBuilder) -> {
                                    String remaining = suggestionsBuilder.getRemaining().toUpperCase();
                                    for (Field field : GLFW.class.getDeclaredFields()) {
                                        String fieldName = field.getName();
                                        if (!fieldName.startsWith("GLFW_KEY_")) continue;
                                        String keyName = fieldName.replace("GLFW_KEY_", "");
                                        if (keyName.startsWith(remaining)) {
                                            suggestionsBuilder.suggest(keyName);
                                        }
                                    }
                                    if ("NONE".startsWith(remaining)) {
                                        suggestionsBuilder.suggest("NONE");
                                    }
                                    return suggestionsBuilder.buildFuture();
                                })
                                .executes(ctx -> {
                                    String moduleName = ctx.getArgument("module", String.class);
                                    Optional<Module> optionalModule = findModuleByName(moduleName);
                                    if (optionalModule.isEmpty()) {
                                        ChatUtils.sendMessage("Модуль " + moduleName + " не найден");
                                        return SINGLE_SUCCESS;
                                    }
                                    Module module = optionalModule.get();
                                    String keyName = ctx.getArgument("key", String.class).toUpperCase();
                                    int keyCode = getKeyCode(keyName);
                                    if (keyCode == -1) {
                                        ChatUtils.sendMessage("Клавиша " + keyName + " не найдена");
                                    } else {
                                        module.setKey(keyCode);
                                        ChatUtils.sendMessage("Модуль " + module.getName() + " привязан к клавише " + keyName);
                                        saveConfig();
                                    }
                                    return SINGLE_SUCCESS;
                                }))));

        builder.then(literal("remove").then(arg("module", word()).executes(ctx -> {
            String moduleName = ctx.getArgument("module", String.class);
            Optional<Module> optionalModule = findModuleByName(moduleName);
            if (optionalModule.isEmpty()) {
                ChatUtils.sendMessage("Модуль " + moduleName + " не найден");
                return SINGLE_SUCCESS;
            }
            Module module = optionalModule.get();
            module.setKey(-1);
            ChatUtils.sendMessage("Привязка клавиши для модуля " + module.getName() + " удалена");
            return SINGLE_SUCCESS;
        })));

        builder.then(literal("clear").executes(ctx -> {
            ModuleClass.INSTANCE.getObject().forEach(module -> module.setKey(-1));
            ChatUtils.sendMessage("Все привязки клавиш удалены");
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("list").executes(ctx -> {
            var boundModules = ModuleClass.INSTANCE.getObject().stream()
                    .filter(m -> m.getKey() != -1)
                    .toList();

            if (boundModules.isEmpty()) {
                ChatUtils.sendMessage("Нет модулей с привязками");
                return SINGLE_SUCCESS;
            }

            for (Module module : boundModules) {
                String keyName = getKeyName(module.getKey());
                ChatUtils.sendMessage("Модуль: " + module.getName() + " -> " + keyName);
            }
            return SINGLE_SUCCESS;
        }));
    }

    private Optional<Module> findModuleByName(String moduleName) {
        return ModuleClass.INSTANCE.getObject().stream()
                .filter(module -> module.getName().equalsIgnoreCase(moduleName))
                .findFirst();
    }

    private int getKeyCode(String keyName) {
        if ("NONE".equalsIgnoreCase(keyName)) return -1;
        try {
            return GLFW.class.getField("GLFW_KEY_" + keyName).getInt(null);
        } catch (NoSuchFieldException | IllegalAccessException ignored) {
            return -1;
        }
    }

    private String getKeyName(int keyCode) {
        for (Field field : GLFW.class.getDeclaredFields()) {
            String fieldName = field.getName();
            if (!fieldName.startsWith("GLFW_KEY_")) continue;
            try {
                if (field.getInt(null) == keyCode) {
                    return fieldName.replace("GLFW_KEY_", "");
                }
            } catch (IllegalAccessException ignored) {
            }
        }
        return "UNKNOWN";
    }

    private void saveConfig() {
        try {
            Snill.INSTANCE.configStorage.saveConfig(Snill.INSTANCE.configStorage.currentConfig);
        } catch (Exception ignored) {
        }
    }
}