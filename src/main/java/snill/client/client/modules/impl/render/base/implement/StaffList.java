package snill.client.client.modules.impl.render.base.implement;

import static snill.client.Snill.INSTANCE;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;
import net.minecraft.world.GameMode;
import snill.client.api.events.implement.EventRender;
import snill.client.api.utils.animation.AnimationUtils;
import snill.client.api.utils.animation.Easings;
import snill.client.api.utils.color.ColorUtils;
import snill.client.api.utils.draggable.Draggable;
import snill.client.api.utils.render.RenderUtils;
import snill.client.api.utils.render.fonts.msdf.Font;
import snill.client.api.utils.render.fonts.msdf.Fonts;
import snill.client.api.utils.render.fonts.ttf.MCFontRenderer;
import snill.client.api.utils.scissor.ScissorUtils;
import snill.client.client.modules.impl.render.base.InterfaceProcessing;

import java.util.*;
import java.util.regex.Pattern;

public class StaffList extends InterfaceProcessing {

    private static final float BASE_MIN_WIDTH = 64f;
    private static final float EXTRA_WIDTH = 0f;
    private static final float ROW_RIGHT_MARGIN = 25f;
    private static final float ROW_HEIGHT = 10.9f;
    private static final float HEADER_HEIGHT = 16f;
    private static final float HEADER_GAP = 0.0f;
    private static final float CONTENT_PAD_TOP = 2.0f;
    private static final float CONTENT_PAD_BOTTOM = 0.8f;

    private static final int STATUS_VANISH_COLOR = 0xFFFF465A;
    private static final int STATUS_GM3_COLOR    = 0xFFFFDC46;
    private static final int STATUS_ONLINE_COLOR = 0xFF64FF78;
    private static final int STATUS_NEAR_COLOR   = 0xFF64CFFF;

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final Map<String, StaffData> staffDataCache = new LinkedHashMap<>();
    private final Map<String, AnimationUtils> staffAnimations = new HashMap<>();
    private final Set<String> activeStaff = new HashSet<>();

    private final Pattern namePattern = Pattern.compile("^\\w{3,16}$");
    private final Pattern botPattern = Pattern.compile("^\\d+$");

    private final Set<String> validStaffPrefixes = new HashSet<>();
    private final AnimationUtils widthAnimation = new AnimationUtils(60, 10.5f, Easings.QUAD_OUT);
    private final AnimationUtils heightAnimation = new AnimationUtils(16, 10.5f, Easings.QUAD_OUT);
    private long lastStaffUpdate = 0;
    private final List<String> visiblePlayers = new ArrayList<>();
    private final Set<String> animationScratch = new HashSet<>();

    private Font font12;
    private Font font13;
    private Font font14;
    private Font iconFont;

    private Font issue(int size) { return Fonts.getFont("suisse", size); }
    private Font icons(int size) { return Fonts.getFont("icon", size); }
    private MCFontRenderer divineIcons(int size) { return snill.client.api.utils.render.fonts.ttf.Fonts.getFont("divine_icons.ttf", size); }

    public StaffList(Draggable draggable) {
        super(draggable);
        validStaffPrefixes.addAll(Arrays.asList(
                // Модерация
                "mod", "der", "мод", "модер", "модератор", "moder", "moderator",
                "ml. moder", "мл. модер", "ml moder",
                "moder+", "модер+","d.moder",
                "st. moder", "ст. модер", "st moder", "старший модер",
                "gl. moder", "гл. модер", "gl moder", "главный модер",
                // Администрация
                "adm", "адм", "админ", "admin", "administrator",
                "ml. admin", "мл. админ", "ml admin",
                "владе", "owner", "wne",
                // Помощники
                "supp", "ꜱupp", "support", "media", "помо", "помощ", "помощник",
                "d. helper", "дежурный", "helper", "хелпер",
                // Разработка и другое
                "dev", "раз", "разработчик", "developer",
                "таф", "taf", "staff", "стафф", "сотрудник",
                "curat", "курато", "куратор",
                "yt", "ютуб", "youtube",
                "стажер", "trainee", "отри"
        ));
    }

    private static class PrefixSegment {
        final String text;
        final int color;
        float width12;
        PrefixSegment(String text, int color) { this.text = text; this.color = color; }
    }

    private static class StaffData {
        String status;
        String prefix;
        String rankLabel;
        List<PrefixSegment> segments;
        float prefixWidth12;
        float rankWidth12;
        float nameWidth12;
        StaffData(String status) { this.status = status; this.segments = new ArrayList<>(); }
    }

    private AnimationUtils getAnimation(String name) {
        return staffAnimations.computeIfAbsent(name, n -> new AnimationUtils(0, 10.5f, Easings.QUAD_OUT));
    }

    private void initFonts() {
        if (font12 == null) {
            font12 = Fonts.getFont("suisse", 12);
            font13 = Fonts.getFont("suisse", 13);
            font14 = Fonts.getFont("suisse", 14);
            iconFont = Fonts.getFont("icon", 13);
        }
    }

    private boolean isBot(String name) {
        return botPattern.matcher(name).matches();
    }

