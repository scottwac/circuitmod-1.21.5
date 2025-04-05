package starduster.circuitmod.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import starduster.circuitmod.Circuitmod;

public class ModNetworking {
    /**
     * Initialize networking for the common module (server side)
     */
    public static void initialize() {
        Circuitmod.LOGGER.info("Initializing mod networking");
        
        // Register the payload type for server->client communication
        PayloadTypeRegistry.playS2C().register(QuarryMiningSpeedPayload.ID, QuarryMiningSpeedPayload.CODEC);
    }
    
    /**
     * Send a mining speed update to a player
     * 
     * @param player The player to send the update to
     * @param miningSpeed The mining speed in blocks per second
     * @param quarryPos The position of the quarry
     */
    public static void sendMiningSpeedUpdate(ServerPlayerEntity player, int miningSpeed, BlockPos quarryPos) {
        // Create the payload and send it
        Circuitmod.LOGGER.info("[SERVER] Sending mining speed packet to player " + 
            player.getName().getString() + ": " + miningSpeed + " blocks/sec for quarry at " + quarryPos);
            
        QuarryMiningSpeedPayload payload = new QuarryMiningSpeedPayload(miningSpeed, quarryPos);
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, payload);
    }
    
    /**
     * Payload for quarry mining speed updates
     */
    public record QuarryMiningSpeedPayload(int miningSpeed, BlockPos quarryPos) implements CustomPayload {
        // Define the ID for this payload type
        public static final CustomPayload.Id<QuarryMiningSpeedPayload> ID = 
            new CustomPayload.Id<>(Identifier.of(Circuitmod.MOD_ID, "quarry_mining_speed"));
        
        // Define the codec for serializing/deserializing the payload
        public static final PacketCodec<PacketByteBuf, QuarryMiningSpeedPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.INTEGER, QuarryMiningSpeedPayload::miningSpeed,
            BlockPos.PACKET_CODEC, QuarryMiningSpeedPayload::quarryPos,
            QuarryMiningSpeedPayload::new
        );
        
        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
} 