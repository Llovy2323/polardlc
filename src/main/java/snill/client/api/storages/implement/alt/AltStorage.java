package snill.client.api.storages.implement.alt;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import lombok.Getter;
import net.minecraft.client.session.Session;
import snill.client.Snill;
import snill.client.api.QClient;
import snill.client.api.utils.render.RenderUtils;
import snill.client.mixin.IMinecraftClientAccessor;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class AltStorage implements QClient {

    @Getter
    private final List<Alt> alts = new CopyOnWriteArrayList<>();
    @Getter
    private String lastSelectedAlt = "";
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final File altFile;
    private final File lastAltFile;

    private static final String[] PREFIXES = {
            "Snill", "Astral", "Cosmo", "Vortex", "Void", "Shadow", "Ghost", "Nexus", "Quantum", "Hyper",
            "Nova", "Solar", "Lunar", "Eclipse", "Cyber", "Dark", "Frost", "Pulse", "Zenith", "Apex"
    };

    private static final String[] SUFFIXES = {
            "PvP", "God", "King", "Lord", "Strike", "Blade", "Demon", "Slayer", "Beast", "Master",
            "1337", "777", "999", "X", "Pro", "Elite", "Legend", "Walker", "Shift", "Flow"
    };

    public AltStorage() {
        File dir = Snill.INSTANCE != null && Snill.INSTANCE.globalsDir != null
                ? Snill.INSTANCE.globalsDir
                : new File("C:\\snill", "snill");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        this.altFile = new File(dir, "alts.snill");
        this.lastAltFile = new File(dir, "lastAlt.snill");
        loadAlts();
        restoreLastAlt();
    }

    public synchronized void loadAlts() {
        alts.clear();
        if (altFile.exists()) {
            try (Reader reader = new InputStreamReader(Files.newInputStream(altFile.toPath()), StandardCharsets.UTF_8)) {
                Type listType = new TypeToken<List<Alt>>() {}.getType();
                List<Alt> loaded = gson.fromJson(reader, listType);
                if (loaded != null) {
                    alts.addAll(loaded);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (alts.isEmpty() && mc != null && mc.getSession() != null && mc.getSession().getUsername() != null) {
            addAlt(mc.getSession().getUsername());
        }
    }

    private void restoreLastAlt() {
        if (lastAltFile.exists()) {
            try {
                String saved = new String(Files.readAllBytes(lastAltFile.toPath()), StandardCharsets.UTF_8).trim();
                if (!saved.isEmpty()) {
                    this.lastSelectedAlt = saved;
                    login(saved);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public synchronized void saveAlts() {
        try {
            if (!altFile.getParentFile().exists()) {
                altFile.getParentFile().mkdirs();
            }
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(altFile, false), StandardCharsets.UTF_8)) {
                gson.toJson(alts, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveLastAlt(String username) {
        try {
            if (!lastAltFile.getParentFile().exists()) {
                lastAltFile.getParentFile().mkdirs();
            }
            Files.write(lastAltFile.toPath(), username.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean addAlt(String username) {
        if (username == null || username.trim().isEmpty()) {
            return false;
        }
        String clean = username.trim();
        for (Alt alt : alts) {
            if (alt.getUsername().equalsIgnoreCase(clean)) {
                return false;
            }
        }
        Alt newAlt = new Alt(clean);
        alts.add(0, newAlt);
        saveAlts();
        return true;
    }

    public void removeAlt(Alt alt) {
        if (alt != null) {
            alts.remove(alt);
            saveAlts();
        }
    }

    public boolean login(String username) {
        if (username == null || username.trim().isEmpty()) {
            return false;
        }
        String clean = username.trim();
        UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + clean).getBytes(StandardCharsets.UTF_8));
        try {
            Session session = new Session(
                    clean,
                    uuid,
                    "",
                    Optional.empty(),
                    Optional.empty(),
                    Session.AccountType.MOJANG
            );
            if (mc != null) {
                ((IMinecraftClientAccessor) mc).setSession(session);
            }
            this.lastSelectedAlt = clean;
            saveLastAlt(clean);
            RenderUtils.clearSkinCache();
            addAlt(clean);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean login(Alt alt) {
        if (alt == null) return false;
        return login(alt.getUsername());
    }

    public String generateRandomNick() {
        Random random = new Random();
        String prefix = PREFIXES[random.nextInt(PREFIXES.length)];
        String suffix = SUFFIXES[random.nextInt(SUFFIXES.length)];
        int num = random.nextInt(999);
        return prefix + "_" + suffix + (num > 500 ? num : "");
    }
}
