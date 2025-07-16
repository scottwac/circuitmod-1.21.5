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

    // Register the drill screen handler type
    public static final ScreenHandlerType<DrillScreenHandler> DRILL_SCREEN_HANDLER =
            Registry.register(
                    Registries.SCREEN_HANDLER,
                    Identifier.of(Circuitmod.MOD_ID, "drill_screen_handler"),
                    new ScreenHandlerType<>(DrillScreenHandler::new, FeatureSet.empty())
            );
    
    // Register bloomery screen handler
    public static final ScreenHandlerType<BloomeryScreenHandler> BLOOMERY_SCREEN_HANDLER = 
        Registry.register(
            Registries.SCREEN_HANDLER,
            Identifier.of(Circuitmod.MOD_ID, "bloomery"),
            new ScreenHandlerType<>(BloomeryScreenHandler::new, FeatureSet.empty())
        );
    
    public static final ScreenHandlerType<MassFabricatorScreenHandler> MASS_FABRICATOR_SCREEN_HANDLER = 
        Registry.register(
            Registries.SCREEN_HANDLER,
            Identifier.of(Circuitmod.MOD_ID, "mass_fabricator_screen_handler"),
            new ScreenHandlerType<>(MassFabricatorScreenHandler::new, FeatureSet.empty())
        );
    
    // Register reactor screen handler
    public static final ScreenHandlerType<ReactorScreenHandler> REACTOR_SCREEN_HANDLER = 
        Registry.register(
            Registries.SCREEN_HANDLER,
            Identifier.of(Circuitmod.MOD_ID, "reactor_screen_handler"),
            new ScreenHandlerType<>(ReactorScreenHandler::new, FeatureSet.empty())
        );
    
    public static void initialize() {
        Circuitmod.LOGGER.info("ModScreenHandlers initialized");
    }
} 