package snill.client.mods.particular.particles;

import snill.client.mods.particular.ParticularParticleTypes;
import net.fabricmc.fabric.api.client.particle.v1.FabricSpriteProvider;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.util.math.MathHelper;

public class WaterSplashParticle extends SpriteBillboardParticle {
    private final SpriteProvider sprites;
    private final float startScale;

    public WaterSplashParticle(ClientWorld world, double x, double y, double z, SpriteProvider sprites, double velocityX, double velocityY, double velocityZ) {
        super(world, x, y, z, velocityX, velocityY, velocityZ);
        this.sprites = sprites;
        this.startScale = 0.08F + this.random.nextFloat() * 0.05F;
        this.maxAge = 18 + this.random.nextInt(8);
        this.collidesWithWorld = false;
        this.scale = this.startScale;
        this.setColor(0.86F, 0.94F, 1.0F);
        this.setAlpha(0.95F);
        this.setSpriteForAge(sprites);
    }

    @Override
    public void tick() {
        if (this.age++ >= this.maxAge) {
            this.markDead();
            return;
        }

        this.velocityY -= 0.02D;
        this.velocityX *= 0.92D;
        this.velocityY *= 0.88D;
        this.velocityZ *= 0.92D;
        this.move(this.velocityX, this.velocityY, this.velocityZ);

        float life = (float) this.age / (float) this.maxAge;
        this.scale = this.startScale * (0.9F + life * 0.7F);
        this.setAlpha(MathHelper.clamp(1.0F - life, 0.0F, 1.0F));
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
            return new WaterSplashParticle(world, x, y, z, this.sprites, velocityX, velocityY, velocityZ);
        }
    }
}
