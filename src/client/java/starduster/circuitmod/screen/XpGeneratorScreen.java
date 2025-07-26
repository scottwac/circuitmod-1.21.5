package starduster.circuitmod.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.client.MinecraftClient;
import starduster.circuitmod.Circuitmod;
import starduster.circuitmod.network.ClientNetworking;
import starduster.circuitmod.block.entity.XpGeneratorBlockEntity;

@Environment(EnvType.CLIENT)
public class XpGeneratorScreen extends HandledScreen<XpGeneratorScreenHandler> {
    private static final Identifier TEXTURE = Identifier.of(Circuitmod.MOD_ID, "textures/gui/machines/xp_generator_gui.png");

    private ButtonWidget collectButton;

    public XpGeneratorScreen(XpGeneratorScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 176;
        this.backgroundHeight = 166;
    }

    @Override
    protected void init() {
        super.init();
        
        // Add collect XP button
        this.collectButton = ButtonWidget.builder(Text.literal("Collect XP"), button -> {
            // Find the XP generator position similar to how quarry screen does it
            BlockPos pos = findXpGeneratorPosition();
            if (!pos.equals(BlockPos.ORIGIN)) {
                Circuitmod.LOGGER.info("[XP-GENERATOR-SCREEN] Collect XP button clicked! Position: {}", pos);
                ClientNetworking.sendCollectXpRequest(pos);
            } else {
                Circuitmod.LOGGER.error("[XP-GENERATOR-SCREEN] Could not find XP Generator position!");
            }
        })
        .dimensions((width - backgroundWidth) / 2 + 95, (height - backgroundHeight) / 2 + 35, 60, 20)
        .build();
        
        this.addDrawableChild(this.collectButton);
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        // Draw main GUI background
        context.drawTexture(RenderLayer::getGuiTextured, TEXTURE, this.x, this.y, 0, 0, this.backgroundWidth, this.backgroundHeight, 256, 256);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        // Draw title
        context.drawText(this.textRenderer, this.title, this.titleX, this.titleY, 4210752, false);
        
        // Draw XP information with better spacing
        int storedXp = this.handler.getStoredXp();
        int maxStoredXp = this.handler.getMaxStoredXp();
        
        // Draw stored XP
        Text xpText = Text.literal("XP: " + storedXp + "/" + maxStoredXp);
        context.drawText(this.textRenderer, xpText, 8, 35, 4210752, false);
        
        // Draw energy demand
        int energyDemand = this.handler.getEnergyDemand();
        Text energyText = Text.literal("Power: " + energyDemand + "/tick");
        context.drawText(this.textRenderer, energyText, 8, 47, 4210752, false);
        
        // Draw status
        Text statusText = this.handler.isPowered() ? 
            Text.literal("Status: Generating") : 
            Text.literal("Status: No Power");
        context.drawText(this.textRenderer, statusText, 8, 59, 4210752, false);
        
        // Draw generation progress as text
        int progress = this.handler.getGenerationProgress();
        int maxProgress = this.handler.getMaxProgress();
        int progressPercent = maxProgress > 0 ? (progress * 100) / maxProgress : 0;
        Text progressText = Text.literal("Progress: " + progressPercent + "%");
        context.drawText(this.textRenderer, progressText, 8, 71, 4210752, false);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(context, mouseX, mouseY);
        
        // Update collect button state
        if (this.collectButton != null) {
            int storedXp = this.handler.getStoredXp();
            this.collectButton.active = storedXp > 0;
        }
    }

    @Override
    protected void drawMouseoverTooltip(DrawContext context, int mouseX, int mouseY) {
        super.drawMouseoverTooltip(context, mouseX, mouseY);
        
        // Collect button tooltip
        if (this.collectButton != null && this.collectButton.isMouseOver(mouseX, mouseY)) {
            int storedXp = this.handler.getStoredXp();
            Text buttonTooltip = storedXp > 0 ? 
                Text.literal("Collect " + storedXp + " XP") :
                Text.literal("No XP to collect");
            context.drawTooltip(this.textRenderer, buttonTooltip, mouseX, mouseY);
        }
    }
    
    private BlockPos findXpGeneratorPosition() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            Circuitmod.LOGGER.warn("[XP-GENERATOR-SCREEN] Client player or world is null!");
            return BlockPos.ORIGIN;
        }
        
        // Search in a reasonable radius around the player for an XP generator with an open screen
        BlockPos playerPos = client.player.getBlockPos();
        int searchRadius = 16; // Search within 16 blocks
        
        for (int x = -searchRadius; x <= searchRadius; x++) {
            for (int y = -searchRadius; y <= searchRadius; y++) {
                for (int z = -searchRadius; z <= searchRadius; z++) {
                    BlockPos checkPos = playerPos.add(x, y, z);
                    if (client.world.getBlockEntity(checkPos) instanceof XpGeneratorBlockEntity) {
                        return checkPos;
                    }
                }
            }
        }
        
        Circuitmod.LOGGER.warn("[XP-GENERATOR-SCREEN] No XP Generator found within {} blocks of player at {}", searchRadius, playerPos);
        return BlockPos.ORIGIN;
    }
} 