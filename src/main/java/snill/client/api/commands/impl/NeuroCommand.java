package snill.client.api.commands.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;
import snill.client.api.commands.Command;
import snill.client.api.storages.implement.RotationStorage;
import snill.client.api.storages.implement.helpertstorages.enumvar.ModuleClass;
import snill.client.api.utils.chat.ChatUtils;
import snill.client.client.modules.impl.combat.Aura;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static com.mojang.brigadier.arguments.StringArgumentType.word;

public class NeuroCommand extends Command {
    public NeuroCommand() { super("neuro"); }

    @Override public void execute(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> { status(); return SINGLE_SUCCESS; })
                .then(literal("recode").executes(context -> {
                    Aura aura = aura();
                    aura.getDataSystem().startRecording();
                    aura.rotationType.set("NoRotate");
                    if (RotationStorage.instance != null) RotationStorage.instance.stopRotation();
                    ChatUtils.sendMessage("Neuro: запись начата в режиме NoRotate. Каждый удар — один сэмпл.");
                    return SINGLE_SUCCESS;
                }))
                .then(literal("stop").then(arg("name", greedyString()).executes(context -> { stop(context.getArgument("name", String.class)); return SINGLE_SUCCESS; })))
                .then(literal("load").then(arg("name", word()).suggests((context, suggestions) -> { aura().getDataSystem().getPatternNames().forEach(suggestions::suggest); return suggestions.buildFuture(); }).executes(context -> { load(context.getArgument("name", String.class)); return SINGLE_SUCCESS; })))
                .then(literal("list").executes(context -> { ChatUtils.sendMessage("Neuro profiles: " + String.join(", ", aura().getDataSystem().getPatternNames())); return SINGLE_SUCCESS; }));
    }

    private void stop(String name) {
        Aura aura = aura();
        if (!aura.getDataSystem().isRecording()) { ChatUtils.sendMessage("Neuro: запись не запущена."); return; }
        if (!aura.getDataSystem().savePatterns(name)) { ChatUtils.sendMessage("Neuro: не удалось сохранить — укажите имя и сделайте хотя бы один удар."); return; }
        aura.getDataSystem().stopRecording();
        ChatUtils.sendMessage("Neuro: сохранено «" + name + "», сэмплов: " + aura.getDataSystem().getPatternCount());
    }

    private void load(String name) {
        Aura aura = aura();
        if (!aura.getDataSystem().loadPatterns(name)) { ChatUtils.sendMessage("Neuro: профиль не найден или повреждён."); return; }
        aura.getDataSystem().setRecording(false);
        aura.getDataSystem().setUsingNeuro(true);
        aura.rotationType.set("Neuro");
        ChatUtils.sendMessage("Neuro: загружен «" + name + "», сэмплов: " + aura.getDataSystem().getPatternCount());
    }

    private void status() { ChatUtils.sendMessage(aura().getDataSystem().getStatusString()); }
    private Aura aura() { return ModuleClass.INSTANCE.aura; }
}
