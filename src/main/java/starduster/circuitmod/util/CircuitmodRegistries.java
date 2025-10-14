package starduster.circuitmod.util;

import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.fabricmc.fabric.api.registry.StrippableBlockRegistry;
import net.minecraft.block.FireBlock;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.block.ModBlocks;

public class CircuitmodRegistries {
    public void onInitialize() {

    }

    public static void initialize() {
        Circuitmod.LOGGER.info("CircuitmodRegistries initialized");

        /**
         * Sharinga Wood
         */
        StrippableBlockRegistry.register(ModBlocks.SHARINGA_LOG, ModBlocks.STRIPPED_SHARINGA_LOG);
        StrippableBlockRegistry.register(ModBlocks.SHARINGA_WOOD, ModBlocks.STRIPPED_SHARINGA_WOOD);
        FlammableBlockRegistry.getDefaultInstance().add(ModBlocks.SHARINGA_LOG, 5,5);
        FlammableBlockRegistry.getDefaultInstance().add(ModBlocks.SHARINGA_WOOD, 5,5);
        FlammableBlockRegistry.getDefaultInstance().add(ModBlocks.STRIPPED_SHARINGA_LOG, 5,5);
        FlammableBlockRegistry.getDefaultInstance().add(ModBlocks.STRIPPED_SHARINGA_WOOD, 5,5);
        FlammableBlockRegistry.getDefaultInstance().add(ModBlocks.SHARINGA_PLANKS, 5,20);
        FlammableBlockRegistry.getDefaultInstance().add(ModBlocks.SHARINGA_LEAVES, 30,60);
    }
}
