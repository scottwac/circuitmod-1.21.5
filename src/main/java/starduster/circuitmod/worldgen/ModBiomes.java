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

    public static final RegistryKey<Biome> LUNAR_MARIA = RegistryKey.of(RegistryKeys.BIOME,
            Identifier.of(Circuitmod.MOD_ID, "lunar_maria"));
    public static final RegistryKey<Biome> LUNAR_ROCKY_MARIA = RegistryKey.of(RegistryKeys.BIOME,
            Identifier.of(Circuitmod.MOD_ID, "lunar_rocky_maria"));
    public static final RegistryKey<Biome> LUNAR_TERRAE = RegistryKey.of(RegistryKeys.BIOME,
            Identifier.of(Circuitmod.MOD_ID, "lunar_terrae"));
    public static final RegistryKey<Biome> LUNAR_ROCKY_TERRAE = RegistryKey.of(RegistryKeys.BIOME,
            Identifier.of(Circuitmod.MOD_ID, "lunar_rocky_terrae"));
    public static final RegistryKey<Biome> LUNAR_GLACIES = RegistryKey.of(RegistryKeys.BIOME,
            Identifier.of(Circuitmod.MOD_ID, "lunar_glacies"));
    public static final RegistryKey<Biome> LUNAR_ROCKY_GLACIES = RegistryKey.of(RegistryKeys.BIOME,
            Identifier.of(Circuitmod.MOD_ID, "lunar_rocky_glacies"));

    
    public static void initialize() {
        // The biome is defined in the data pack, so we just need to register the key
        // TerraBlender will handle the actual biome registration
        Circuitmod.LOGGER.info("Initializing ModBiomes");
    }
} 