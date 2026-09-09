package snill.client.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.scoreboard.number.NumberFormat;
import net.minecraft.scoreboard.number.StyledNumberFormat;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import snill.client.api.QClient;
import snill.client.api.events.EventInvoker;
import snill.client.api.events.implement.EventRender;
import snill.client.api.storages.implement.helpertstorages.enumvar.ModuleClass;
import snill.client.api.utils.SidebarEntry;
import snill.client.client.modules.impl.misc.NameProtect;
import snill.client.client.modules.impl.render.CustomCrosshair;
import snill.client.client.modules.impl.render.SmoothSwapping;

import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

@Mixin(InGameHud.class)
public class InGameGuiMixin implements QClient {

    private boolean snill$smoothHotbarPushed;

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void snill$cancelVanillaCrosshair(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (CustomCrosshair.INSTANCE != null && CustomCrosshair.INSTANCE.isEnable()) {
            ci.cancel();
        }
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void render(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (EventInvoker.hasListeners(EventRender.Default.class)) {
            new EventRender.Default(context, tickCounter.getTickDelta(true)).call();
        }
    }

    private static final int DOMAIN_COLOR = 0xED6521;

    @Shadow
    @Final
    private MinecraftClient client;

    @Shadow
    private PlayerEntity getCameraPlayer() {
        return null;
    }

    @Shadow
    @Final
    private static Identifier HOTBAR_SELECTION_TEXTURE;

    @Inject(method = "renderHotbar", at = @At("HEAD"))
    private void snill$renderHotbarHead(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        PlayerEntity player = getCameraPlayer();
        if (player != null) {
            SmoothSwapping.INSTANCE.beginHotbarFrame(context.getScaledWindowWidth(), context.getScaledWindowHeight(), player.getInventory());
        }
    }

    @Inject(method = "renderHotbarItem", at = @At("HEAD"))
    private void snill$renderHotbarItemHead(DrawContext context, int x, int y, RenderTickCounter tickCounter, PlayerEntity player, ItemStack stack, int seed, CallbackInfo ci) {
        int slot = seed - 1;
        snill$smoothHotbarPushed = slot >= 0 && slot < 9
                && SmoothSwapping.INSTANCE.applyHotbarTransform(context.getMatrices(), slot, stack, x, y);
    }

    @Inject(method = "renderHotbarItem", at = @At("RETURN"))
    private void snill$renderHotbarItemReturn(DrawContext context, int x, int y, RenderTickCounter tickCounter, PlayerEntity player, ItemStack stack, int seed, CallbackInfo ci) {
        SmoothSwapping.INSTANCE.popTransform(context.getMatrices(), snill$smoothHotbarPushed);
        snill$smoothHotbarPushed = false;
    }

    @Redirect(
            method = "renderHotbar",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Ljava/util/function/Function;Lnet/minecraft/util/Identifier;IIII)V",
                    ordinal = 1
            )
    )
    private void snill$drawAnimatedHotbarSelection(DrawContext context, Function<Identifier, RenderLayer> renderLayers, Identifier texture, int x, int y, int width, int height) {
        if (HOTBAR_SELECTION_TEXTURE.equals(texture)) {
            x = SmoothSwapping.INSTANCE.getAnimatedSelectedSlotX(x);
        }
        context.drawGuiTexture(renderLayers, texture, x, y, width, height);
    }

    @Inject(
            method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void snill$renderPatchedScoreboard(DrawContext drawContext, ScoreboardObjective objective, CallbackInfo ci) {
        if (!snill$shouldPatchScoreboard()) {
            return;
        }

        try {
            Scoreboard scoreboard = objective.getScoreboard();
            NumberFormat numberFormat = objective.getNumberFormatOr(StyledNumberFormat.RED);

            List<SidebarEntry> lines = scoreboard.getScoreboardEntries(objective).stream()
                    .filter(entry -> !entry.hidden())
                    .sorted(Comparator.comparing(ScoreboardEntry::value).reversed()
                            .thenComparing(ScoreboardEntry::owner, String.CASE_INSENSITIVE_ORDER))
                    .limit(15L)
                    .map(entry -> {
                        try {
                            Team team = scoreboard.getScoreHolderTeam(entry.owner());
                            Text name = snill$patchText(Team.decorateName(team, entry.name()));
                            Text score = entry.formatted(numberFormat);
                            int scoreWidth = this.client.textRenderer.getWidth(score);
                            return new SidebarEntry(name, score, scoreWidth);
                        } catch (Exception e) {
                            Text fallback = Text.literal("???");
                            return new SidebarEntry(fallback, fallback, 0);
                        }
                    })
                    .toList();

            Text title;
            try {
                title = snill$patchText(objective.getDisplayName());
            } catch (Exception e) {
                title = Text.literal("???");
            }

            int titleWidth = this.client.textRenderer.getWidth(title);
            int maxWidth = titleWidth;
            int separatorWidth = this.client.textRenderer.getWidth(": ");

            for (SidebarEntry line : lines) {
                maxWidth = Math.max(maxWidth, this.client.textRenderer.getWidth(line.name) + (line.scoreWidth > 0 ? separatorWidth + line.scoreWidth : 0));
            }

            int lineCount = lines.size();
            int totalHeight = lineCount * 9;
            int bottom = drawContext.getScaledWindowHeight() / 2 + totalHeight / 3;
            int left = drawContext.getScaledWindowWidth() - maxWidth - 3;
            int right = drawContext.getScaledWindowWidth() - 1;
            int bodyColor = this.client.options.getTextBackgroundColor(0.3F);
            int headerColor = this.client.options.getTextBackgroundColor(0.4F);
            int top = bottom - lineCount * 9;

            drawContext.fill(left - 2, top - 10, right, top - 1, headerColor);
            drawContext.fill(left - 2, top - 1, right, bottom, bodyColor);
            drawContext.drawText(this.client.textRenderer, title, left + maxWidth / 2 - titleWidth / 2, top - 9, -1, false);

            for (int index = 0; index < lineCount; ++index) {
                SidebarEntry line = lines.get(index);
                int y = bottom - (lineCount - index) * 9;
                drawContext.drawText(this.client.textRenderer, line.name, left, y, -1, false);
                drawContext.drawText(this.client.textRenderer, line.score, right - line.scoreWidth, y, -1, false);
            }

            ci.cancel();
        } catch (Exception ignored) {
        }
    }

    private boolean snill$shouldPatchScoreboard() {
        return ModuleClass.INSTANCE != null
                && ModuleClass.INSTANCE.nameProtect != null
                && ModuleClass.INSTANCE.nameProtect.isEnable();
    }

    private Text snill$patchText(Text text) {
        NameProtect nameProtect = ModuleClass.INSTANCE.nameProtect;
        Text patched = nameProtect.patchText(text);
        String patchedString = patched.getString();

        if (nameProtect.shouldHideGrief()) {
            if (patchedString.contains("Анархия-")) {
                patchedString = patchedString.replaceAll("Анархия-\\d+", "polardlc.fun");
            }
            if (patchedString.contains("ГРИФ #")) {
                patchedString = patchedString.replaceAll("ГРИФ #\\d+", "polardlc.fun");
            }
        }

        patchedString = snill$sanitizeForIdentifier(patchedString);

        if (patchedString.equals(patched.getString())) {
            return patched;
        }
        return Text.literal(patchedString).setStyle(patched.getStyle().withColor(TextColor.fromRgb(DOMAIN_COLOR)));
    }

    private String snill$sanitizeForIdentifier(String input) {
        if (input == null) return "";
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c >= 0x20 && c != 0x7F) {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
