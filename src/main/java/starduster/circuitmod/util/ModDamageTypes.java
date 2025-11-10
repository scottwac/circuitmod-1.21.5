package starduster.circuitmod.util;

import net.minecraft.entity.damage.DamageType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import starduster.circuitmod.Circuitmod;

public class ModDamageTypes {
    public static final RegistryKey<DamageType> SPACE_SUFFOCATION_DAMAGE = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, Identifier.of(Circuitmod.MOD_ID, "space_suffocation"));

    public static void initialize() {
        Circuitmod.LOGGER.info("ModDamageTypes initialized");

    }
}
