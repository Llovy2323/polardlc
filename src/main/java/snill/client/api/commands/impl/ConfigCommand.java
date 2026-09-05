package snill.client.api.commands.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;

import snill.client.Snill;
import snill.client.api.commands.Command;
import snill.client.api.utils.chat.ChatUtils;
import snill.client.api.storages.implement.helpertstorages.enumvar.ModuleClass;
import snill.client.client.modules.Module;

import java.io.File;
import java.util.Arrays;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;
import static com.mojang.brigadier.arguments.StringArgumentType.word;

public class ConfigCommand extends Command {

    public ConfigCommand() {
        this("config");
    }

    public ConfigCommand(String command) {
        super(command);
    }

    @Override
    public void execute(LiteralArgumentBuilder<CommandSource> builder) {
        builder
                .then(literal("reset")
                        .executes(context -> {
                            int disabled = 0;
                            for (Module module : ModuleClass.INSTANCE.getObject()) {
                                if (module.isEnable()) {
                                    module.setEnabled(false);
                                    disabled++;
                                }
                            }
                            ChatUtils.sendMessage("Отключено модулей: " + disabled);
                            return SINGLE_SUCCESS;
                        })
                )
                .then(literal("save")
                        .then(arg("config", word())
                                .suggests((context, builder1) -> {
                                    if (Snill.INSTANCE.configsDir.exists() && Snill.INSTANCE.configsDir.isDirectory()) {
                                        Arrays.stream(Snill.INSTANCE.configStorage.listConfigFiles())
                                                .map(File::getName)
                                                .map(ConfigCommand::withoutExtension)
                                                .forEach(builder1::suggest);
                                    }
                                    return builder1.buildFuture();
                                })
                                .executes(context -> {
                                    String config = context.getArgument("config", String.class);
                                    try {
                                        Snill.INSTANCE.configStorage.saveConfig(config);
                                        ChatUtils.sendMessage("Конфиг " + config + " успешно сохранён!");
                                    } catch (Exception e) {
                                        ChatUtils.sendMessage("Ошибка при сохранении конфига " + config + "!");
                                        e.printStackTrace();
                                    }
                                    return SINGLE_SUCCESS;
                                })
                        )
                )
                .then(literal("load")
                        .then(arg("config", word())
                                .suggests((context, builder1) -> {
                                    if (Snill.INSTANCE.configsDir.exists() && Snill.INSTANCE.configsDir.isDirectory()) {
                                        Arrays.stream(Snill.INSTANCE.configStorage.listConfigFiles())
                                                .map(File::getName)
                                                .map(ConfigCommand::withoutExtension)
                                                .forEach(builder1::suggest);
                                    }
                                    return builder1.buildFuture();
                                })
                                .executes(context -> {
                                    String config = context.getArgument("config", String.class);
                                    try {
                                        Snill.INSTANCE.configStorage.loadConfig(config);
                                        ChatUtils.sendMessage("Конфиг " + config + " успешно загружен!");
                                    } catch (Exception e) {
                                        ChatUtils.sendMessage("Ошибка при загрузке конфига " + config + "!");
                                        e.printStackTrace();
                                    }
                                    return SINGLE_SUCCESS;
                                })
                        )
                )
                .then(literal("list")
                        .executes(context -> {
                            File[] files = Snill.INSTANCE.configStorage.listConfigFiles();
                            if (files.length == 0) {
                                ChatUtils.sendMessage("Список конфигов пуст!");
                            } else {
                                StringBuilder builder1 = new StringBuilder();
                                for (int i = 0; i < files.length; i++) {
                                    String fileName = withoutExtension(files[i].getName());
                                    builder1.append(fileName);
                                    if (i < files.length - 1) builder1.append(", ");
                                }
                                ChatUtils.sendMessage("Конфиги: " + builder1);
                            }
                            return SINGLE_SUCCESS;
                        })
                )
                .then(literal("dir")
                        .executes(context -> {
                            try {
                                File configsDir = new File(Snill.INSTANCE.globalsDir, "configs");
                                if (!configsDir.exists()) {
                                    configsDir.mkdirs();
                                }
                                new ProcessBuilder("explorer.exe", configsDir.getAbsolutePath()).start();
                                ChatUtils.sendMessage("Папка с конфигами открыта!");
                            } catch (Exception e) {
                                ChatUtils.sendMessage("Ошибка при открытии папки с конфигами!");
                                e.printStackTrace();
                            }
                            return SINGLE_SUCCESS;
                        })
                );
    }

    private static String withoutExtension(String name) {
        return name.replaceFirst("(?i)\\.(polar|cfg)$", "");
    }
}
