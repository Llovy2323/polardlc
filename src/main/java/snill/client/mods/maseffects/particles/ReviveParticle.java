package snill.client.mods.maseffects.particles;

import snill.client.mods.maseffects.MaseffectsParticleTypes;
import net.fabricmc.fabric.api.client.particle.v1.FabricSpriteProvider;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.util.math.MathHelper;

public class ReviveParticle extends SpriteBillboardParticle {
    private final SpriteProvider sprites;
    private final int targetEntityId;
    private final float baseScale;

    public ReviveParticle(ClientWorld world, double x, double y, double z, SpriteProvider sprites, double scale, double targetEntityId, double ignored) {
        super(world, x, y, z, 0.0D, 0.0D, 0.0D);
        this.sprites = sprites;
        this.targetEntityId = MathHelper.floor(targetEntityId);
        this.baseScale = 0.5F + (float) Math.max(0.0D, scale) * 0.2F;
        this.maxAge = 24 + this.random.nextInt(10);
        this.collidesWithWorld = false;
        this.setColor(0.82F, 0.67F, 1.0F);
        this.setAlpha(0.0F);
        this.scale = this.baseScale;
        this.setSpriteForAge(sprites);
    }

    @Override
    public void tick() {
        if (this.age++ >= this.maxAge) {
            this.markDead();
            return;
        }

        Entity entity = this.world.getEntityById(this.targetEntityId);
        if (entity != null) {
            double tx = entity.getX();
            double ty = entity.getY() + entity.getHeight() * 0.5D;
            double tz = entity.getZ();
            double dx = tx - this.x;
            double dy = ty - this.y;
            double dz = tz - this.z;
            double distance = Math.max(0.001D, Math.sqrt(dx * dx + dy * dy + dz * dz));
            double pull = 0.045D + Math.min(0.08D, distance * 0.012D);

            this.velocityX += dx / distance * pull;
            this.velocityY += dy / distance * pull;
            this.velocityZ += dz / distance * pull;
        }

        this.velocityX *= 0.91D;
        this.velocityY *= 0.91D;
        this.velocityZ *= 0.91D;

        this.move(this.velocityX, this.velocityY, this.velocityZ);
        this.setSpriteForAge(this.sprites);

        float life = (float) this.age / (float) this.maxAge;
        float fadeIn = MathHelper.clamp(life / 0.18F, 0.0F, 1.0F);
        float fadeOut = MathHelper.clamp((1.0F - life) / 0.22F, 0.0F, 1.0F);
        this.setAlpha(Math.min(fadeIn, fadeOut));
        this.scale = this.baseScale * (0.85F + MathHelper.sin(life * (float) Math.PI) * 0.35F);
    }

    @Override
    public ParticleTextureSheet getType() {
        return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static final class Factory implements ParticleFactory<SimpleParticleType> {
        private final FabricSpriteProvider sprites;

        public Factory(FabricSpriteProvider sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType parameters, ClientWorld world, double x, double y, double z, double velocityX, double velocityY, double velocityZ) {
            return new ReviveParticle(world, x, y, z, this.sprites, velocityX, velocityY, velocityZ);
        }
    }
}
