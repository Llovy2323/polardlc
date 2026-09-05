package snill.client.api.utils.features;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PriceParser {

    private static final Pattern NUM_PATTERN = Pattern.compile("(\\d{1,3}(?:[\\s,._]\\d{3})+|\\d+)");

    public int getPrice(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return -1;
        }

        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore == null) {
            return -1;
        }

        int count = Math.max(1, stack.getCount());
        int best = -1;

        for (Text line : lore.lines()) {
            int price = findTotalPriceInText(stripFormatting(line.getString()), count);
            if (price >= 0 && (best < 0 || price < best)) {
                best = price;
            }
        }

        return best;
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
}
