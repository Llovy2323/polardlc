package snill.client.client.modules.impl.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import snill.client.api.events.EventLink;
import snill.client.api.events.implement.EventAttackEntity;
import snill.client.api.events.implement.EventUpdate;
import snill.client.client.modules.Module;
import snill.client.client.modules.settings.implement.BooleanSetting;
import snill.client.client.modules.settings.implement.FloatSetting;
import snill.client.client.modules.settings.implement.ModeSetting;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public class HitSounds extends Module {

    public static final String[] SOUND_NAMES = new String[]{
            "Нет", "Moan", "Boykisser", "Bonk", "Bring", "Click", "Glass",
            "Hit", "Orb", "Meow", "Magic Squash", "Nya", "Pop", "Soft", "Squash", "Tung", "Uwu"
    };

    public static HitSounds INSTANCE = new HitSounds();

    public final ModeSetting attackSound = new ModeSetting("Звук удара", "Moan", SOUND_NAMES);
    public final ModeSetting damageSound = new ModeSetting("Звук урона", "Moan", SOUND_NAMES);
    public final ModeSetting soundOrder = new ModeSetting("Порядок", "По очереди", "По очереди", "Случайно");
    public final FloatSetting volume = new FloatSetting("Громкость", 1.0f, 0.1f, 2.0f, 0.05f);
    public final FloatSetting pitch = new FloatSetting("Высота тона", 1.0f, 0.5f, 2.0f, 0.05f);
    public final BooleanSetting randomPitch = new BooleanSetting("Случайный питч", false);
    public final BooleanSetting onlyPlayers = new BooleanSetting("Только игроки", false);

    private final AtomicInteger attackCounter = new AtomicInteger(0);
    private final AtomicInteger damageCounter = new AtomicInteger(0);
    private final Random random = new Random();

    private int prevHurtTime = 0;
    public boolean playedHurtThisTick = false;

    public HitSounds() {
        super("HitSounds", "Кастомные звуки из LiquidBounce при ударе и получении урона", ModuleCategory.RENDER);
        addSettings(attackSound, damageSound, soundOrder, volume, pitch, randomPitch, onlyPlayers);
    }

    public static void registerAll() {
        String[] allSoundIds = new String[]{
                "bonk", "pop", "uwu", "nya", "tung", "meow", "bring", "soft", "magicsquash", "squash",
                "boykisser-1", "boykisser-2", "boykisser-3", "boykisser-4", "boykisser-5", "boykisser-6",
                "click-1", "click-2", "click-3",
                "moan-1", "moan-2", "moan-3", "moan-4",
                "glass-1", "glass-2", "glass-3"
        };

        for (String sId : allSoundIds) {
            try {
                Identifier id = Identifier.of("polar", sId);
                if (!Registries.SOUND_EVENT.containsId(id)) {
                    Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
                }
            } catch (Throwable ignored) {
            }
        }
    }

    @EventLink
    public void onAttack(EventAttackEntity event) {
        if (!isEnable() || mc.player == null || mc.world == null) return;
        if (event.getPlayer() != mc.player) return;
        if (attackSound.is("Нет")) return;

        Entity target = event.getTarget();
        if (target instanceof LivingEntity living) {
            if (!living.isAlive() || living.isRemoved()) return;
            if (onlyPlayers.isState() && !(target instanceof PlayerEntity)) {
                return;
            }
            if (mc.player.squaredDistanceTo(target) > 36.0) return;
            SoundEvent sound = resolveSound(attackSound.getCurrent(), attackCounter);
            if (sound != null) {
                playSound(sound);
            }
        }
    }

    @EventLink
    public void onUpdate(EventUpdate event) {
        if (!isEnable() || mc.player == null) return;

        int currentHurt = mc.player.hurtTime;
        if (currentHurt > 0 && prevHurtTime == 0) {
            if (!playedHurtThisTick && !damageSound.is("Нет")) {
                SoundEvent sound = resolveSound(damageSound.getCurrent(), damageCounter);
                if (sound != null) {
                    playSound(sound);
                }
            }
        }

        prevHurtTime = currentHurt;
        playedHurtThisTick = false;
    }

    public SoundEvent getDamageSoundEvent() {
        if (!isEnable() || damageSound.is("Нет")) return null;
        return resolveSound(damageSound.getCurrent(), damageCounter);
    }

    public void playCustom(SoundEvent sound) {
        if (sound != null) {
            playSound(sound);
        }
    }

    private SoundEvent resolveSound(String mode, AtomicInteger counter) {
        boolean sequential = soundOrder.is("По очереди");

        return switch (mode) {
            case "Moan" -> {
                int idx = sequential
                        ? (counter.getAndIncrement() % 4) + 1
                        : random.nextInt(4) + 1;
                yield getPolarSound("moan-" + idx);
            }
            case "Boykisser" -> {
                int idx = sequential
                        ? (counter.getAndIncrement() % 6) + 1
                        : random.nextInt(6) + 1;
                yield getPolarSound("boykisser-" + idx);
            }
            case "Glass" -> {
                int idx = sequential
                        ? (counter.getAndIncrement() % 3) + 1
                        : random.nextInt(3) + 1;
                yield getPolarSound("glass-" + idx);
            }
            case "Click" -> {
                int idx = sequential
                        ? (counter.getAndIncrement() % 3) + 1
                        : random.nextInt(3) + 1;
                yield getPolarSound("click-" + idx);
            }
            case "Bonk" -> getPolarSound("bonk");
            case "Bring" -> getPolarSound("bring");
            case "Hit" -> SoundEvents.ENTITY_ARROW_HIT_PLAYER;
            case "Orb" -> SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP;
            case "Meow" -> getPolarSound("meow");
            case "Magic Squash" -> getPolarSound("magicsquash");
            case "Nya" -> getPolarSound("nya");
            case "Pop" -> getPolarSound("pop");
            case "Soft" -> getPolarSound("soft");
            case "Squash" -> getPolarSound("squash");
            case "Tung" -> getPolarSound("tung");
            case "Uwu" -> getPolarSound("uwu");
            default -> null;
        };
    }

    private SoundEvent getPolarSound(String name) {
        Identifier id = Identifier.of("polar", name);
        if (Registries.SOUND_EVENT.containsId(id)) {
            return Registries.SOUND_EVENT.get(id);
        }
        return SoundEvent.of(id);
    }

    private void playSound(SoundEvent sound) {
        MinecraftClient client = mc != null ? mc : MinecraftClient.getInstance();
        if (sound == null || client.getSoundManager() == null) return;

        float p = pitch.get();
        if (randomPitch.isState()) {
            p += (random.nextFloat() - 0.5f) * 0.2f;
            p = Math.max(0.5f, Math.min(2.0f, p));
        }

        float v = volume.get() * 0.75f;
        try {
            PositionedSoundInstance instance = new PositionedSoundInstance(
                    sound.id(),
                    SoundCategory.PLAYERS,
                    v,
                    p,
                    net.minecraft.util.math.random.Random.create(),
                    false,
                    0,
                    net.minecraft.client.sound.SoundInstance.AttenuationType.NONE,
                    0.0, 0.0, 0.0,
                    true
            );
            client.getSoundManager().play(instance);
        } catch (Exception ignored) {
        }
    }
}
