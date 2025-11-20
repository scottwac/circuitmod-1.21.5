package starduster.circuitmod.block.entity;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import starduster.circuitmod.screen.ModScreenHandlers;
import starduster.circuitmod.screen.SatelliteControlScreenHandler;
import starduster.circuitmod.satellite.Satellite;
import starduster.circuitmod.satellite.SatelliteRegistry;
import starduster.circuitmod.util.SatelliteCommandHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class SatelliteControlBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory<ModScreenHandlers.SatelliteControlData> {
    private static final int MAX_HISTORY = 100; // Maximum number of lines to store
    
    // Command history (output lines)
    private final List<String> outputLines = new ArrayList<>();
    
    // Time when the block entity was created (for uptime tracking)
    private long creationTime = -1;

    // Satellite session state
    @Nullable
    private UUID connectedSatelliteId;
    @Nullable
    private String connectedAccessCode;

    // Active beam rendering
    @Nullable
    private BlockPos activeBeamTarget;
    private float activeBeamHeight;
    private float activeBeamRadius;

    public SatelliteControlBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SATELLITE_CONTROL_BLOCK_ENTITY, pos, state);
        this.outputLines.add("Satellite Control Terminal v1.0");
        this.outputLines.add("Type 'help' for available commands");
        this.outputLines.add("");
    }

    /**
     * Execute a command and add the result to the output
     */
    public void executeCommand(String command) {
        if (world == null || world.isClient) {
            return;
        }
        
        // Initialize creation time on first command if not set
        if (creationTime < 0) {
            creationTime = world.getTime();
        }
        
        // Add the command to output with prompt
        if (!command.trim().isEmpty()) {
            outputLines.add("> " + command);
        }
        
        // Execute the command
        SatelliteCommandHandler.CommandResult result = SatelliteCommandHandler.executeCommand(
            command,
            (ServerWorld) world,
            pos,
            creationTime,
            this
        );
        
        // Handle clear screen command
        if (result.clearScreen()) {
            outputLines.clear();
        } else if (result.output() != null && !result.output().isEmpty()) {
            // Add each line of output
            String[] lines = result.output().split("\n");
            for (String line : lines) {
                outputLines.add(line);
            }
        }
        
        // Add blank line after command output
        outputLines.add("");
        
        // Limit history size
        while (outputLines.size() > MAX_HISTORY) {
            outputLines.remove(0);
        }
        
        markDirty();
        syncToClient();
    }

    /**
     * Get the output lines for display
     */
    public List<String> getOutputLines() {
        return new ArrayList<>(outputLines);
    }
    
    /**
     * Append a line from asynchronous satellite events (e.g., progress updates)
     */
    public void appendOutputLine(String line) {
        if (line == null || line.isEmpty()) {
            return;
        }
        outputLines.add(line);
        while (outputLines.size() > MAX_HISTORY) {
            outputLines.remove(0);
        }
        syncToClient();
    }

    /**
     * Clear the output
     */
    public void clearOutput() {
        outputLines.clear();
        outputLines.add("Satellite Control Terminal v1.0");
        outputLines.add("Type 'help' for available commands");
        outputLines.add("");
        markDirty();
        syncToClient();
    }

    /**
     * Set the active beam for rendering
     */
    public void setActiveBeam(@Nullable BlockPos target, float height, float radius) {
        this.activeBeamTarget = target;
        this.activeBeamHeight = height;
        this.activeBeamRadius = radius;
        markDirty();
        syncToClient();
    }

    /**
     * Clear the active beam
     */
    public void clearActiveBeam() {
        setActiveBeam(null, 0, 0);
    }

    /**
     * Update just the beam target position (for dynamic mining tracking)
     */
    public void updateBeamTarget(@Nullable BlockPos target) {
        if (this.activeBeamTarget != null && target != null) {
            this.activeBeamTarget = target;
            // Recalculate height from target to bottom
            if (world != null) {
                this.activeBeamHeight = Math.max(32f, target.getY() - world.getBottomY() + 1);
            }
            markDirty();
            syncToClient();
        }
    }

    /**
     * Get the active beam target (for renderer)
     */
    @Nullable
    public BlockPos getActiveBeamTarget() {
        return activeBeamTarget;
    }

    /**
     * Get the active beam height (for renderer)
     */
    public float getActiveBeamHeight() {
        return activeBeamHeight;
    }

    /**
     * Get the active beam radius (for renderer)
     */
    public float getActiveBeamRadius() {
        return activeBeamRadius;
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        
        // Save output lines
        NbtList linesList = new NbtList();
        for (String line : outputLines) {
            linesList.add(NbtString.of(line));
        }
        nbt.put("OutputLines", linesList);
        nbt.putLong("CreationTime", creationTime);
        if (connectedSatelliteId != null) {
            nbt.putString("ConnectedSatelliteId", connectedSatelliteId.toString());
        }
        if (connectedAccessCode != null) {
            nbt.putString("ConnectedAccessCode", connectedAccessCode);
        }
        if (activeBeamTarget != null) {
            nbt.putLong("BeamTargetPos", activeBeamTarget.asLong());
            nbt.putFloat("BeamHeight", activeBeamHeight);
            nbt.putFloat("BeamRadius", activeBeamRadius);
        }
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        
        // Load output lines
        outputLines.clear();
        NbtList linesList = nbt.getListOrEmpty("OutputLines");
        for (int i = 0; i < linesList.size(); i++) {
            linesList.getString(i).ifPresent(outputLines::add);
        }
        
        // If no lines were loaded, add default
        if (outputLines.isEmpty()) {
            outputLines.add("Satellite Control Terminal v1.0");
            outputLines.add("Type 'help' for available commands");
            outputLines.add("");
        }
        
        creationTime = nbt.getLong("CreationTime", -1L);
        connectedSatelliteId = nbt.getString("ConnectedSatelliteId")
            .flatMap(str -> {
                if (str.isEmpty()) return java.util.Optional.empty();
                try {
                    return java.util.Optional.of(java.util.UUID.fromString(str));
                } catch (IllegalArgumentException ex) {
                    return java.util.Optional.empty();
                }
            })
            .orElse(null);
        connectedAccessCode = nbt.getString("ConnectedAccessCode").orElse(null);
        
        if (nbt.contains("BeamTargetPos")) {
            long posLong = nbt.getLong("BeamTargetPos").orElse(0L);
            if (posLong != 0L) {
                activeBeamTarget = BlockPos.fromLong(posLong);
                activeBeamHeight = nbt.getFloat("BeamHeight").orElse(0f);
                activeBeamRadius = nbt.getFloat("BeamRadius").orElse(0f);
            } else {
                activeBeamTarget = null;
                activeBeamHeight = 0;
                activeBeamRadius = 0;
            }
        } else {
            activeBeamTarget = null;
            activeBeamHeight = 0;
            activeBeamRadius = 0;
        }
    }

    @Nullable
    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        NbtCompound nbt = new NbtCompound();
        writeNbt(nbt, registries);
        return nbt;
    }

    private void syncToClient() {
        if (world == null) {
            return;
        }
        markDirty();
        if (world instanceof ServerWorld serverWorld) {
            world.updateListeners(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
            serverWorld.getChunkManager().markForUpdate(pos);
        }
    }

    @Override
    public Text getDisplayName() {
        return Text.translatable("block.circuitmod.satellite_control_block");
    }

    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new SatelliteControlScreenHandler(syncId, playerInventory, this.pos);
    }

    @Override
    public ModScreenHandlers.SatelliteControlData getScreenOpeningData(ServerPlayerEntity player) {
        return new ModScreenHandlers.SatelliteControlData(this.pos, new ArrayList<>(this.outputLines));
    }

    public SatelliteCommandHandler.CommandResult connectToSatellite(ServerWorld world, String accessCode) {
        if (world == null) {
            return new SatelliteCommandHandler.CommandResult("World not available.");
        }
        String sanitized = accessCode == null ? "" : accessCode.trim();
        if (sanitized.isEmpty()) {
            return new SatelliteCommandHandler.CommandResult("Usage: connect <access-code>");
        }
        Optional<Satellite> satellite = SatelliteRegistry.get(world).findByAccessCode(sanitized);
        if (satellite.isEmpty()) {
            return new SatelliteCommandHandler.CommandResult("Access denied. No satellite matches that code.");
        }
        this.connectedSatelliteId = satellite.get().getId();
        this.connectedAccessCode = sanitized;
        markDirty();
        return new SatelliteCommandHandler.CommandResult("Linked to " + satellite.get().formatLabel());
    }

    public SatelliteCommandHandler.CommandResult disconnectSatellite() {
        if (connectedSatelliteId == null) {
            return new SatelliteCommandHandler.CommandResult("No satellite link to disconnect.");
        }
        clearConnection();
        return new SatelliteCommandHandler.CommandResult("Satellite link terminated.");
    }

    public SatelliteCommandHandler.CommandResult describeSatellite(ServerWorld world) {
        if (world == null) {
            return new SatelliteCommandHandler.CommandResult("World not available.");
        }
        Optional<Satellite> satellite = resolveConnectedSatellite(world);
        if (satellite.isEmpty()) {
            return new SatelliteCommandHandler.CommandResult("No satellite connected. Use 'connect <code>'.");
        }
        return new SatelliteCommandHandler.CommandResult(satellite.get().describe());
    }

    public SatelliteCommandHandler.CommandResult executeSatelliteCommand(ServerWorld world, String commandName, String[] args) {
        if (world == null) {
            return new SatelliteCommandHandler.CommandResult("World not available.");
        }
        Optional<Satellite> satellite = resolveConnectedSatellite(world);
        if (satellite.isEmpty()) {
            return new SatelliteCommandHandler.CommandResult("No satellite connected. Use 'connect <code>'.");
        }
        Satellite.SatelliteResult result = satellite.get().executeSatelliteCommand(commandName, args, world, pos);
        String message = result.message();
        if (!result.success()) {
            message = "Satellite Error: " + message;
        }
        return new SatelliteCommandHandler.CommandResult(message);
    }

    public SatelliteCommandHandler.CommandResult deploySatellite(ServerWorld world, String type, String accessCode) {
        if (world == null) {
            return new SatelliteCommandHandler.CommandResult("World not available.");
        }
        if (type == null || type.isEmpty() || accessCode == null || accessCode.isEmpty()) {
            return new SatelliteCommandHandler.CommandResult("Usage: deploysat <type> <access-code>");
        }
        Optional<Satellite> satellite = SatelliteRegistry.get(world).createSatellite(type, accessCode);
        if (satellite.isEmpty()) {
            return new SatelliteCommandHandler.CommandResult("Failed to deploy satellite. Invalid type or duplicate access code.");
        }
        return new SatelliteCommandHandler.CommandResult("Launched " + satellite.get().formatLabel());
    }

    private Optional<Satellite> resolveConnectedSatellite(ServerWorld world) {
        if (connectedSatelliteId == null) {
            return Optional.empty();
        }
        Optional<Satellite> satellite = SatelliteRegistry.get(world).getSatellite(connectedSatelliteId);
        if (satellite.isEmpty()) {
            clearConnection();
        }
        return satellite;
    }

    /**
     * Check if this terminal is currently connected to a satellite
     */
    public boolean isConnectedToSatellite() {
        return connectedSatelliteId != null;
    }

    private void clearConnection() {
        this.connectedSatelliteId = null;
        this.connectedAccessCode = null;
        markDirty();
    }
}

