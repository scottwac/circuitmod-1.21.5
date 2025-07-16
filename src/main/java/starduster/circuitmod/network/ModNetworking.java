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
        
        // Register the payload type for client->server communication
        PayloadTypeRegistry.playC2S().register(ToggleMiningPayload.ID, ToggleMiningPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(QuarryDimensionsPayload.ID, QuarryDimensionsPayload.CODEC);
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
  
    

} 