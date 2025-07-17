package starduster.circuitmod.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import starduster.circuitmod.Circuitmod;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;

import java.util.Map;

public class ModNetworking {
    /**
     * Initialize networking for the common module (server side)
     */
    public static void initialize() {
        Circuitmod.LOGGER.info("Initializing mod networking");
        
        // Register the payload type for server->client communication
        PayloadTypeRegistry.playS2C().register(QuarryMiningProgressPayload.ID, QuarryMiningProgressPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(MiningEnabledStatusPayload.ID, MiningEnabledStatusPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(QuarryDimensionsSyncPayload.ID, QuarryDimensionsSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ItemMovePayload.ID, ItemMovePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DrillMiningProgressPayload.ID, DrillMiningProgressPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DrillMiningEnabledPayload.ID, DrillMiningEnabledPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DrillDimensionsSyncPayload.ID, DrillDimensionsSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ConstructorBuildingStatusPayload.ID, ConstructorBuildingStatusPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ConstructorPowerStatusPayload.ID, ConstructorPowerStatusPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ConstructorStatusMessagePayload.ID, ConstructorStatusMessagePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BlueprintNameSyncPayload.ID, BlueprintNameSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ConstructorMaterialsSyncPayload.ID, ConstructorMaterialsSyncPayload.CODEC);
        
        // Register the payload type for client->server communication
        PayloadTypeRegistry.playC2S().register(ToggleMiningPayload.ID, ToggleMiningPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(QuarryDimensionsPayload.ID, QuarryDimensionsPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(DrillDimensionsPayload.ID, DrillDimensionsPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ConstructorBuildingPayload.ID, ConstructorBuildingPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(BlueprintNamePayload.ID, BlueprintNamePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(BlueprintNameRequestPayload.ID, BlueprintNameRequestPayload.CODEC);
    }
    

    
    /**
     * Send a mining progress update to a player
     * 
     * @param player The player to send the update to
     * @param miningProgress The mining progress (0-100)
     * @param miningPos The position being mined
     * @param quarryPos The position of the quarry
     */
    public static void sendMiningProgressUpdate(ServerPlayerEntity player, int miningProgress, BlockPos miningPos, BlockPos quarryPos) {
        // Create the payload and send it
        Circuitmod.LOGGER.info("[SERVER] Sending mining progress packet to player " + 
            player.getName().getString() + ": " + miningProgress + "% at " + miningPos + " for quarry at " + quarryPos);
            
        QuarryMiningProgressPayload payload = new QuarryMiningProgressPayload(miningProgress, miningPos, quarryPos);
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, payload);
    }
    
    /**
     * Send a mining enabled status update to a player
     * 
     * @param player The player to send the update to
     * @param enabled Whether mining is enabled
     * @param machinePos The position of the machine
     */
    public static void sendMiningEnabledStatus(ServerPlayerEntity player, boolean enabled, BlockPos machinePos) {
        Circuitmod.LOGGER.info("[SERVER] Sending mining enabled status to player " + 
            player.getName().getString() + ": " + enabled + " for machine at " + machinePos);
            
        MiningEnabledStatusPayload payload = new MiningEnabledStatusPayload(enabled, machinePos);
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, payload);
    }
    
    /**
     * Send quarry dimensions sync to a player
     * 
     * @param player The player to send the update to
     * @param width The width of the mining area
     * @param length The length of the mining area
     * @param quarryPos The position of the quarry
     */
    public static void sendQuarryDimensionsSync(ServerPlayerEntity player, int width, int length, BlockPos quarryPos) {
        Circuitmod.LOGGER.info("[SERVER] Sending quarry dimensions sync to player " + 
            player.getName().getString() + ": " + width + "x" + length + " for quarry at " + quarryPos);
            
        QuarryDimensionsSyncPayload payload = new QuarryDimensionsSyncPayload(quarryPos, width, length);
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, payload);
    }
    
    /**
     * Send an item move animation to a player
     * 
     * @param player The player to send the animation to
     * @param stack The item stack being moved
     * @param from The starting position
     * @param to The ending position
     * @param startTick The server tick when the animation starts
     * @param durationTicks The duration of the animation in ticks
     */
    public static void sendItemMoveAnimation(ServerPlayerEntity player, ItemStack stack, BlockPos from, BlockPos to, long startTick, int durationTicks) {
        Circuitmod.LOGGER.info("[SERVER] Sending item move animation to player " + 
            player.getName().getString() + ": " + stack.getItem().getName().getString() + " from " + from + " to " + to);
            
        ItemMovePayload payload = new ItemMovePayload(stack, from, to, startTick, durationTicks);
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, payload);
    }
    
    /**
     * Send a drill mining progress update to players
     * 
     * @param players The players to send the update to
     * @param miningProgress The mining progress (0-100)
     * @param miningPos The position being mined
     */
    public static void sendDrillMiningProgressUpdate(Iterable<ServerPlayerEntity> players, int miningProgress, BlockPos miningPos) {
        Circuitmod.LOGGER.info("[SERVER] Sending drill mining progress packet: {}% at {}", miningProgress, miningPos);
            
        DrillMiningProgressPayload payload = new DrillMiningProgressPayload(miningProgress, miningPos);
        for (ServerPlayerEntity player : players) {
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, payload);
        }
    }
    
    /**
     * Send a drill mining enabled status update to players
     * 
     * @param players The players to send the update to
     * @param enabled Whether mining is enabled
     */
    public static void sendDrillMiningEnabledUpdate(Iterable<ServerPlayerEntity> players, boolean enabled) {
        Circuitmod.LOGGER.info("[SERVER] Sending drill mining enabled status: {}", enabled);
            
        DrillMiningEnabledPayload payload = new DrillMiningEnabledPayload(enabled);
        for (ServerPlayerEntity player : players) {
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, payload);
        }
    }
    
