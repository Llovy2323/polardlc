package snill.client.client.modules;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import snill.client.Snill;
import snill.client.api.QClient;
import snill.client.api.events.EventInvoker;
import snill.client.api.utils.animation.AnimationUtils;
import snill.client.api.utils.animation.Easings;
import snill.client.api.utils.notification.NotificationManager;
import snill.client.client.modules.settings.Setting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Getter
@Setter
public abstract class Module implements QClient {
    private String name;
    private String description;
    private int key;
    private ModuleCategory category;
    private boolean isOpen;
    private boolean enable;
    private final List<Setting> settings = new ArrayList<>();
    private final AnimationUtils animka = new AnimationUtils(60, 11, Easings.LINEAR);
    private final AnimationUtils arrayAnimka = new AnimationUtils(0, 11, Easings.LINEAR);

    public Module(final String name, final String description, final ModuleCategory category) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.key = -1;
    }

    public Module(final String name, final ModuleCategory category) {
        this.name = name;
        this.description = "NULLABLE";
        this.category = category;
        this.key = -1;
    }
    public void onEnable() {
        enable = true;
        EventInvoker.register(this);
        animka.update(1);
        NotificationManager.push(this.name, this.category.getIcons(), true);
    }

    public void onDisable() {
        enable = false;
        EventInvoker.unregister(this);
        animka.update(0);
        NotificationManager.push(this.name, this.category.getIcons(), false);
    }

    public void toggle() {
        this.enable = !this.enable;
        if (enable) this.onEnable();
        else this.onDisable();
    }

    public void setEnabled(final boolean state) {
        boolean lastState = this.enable;
        this.enable = state;

        try {
            if (state) {
                onEnable();
            } else if (lastState) {
                onDisable();
            }
        } catch (Exception e) {
            this.enable = false;
            this.onDisable();
        }
    }

    public void addSettings(Setting... settings) {
        if (settings == null || settings.length == 0) return;
        Arrays.stream(settings)
                .filter(Objects::nonNull)
                .forEach(this.settings::add);
    }

    public String getDisplayName() {
        return Snill.INSTANCE.localizationStorage == null ? name : Snill.INSTANCE.localizationStorage.translate(name);
    }

    public String getDisplayDescription() {
        if (description == null || description.isBlank() || "NULLABLE".equalsIgnoreCase(description) || "desc".equalsIgnoreCase(description)) {
            return "";
        }
        return Snill.INSTANCE.localizationStorage == null
                ? description
                : Snill.INSTANCE.localizationStorage.translateModuleDescription(description);
    }

    @RequiredArgsConstructor @Getter
    public enum ModuleCategory {
        COMBAT("Combat", "B"),
        MOVEMENT("Movement", "C"),
        RENDER("Visuals", "D"),
        MISC("Util", "E"),
        PLAYER("Player", "A");
        private final String name, icons;
    }
}
