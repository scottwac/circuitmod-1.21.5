package starduster.circuitmod.client.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import starduster.circuitmod.entity.MissileEntity;
import starduster.circuitmod.entity.client.MissileEntityModel;

import java.util.Map;

@Environment(EnvType.CLIENT)
public class MissileEntityRenderer extends GeoEntityRenderer<MissileEntity, MissileEntityRenderer.MissileRenderState> {
    // Debug counter for logging once per second
    private int debugTickCounter = 0;

    public MissileEntityRenderer(EntityRendererFactory.Context context) {
        super(context, new MissileEntityModel());
        this.shadowRadius = 0.3F; // Small shadow for missile
    }

    @Override
    public MissileRenderState createRenderState() {
        return new MissileRenderState();
    }

    @Override
    public void updateRenderState(MissileEntity entity, MissileRenderState state, float tickDelta) {
        super.updateRenderState(entity, state, tickDelta);
        // Transfer entity rotation to render state for proper model orientation
        state.pitch = entity.getPitch(tickDelta);
        state.yaw = entity.getYaw(tickDelta);
        state.addGeckolibData(DataTickets.ENTITY_PITCH, state.pitch);
        state.addGeckolibData(DataTickets.ENTITY_BODY_YAW, state.yaw);
        
        // Debug logging disabled
        debugTickCounter++;
        if (debugTickCounter >= 20) {
            debugTickCounter = 0;
            // Render printouts disabled
        }
    }

    /**
     * Override to apply pitch rotation for the missile
     * By default, GeckoLib only applies pitch for LivingEntities in special states
     * 
     * This aligns the missile model so its nose points in the direction of travel.
     * Uses standard Euler rotation: Y (yaw) then X (pitch).
     */
    @Override
    protected void applyRotations(MissileRenderState renderState, MatrixStack poseStack, float nativeScale) {
        float rotationYaw = renderState.yaw;
        float pitch = renderState.pitch;
        
        // Apply rotations in order: yaw first, then pitch
        poseStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotationYaw));
        poseStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
    }

    public static class MissileRenderState extends EntityRenderState implements GeoRenderState {
        private final GeoRenderState delegate = new GeoRenderState.Impl();
        public float pitch;
        public float yaw;

        @Override
        public <D> void addGeckolibData(DataTicket<D> dataTicket, @Nullable D data) {
            delegate.addGeckolibData(dataTicket, data);
        }

        @Override
        public boolean hasGeckolibData(DataTicket<?> dataTicket) {
            return delegate.hasGeckolibData(dataTicket);
        }

        @Nullable
        @Override
        public <D> D getGeckolibData(DataTicket<D> dataTicket) {
            return delegate.getGeckolibData(dataTicket);
        }

        @Override
        public Map<DataTicket<?>, Object> getDataMap() {
            return delegate.getDataMap();
        }
    }
}

