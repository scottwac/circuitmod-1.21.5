package starduster.circuitmod.block.entity;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.entity.MissileEntity;
import starduster.circuitmod.screen.MissileControlScreenHandler;
import starduster.circuitmod.screen.ModScreenHandlers;

public class MissileControlBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory<ModScreenHandlers.MissileControlData> {
    public static final int PROPERTY_COUNT = 3;

    // Target coordinates for the missile
    private int targetX = 0;
    private int targetY = 64;
    private int targetZ = 0;
    
    // Reference to the missile entity currently on this block (if any)
    private java.util.UUID attachedMissileUuid = null;

    private final PropertyDelegate propertyDelegate = new PropertyDelegate() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> targetX;
                case 1 -> targetY;
                case 2 -> targetZ;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            // Property updates only travel from server -> client for this delegate.
            // No-op to avoid mutating server-side state accidentally.
        }

        @Override
        public int size() {
            return PROPERTY_COUNT;
        }
    };

    public MissileControlBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MISSILE_CONTROL_BLOCK_ENTITY, pos, state);
    }

    public static void tick(World world, BlockPos pos, BlockState state, MissileControlBlockEntity entity) {
        // The entity doesn't need much server-side ticking
        // Just check if the attached missile is still valid
        if (!world.isClient && entity.attachedMissileUuid != null) {
            Entity missile = ((ServerWorld) world).getEntity(entity.attachedMissileUuid);
            if (missile == null || missile.isRemoved()) {
                entity.attachedMissileUuid = null;
                entity.markDirty();
            }
        }
    }

    public int getTargetX() {
        return targetX;
    }

    public int getTargetY() {
        return targetY;
    }

    public int getTargetZ() {
        return targetZ;
    }

    public boolean hasMissile() {
        if (world == null || world.isClient) {
            return attachedMissileUuid != null;
        }
        if (attachedMissileUuid == null) {
            return false;
        }
        Entity missile = ((ServerWorld) world).getEntity(attachedMissileUuid);
        return missile != null && !missile.isRemoved();
    }

    /**
     * Update the target coordinates
     */
    public void updateTargetCoordinates(int x, int y, int z) {
        boolean changed = x != targetX || y != targetY || z != targetZ;
        this.targetX = x;
        this.targetY = y;
        this.targetZ = z;

        if (changed) {
            syncToClient();
            Circuitmod.LOGGER.info("[MISSILE-CONTROL] Updated target coordinates to ({}, {}, {})", x, y, z);
        }
    }

    /**
     * Attach a missile to this control block
     */
    public void attachMissile(MissileEntity missile) {
        this.attachedMissileUuid = missile.getUuid();
        missile.setControlBlock(pos);
        missile.setTargetPosition(new Vec3d(targetX + 0.5, targetY, targetZ + 0.5));
        markDirty();
        syncToClient();
        Circuitmod.LOGGER.info("[MISSILE-CONTROL] Attached missile {} to control block at {}", missile.getUuid(), pos);
    }

    /**
     * Fire the attached missile
     */
    public boolean fireMissile() {
        if (!hasMissile() || world == null || world.isClient) {
            return false;
        }

        Entity entity = ((ServerWorld) world).getEntity(attachedMissileUuid);
        if (entity instanceof MissileEntity missile) {
            // Update target coordinates one more time before firing
            missile.setTargetPosition(new Vec3d(targetX + 0.5, targetY, targetZ + 0.5));
            missile.launch();
            attachedMissileUuid = null;
            markDirty();
            syncToClient();
            Circuitmod.LOGGER.info("[MISSILE-CONTROL] Fired missile from control block at {}", pos);
            return true;
        }

        return false;
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        nbt.putInt("targetX", targetX);
        nbt.putInt("targetY", targetY);
        nbt.putInt("targetZ", targetZ);
        if (attachedMissileUuid != null) {
            nbt.putString("attachedMissileUuid", attachedMissileUuid.toString());
        }
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        targetX = nbt.getInt("targetX").orElse(0);
        targetY = nbt.getInt("targetY").orElse(64);
        targetZ = nbt.getInt("targetZ").orElse(0);
        if (nbt.contains("attachedMissileUuid")) {
            String uuidString = nbt.getString("attachedMissileUuid").orElse(null);
            attachedMissileUuid = uuidString != null ? java.util.UUID.fromString(uuidString) : null;
        } else {
            attachedMissileUuid = null;
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

    public PropertyDelegate getPropertyDelegate() {
        return propertyDelegate;
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
        return Text.translatable("block.circuitmod.missile_control_block");
    }

    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new MissileControlScreenHandler(syncId, playerInventory, this.pos, this.propertyDelegate);
    }

    @Override
    public ModScreenHandlers.MissileControlData getScreenOpeningData(ServerPlayerEntity player) {
        return new ModScreenHandlers.MissileControlData(this.pos);
    }
}

