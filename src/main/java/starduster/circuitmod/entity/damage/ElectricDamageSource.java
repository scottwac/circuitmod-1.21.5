package starduster.circuitmod.entity.damage;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

public class ElectricDamageSource extends DamageSource {
    public ElectricDamageSource(RegistryEntry<DamageType> type) {
        super(type);
    }

    @Override
    public Text getDeathMessage(LivingEntity entity) {
        return Text.translatable("death.attack.electrocution", entity.getDisplayName());
    }

    /**
     * Factory method to create an electric damage source using the world's lightning bolt type.
     */
    public static ElectricDamageSource create(ServerWorld world) {
        DamageSource base = world.getDamageSources().lightningBolt();
        return new ElectricDamageSource(world.getDamageSources().registry.getEntry(base.getType()));
    }
    
} 