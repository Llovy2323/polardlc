package snill.client.client;

import snill.client.mods.maseffects.MaseffectsParticleTypes;
import snill.client.mods.maseffects.particles.ReviveParticle;
import snill.client.mods.maseffects.particles.ReviveSparkParticle;
import snill.client.mods.particular.ParticularParticleTypes;
import snill.client.mods.particular.particles.WaterSplashEmitterParticle;
import snill.client.mods.particular.particles.WaterSplashFoamParticle;
import snill.client.mods.particular.particles.WaterSplashParticle;
import snill.client.mods.particular.particles.WaterSplashRingParticle;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import ru.virtuoz.convert.Convert;

public class SnillDLC implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ParticleFactoryRegistry registry = ParticleFactoryRegistry.getInstance();

        registry.register((net.minecraft.particle.ParticleType) MaseffectsParticleTypes.REVIVE, ReviveParticle.Factory::new);
        registry.register((net.minecraft.particle.ParticleType) MaseffectsParticleTypes.REVIVE_SPARK, ReviveSparkParticle.Factory::new);

        registry.register((net.minecraft.particle.ParticleType) ParticularParticleTypes.WATER_SPLASH, WaterSplashParticle.Factory::new);
        registry.register((net.minecraft.particle.ParticleType) ParticularParticleTypes.WATER_SPLASH_FOAM, WaterSplashFoamParticle.Factory::new);
        registry.register((net.minecraft.particle.ParticleType) ParticularParticleTypes.WATER_SPLASH_RING, WaterSplashRingParticle.Factory::new);
        registry.register((net.minecraft.particle.ParticleType) ParticularParticleTypes.WATER_SPLASH_EMITTER, (type, world, x, y, z, velocityX, velocityY, velocityZ) ->
                new WaterSplashEmitterParticle(world, x, y, z, velocityX, velocityY, velocityZ));
    }
}
