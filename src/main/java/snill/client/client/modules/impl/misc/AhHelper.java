package snill.client.client.modules.impl.misc;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import snill.client.api.events.EventLink;
import snill.client.api.events.implement.EventPacket;
import snill.client.api.events.implement.EventUpdate;
import snill.client.api.utils.color.ColorUtils;
import snill.client.api.utils.features.PriceParser;
import snill.client.api.utils.render.RenderUtils;
import snill.client.api.utils.script.DelayScript;
import snill.client.client.modules.Module;
import snill.client.client.modules.settings.implement.ModeSetting;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AhHelper extends Module {

    private static final long PULSE_MS = 900L;
    private static final float PULSE_MIN_ALPHA = 0.35f;
    private static final float PULSE_MAX_ALPHA = 1.0f;

    private static final long RECALC_MIN_MS = 90L;
    private static final long RECALC_IDLE_MS = 650L;

    private static final int IGNORE_CONTROL_ROW_Y = 104;

    private static final int COLOR_GREEN = 0xFF4BFF4B;
    private static final int COLOR_RED = 0xFFFF4B4B;

    private static final Pattern NUM_PATTERN = Pattern.compile("(\\d{1,3}(?:[\\s,._]\\d{3})+|\\d+)");

    private static boolean tooltipInit;
    private static Method mGetTooltip;
    private static TooltipArg[] tooltipArgs;

    private static Field guiLeftField;
    private static Field guiTopField;
    private static boolean screenFieldsInit;

    public static AhHelper INSTANCE = new AhHelper();

    private final PriceParser priceParser = new PriceParser();
    private final DelayScript script = new DelayScript();

    private Slot cheapestSlot;
    private Slot costEffectiveSlot;

    private int lastSyncId = -1;
    private long lastRecalcMs;
    private boolean dirty;
    private boolean recalcQueued;

    private final ModeSetting cheapestItemColorSetting = new ModeSetting(
            "Самый дешевый предмет", "Зелёный", "Зелёный", "Красный"
    );
    private final ModeSetting costEffectiveItemColorSetting = new ModeSetting(
            "Экономичный предмет", "Красный", "Зелёный", "Красный"
    );

    public AhHelper() {
        super("AhHelper", "Подсветка самых выгодных лотов на аукционе", ModuleCategory.RENDER);
        addSettings(cheapestItemColorSetting, costEffectiveItemColorSetting);
        initScreenReflection();
    }

    @EventLink
    public void onPacket(EventPacket event) {
        if (event.getType() != EventPacket.Type.RECEIVE) {
            return;
        }

        if (event.getPacket() instanceof ScreenHandlerSlotUpdateS2CPacket) {
            if (!(mc.currentScreen instanceof GenericContainerScreen screen)) {
                return;
            }
            if (!isAuctionScreen(screen)) {
                return;
            }

            dirty = true;
            if (!recalcQueued) {
                recalcQueued = true;
                script.cleanup().addTickStep(0, () -> {
                    recalcQueued = false;
                    if (mc.currentScreen instanceof GenericContainerScreen s && isAuctionScreen(s)) {
                        recalc(s);
                    }
                });
            }
        }
    }

    @EventLink
    public void onTick(EventUpdate event) {
        script.update(event);

        if (!(mc.currentScreen instanceof GenericContainerScreen screen)) {
            resetCalcState();
            return;
        }
        if (!isAuctionScreen(screen)) {
            resetCalcState();
            return;
        }

        long now = System.currentTimeMillis();
        if (!dirty && now - lastRecalcMs >= RECALC_IDLE_MS) {
            recalc(screen);
        }
    }

    public void renderFromMixin(DrawContext context, int mouseX, int mouseY) {
        if (!isEnable()) {
            return;
        }
        if (!(mc.currentScreen instanceof GenericContainerScreen screen) || !isAuctionScreen(screen)) {
            resetCalcState();
            return;
        }

        long now = System.currentTimeMillis();
        ensureCalculated(screen, now);

        int guiLeft = getGuiLeft(screen);
        int guiTop = getGuiTop(screen);

        int cheapColor = pulsing(colorOf(cheapestItemColorSetting));
        int effColor = pulsing(colorOf(costEffectiveItemColorSetting));

        if (cheapestSlot != null) {
            highlightSlot(context, guiLeft, guiTop, cheapestSlot, cheapColor);
        }
        if (costEffectiveSlot != null) {
            highlightSlot(context, guiLeft, guiTop, costEffectiveSlot, effColor);
        }
    }

    private void ensureCalculated(GenericContainerScreen screen, long now) {
        int syncId = screen.getScreenHandler().syncId;
        if (syncId != lastSyncId) {
            lastSyncId = syncId;
            dirty = true;
            recalcQueued = false;
            cheapestSlot = null;
            costEffectiveSlot = null;
        }

        if (dirty && now - lastRecalcMs >= RECALC_MIN_MS) {
            recalc(screen);
        }
    }

    private void recalc(GenericContainerScreen screen) {
        if (mc.player == null) {
            resetCalcState();
            return;
        }

        long now = System.currentTimeMillis();
        List<Slot> slots = screen.getScreenHandler().slots;

        int n = slots.size();
        int[] prices = new int[n];
        int[] counts = new int[n];

        Slot bestCheap = null;
        int bestCheapPrice = Integer.MAX_VALUE;

        for (int i = 0; i < n; i++) {
            Slot slot = slots.get(i);
            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) {
                prices[i] = -1;
                counts[i] = 0;
                continue;
            }

            if (slot.inventory == mc.player.getInventory()) {
                prices[i] = -1;
                counts[i] = 0;
                continue;
            }
            if (slot.y >= IGNORE_CONTROL_ROW_Y) {
                prices[i] = -1;
                counts[i] = 0;
                continue;
            }

            int price = getTotalPrice(stack);
            prices[i] = price;
            counts[i] = Math.max(1, stack.getCount());

            if (price < 0) {
                continue;
            }

            if (price < bestCheapPrice) {
                bestCheapPrice = price;
                bestCheap = slot;
            }
        }

        Slot bestEff = null;
        double bestEffPpi = Double.POSITIVE_INFINITY;
        int bestEffTotal = Integer.MAX_VALUE;

        for (int i = 0; i < n; i++) {
            int price = prices[i];
            if (price < 0) {
                continue;
            }

            Slot slot = slots.get(i);
            if (slot == bestCheap) {
                continue;
            }

            int count = Math.max(1, counts[i]);
            double ppi = (double) price / (double) count;

            boolean better = ppi < bestEffPpi - 1.0E-9;
            boolean equal = Math.abs(ppi - bestEffPpi) <= 1.0E-9;
            if (better || (equal && price < bestEffTotal)) {
                bestEffPpi = ppi;
                bestEffTotal = price;
                bestEff = slot;
            }
        }

        cheapestSlot = bestCheap;
        costEffectiveSlot = bestEff;
        dirty = false;
        lastRecalcMs = now;
    }

    private int getTotalPrice(ItemStack stack) {
        int price = -1;
        try {
            price = priceParser.getPrice(stack);
        } catch (Throwable ignored) {
        }
        if (price >= 0) {
            return price;
        }
        return extractTotalPriceFallback(stack);
    }

    private int extractTotalPriceFallback(ItemStack stack) {
        try {
            int count = Math.max(1, stack.getCount());
            List<String> lines = collectTooltipLines(stack);
            String blob = String.join(" ", lines);
            return findTotalPriceInText(blob, count);
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private int findTotalPriceInText(String s, int count) {
        if (s == null || s.isEmpty()) {
            return -1;
        }

        String lower = s.toLowerCase();
        Matcher m = NUM_PATTERN.matcher(s);

        int bestScore = -1;
        long best = -1;

        while (m.find()) {
            int start = m.start(1);
            int end = m.end(1);

            long val = parseDigitsToLong(m.group(1));
            if (val <= 0) {
                continue;
            }

            long mul = readSuffixMultiplier(lower, end);
            if (mul != 1L) {
                val *= mul;
            }

            int cs = Math.max(0, start - 52);
            int ce = Math.min(lower.length(), end + 52);
            String ctx = lower.substring(cs, ce);

            if (!hasPriceKeyword(ctx)) {
                continue;
            }

            boolean per = ctx.contains("за шт") || ctx.contains("/шт") || ctx.contains("шт.")
                    || ctx.contains(" per ") || ctx.contains(" each ") || ctx.contains("за 1") || ctx.contains("за шту");
            boolean total = ctx.contains("всего") || ctx.contains("итого") || ctx.contains("total")
                    || ctx.contains("сумм") || ctx.contains("общ");

            int score = 3;
            if (total) {
                score += 3;
            }
            if (per) {
                score += 1;
            }

            long totalVal = per ? val * (long) count : val;
            if (totalVal <= 0) {
                continue;
            }

            if (score > bestScore || (score == bestScore && totalVal > best)) {
                bestScore = score;
                best = totalVal;
            }
        }

        if (best <= 0) {
            return -1;
        }
        if (best > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) best;
    }

    private boolean hasPriceKeyword(String ctx) {
        return ctx.contains("цена") || ctx.contains("price") || ctx.contains("стоим")
                || ctx.contains("руб") || ctx.contains("монет") || ctx.contains("coins") || ctx.contains("коин")
                || ctx.contains("buy") || ctx.contains("куп") || ctx.contains("$") || ctx.contains("₽");
    }

    private long readSuffixMultiplier(String lower, int end) {
        if (end >= lower.length()) {
            return 1L;
        }
        char c0 = lower.charAt(end);
        char c1 = (end + 1 < lower.length()) ? lower.charAt(end + 1) : 0;
        if (c0 == 'k' || c0 == 'к') {
            return (c1 == 'k' || c1 == 'к') ? 1_000_000L : 1_000L;
        }
        if (c0 == 'm' || c0 == 'м') {
            return 1_000_000L;
        }
        return 1L;
    }

    private long parseDigitsToLong(String raw) {
        long v = 0L;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c >= '0' && c <= '9') {
                v = v * 10L + (c - '0');
            }
        }
        return v;
    }

    private List<String> collectTooltipLines(ItemStack stack) {
        ArrayList<String> api = tryCollectTooltipApi(stack);
        if (api != null && !api.isEmpty()) {
            for (int i = 0; i < api.size(); i++) {
                api.set(i, stripFormatting(api.get(i)));
            }
            return api;
        }

        ArrayList<String> out = new ArrayList<>(8);
        out.add(safeName(stack));
        for (int i = 0; i < out.size(); i++) {
            out.set(i, stripFormatting(out.get(i)));
        }
        return out;
    }

    private ArrayList<String> tryCollectTooltipApi(ItemStack stack) {
        try {
            if (!tooltipInit) {
                initTooltipApi(stack);
            }
            if (mGetTooltip == null || tooltipArgs == null) {
                return null;
            }

            Object[] args = new Object[tooltipArgs.length];
            for (int i = 0; i < tooltipArgs.length; i++) {
                args[i] = tooltipArgs[i].value(mc.player);
            }

            Object res = mGetTooltip.invoke(stack, args);
            if (!(res instanceof List<?> list)) {
                return null;
            }

            ArrayList<String> out = new ArrayList<>(list.size());
            for (Object o : list) {
                if (o instanceof Text t) {
                    out.add(t.getString());
                } else if (o != null) {
                    out.add(String.valueOf(o));
                }
            }
            return out;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void initTooltipApi(ItemStack stack) {
        tooltipInit = true;
        if (stack == null) {
            return;
        }

        for (Method m : stack.getClass().getMethods()) {
            if (!"getTooltip".equals(m.getName())) {
                continue;
            }
            if (!List.class.isAssignableFrom(m.getReturnType())) {
                continue;
            }

            Class<?>[] pts = m.getParameterTypes();
            TooltipArg[] plan = buildTooltipPlan(pts);
            if (plan == null) {
                continue;
            }

            try {
                Object[] args = new Object[plan.length];
                for (int i = 0; i < plan.length; i++) {
                    args[i] = plan[i].value(mc.player);
                }
                Object res = m.invoke(stack, args);
                if (res instanceof List<?>) {
                    mGetTooltip = m;
                    tooltipArgs = plan;
                    return;
                }
            } catch (Throwable ignored) {
            }
        }

        mGetTooltip = null;
        tooltipArgs = null;
    }

    private TooltipArg[] buildTooltipPlan(Class<?>[] pts) {
        if (pts == null) {
            return null;
        }
        TooltipArg[] out = new TooltipArg[pts.length];

        for (int i = 0; i < pts.length; i++) {
            Class<?> pt = pts[i];
            if (pt == null) {
                return null;
            }

            if (mc.player != null && pt.isAssignableFrom(mc.player.getClass())) {
                out[i] = TooltipArg.player();
                continue;
            }

            if (pt == boolean.class || pt == Boolean.class) {
                out[i] = TooltipArg.boolFalse();
                continue;
            }

            if (pt == int.class || pt == Integer.class) {
                out[i] = TooltipArg.intZero();
                continue;
            }

            if (pt.isEnum()) {
                Object pick = pickEnum(pt, "NORMAL", "DEFAULT", "BASIC", "REGULAR");
                out[i] = TooltipArg.fixed(pick);
                continue;
            }

            Object st = pickStatic(pt, "DEFAULT", "NORMAL", "BASIC", "REGULAR", "STANDARD");
            if (st != null) {
                out[i] = TooltipArg.fixed(st);
                continue;
            }

            if (pt.isInterface()) {
                out[i] = TooltipArg.proxy(pt);
                continue;
            }

            out[i] = TooltipArg.fixed(null);
        }

        return out;
    }

    private Object pickStatic(Class<?> type, String... names) {
        try {
            for (String n : names) {
                try {
                    Field f = type.getField(n);
                    if (!type.isAssignableFrom(f.getType())) {
                        continue;
                    }
                    return f.get(null);
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private Object pickEnum(Class<?> enumType, String... prefer) {
        try {
            Object[] cs = enumType.getEnumConstants();
            if (cs == null || cs.length == 0) {
                return null;
            }

            for (String p : prefer) {
                if (p == null) {
                    continue;
                }
                for (Object c : cs) {
                    if (c != null && p.equalsIgnoreCase(String.valueOf(c))) {
                        return c;
                    }
                }
            }
            return cs[0];
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String safeName(ItemStack stack) {
        try {
            return stack.getName().getString();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private int pulsing(int color) {
        long now = System.currentTimeMillis();
        float t = (now % PULSE_MS) / (float) PULSE_MS;
        float wave = 0.5f - 0.5f * MathHelper.cos(t * 6.2831855f);
        float alpha = MathHelper.clamp(PULSE_MIN_ALPHA + (PULSE_MAX_ALPHA - PULSE_MIN_ALPHA) * wave, 0.0f, 1.0f);
        return ColorUtils.multAlpha(color, alpha);
    }

    private void highlightSlot(DrawContext context, int guiLeft, int guiTop, Slot slot, int color) {
        int x = guiLeft + slot.x;
        int y = guiTop + slot.y;
        RenderUtils.drawRoundedRect(context.getMatrices(), x, y, 16, 16, 0, color);
    }

    private int colorOf(ModeSetting setting) {
        return setting.is("Красный") ? COLOR_RED : COLOR_GREEN;
    }

    private boolean isAuctionScreen(GenericContainerScreen screen) {
        String title = screen.getTitle() == null ? "" : screen.getTitle().getString();
        return title.contains("Аукцион") || title.contains("Аукционы") || title.contains("Поиск");
    }

    private void resetCalcState() {
        cheapestSlot = null;
        costEffectiveSlot = null;
        lastSyncId = -1;
        lastRecalcMs = 0L;
        dirty = false;
        recalcQueued = false;
        script.cleanup();
    }

    private String stripFormatting(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '§') {
                i++;
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    private void initScreenReflection() {
        if (screenFieldsInit) {
            return;
        }
        screenFieldsInit = true;

        try {
            for (Field field : HandledScreen.class.getDeclaredFields()) {
                field.setAccessible(true);
                String name = field.getName();
                if (field.getType() != int.class) {
                    continue;
                }
                if (guiLeftField == null && (name.equals("x") || name.contains("Left") || name.equals("field_2776"))) {
                    guiLeftField = field;
                } else if (guiTopField == null && (name.equals("y") || name.contains("Top") || name.equals("field_2800"))) {
                    guiTopField = field;
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private int getGuiLeft(HandledScreen<?> screen) {
        try {
            if (guiLeftField != null) {
                return guiLeftField.getInt(screen);
            }
        } catch (Throwable ignored) {
        }
        return (mc.getWindow().getScaledWidth() - 176) / 2;
    }

    private int getGuiTop(HandledScreen<?> screen) {
        try {
            if (guiTopField != null) {
                return guiTopField.getInt(screen);
            }
        } catch (Throwable ignored) {
        }
        return (mc.getWindow().getScaledHeight() - 166) / 2;
    }

    private static class TooltipArg {
        private final int kind;
        private final Object fixed;
        private final Class<?> iface;

        private TooltipArg(int kind, Object fixed, Class<?> iface) {
            this.kind = kind;
            this.fixed = fixed;
            this.iface = iface;
        }

        static TooltipArg fixed(Object v) {
            return new TooltipArg(0, v, null);
        }

        static TooltipArg player() {
            return new TooltipArg(1, null, null);
        }

        static TooltipArg boolFalse() {
            return new TooltipArg(2, null, null);
        }

        static TooltipArg intZero() {
            return new TooltipArg(3, null, null);
        }

        static TooltipArg proxy(Class<?> iface) {
            return new TooltipArg(4, null, iface);
        }

        Object value(Object player) {
            return switch (kind) {
                case 1 -> player;
                case 2 -> false;
                case 3 -> 0;
                case 4 -> makeProxy(iface);
                default -> fixed;
            };
        }

        private Object makeProxy(Class<?> iface) {
            try {
                return Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[]{iface}, (p, m, a) -> {
                    Class<?> rt = m.getReturnType();
                    if (rt == boolean.class || rt == Boolean.class) {
                        return false;
                    }
                    if (rt == int.class || rt == Integer.class) {
                        return 0;
                    }
                    if (rt == float.class || rt == Float.class) {
                        return 0f;
                    }
                    if (rt == double.class || rt == Double.class) {
                        return 0.0;
                    }
                    return null;
                });
            } catch (Throwable ignored) {
                return null;
            }
        }
    }
}
