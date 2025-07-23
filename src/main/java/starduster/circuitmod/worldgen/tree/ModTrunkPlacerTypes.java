package starduster.circuitmod.worldgen.tree;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.trunk.TrunkPlacerType;
import starduster.circuitmod.Circuitmod;

public class ModTrunkPlacerTypes {
    public static final TrunkPlacerType<MegaTrunkPlacer> MEGA_TRUNK_PLACER = 
        Registry.register(Registries.TRUNK_PLACER_TYPE, 
            Identifier.of(Circuitmod.MOD_ID, "mega_trunk_placer"), 
            new TrunkPlacerType<>(MegaTrunkPlacer.CODEC));

    public static void initialize() {
        Circuitmod.LOGGER.info("Registering trunk placer types");
    }
} 