    @Override
    public void onRender(EventRender.Default eventRender) {
        if (mc.player == null || mc.world == null) return;
        initFonts();
        long now = System.currentTimeMillis();
        if (now - lastStaffUpdate > 500) { updateStaffCache(); lastStaffUpdate = now; }
        updateAnimations();
        
        // Check if any staff members are actually visible
        List<String> visible = getVisiblePlayers();
        boolean hasVisibleStaff = false;
        for (String name : visible) {
            AnimationUtils anim = getAnimation(name);
            if (anim.getValue() > 0.01f) {
                hasVisibleStaff = true;
                break;
            }
        }
        
        // Hide HUD element if no staff online
        if (!hasVisibleStaff) {
            draggable.setWidth(0);
            draggable.setHeight(0);
            return;
        }
        
        renderDefaultStyle(eventRender);
        super.onRender(eventRender);
    }

    private boolean matchesStaffPrefix(String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        for (String p : validStaffPrefixes) if (lower.contains(p)) return true;
        return false;
    }

    private void updateStaffCache() {
        activeStaff.clear();
        String selfName = mc.player.getName().getString();

        for (Team team : mc.world.getScoreboard().getTeams()) {
            Collection<String> players = team.getPlayerList();
            if (players.size() != 1) continue;
            String name = players.iterator().next();
            if (!namePattern.matcher(name).matches() || isBot(name) || name.equals(selfName)) continue;

            PlayerListEntry info = mc.getNetworkHandler().getPlayerListEntry(name);
            boolean vanish = info == null;
            boolean isGM3 = info != null && info.getGameMode() == GameMode.SPECTATOR;
            Text prefixText = team.getPrefix();

            if (matchesStaffPrefix(prefixText.getString()) || vanish || isGM3
                    || INSTANCE.staffStorage.isStaff(name)) {
                activeStaff.add(name);
                boolean isNear = !vanish && !isGM3 && isPlayerNearby(name);
                String status = vanish ? "VANISH" : isGM3 ? "GM3" : isNear ? "NEAR" : "ONLINE";

                StaffData existing = staffDataCache.computeIfAbsent(name, n -> new StaffData(status));
                existing.status = status;
                existing.prefix = prefixText.getString();
                existing.rankLabel = resolveRankLabel(team, existing.prefix, name);
                existing.segments = new ArrayList<>();
                calculateWidths(existing, name);
            }
        }
    }

    private boolean isPlayerNearby(String name) {
        if (mc.world == null) return false;
        for (var player : mc.world.getPlayers())
            if (player.getName().getString().equals(name)) return true;
        return false;
    }

    private void calculateWidths(StaffData data, String name) {
        data.prefixWidth12 = issue(12).getWidth(data.prefix == null ? "" : data.prefix) + 4f;
        data.rankWidth12 = data.rankLabel == null || data.rankLabel.isEmpty() ? 0f : issue(12).getWidth(data.rankLabel) + 3f;
        data.nameWidth12 = issue(12).getWidth(name);
    }

    private void updateAnimations() {
        animationScratch.clear();
        animationScratch.addAll(staffAnimations.keySet());
        animationScratch.addAll(activeStaff);
        for (String name : animationScratch) {
            getAnimation(name).update(activeStaff.contains(name) ? 1 : 0);
        }
    }

    private List<String> getVisiblePlayers() {
        visiblePlayers.clear();
        for (Map.Entry<String, AnimationUtils> e : staffAnimations.entrySet())
            if (e.getValue().getValue() > 0.01f) visiblePlayers.add(e.getKey());
        Collections.sort(visiblePlayers);
        return visiblePlayers;
    }

    private int getStatusColor(String status) {
        return switch (status) {
            case "VANISH" -> STATUS_VANISH_COLOR;
            case "GM3" -> STATUS_GM3_COLOR;
            case "NEAR" -> STATUS_NEAR_COLOR;
            default -> STATUS_ONLINE_COLOR;
        };
    }

    private String getStatusLabel(String status) {
        return switch (status) {
            case "VANISH" -> "Vanish";
            case "GM3" -> "GM3";
            case "NEAR" -> "Near";
            default -> "Online";
        };
    }

    private String resolveRankLabel(Team team, String prefix, String name) {
        String label = getRankLabel(prefix);
        if (!label.isEmpty()) {
            return label;
        }
        if (team != null) {
            label = getRankLabel(team.getName());
            if (!label.isEmpty()) {
                return label;
            }
        }
        if (INSTANCE.staffStorage != null && INSTANCE.staffStorage.isStaff(name)) {
            return "staff";
        }
        return "";
    }

