package starduster.circuitmod.screen;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandlerType;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.util.Identifier;
import net.minecraft.resource.featuretoggle.FeatureSet;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.math.BlockPos;
import starduster.circuitmod.Circuitmod;

public class ModScreenHandlers {
    // Data class for reactor screen opening data
    public record ReactorData(BlockPos pos) {
        public static final PacketCodec<RegistryByteBuf, ReactorData> PACKET_CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC,
            ReactorData::pos,
            ReactorData::new
        );
    }
    
    // Data class for quarry screen opening data
    public record QuarryData(BlockPos pos) {
        public static final PacketCodec<RegistryByteBuf, QuarryData> PACKET_CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC,
            QuarryData::pos,
            QuarryData::new
        );
    }
    
    // Data class for drill screen opening data
    public record DrillData(BlockPos pos) {
        public static final PacketCodec<RegistryByteBuf, DrillData> PACKET_CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC,
            DrillData::pos,
            DrillData::new
        );
    }
    
    // Data class for blueprint desk screen opening data
    public record BlueprintDeskData(BlockPos pos) {
        public static final PacketCodec<RegistryByteBuf, BlueprintDeskData> PACKET_CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC,
            BlueprintDeskData::pos,
            BlueprintDeskData::new
        );
    }
    
    // Data class for constructor screen opening data
    public record ConstructorData(BlockPos pos) {
        public static final PacketCodec<RegistryByteBuf, ConstructorData> PACKET_CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC,
            ConstructorData::pos,
            ConstructorData::new
        );
    }
    // Register the quarry screen handler type
    public static final ExtendedScreenHandlerType<QuarryScreenHandler, QuarryData> QUARRY_SCREEN_HANDLER = 
        Registry.register(
            Registries.SCREEN_HANDLER,
            Identifier.of(Circuitmod.MOD_ID, "quarry_screen_handler"),
            new ExtendedScreenHandlerType<>((syncId, inventory, data) -> new QuarryScreenHandler(syncId, inventory, data), QuarryData.PACKET_CODEC)
        );

    // Register the drill screen handler type
    public static final ExtendedScreenHandlerType<DrillScreenHandler, DrillData> DRILL_SCREEN_HANDLER = 
        Registry.register(
            Registries.SCREEN_HANDLER,
            Identifier.of(Circuitmod.MOD_ID, "drill_screen_handler"),
            new ExtendedScreenHandlerType<>((syncId, inventory, data) -> new DrillScreenHandler(syncId, inventory, data), DrillData.PACKET_CODEC)
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
    public static final ExtendedScreenHandlerType<ReactorScreenHandler, ReactorData> REACTOR_SCREEN_HANDLER = 
        Registry.register(
            Registries.SCREEN_HANDLER,
            Identifier.of(Circuitmod.MOD_ID, "reactor_screen_handler"),
            new ExtendedScreenHandlerType<>((syncId, inventory, data) -> new ReactorScreenHandler(syncId, inventory, data), ReactorData.PACKET_CODEC)
        );
    
    // Register blueprint desk screen handler
    public static final ExtendedScreenHandlerType<BlueprintDeskScreenHandler, BlueprintDeskData> BLUEPRINT_DESK_SCREEN_HANDLER = 
        Registry.register(
            Registries.SCREEN_HANDLER,
            Identifier.of(Circuitmod.MOD_ID, "blueprint_desk_screen_handler"),
            new ExtendedScreenHandlerType<>((syncId, inventory, data) -> new BlueprintDeskScreenHandler(syncId, inventory), BlueprintDeskData.PACKET_CODEC)
        );
    
    // Register constructor screen handler
    public static final ExtendedScreenHandlerType<ConstructorScreenHandler, ConstructorData> CONSTRUCTOR_SCREEN_HANDLER = 
        Registry.register(
            Registries.SCREEN_HANDLER,
            Identifier.of(Circuitmod.MOD_ID, "constructor_screen_handler"),
            new ExtendedScreenHandlerType<>((syncId, inventory, data) -> new ConstructorScreenHandler(syncId, inventory, data), ConstructorData.PACKET_CODEC)
        );
    
    public static void initialize() {
        Circuitmod.LOGGER.info("ModScreenHandlers initialized");
    }
} 