package starduster.circuitmod.screen;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;
import net.minecraft.resource.featuretoggle.FeatureSet;
import starduster.circuitmod.Circuitmod;

public class ModScreenHandlers {
    // Register the quarry screen handler type
    public static final ScreenHandlerType<QuarryScreenHandler> QUARRY_SCREEN_HANDLER = 
        Registry.register(
            Registries.SCREEN_HANDLER,
            Identifier.of(Circuitmod.MOD_ID, "quarry_screen_handler"),
            new ScreenHandlerType<>(QuarryScreenHandler::new, FeatureSet.empty())
        );
    
    public static void initialize() {
        Circuitmod.LOGGER.info("ModScreenHandlers initialized");
    }
} 