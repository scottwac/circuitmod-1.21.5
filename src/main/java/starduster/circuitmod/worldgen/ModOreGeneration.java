package starduster.circuitmod.worldgen;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.gen.GenerationStep;
import starduster.circuitmod.Circuitmod;

public class ModOreGeneration {

    public static final RegistryKey<net.minecraft.world.gen.feature.PlacedFeature> URANIUM_ORE_PLACED_KEY = RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.of(Circuitmod.MOD_ID, "uranium_ore"));
    public static final RegistryKey<net.minecraft.world.gen.feature.PlacedFeature> BAUXITE_ORE_PLACED_KEY = RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.of(Circuitmod.MOD_ID, "bauxite_ore"));
    public static final RegistryKey<net.minecraft.world.gen.feature.PlacedFeature> LEAD_ORE_UPPER_PLACED_KEY = RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.of(Circuitmod.MOD_ID, "lead_ore_upper"));
    public static final RegistryKey<net.minecraft.world.gen.feature.PlacedFeature> LEAD_ORE_LOWER_PLACED_KEY = RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.of(Circuitmod.MOD_ID, "lead_ore_lower"));
    public static final RegistryKey<net.minecraft.world.gen.feature.PlacedFeature> ZIRCON_ORE_PLACED_KEY = RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.of(Circuitmod.MOD_ID, "zircon_ore"));

    public static final RegistryKey<net.minecraft.world.gen.feature.PlacedFeature> LUNA_DIAMOND_ORE_PLACED_KEY = RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.of(Circuitmod.MOD_ID, "luna_diamond_ore_main"));
    public static final RegistryKey<net.minecraft.world.gen.feature.PlacedFeature> LUNA_DIAMOND_ORE_SMALL_PLACED_KEY = RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.of(Circuitmod.MOD_ID, "luna_diamond_ore_main_small"));
    public static final RegistryKey<net.minecraft.world.gen.feature.PlacedFeature> LUNA_DIAMOND_ORE_SURFACE_PLACED_KEY = RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.of(Circuitmod.MOD_ID, "luna_diamond_ore_surface"));

    public static final RegistryKey<net.minecraft.world.gen.feature.PlacedFeature> LUNA_IRON_ORE_UNIFORM_PLACED_KEY = RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.of(Circuitmod.MOD_ID, "luna_iron_ore_uniform"));
    public static final RegistryKey<net.minecraft.world.gen.feature.PlacedFeature> LUNA_IRON_ORE_UNIFORM_SMALL_PLACED_KEY = RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.of(Circuitmod.MOD_ID, "luna_iron_ore_uniform_small"));
    public static final RegistryKey<net.minecraft.world.gen.feature.PlacedFeature> LUNA_IRON_ORE_SURFACE_PLACED_KEY = RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.of(Circuitmod.MOD_ID, "luna_iron_ore_surface"));
    public static final RegistryKey<net.minecraft.world.gen.feature.PlacedFeature> LUNA_IRON_ORE_DEPTH_PLACED_KEY = RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.of(Circuitmod.MOD_ID, "luna_iron_ore_depth"));

    public static final RegistryKey<net.minecraft.world.gen.feature.PlacedFeature> LUNA_COPPER_ORE_UNIFORM_PLACED_KEY = RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.of(Circuitmod.MOD_ID, "luna_copper_ore_uniform"));
    public static final RegistryKey<net.minecraft.world.gen.feature.PlacedFeature> LUNA_COPPER_ORE_UNIFORM_SMALL_PLACED_KEY = RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.of(Circuitmod.MOD_ID, "luna_copper_ore_uniform_small"));
    public static final RegistryKey<net.minecraft.world.gen.feature.PlacedFeature> LUNA_COPPER_ORE_SURFACE_PLACED_KEY = RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.of(Circuitmod.MOD_ID, "luna_copper_ore_surface"));

    public static final RegistryKey<net.minecraft.world.gen.feature.PlacedFeature> LUNA_GOLD_ORE_SURFACE_PLACED_KEY = RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.of(Circuitmod.MOD_ID, "luna_gold_ore_surface"));
    public static final RegistryKey<net.minecraft.world.gen.feature.PlacedFeature> LUNA_GOLD_ORE_UNIFORM_PLACED_KEY = RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.of(Circuitmod.MOD_ID, "luna_gold_ore_uniform"));
    public static final RegistryKey<net.minecraft.world.gen.feature.PlacedFeature> LUNA_GOLD_ORE_MAIN_PLACED_KEY = RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.of(Circuitmod.MOD_ID, "luna_gold_ore_main"));

    public static final RegistryKey<net.minecraft.world.gen.feature.PlacedFeature> LUNA_ZIRCON_ORE_UNIFORM_PLACED_KEY = RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.of(Circuitmod.MOD_ID, "luna_zircon_ore_uniform"));
    public static final RegistryKey<net.minecraft.world.gen.feature.PlacedFeature> LUNA_ZIRCON_ORE_DEPTH_PLACED_KEY = RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.of(Circuitmod.MOD_ID, "luna_zircon_ore_depth"));

    public static final RegistryKey<net.minecraft.world.gen.feature.PlacedFeature> LUNA_URANIUM_ORE_DEPTH_PLACED_KEY = RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.of(Circuitmod.MOD_ID, "luna_uranium_ore_depth"));
    public static final RegistryKey<net.minecraft.world.gen.feature.PlacedFeature> LUNA_URANIUM_ORE_DEPTH_SMALL_PLACED_KEY = RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.of(Circuitmod.MOD_ID, "luna_uranium_ore_depth_small"));
    public static final RegistryKey<net.minecraft.world.gen.feature.PlacedFeature> LUNA_URANIUM_ORE_UNIFORM_PLACED_KEY = RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.of(Circuitmod.MOD_ID, "luna_uranium_ore_uniform"));
    public static final RegistryKey<net.minecraft.world.gen.feature.PlacedFeature> LUNA_URANIUM_ORE_UNIFORM_SMALL_PLACED_KEY = RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.of(Circuitmod.MOD_ID, "luna_uranium_ore_uniform_small"));

    public static final RegistryKey<net.minecraft.world.gen.feature.PlacedFeature> LUNA_TITANIUM_ORE_SURFACE_PLACED_KEY = RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.of(Circuitmod.MOD_ID, "luna_titanium_ore_surface"));
    public static final RegistryKey<net.minecraft.world.gen.feature.PlacedFeature> LUNA_TITANIUM_ORE_SPREAD_PLACED_KEY = RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.of(Circuitmod.MOD_ID, "luna_titanium_ore_spread"));
    public static final RegistryKey<net.minecraft.world.gen.feature.PlacedFeature> LUNA_TITANIUM_ORE_SMALL_PLACED_KEY = RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.of(Circuitmod.MOD_ID, "luna_titanium_ore_small"));
    public static final RegistryKey<net.minecraft.world.gen.feature.PlacedFeature> LUNA_TITANIUM_ORE_MEDIUM_PLACED_KEY = RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.of(Circuitmod.MOD_ID, "luna_titanium_ore_medium"));
    public static final RegistryKey<net.minecraft.world.gen.feature.PlacedFeature> LUNA_TITANIUM_ORE_LARGE_PLACED_KEY = RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.of(Circuitmod.MOD_ID, "luna_titanium_ore_large"));

    public static final RegistryKey<net.minecraft.world.gen.feature.PlacedFeature> LUNA_ICE_PLACED_KEY = RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.of(Circuitmod.MOD_ID, "luna_ice"));
    public static final RegistryKey<net.minecraft.world.gen.feature.PlacedFeature> LUNA_ICE_GLACIES_PLACED_KEY = RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.of(Circuitmod.MOD_ID, "luna_ice_glacies"));
    public static final RegistryKey<net.minecraft.world.gen.feature.PlacedFeature> LUNA_ICE_LARGE_PLACED_KEY = RegistryKey.of(RegistryKeys.PLACED_FEATURE, Identifier.of(Circuitmod.MOD_ID, "luna_ice_large"));

    public static void generateOres() {
        BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Feature.UNDERGROUND_ORES, URANIUM_ORE_PLACED_KEY); //Uranium
        BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Feature.UNDERGROUND_ORES, BAUXITE_ORE_PLACED_KEY); //Bauxite
        BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Feature.UNDERGROUND_ORES, LEAD_ORE_UPPER_PLACED_KEY); //Upper Lead
        BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Feature.UNDERGROUND_ORES, LEAD_ORE_LOWER_PLACED_KEY); //Lower Lead
        BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Feature.UNDERGROUND_ORES, ZIRCON_ORE_PLACED_KEY); //Zircon

        Circuitmod.LOGGER.info("All mod ores generation registered for overworld biomes");


        /**
         * Luna
         */

        //Diamond
        BiomeModifications.addFeature(BiomeSelectors.includeByKey(ModBiomes.LUNAR_MARIA, ModBiomes.LUNAR_ROCKY_MARIA, ModBiomes.LUNAR_GLACIES, ModBiomes.LUNAR_ROCKY_GLACIES, ModBiomes.LUNAR_TERRAE, ModBiomes.LUNAR_ROCKY_TERRAE),
                GenerationStep.Feature.UNDERGROUND_ORES, LUNA_DIAMOND_ORE_PLACED_KEY);
        BiomeModifications.addFeature(BiomeSelectors.includeByKey(ModBiomes.LUNAR_MARIA, ModBiomes.LUNAR_ROCKY_MARIA, ModBiomes.LUNAR_GLACIES, ModBiomes.LUNAR_ROCKY_GLACIES, ModBiomes.LUNAR_TERRAE, ModBiomes.LUNAR_ROCKY_TERRAE),
                GenerationStep.Feature.UNDERGROUND_ORES, LUNA_DIAMOND_ORE_SMALL_PLACED_KEY);
        BiomeModifications.addFeature(BiomeSelectors.includeByKey(ModBiomes.LUNAR_MARIA, ModBiomes.LUNAR_ROCKY_MARIA, ModBiomes.LUNAR_GLACIES, ModBiomes.LUNAR_ROCKY_GLACIES, ModBiomes.LUNAR_TERRAE, ModBiomes.LUNAR_ROCKY_TERRAE),
                GenerationStep.Feature.UNDERGROUND_ORES, LUNA_DIAMOND_ORE_SURFACE_PLACED_KEY);

        //Iron
        BiomeModifications.addFeature(BiomeSelectors.includeByKey(ModBiomes.LUNAR_MARIA, ModBiomes.LUNAR_ROCKY_MARIA, ModBiomes.LUNAR_GLACIES, ModBiomes.LUNAR_ROCKY_GLACIES, ModBiomes.LUNAR_TERRAE, ModBiomes.LUNAR_ROCKY_TERRAE),
                GenerationStep.Feature.UNDERGROUND_ORES, LUNA_IRON_ORE_DEPTH_PLACED_KEY);
        BiomeModifications.addFeature(BiomeSelectors.includeByKey(ModBiomes.LUNAR_MARIA, ModBiomes.LUNAR_ROCKY_MARIA, ModBiomes.LUNAR_GLACIES, ModBiomes.LUNAR_ROCKY_GLACIES, ModBiomes.LUNAR_TERRAE, ModBiomes.LUNAR_ROCKY_TERRAE),
                GenerationStep.Feature.UNDERGROUND_ORES, LUNA_IRON_ORE_UNIFORM_PLACED_KEY);
        BiomeModifications.addFeature(BiomeSelectors.includeByKey(ModBiomes.LUNAR_MARIA, ModBiomes.LUNAR_ROCKY_MARIA, ModBiomes.LUNAR_GLACIES, ModBiomes.LUNAR_ROCKY_GLACIES, ModBiomes.LUNAR_TERRAE, ModBiomes.LUNAR_ROCKY_TERRAE),
                GenerationStep.Feature.UNDERGROUND_ORES, LUNA_IRON_ORE_SURFACE_PLACED_KEY);
        BiomeModifications.addFeature(BiomeSelectors.includeByKey(ModBiomes.LUNAR_MARIA, ModBiomes.LUNAR_ROCKY_MARIA, ModBiomes.LUNAR_GLACIES, ModBiomes.LUNAR_ROCKY_GLACIES, ModBiomes.LUNAR_TERRAE, ModBiomes.LUNAR_ROCKY_TERRAE),
                GenerationStep.Feature.UNDERGROUND_ORES, LUNA_IRON_ORE_UNIFORM_SMALL_PLACED_KEY);

        //Copper
        BiomeModifications.addFeature(BiomeSelectors.includeByKey(ModBiomes.LUNAR_MARIA, ModBiomes.LUNAR_ROCKY_MARIA, ModBiomes.LUNAR_GLACIES, ModBiomes.LUNAR_ROCKY_GLACIES, ModBiomes.LUNAR_TERRAE, ModBiomes.LUNAR_ROCKY_TERRAE),
                GenerationStep.Feature.UNDERGROUND_ORES, LUNA_COPPER_ORE_SURFACE_PLACED_KEY);
        BiomeModifications.addFeature(BiomeSelectors.includeByKey(ModBiomes.LUNAR_MARIA, ModBiomes.LUNAR_ROCKY_MARIA, ModBiomes.LUNAR_GLACIES, ModBiomes.LUNAR_ROCKY_GLACIES, ModBiomes.LUNAR_TERRAE, ModBiomes.LUNAR_ROCKY_TERRAE),
                GenerationStep.Feature.UNDERGROUND_ORES, LUNA_COPPER_ORE_UNIFORM_PLACED_KEY);
        BiomeModifications.addFeature(BiomeSelectors.includeByKey(ModBiomes.LUNAR_MARIA, ModBiomes.LUNAR_ROCKY_MARIA, ModBiomes.LUNAR_GLACIES, ModBiomes.LUNAR_ROCKY_GLACIES, ModBiomes.LUNAR_TERRAE, ModBiomes.LUNAR_ROCKY_TERRAE),
                GenerationStep.Feature.UNDERGROUND_ORES, LUNA_COPPER_ORE_UNIFORM_SMALL_PLACED_KEY);

        //Gold
        BiomeModifications.addFeature(BiomeSelectors.includeByKey(ModBiomes.LUNAR_MARIA, ModBiomes.LUNAR_ROCKY_MARIA, ModBiomes.LUNAR_GLACIES, ModBiomes.LUNAR_ROCKY_GLACIES, ModBiomes.LUNAR_TERRAE, ModBiomes.LUNAR_ROCKY_TERRAE),
                GenerationStep.Feature.UNDERGROUND_ORES, LUNA_GOLD_ORE_MAIN_PLACED_KEY);
        BiomeModifications.addFeature(BiomeSelectors.includeByKey(ModBiomes.LUNAR_MARIA, ModBiomes.LUNAR_ROCKY_MARIA, ModBiomes.LUNAR_GLACIES, ModBiomes.LUNAR_ROCKY_GLACIES, ModBiomes.LUNAR_TERRAE, ModBiomes.LUNAR_ROCKY_TERRAE),
                GenerationStep.Feature.UNDERGROUND_ORES, LUNA_GOLD_ORE_SURFACE_PLACED_KEY);
        BiomeModifications.addFeature(BiomeSelectors.includeByKey(ModBiomes.LUNAR_MARIA, ModBiomes.LUNAR_ROCKY_MARIA, ModBiomes.LUNAR_GLACIES, ModBiomes.LUNAR_ROCKY_GLACIES, ModBiomes.LUNAR_TERRAE, ModBiomes.LUNAR_ROCKY_TERRAE),
                GenerationStep.Feature.UNDERGROUND_ORES, LUNA_GOLD_ORE_UNIFORM_PLACED_KEY);

        //Zircon
        BiomeModifications.addFeature(BiomeSelectors.includeByKey(ModBiomes.LUNAR_MARIA, ModBiomes.LUNAR_ROCKY_MARIA, ModBiomes.LUNAR_GLACIES, ModBiomes.LUNAR_ROCKY_GLACIES, ModBiomes.LUNAR_TERRAE, ModBiomes.LUNAR_ROCKY_TERRAE),
                GenerationStep.Feature.UNDERGROUND_ORES, LUNA_ZIRCON_ORE_DEPTH_PLACED_KEY);
        BiomeModifications.addFeature(BiomeSelectors.includeByKey(ModBiomes.LUNAR_MARIA, ModBiomes.LUNAR_ROCKY_MARIA, ModBiomes.LUNAR_GLACIES, ModBiomes.LUNAR_ROCKY_GLACIES, ModBiomes.LUNAR_TERRAE, ModBiomes.LUNAR_ROCKY_TERRAE),
                GenerationStep.Feature.UNDERGROUND_ORES, LUNA_ZIRCON_ORE_UNIFORM_PLACED_KEY);

        //Uranium
        BiomeModifications.addFeature(BiomeSelectors.includeByKey(ModBiomes.LUNAR_MARIA, ModBiomes.LUNAR_ROCKY_MARIA, ModBiomes.LUNAR_GLACIES, ModBiomes.LUNAR_ROCKY_GLACIES, ModBiomes.LUNAR_TERRAE, ModBiomes.LUNAR_ROCKY_TERRAE),
                GenerationStep.Feature.UNDERGROUND_ORES, LUNA_URANIUM_ORE_UNIFORM_PLACED_KEY);
        BiomeModifications.addFeature(BiomeSelectors.includeByKey(ModBiomes.LUNAR_MARIA, ModBiomes.LUNAR_ROCKY_MARIA, ModBiomes.LUNAR_GLACIES, ModBiomes.LUNAR_ROCKY_GLACIES, ModBiomes.LUNAR_TERRAE, ModBiomes.LUNAR_ROCKY_TERRAE),
                GenerationStep.Feature.UNDERGROUND_ORES, LUNA_URANIUM_ORE_UNIFORM_SMALL_PLACED_KEY);
        BiomeModifications.addFeature(BiomeSelectors.includeByKey(ModBiomes.LUNAR_MARIA, ModBiomes.LUNAR_ROCKY_MARIA, ModBiomes.LUNAR_GLACIES, ModBiomes.LUNAR_ROCKY_GLACIES, ModBiomes.LUNAR_TERRAE, ModBiomes.LUNAR_ROCKY_TERRAE),
                GenerationStep.Feature.UNDERGROUND_ORES, LUNA_URANIUM_ORE_DEPTH_PLACED_KEY);
        BiomeModifications.addFeature(BiomeSelectors.includeByKey(ModBiomes.LUNAR_MARIA, ModBiomes.LUNAR_ROCKY_MARIA, ModBiomes.LUNAR_GLACIES, ModBiomes.LUNAR_ROCKY_GLACIES, ModBiomes.LUNAR_TERRAE, ModBiomes.LUNAR_ROCKY_TERRAE),
                GenerationStep.Feature.UNDERGROUND_ORES, LUNA_URANIUM_ORE_DEPTH_SMALL_PLACED_KEY);

        //Titanium
        BiomeModifications.addFeature(BiomeSelectors.includeByKey(ModBiomes.LUNAR_MARIA, ModBiomes.LUNAR_ROCKY_MARIA, ModBiomes.LUNAR_GLACIES, ModBiomes.LUNAR_ROCKY_GLACIES, ModBiomes.LUNAR_TERRAE, ModBiomes.LUNAR_ROCKY_TERRAE),
                GenerationStep.Feature.UNDERGROUND_ORES, LUNA_TITANIUM_ORE_SURFACE_PLACED_KEY);
        BiomeModifications.addFeature(BiomeSelectors.includeByKey(ModBiomes.LUNAR_MARIA, ModBiomes.LUNAR_ROCKY_MARIA, ModBiomes.LUNAR_GLACIES, ModBiomes.LUNAR_ROCKY_GLACIES, ModBiomes.LUNAR_TERRAE, ModBiomes.LUNAR_ROCKY_TERRAE),
                GenerationStep.Feature.UNDERGROUND_ORES, LUNA_TITANIUM_ORE_SPREAD_PLACED_KEY);
        BiomeModifications.addFeature(BiomeSelectors.includeByKey(ModBiomes.LUNAR_MARIA, ModBiomes.LUNAR_ROCKY_MARIA, ModBiomes.LUNAR_GLACIES, ModBiomes.LUNAR_ROCKY_GLACIES, ModBiomes.LUNAR_TERRAE, ModBiomes.LUNAR_ROCKY_TERRAE),
                GenerationStep.Feature.UNDERGROUND_ORES, LUNA_TITANIUM_ORE_SMALL_PLACED_KEY);
        BiomeModifications.addFeature(BiomeSelectors.includeByKey(ModBiomes.LUNAR_MARIA, ModBiomes.LUNAR_ROCKY_MARIA, ModBiomes.LUNAR_GLACIES, ModBiomes.LUNAR_ROCKY_GLACIES, ModBiomes.LUNAR_TERRAE, ModBiomes.LUNAR_ROCKY_TERRAE),
                GenerationStep.Feature.UNDERGROUND_ORES, LUNA_TITANIUM_ORE_MEDIUM_PLACED_KEY);
        BiomeModifications.addFeature(BiomeSelectors.includeByKey(ModBiomes.LUNAR_MARIA, ModBiomes.LUNAR_ROCKY_MARIA, ModBiomes.LUNAR_GLACIES, ModBiomes.LUNAR_ROCKY_GLACIES, ModBiomes.LUNAR_TERRAE, ModBiomes.LUNAR_ROCKY_TERRAE),
                GenerationStep.Feature.UNDERGROUND_ORES, LUNA_TITANIUM_ORE_LARGE_PLACED_KEY);

        //Ice
        BiomeModifications.addFeature(BiomeSelectors.includeByKey(ModBiomes.LUNAR_MARIA, ModBiomes.LUNAR_GLACIES, ModBiomes.LUNAR_ROCKY_GLACIES, ModBiomes.LUNAR_TERRAE),
                GenerationStep.Feature.UNDERGROUND_ORES, LUNA_ICE_PLACED_KEY);
        BiomeModifications.addFeature(BiomeSelectors.includeByKey(ModBiomes.LUNAR_MARIA, ModBiomes.LUNAR_GLACIES, ModBiomes.LUNAR_ROCKY_GLACIES, ModBiomes.LUNAR_TERRAE),
                GenerationStep.Feature.UNDERGROUND_ORES, LUNA_ICE_LARGE_PLACED_KEY);
        BiomeModifications.addFeature(BiomeSelectors.includeByKey(ModBiomes.LUNAR_GLACIES, ModBiomes.LUNAR_ROCKY_GLACIES),
                GenerationStep.Feature.UNDERGROUND_ORES, LUNA_ICE_GLACIES_PLACED_KEY);

        Circuitmod.LOGGER.info("All mod ores generation registered for Luna biomes");

    }
} 