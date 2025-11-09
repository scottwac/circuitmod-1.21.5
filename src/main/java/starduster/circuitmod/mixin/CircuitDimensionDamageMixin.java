package starduster.circuitmod.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import starduster.circuitmod.entity.CustomPlayerInventory;
import starduster.circuitmod.item.EmuSuitArmorItem;
import starduster.circuitmod.item.OxygenTankItem;

@Mixin(LivingEntity.class)
public class CircuitDimensionDamageMixin {

    private static final Identifier LUNA_DIMENSION_ID = Identifier.of("circuitmod", "luna");
    private static final int DAMAGE_INTERVAL = 60; // Damage every 3 seconds (60 ticks)
    private static final int OXYGEN_TICK_INTERVAL = 20; // Consume 1 oxygen per second
    private static final float DAMAGE_AMOUNT = 1.0f; // Half a heart of damage

    @Unique
    private int circuitmod$lunaDamageTimer = 0;

    @Unique
    private int circuitmod$lunaOxygenTimer = 0;

    @Inject(method = "tick", at = @At("HEAD"))
    private void circuitmod$applyLunaAtmosphereDamage(CallbackInfo ci) {
        LivingEntity livingEntity = (LivingEntity) (Object) this;
        World world = livingEntity.getWorld();

        // Only run on server side
        if (world.isClient) {
            return;
        }

        if (!world.getRegistryKey().getValue().equals(LUNA_DIMENSION_ID)) {
            circuitmod$resetTimers();
            return;
        }

        boolean shouldTakeDamage = true;

        if (livingEntity instanceof PlayerEntity player) {
            boolean hasFullSuit = circuitmod$isWearingFullEmuSuit(player);
            boolean hasOxygen = hasFullSuit && circuitmod$hasOxygen(player);
            shouldTakeDamage = !hasFullSuit || !hasOxygen;
        }

        if (shouldTakeDamage) {
            circuitmod$lunaDamageTimer++;

            if (circuitmod$lunaDamageTimer >= DAMAGE_INTERVAL && world instanceof ServerWorld serverWorld) {
                livingEntity.damage(serverWorld, world.getDamageSources().generic(), DAMAGE_AMOUNT);
                circuitmod$lunaDamageTimer = 0;
            }
        } else {
            circuitmod$lunaDamageTimer = 0;
        }
    }

    @Unique
    private void circuitmod$resetTimers() {
        circuitmod$lunaDamageTimer = 0;
        circuitmod$lunaOxygenTimer = 0;
    }

    @Unique
    private boolean circuitmod$isWearingFullEmuSuit(LivingEntity entity) {
        ItemStack helmet = entity.getEquippedStack(net.minecraft.entity.EquipmentSlot.HEAD);
        ItemStack chestplate = entity.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST);
        ItemStack leggings = entity.getEquippedStack(net.minecraft.entity.EquipmentSlot.LEGS);
        ItemStack boots = entity.getEquippedStack(net.minecraft.entity.EquipmentSlot.FEET);

        return helmet.getItem() instanceof EmuSuitArmorItem &&
               chestplate.getItem() instanceof EmuSuitArmorItem &&
               leggings.getItem() instanceof EmuSuitArmorItem &&
               boots.getItem() instanceof EmuSuitArmorItem;
    }

    @Unique
    private boolean circuitmod$hasOxygen(PlayerEntity player) {
        PlayerInventory inventory = player.getInventory();

        if (!(inventory instanceof CustomPlayerInventory customInv)) {
            return false;
        }

        ItemStack tank1 = customInv.getStack(CustomPlayerInventory.OXYGEN_TANK_1_SLOT);
        ItemStack tank2 = customInv.getStack(CustomPlayerInventory.OXYGEN_TANK_2_SLOT);

        if (circuitmod$hasOxygenInTank(tank1)) {
            return true;
        }

        if (circuitmod$hasOxygenInTank(tank2)) {
            return true;
        }

        circuitmod$lunaOxygenTimer = 0;
        return false;
    }

    @Unique
    private boolean circuitmod$hasOxygenInTank(ItemStack tank) {
        if (tank.isEmpty() || !(tank.getItem() instanceof OxygenTankItem oxygenTank)) {
            return false;
        }

        int currentOxygen = oxygenTank.getOxygen(tank);
        if (currentOxygen <= 0) {
            return false;
        }

        circuitmod$lunaOxygenTimer++;
        if (circuitmod$lunaOxygenTimer >= OXYGEN_TICK_INTERVAL) {
            oxygenTank.consumeOxygen(tank, 1);
            circuitmod$lunaOxygenTimer = 0;
        }

        return true;
    }
}