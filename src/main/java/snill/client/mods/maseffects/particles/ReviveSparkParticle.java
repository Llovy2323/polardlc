package snill.client.mods.maseffects.particles;

import snill.client.mods.maseffects.MaseffectsParticleTypes;
import net.fabricmc.fabric.api.client.particle.v1.FabricSpriteProvider;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.util.math.MathHelper;

public class ReviveSparkParticle extends SpriteBillboardParticle {
    private final SpriteProvider sprites;
    private final int targetEntityId;
    private final float startScale;

    public ReviveSparkParticle(ClientWorld world, double x, double y, double z, SpriteProvider sprites, double targetEntityId, double velocityX, double velocityY) {
        super(world, x, y, z, 0.0D, 0.0D, 0.0D);
        this.sprites = sprites;
        this.targetEntityId = MathHelper.floor(targetEntityId);
        this.startScale = 0.16F + this.random.nextFloat() * 0.08F;
        this.maxAge = 18 + this.random.nextInt(8);
        this.collidesWithWorld = false;
        this.setColor(1.0F, 1.0F, 1.0F);
        this.setAlpha(0.0F);
        this.scale = this.startScale;
        this.velocityX = velocityX * 0.08D + (this.random.nextDouble() - 0.5D) * 0.02D;
        this.velocityY = velocityY * 0.08D + (this.random.nextDouble() - 0.5D) * 0.02D + 0.02D;
        this.velocityZ = (this.random.nextDouble() - 0.5D) * 0.02D;
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
            double pull = this.age > 6 ? 0.075D : 0.025D;

            this.velocityX += dx / distance * pull;
            this.velocityY += dy / distance * pull;
            this.velocityZ += dz / distance * pull;
        }

        this.velocityX *= 0.94D;
        this.velocityY *= 0.94D;
        this.velocityZ *= 0.94D;

        this.move(this.velocityX, this.velocityY, this.velocityZ);
        this.setSpriteForAge(this.sprites);

        float life = (float) this.age / (float) this.maxAge;
        this.setAlpha(MathHelper.clamp(1.0F - life * 1.25F, 0.0F, 1.0F));
        this.scale = this.startScale * (1.0F - life * 0.45F);
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
            return new ReviveSparkParticle(world, x, y, z, this.sprites, velocityX, velocityY, velocityZ);
        }
    }
}
