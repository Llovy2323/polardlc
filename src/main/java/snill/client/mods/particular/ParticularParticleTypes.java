package snill.client.mods.particular;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ParticularParticleTypes {
    public static final Object WATER_SPLASH_EMITTER = FabricParticleTypes.simple(true);
    public static final Object WATER_SPLASH = FabricParticleTypes.simple(true);
    public static final Object WATER_SPLASH_FOAM = FabricParticleTypes.simple(true);
    public static final Object WATER_SPLASH_RING = FabricParticleTypes.simple(true);

    private ParticularParticleTypes() {
    }

    public static void register() {
        Registry.register((Registry) Registries.PARTICLE_TYPE, Identifier.of("snill", "water_splash_emitter"), WATER_SPLASH_EMITTER);
        Registry.register((Registry) Registries.PARTICLE_TYPE, Identifier.of("snill", "water_splash"), WATER_SPLASH);
        Registry.register((Registry) Registries.PARTICLE_TYPE, Identifier.of("snill", "water_splash_foam"), WATER_SPLASH_FOAM);
        Registry.register((Registry) Registries.PARTICLE_TYPE, Identifier.of("snill", "water_splash_ring"), WATER_SPLASH_RING);
    }
}
