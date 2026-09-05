package snill.client.api.commands.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.command.CommandSource;

import snill.client.Snill;
import snill.client.api.commands.Command;
import snill.client.api.utils.chat.ChatUtils;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;
import static com.mojang.brigadier.arguments.StringArgumentType.word;

public class StaffCommand extends Command {

    public StaffCommand() {
        super("staff");
    }

    
    @Override
    public void execute(LiteralArgumentBuilder<CommandSource> builder) {
        builder
                .then(literal("add")
                        .then(arg("player", word())
                                .suggests((context, builder1) -> {
                                    for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
                                        String name = entry.getProfile().getName();
                                        if (name.toLowerCase().startsWith(builder1.getRemaining().toLowerCase())) {
                                            builder1.suggest(name);
                                        }
                                    }
                                    return builder1.buildFuture();
                                })
                                .executes(context -> {
                                    String player = context.getArgument("player", String.class);
                                    if (!Snill.INSTANCE.staffStorage.isStaff(player)) {
                                        Snill.INSTANCE.staffStorage.add(player);
                                        ChatUtils.sendMessage("Игрок " + player + " добавлен в список стаффов!");
                                    } else {
                                        ChatUtils.sendMessage("Игрок " + player + " уже в списке стаффов!");
                                    }
                                    return SINGLE_SUCCESS;
                                })
                        )
                )
                .then(literal("remove")
                        .then(arg("player", word())
                                .suggests((context, builder1) -> {
                                    Snill.INSTANCE.staffStorage.getStaffs().stream()
                                            .sorted(String::compareTo)
                                            .filter(name -> name.startsWith(builder1.getRemaining()))
                                            .forEach(builder1::suggest);
                                    return builder1.buildFuture();
                                })
                                .executes(context -> {
                                    String player = context.getArgument("player", String.class);
                                    if (Snill.INSTANCE.staffStorage.isStaff(player)) {
                                        Snill.INSTANCE.staffStorage.remove(player);
                                        ChatUtils.sendMessage("Игрок " + player + " удалён из списка стаффов!");
                                    } else {
                                        ChatUtils.sendMessage("Игрок " + player + " не найден в списке стаффов!");
                                    }
                                    return SINGLE_SUCCESS;
                                })
                        )
                )
                .then(literal("list")
                        .executes(context -> {
                            StringBuilder builder1 = new StringBuilder();
                            if (Snill.INSTANCE.staffStorage.getStaffs().isEmpty()) {
                                ChatUtils.sendMessage("Список стаффов пуст!");
                            } else {
                                for (int i = 0; i < Snill.INSTANCE.staffStorage.getStaffs().size(); i++) {
                                    builder1.append(Snill.INSTANCE.staffStorage.getStaffs().get(i));
                                    if (i < Snill.INSTANCE.staffStorage.getStaffs().size() - 1) {
                                        builder1.append(", ");
                                    }
                                }
                                builder1.append(".");
                                ChatUtils.sendMessage("Стаффы: " + builder1);
                            }
                            return SINGLE_SUCCESS;
                        })
                )
                .then(literal("clear")
                        .executes(context -> {
                            if (!Snill.INSTANCE.staffStorage.isEmpty()) {
                                Snill.INSTANCE.staffStorage.clear();
                                ChatUtils.sendMessage("Список стаффов очищен!");
                            } else {
                                ChatUtils.sendMessage("Список стаффов пуст!");
                            }
                            return SINGLE_SUCCESS;
                        })
                );
    }
}