    /**
     * Send drill dimensions sync to a player
     * 
     * @param player The player to send the update to
     * @param height The height of the mining area
     * @param width The width of the mining area
     */
    public static void sendDrillDimensionsUpdate(ServerPlayerEntity player, int height, int width) {
        Circuitmod.LOGGER.info("[SERVER] Sending drill dimensions sync: {}x{} to player {}", height, width, player.getName().getString());
            
        DrillDimensionsSyncPayload payload = new DrillDimensionsSyncPayload(height, width);
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, payload);
    }
    
    /**
     * Send drill dimensions sync to players
     * 
     * @param players The players to send the update to
     * @param height The height of the mining area
     * @param width The width of the mining area
     */
    public static void sendDrillDimensionsUpdate(Iterable<ServerPlayerEntity> players, int height, int width) {
        Circuitmod.LOGGER.info("[SERVER] Sending drill dimensions sync: {}x{}", height, width);
            
        DrillDimensionsSyncPayload payload = new DrillDimensionsSyncPayload(height, width);
        for (ServerPlayerEntity player : players) {
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, payload);
        }
    }
    
    /**
     * Send constructor building status update to a player
     * 
     * @param player The player to send the update to
     * @param constructorPos The position of the constructor
     * @param building Whether the constructor is building
     * @param hasBlueprint Whether the constructor has a blueprint
     */
    public static void sendConstructorBuildingStatusUpdate(ServerPlayerEntity player, BlockPos constructorPos, boolean building, boolean hasBlueprint) {
        Circuitmod.LOGGER.info("[SERVER] Sending constructor building status to player " + 
            player.getName().getString() + ": building=" + building + ", hasBlueprint=" + hasBlueprint + " for constructor at " + constructorPos);
            
        ConstructorBuildingStatusPayload payload = new ConstructorBuildingStatusPayload(constructorPos, building, hasBlueprint);
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, payload);
    }
    
    /**
     * Send constructor power status update to a player
     * 
     * @param player The player to send the update to
     * @param constructorPos The position of the constructor
     * @param hasPower Whether the constructor is receiving power
     */
    public static void sendConstructorPowerStatusUpdate(ServerPlayerEntity player, BlockPos constructorPos, boolean hasPower) {
        Circuitmod.LOGGER.info("[SERVER] Sending constructor power status to player " + 
            player.getName().getString() + ": hasPower=" + hasPower + " for constructor at " + constructorPos);
            
        ConstructorPowerStatusPayload payload = new ConstructorPowerStatusPayload(constructorPos, hasPower);
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, payload);
    }
    
    /**
     * Send constructor status message update to a player
     * 
     * @param player The player to send the update to
     * @param constructorPos The position of the constructor
     * @param message The status message
     */
    public static void sendConstructorStatusMessageUpdate(ServerPlayerEntity player, BlockPos constructorPos, String message) {
        Circuitmod.LOGGER.info("[SERVER] Sending constructor status message to player " + 
            player.getName().getString() + ": " + message + " for constructor at " + constructorPos);
            
        ConstructorStatusMessagePayload payload = new ConstructorStatusMessagePayload(constructorPos, message);
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, payload);
    }
    
    /**
     * Send blueprint name sync to client
     * 
     * @param player The player to send the update to
     * @param blueprintDeskPos The position of the blueprint desk
     * @param name The blueprint name
     */
    public static void sendBlueprintNameSync(ServerPlayerEntity player, BlockPos blueprintDeskPos, String name) {
        Circuitmod.LOGGER.info("[SERVER] Sending blueprint name sync to player " + 
            player.getName().getString() + ": name=" + name + " for blueprint desk at " + blueprintDeskPos);
            
        BlueprintNameSyncPayload payload = new BlueprintNameSyncPayload(blueprintDeskPos, name);
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, payload);
    }
    
    /**
     * Send constructor materials sync to client
     * 
     * @param player The player to send the update to
     * @param constructorPos The position of the constructor
     * @param required The required materials
     * @param available The available materials
     */
    public static void sendConstructorMaterialsSync(ServerPlayerEntity player, BlockPos constructorPos, Map<String, Integer> required, Map<String, Integer> available) {
        Circuitmod.LOGGER.info("[SERVER] Sending constructor materials sync to player " + 
            player.getName().getString() + ": required=" + required + ", available=" + available + " for constructor at " + constructorPos);
            
        ConstructorMaterialsSyncPayload payload = new ConstructorMaterialsSyncPayload(constructorPos, required, available);
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, payload);
    }

    
    /**
     * Payload for quarry mining progress updates
     */
    public record QuarryMiningProgressPayload(int miningProgress, BlockPos miningPos, BlockPos quarryPos) implements CustomPayload {
        // Define the ID for this payload type
        public static final CustomPayload.Id<QuarryMiningProgressPayload> ID = 
            new CustomPayload.Id<>(Identifier.of(Circuitmod.MOD_ID, "quarry_mining_progress"));
        
        // Define the codec for serializing/deserializing the payload
        public static final PacketCodec<PacketByteBuf, QuarryMiningProgressPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.INTEGER, QuarryMiningProgressPayload::miningProgress,
            BlockPos.PACKET_CODEC, QuarryMiningProgressPayload::miningPos,
            BlockPos.PACKET_CODEC, QuarryMiningProgressPayload::quarryPos,
            QuarryMiningProgressPayload::new
        );
        
        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
    
    /**
     * Payload for toggle mining button presses (client -> server)
     */
    public record ToggleMiningPayload(BlockPos machinePos) implements CustomPayload {
        // Define the ID for this payload type
        public static final CustomPayload.Id<ToggleMiningPayload> ID = 
            new CustomPayload.Id<>(Identifier.of(Circuitmod.MOD_ID, "toggle_mining"));
        
        // Define the codec for serializing/deserializing the payload
        public static final PacketCodec<PacketByteBuf, ToggleMiningPayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, ToggleMiningPayload::machinePos,
            ToggleMiningPayload::new
        );
        
        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
    
    /**
     * Payload for mining enabled status updates (server -> client)
     */
    public record MiningEnabledStatusPayload(boolean enabled, BlockPos machinePos) implements CustomPayload {
        // Define the ID for this payload type
        public static final CustomPayload.Id<MiningEnabledStatusPayload> ID = 
            new CustomPayload.Id<>(Identifier.of(Circuitmod.MOD_ID, "mining_enabled_status"));
        
        // Define the codec for serializing/deserializing the payload
        public static final PacketCodec<PacketByteBuf, MiningEnabledStatusPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.BOOLEAN, MiningEnabledStatusPayload::enabled,
            BlockPos.PACKET_CODEC, MiningEnabledStatusPayload::machinePos,
            MiningEnabledStatusPayload::new
        );
        
        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
    
    /**
     * Payload for quarry dimensions updates (client -> server)
     */
    public record QuarryDimensionsPayload(BlockPos quarryPos, int width, int length) implements CustomPayload {
        // Define the ID for this payload type
        public static final CustomPayload.Id<QuarryDimensionsPayload> ID = 
            new CustomPayload.Id<>(Identifier.of(Circuitmod.MOD_ID, "quarry_dimensions"));
        
        // Define the codec for serializing/deserializing the payload
        public static final PacketCodec<PacketByteBuf, QuarryDimensionsPayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, QuarryDimensionsPayload::quarryPos,
            PacketCodecs.INTEGER, QuarryDimensionsPayload::width,
            PacketCodecs.INTEGER, QuarryDimensionsPayload::length,
            QuarryDimensionsPayload::new
        );
        
        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
    
    /**
     * Payload for quarry dimensions sync (server -> client)
     */
    public record QuarryDimensionsSyncPayload(BlockPos quarryPos, int width, int length) implements CustomPayload {
        // Define the ID for this payload type
        public static final CustomPayload.Id<QuarryDimensionsSyncPayload> ID = 
            new CustomPayload.Id<>(Identifier.of(Circuitmod.MOD_ID, "quarry_dimensions_sync"));
        
        // Define the codec for serializing/deserializing the payload
        public static final PacketCodec<PacketByteBuf, QuarryDimensionsSyncPayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, QuarryDimensionsSyncPayload::quarryPos,
            PacketCodecs.INTEGER, QuarryDimensionsSyncPayload::width,
            PacketCodecs.INTEGER, QuarryDimensionsSyncPayload::length,
            QuarryDimensionsSyncPayload::new
        );
        
        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
    
    /**
     * Payload for drill mining progress updates (server -> client)
     */
    public record DrillMiningProgressPayload(int miningProgress, BlockPos miningPos) implements CustomPayload {
        // Define the ID for this payload type
        public static final CustomPayload.Id<DrillMiningProgressPayload> ID = 
            new CustomPayload.Id<>(Identifier.of(Circuitmod.MOD_ID, "drill_mining_progress"));
        
        // Define the codec for serializing/deserializing the payload
        public static final PacketCodec<PacketByteBuf, DrillMiningProgressPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.INTEGER, DrillMiningProgressPayload::miningProgress,
            BlockPos.PACKET_CODEC, DrillMiningProgressPayload::miningPos,
            DrillMiningProgressPayload::new
        );
        
        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
    
    /**
     * Payload for drill mining enabled status updates (server -> client)
     */
    public record DrillMiningEnabledPayload(boolean enabled) implements CustomPayload {
        // Define the ID for this payload type
        public static final CustomPayload.Id<DrillMiningEnabledPayload> ID = 
            new CustomPayload.Id<>(Identifier.of(Circuitmod.MOD_ID, "drill_mining_enabled"));
        
        // Define the codec for serializing/deserializing the payload
        public static final PacketCodec<PacketByteBuf, DrillMiningEnabledPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.BOOLEAN, DrillMiningEnabledPayload::enabled,
            DrillMiningEnabledPayload::new
        );
        
        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
    
        /**
     * Payload for drill dimensions sync (server -> client)
     */
    public record DrillDimensionsSyncPayload(int height, int width) implements CustomPayload {
        // Define the ID for this payload type
        public static final CustomPayload.Id<DrillDimensionsSyncPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Circuitmod.MOD_ID, "drill_dimensions_sync"));
        
        // Define the codec for serializing/deserializing the payload
        public static final PacketCodec<PacketByteBuf, DrillDimensionsSyncPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.INTEGER, DrillDimensionsSyncPayload::height,
            PacketCodecs.INTEGER, DrillDimensionsSyncPayload::width,
            DrillDimensionsSyncPayload::new
        );
        
        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
    
    /**
     * Payload for drill dimensions (client -> server)
     */
    public record DrillDimensionsPayload(BlockPos drillPos, int height, int width) implements CustomPayload {
        // Define the ID for this payload type
        public static final CustomPayload.Id<DrillDimensionsPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Circuitmod.MOD_ID, "drill_dimensions"));
        
        // Define the codec for serializing/deserializing the payload
        public static final PacketCodec<PacketByteBuf, DrillDimensionsPayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, DrillDimensionsPayload::drillPos,
            PacketCodecs.INTEGER, DrillDimensionsPayload::height,
            PacketCodecs.INTEGER, DrillDimensionsPayload::width,
            DrillDimensionsPayload::new
        );
        
        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * Payload for item pipe network animations (server -> client)
     */
    public record ItemMovePayload(ItemStack stack, BlockPos from, BlockPos to, long startTick, int durationTicks) implements CustomPayload {
        // Define the ID for this payload type
        public static final CustomPayload.Id<ItemMovePayload> ID = 
            new CustomPayload.Id<>(Identifier.of(Circuitmod.MOD_ID, "item_move"));
        
        // Use RegistryByteBuf for ItemStack serialization with proper registry lookup
        private static final PacketCodec<RegistryByteBuf, ItemStack> ITEM_STACK_CODEC = new PacketCodec<RegistryByteBuf, ItemStack>() {
            @Override
            public ItemStack decode(RegistryByteBuf buf) {
                return ItemStack.OPTIONAL_PACKET_CODEC.decode(buf);
            }
            
            @Override
            public void encode(RegistryByteBuf buf, ItemStack stack) {
                ItemStack.OPTIONAL_PACKET_CODEC.encode(buf, stack);
            }
        };
        
        // Define the codec for serializing/deserializing the payload
        public static final PacketCodec<RegistryByteBuf, ItemMovePayload> CODEC = PacketCodec.tuple(
            ITEM_STACK_CODEC, ItemMovePayload::stack,
            BlockPos.PACKET_CODEC, ItemMovePayload::from,
            BlockPos.PACKET_CODEC, ItemMovePayload::to,
            PacketCodecs.LONG, ItemMovePayload::startTick,
            PacketCodecs.INTEGER, ItemMovePayload::durationTicks,
            ItemMovePayload::new
        );
        
        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    
    public static final Identifier ENERGY_TO_MASS_SELECT_RESOURCE = Identifier.of(Circuitmod.MOD_ID, "energy_to_mass_select_resource");
    public static final Identifier ENERGY_TO_MASS_FIREWORK = Identifier.of(Circuitmod.MOD_ID, "energy_to_mass_firework");
    
    /**
     * Payload for constructor building toggle (client -> server)
     */
    public record ConstructorBuildingPayload(BlockPos constructorPos) implements CustomPayload {
        // Define the ID for this payload type
        public static final CustomPayload.Id<ConstructorBuildingPayload> ID = 
            new CustomPayload.Id<>(Identifier.of(Circuitmod.MOD_ID, "constructor_building"));
        
        // Define the codec for serializing/deserializing the payload
        public static final PacketCodec<PacketByteBuf, ConstructorBuildingPayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, ConstructorBuildingPayload::constructorPos,
            ConstructorBuildingPayload::new
        );
        
        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
    
    /**
     * Payload for constructor building status updates (server -> client)
     */
    public record ConstructorBuildingStatusPayload(BlockPos constructorPos, boolean building, boolean hasBlueprint) implements CustomPayload {
        // Define the ID for this payload type
        public static final CustomPayload.Id<ConstructorBuildingStatusPayload> ID = 
            new CustomPayload.Id<>(Identifier.of(Circuitmod.MOD_ID, "constructor_building_status"));
        
        // Define the codec for serializing/deserializing the payload
        public static final PacketCodec<PacketByteBuf, ConstructorBuildingStatusPayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, ConstructorBuildingStatusPayload::constructorPos,
            PacketCodecs.BOOLEAN, ConstructorBuildingStatusPayload::building,
            PacketCodecs.BOOLEAN, ConstructorBuildingStatusPayload::hasBlueprint,
            ConstructorBuildingStatusPayload::new
        );
        
        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
    
    /**
     * Payload for constructor power status updates (server -> client)
     */
    public record ConstructorPowerStatusPayload(BlockPos constructorPos, boolean hasPower) implements CustomPayload {
        // Define the ID for this payload type
        public static final CustomPayload.Id<ConstructorPowerStatusPayload> ID = 
            new CustomPayload.Id<>(Identifier.of(Circuitmod.MOD_ID, "constructor_power_status"));
        
        // Define the codec for serializing/deserializing the payload
        public static final PacketCodec<PacketByteBuf, ConstructorPowerStatusPayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, ConstructorPowerStatusPayload::constructorPos,
            PacketCodecs.BOOLEAN, ConstructorPowerStatusPayload::hasPower,
            ConstructorPowerStatusPayload::new
        );
        
        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
    
    /**
     * Payload for constructor status message updates (server -> client)
     */
    public record ConstructorStatusMessagePayload(BlockPos constructorPos, String message) implements CustomPayload {
        // Define the ID for this payload type
        public static final CustomPayload.Id<ConstructorStatusMessagePayload> ID = 
            new CustomPayload.Id<>(Identifier.of(Circuitmod.MOD_ID, "constructor_status_message"));
        
        // Define the codec for serializing/deserializing the payload
        public static final PacketCodec<PacketByteBuf, ConstructorStatusMessagePayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, ConstructorStatusMessagePayload::constructorPos,
            PacketCodecs.STRING, ConstructorStatusMessagePayload::message,
            ConstructorStatusMessagePayload::new
        );
        
        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
    
    /**
     * Payload for blueprint name update (client -> server)
     */
    public record BlueprintNamePayload(BlockPos blueprintDeskPos, String name) implements CustomPayload {
        // Define the ID for this payload type
        public static final CustomPayload.Id<BlueprintNamePayload> ID = 
            new CustomPayload.Id<>(Identifier.of(Circuitmod.MOD_ID, "blueprint_name"));
        
        // Define the codec for serializing/deserializing the payload
        public static final PacketCodec<PacketByteBuf, BlueprintNamePayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, BlueprintNamePayload::blueprintDeskPos,
            PacketCodecs.STRING, BlueprintNamePayload::name,
            BlueprintNamePayload::new
        );
        
        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
    
    /**
     * Payload for blueprint name sync (server -> client)
     */
    public record BlueprintNameSyncPayload(BlockPos blueprintDeskPos, String name) implements CustomPayload {
        // Define the ID for this payload type
        public static final CustomPayload.Id<BlueprintNameSyncPayload> ID = 
            new CustomPayload.Id<>(Identifier.of(Circuitmod.MOD_ID, "blueprint_name_sync"));
        
        // Define the codec for serializing/deserializing the payload
        public static final PacketCodec<PacketByteBuf, BlueprintNameSyncPayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, BlueprintNameSyncPayload::blueprintDeskPos,
            PacketCodecs.STRING, BlueprintNameSyncPayload::name,
            BlueprintNameSyncPayload::new
        );
        
        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
    
    /**
     * Payload for blueprint name request (client -> server)
     */
    public record BlueprintNameRequestPayload(BlockPos blueprintDeskPos) implements CustomPayload {
        // Define the ID for this payload type
        public static final CustomPayload.Id<BlueprintNameRequestPayload> ID = 
            new CustomPayload.Id<>(Identifier.of(Circuitmod.MOD_ID, "blueprint_name_request"));
        
        // Define the codec for serializing/deserializing the payload
        public static final PacketCodec<PacketByteBuf, BlueprintNameRequestPayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, BlueprintNameRequestPayload::blueprintDeskPos,
            BlueprintNameRequestPayload::new
        );
        
        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * Payload for constructor materials sync (server -> client)
     */
    public record ConstructorMaterialsSyncPayload(BlockPos constructorPos, Map<String, Integer> required, Map<String, Integer> available) implements CustomPayload {
        public static final CustomPayload.Id<ConstructorMaterialsSyncPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Circuitmod.MOD_ID, "constructor_materials_sync"));

        public static final PacketCodec<PacketByteBuf, ConstructorMaterialsSyncPayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, ConstructorMaterialsSyncPayload::constructorPos,
            PacketCodecs.map(java.util.HashMap::new, PacketCodecs.STRING, PacketCodecs.INTEGER), ConstructorMaterialsSyncPayload::required,
            PacketCodecs.map(java.util.HashMap::new, PacketCodecs.STRING, PacketCodecs.INTEGER), ConstructorMaterialsSyncPayload::available,
            ConstructorMaterialsSyncPayload::new
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
} 