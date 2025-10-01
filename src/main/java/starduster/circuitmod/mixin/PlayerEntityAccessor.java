package starduster.circuitmod.mixin;

import net.minecraft.entity.EntityEquipment;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor mixin to access protected fields in LivingEntity.
 */
@Mixin(LivingEntity.class)
public interface PlayerEntityAccessor {
    
    @Accessor("equipment")
    EntityEquipment getEquipment();
}
