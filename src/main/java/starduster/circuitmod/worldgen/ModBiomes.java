package starduster.circuitmod.worldgen;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;
import starduster.circuitmod.Circuitmod;

public class ModBiomes {
    
    public static final RegistryKey<Biome> TECHNO_JUNGLE = RegistryKey.of(RegistryKeys.BIOME, 
        Identifier.of(Circuitmod.MOD_ID, "techno_jungle"));
    
    public static void initialize() {
        // The biome is defined in the data pack, so we just need to register the key
        // TerraBlender will handle the actual biome registration
        Circuitmod.LOGGER.info("Initializing ModBiomes");
    }
} 