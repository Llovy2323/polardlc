package snill.client.mods.maseffects;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class MaseffectsParticleTypes {
    public static final Object REVIVE = FabricParticleTypes.simple(true);
    public static final Object REVIVE_SPARK = FabricParticleTypes.simple(true);

    private MaseffectsParticleTypes() {
    }

    public static void register() {
        Registry.register((Registry) Registries.PARTICLE_TYPE, Identifier.of("snill", "revive"), REVIVE);
        Registry.register((Registry) Registries.PARTICLE_TYPE, Identifier.of("snill", "revive_spark"), REVIVE_SPARK);
    }
}
