package starduster.circuitmod.worldgen.tree;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.foliage.FoliagePlacerType;
import starduster.circuitmod.Circuitmod;

public class ModFoliagePlacerTypes {
    public static final FoliagePlacerType<MegaFoliagePlacer> MEGA_FOLIAGE_PLACER = 
        Registry.register(Registries.FOLIAGE_PLACER_TYPE, 
            Identifier.of(Circuitmod.MOD_ID, "mega_foliage_placer"), 
            new FoliagePlacerType<>(MegaFoliagePlacer.CODEC));

    public static void initialize() {
        Circuitmod.LOGGER.info("Registering foliage placer types");
    }
} 