    private String getRankLabel(String prefix) {
        if (prefix == null) return "";
        String lower = prefix.toLowerCase(Locale.ROOT);
        if (lower.contains("?") || lower.contains("spectator")) return "? Spectator";
        if (lower.contains("media")) return "ꔁ media";
        if (lower.contains("helper")) return "ꔉ helper";
        if (lower.contains("ml. moder") || lower.contains("ml moder")) return "ꔓ ml.moder";
        if (lower.contains("moder+") || lower.contains("d.moder")) return "ꔡ moder+";
        if (lower.contains("st. moder") || lower.contains("st moder") || lower.contains("старший мод")) return "ꔥ st.moder";
        if (lower.contains("gl. moder") || lower.contains("gl moder") || lower.contains("главный мод")) return "ꔩ gl.moder";
        if (lower.contains("ml. admin") || lower.contains("ml admin")) return "ꔳ ml.admin";
        if (lower.contains("?") || lower.contains("d.mladmin") || lower.contains("d.ml admin")) return "? d.mladmin";
        if (lower.contains("admin") || lower.contains("adm") || lower.contains("админ")) return "ꔷ admin";
        if (lower.contains("moder")) return "ꔗ moder";
        return "";
    }

    private void renderDefaultStyle(EventRender.Default eventRender) {
        float baseX = draggable.getX(), y = draggable.getY();
        MatrixStack matrices = eventRender.getContext().getMatrices();
        int colorTheme = getStableThemeColor();

        List<String> visible = getVisiblePlayers();
        float targetWidth = BASE_MIN_WIDTH;
        int visibleCount = 0;

        for (String name : visible) {
            AnimationUtils anim = getAnimation(name);
            if (anim.getValue() <= 0.01f) continue;
            StaffData data = staffDataCache.get(name);
            if (data == null) continue;
            visibleCount++;
            String statusLabel = getStatusLabel(data.status);
            float statusWidth = issue(10).getWidth(statusLabel) + 4f;
            float rowWidth = data.nameWidth12 + 4f + statusWidth + ROW_RIGHT_MARGIN + 5f;
            if (rowWidth > targetWidth) targetWidth = rowWidth;
        }

        float targetHeight = HEADER_HEIGHT + HEADER_GAP + CONTENT_PAD_TOP + visibleCount * ROW_HEIGHT + CONTENT_PAD_BOTTOM;
        widthAnimation.update(targetWidth);
        heightAnimation.update(targetHeight);

        float width = widthAnimation.getValue() + EXTRA_WIDTH;
        float height = heightAnimation.getValue();
        float rightEdge = baseX + width;
        float x = baseX;

        RenderUtils.drawDefaultHudElementRects(matrices, x, y, width, height, colorTheme, isUnusualRectType());
        issue(14).draw(matrices, "Staffs", x + 5.2f, y + 6f, -1);
        MCFontRenderer divineIcon = divineIcons(14);
        if (divineIcon != null) {
            divineIcon.drawString("d", rightEdge - 12f, y + 7f, colorTheme);
        } else {
            icons(14).draw(matrices, "F", rightEdge - 12f, y + 7f, colorTheme);
        }

        float offsetY = HEADER_HEIGHT + HEADER_GAP + CONTENT_PAD_TOP;

        for (String name : visible) {
            AnimationUtils anim = getAnimation(name);
            float animValue = anim.getValue();
            if (animValue <= 0.01f) continue;
            StaffData data = staffDataCache.get(name);
            if (data == null) continue;

            ScissorUtils.push();
            ScissorUtils.setFromComponentCoordinates(x, y, width, height);

            int alpha = (int) (255 * animValue);
            int textColor = ColorUtils.rgba(255, 255, 255, alpha);

            float textX = x + 5.2f;
            if (data.rankLabel != null && !data.rankLabel.isEmpty()) {
                String roleText = "  [" + data.rankLabel + "]";
                issue(12).draw(matrices, roleText, textX, y + offsetY + 1f, ColorUtils.rgba(150, 255, 180, alpha));
                textX += issue(12).getWidth(roleText) + 4f;
            }
            issue(13).draw(matrices, name, textX, y + offsetY + 1f, textColor);

            String statusLabel = getStatusLabel(data.status);
            float statusBoxWidth = Math.max(issue(10).getStringWidth(statusLabel) + 4, 9f);
            float statusBoxX = rightEdge - statusBoxWidth - 3;

            RenderUtils.drawBlur(matrices,
                    statusBoxX - 0.25f, y + offsetY - 2.4f,
                    statusBoxWidth + 0.5f, 9.5f,
                    1.5f, 5f,
                    ColorUtils.rgba(255, 255, 255, 255));
            RenderUtils.drawBlur(matrices,
                    statusBoxX - 0.25f, y + offsetY - 2.4f,
                    statusBoxWidth + 0.5f, 9.5f,
                    1.5f, 5f,
                    ColorUtils.rgba(0, 0, 0, 180));

            int statusColor = getStatusColor(data.status);
            issue(12).drawCenteredString(matrices,
                    statusLabel,
                    statusBoxX + statusBoxWidth / 2,
                    y + offsetY + 1.5f,
                    ColorUtils.setAlphaColor(statusColor, alpha));

            offsetY += ROW_HEIGHT * animValue;

            ScissorUtils.pop();
            ScissorUtils.unset();
        }

        draggable.setWidth(width);
        draggable.setHeight(height);
    }

    private int getStableThemeColor() {
        if (!INSTANCE.themeStorage.getThemes().getTheme().getName().equals("Rainbow")) {
            return INSTANCE.themeStorage.getThemes().getTheme().color[0];
        } else {
            return ColorUtils.getThemeColor();
        }
    }
}


