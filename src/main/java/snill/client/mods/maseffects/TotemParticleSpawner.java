package snill.client.mods.maseffects;

import net.minecraft.entity.LivingEntity;
import net.minecraft.world.World;

public final class TotemParticleSpawner {
    private TotemParticleSpawner() {
    }

    public static void spawn(LivingEntity entity) {
        if (entity == null) {
            return;
        }

        World world = entity.getWorld();
        if (world == null || !world.isClient) {
            return;
        }

        double x = entity.getX();
        double y = entity.getY() + entity.getHeight() * 0.5D;
        double z = entity.getZ();
        float scale = Math.max(0.4F, entity.getWidth());
        int entityId = entity.getId();

        for (int i = 0; i < 17; i++) {
            double offsetX = (Math.random() - 0.5D) * entity.getWidth();
            double offsetY = Math.random() * entity.getHeight();
            double offsetZ = (Math.random() - 0.5D) * entity.getWidth();
            world.addParticle((net.minecraft.particle.ParticleEffect) MaseffectsParticleTypes.REVIVE, x + offsetX, y + offsetY, z + offsetZ, scale, entityId, 0.0D);

        }

        for (int i = 0; i < 99; i++) {
            double spread = 0.32D;
            double vx = (Math.random() - 0.5D) * spread;
            double vy = Math.random() * 0.22D;
            double vz = (Math.random() - 0.5D) * spread;
            world.addParticle((net.minecraft.particle.ParticleEffect) MaseffectsParticleTypes.REVIVE_SPARK, x, y, z, entityId, vx, vy);
        }
    }
}
