package snill.client.mods.particular.particles;

import net.fabricmc.fabric.api.client.particle.v1.FabricSpriteProvider;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.util.math.MathHelper;

public class WaterSplashRingParticle extends SpriteBillboardParticle {
    private final SpriteProvider sprites;
    private final float startScale;

    public WaterSplashRingParticle(ClientWorld world, double x, double y, double z, SpriteProvider sprites, double velocityX, double velocityY, double velocityZ) {
        super(world, x, y, z, velocityX, velocityY, velocityZ);
        this.sprites = sprites;
        this.startScale = 0.14F + this.random.nextFloat() * 0.06F;
        this.maxAge = 16 + this.random.nextInt(6);
        this.collidesWithWorld = false;
        this.velocityY = 0.01D;
        this.setColor(0.82F, 0.92F, 1.0F);
        this.scale = this.startScale;
        this.setAlpha(0.9F);
        this.setSpriteForAge(sprites);
    }

    @Override
    public void tick() {
        if (this.age++ >= this.maxAge) {
            this.markDead();
            return;
        }

        this.velocityX *= 0.92D;
        this.velocityY *= 0.90D;
        this.velocityZ *= 0.92D;
        this.move(this.velocityX, this.velocityY, this.velocityZ);

        float life = (float) this.age / (float) this.maxAge;
        this.scale = this.startScale * (1.0F + life * 2.2F);
        this.setAlpha(MathHelper.clamp(1.0F - life * 1.15F, 0.0F, 1.0F));
        this.setSpriteForAge(this.sprites);
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
            return new WaterSplashRingParticle(world, x, y, z, this.sprites, velocityX, velocityY, velocityZ);
        }
    }
}
