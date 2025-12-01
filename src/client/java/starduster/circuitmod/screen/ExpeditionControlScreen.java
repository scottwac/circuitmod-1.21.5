package starduster.circuitmod.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;
import starduster.circuitmod.expedition.ExpeditionDestination;
import starduster.circuitmod.network.ClientNetworking;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

public class ExpeditionControlScreen extends HandledScreen<ExpeditionControlScreenHandler> {
    private static final int PANEL_WIDTH = 300;
    private static final int PANEL_HEIGHT = 220;
    private static final int CONSOLE_HEIGHT = 160;
    private static final int LINE_HEIGHT = 9;
    private static final float TEXT_SCALE = 0.85f;
    
    // Progress bar dimensions
    private static final int BAR_WIDTH = 200;
    private static final int BAR_HEIGHT = 16;
    private static final int BAR_SPACING = 35;
    
    // Decision button dimensions
    private static final int BUTTON_WIDTH = 30;
    private static final int BUTTON_HEIGHT = 14;
    
    private List<String> outputLines = new ArrayList<>();
    private List<String> commandHistory = new ArrayList<>();
    private int historyIndex = -1;
    private int scrollOffset = 0;
    private String currentInput = "";
    private int storedFuel = 0;
    private boolean monitorMode = false;
    
    // Destination picker mode
    private boolean destinationPickerMode = false;
    private int selectedDestinationIndex = 0;
    private int destinationScrollOffset = 0;
    
    // Monitor mode notification typing
    private List<String> typingNotifications = new ArrayList<>();
    private int typingCharIndex = 0;
    private long lastTypingTime = 0;
    private static final long TYPING_DELAY_MS = 30;
    
    // Track which expeditions we've notified about (for sounds)
    private Set<String> notifiedExpeditions = new HashSet<>();
    private Set<String> completedExpeditions = new HashSet<>();
    
    // Store decision button positions for click detection
    private List<DecisionButtonInfo> decisionButtons = new ArrayList<>();
    
    // Destination button positions for click detection
    private List<DestinationButtonInfo> destinationButtons = new ArrayList<>();
    
    // Expedition bar click areas for detail view
    private List<ExpeditionBarInfo> expeditionBars = new ArrayList<>();
    
    // Selected expedition for detail view (shows event info)
    private String selectedExpeditionId = null;
    private ExpeditionDisplayData selectedExpeditionData = null;
    
    // Auto-refresh timer (works in both modes now)
    private long lastRefreshTime = 0;
    private static final long REFRESH_INTERVAL_MS = 2000; // Refresh every 2 seconds
    
    // Sound cooldown to prevent spam
    private long lastSoundTime = 0;
    private static final long SOUND_COOLDOWN_MS = 3000;
    
    public ExpeditionControlScreen(ExpeditionControlScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = PANEL_WIDTH;
        this.backgroundHeight = PANEL_HEIGHT;
        this.playerInventoryTitleY = 10000; // Hide player inventory title
        
        // Initialize from handler
        this.outputLines = new ArrayList<>(handler.getOutputLines());
        this.storedFuel = handler.getStoredFuel();
        this.monitorMode = handler.isMonitorMode();
    }
    
    // Helper record for tracking decision button positions
    private record DecisionButtonInfo(int x, int y, int width, int height, String expeditionId, boolean isChoiceB) {}
    
    // Helper record for tracking destination button positions
    private record DestinationButtonInfo(int x, int y, int width, int height, ExpeditionDestination destination) {}
    
    // Helper record for tracking expedition bar click areas
    private record ExpeditionBarInfo(int x, int y, int width, int height, ExpeditionDisplayData data) {}

    @Override
    protected void init() {
        super.init();
        titleX = (backgroundWidth - textRenderer.getWidth(title)) / 2;
        titleY = 7;

        // Scroll to bottom
        scrollToBottom();
        
        // If in monitor mode, request status update to populate expedition data
        if (monitorMode) {
            lastRefreshTime = System.currentTimeMillis();
            requestStatusUpdate();
        }
    }
    
    private void requestStatusUpdate() {
        BlockPos pos = handler.getBlockEntityPos();
        if (pos != null && client != null) {
            ClientNetworking.sendExpeditionCommand(pos, "status");
        }
    }
    
    private void checkAutoRefresh() {
        // Auto-refresh only in monitor mode (terminal mode would spam the output)
        if (!monitorMode) return;
        
        long now = System.currentTimeMillis();
        if (now - lastRefreshTime >= REFRESH_INTERVAL_MS) {
            lastRefreshTime = now;
            requestStatusUpdate();
        }
    }
    
    private void checkForSoundNotifications(List<ExpeditionDisplayData> expeditions) {
        if (client == null) return;
        
        long now = System.currentTimeMillis();
        boolean shouldPlaySound = false;
        
        for (ExpeditionDisplayData exp : expeditions) {
            // Check for new decisions needed
            if (exp.needsDecision && !notifiedExpeditions.contains(exp.expeditionId)) {
                notifiedExpeditions.add(exp.expeditionId);
                shouldPlaySound = true;
            }
            
            // Check for newly completed expeditions
            if (exp.isComplete && !completedExpeditions.contains(exp.expeditionId)) {
                completedExpeditions.add(exp.expeditionId);
                shouldPlaySound = true;
            }
        }
        
        // Play sound if needed and not on cooldown
        if (shouldPlaySound && now - lastSoundTime >= SOUND_COOLDOWN_MS) {
            lastSoundTime = now;
            client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), 1.0F));
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Handle escape in all modes
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (selectedExpeditionId != null) {
                // Close decision detail view
                selectedExpeditionId = null;
                selectedExpeditionData = null;
                return true;
            }
            if (destinationPickerMode) {
                destinationPickerMode = false;
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        
        // In decision detail view, A/B keys make choice
        if (selectedExpeditionId != null) {
            if (keyCode == GLFW.GLFW_KEY_A) {
                sendCommand("decide " + selectedExpeditionId + " a");
                selectedExpeditionId = null;
                selectedExpeditionData = null;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_B) {
                sendCommand("decide " + selectedExpeditionId + " b");
                selectedExpeditionId = null;
                selectedExpeditionData = null;
                return true;
            }
            // Any other key closes the detail view
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE || keyCode == GLFW.GLFW_KEY_ENTER) {
                selectedExpeditionId = null;
                selectedExpeditionData = null;
                return true;
            }
            return true;
        }
        
        // In destination picker mode
        if (destinationPickerMode) {
            ExpeditionDestination[] destinations = ExpeditionDestination.values();
            
            if (keyCode == GLFW.GLFW_KEY_UP) {
                selectedDestinationIndex = Math.max(0, selectedDestinationIndex - 1);
                // Scroll up if needed
                if (selectedDestinationIndex < destinationScrollOffset) {
                    destinationScrollOffset = selectedDestinationIndex;
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DOWN) {
                selectedDestinationIndex = Math.min(destinations.length - 1, selectedDestinationIndex + 1);
                // Scroll down if needed (max 5 visible)
                if (selectedDestinationIndex >= destinationScrollOffset + 5) {
                    destinationScrollOffset = selectedDestinationIndex - 4;
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                // Launch expedition to selected destination
                ExpeditionDestination selected = destinations[selectedDestinationIndex];
                sendCommand("launch " + selected.getDisplayName());
                destinationPickerMode = false;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE || keyCode == GLFW.GLFW_KEY_T) {
                destinationPickerMode = false;
                return true;
            }
            return true;
        }
        
        // In monitor mode, handle 'T' or Enter to exit
        if (monitorMode) {
            if (keyCode == GLFW.GLFW_KEY_T || keyCode == GLFW.GLFW_KEY_ENTER) {
                sendCommand("terminal");
                monitorMode = false;
                handler.setMonitorMode(false);
            }
            return true;
        }
        
        // Handle Enter key to submit command
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (!currentInput.isEmpty()) {
                String trimmedInput = currentInput.trim().toLowerCase();
                
                // Check if command switches to monitor mode
                if (trimmedInput.equals("monitor")) {
                    monitorMode = true;
                    handler.setMonitorMode(true);
                }
                
                // Check if command opens destination picker
                if (trimmedInput.equals("launch") || trimmedInput.equals("destinations") || trimmedInput.equals("dest")) {
                    destinationPickerMode = true;
                    selectedDestinationIndex = 0;
                    destinationScrollOffset = 0;
                    // Still send the command to update terminal
                    sendCommand(currentInput);
                    commandHistory.add(currentInput);
                    historyIndex = commandHistory.size();
                    currentInput = "";
                    return true;
                }
                
                sendCommand(currentInput);
                
                // Add to command history
                commandHistory.add(currentInput);
                historyIndex = commandHistory.size();
                
                currentInput = "";
            }
            return true;
        }
        
        // Handle Up arrow for command history (previous)
        if (keyCode == GLFW.GLFW_KEY_UP) {
            if (!commandHistory.isEmpty() && historyIndex > 0) {
                historyIndex--;
                currentInput = commandHistory.get(historyIndex);
            } else if (commandHistory.isEmpty()) {
                currentInput = "";
            }
            return true;
        }
        
        // Handle Down arrow for command history (next)
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            if (!commandHistory.isEmpty()) {
                historyIndex++;
                if (historyIndex >= commandHistory.size()) {
                    historyIndex = commandHistory.size();
                    currentInput = "";
                } else {
                    currentInput = commandHistory.get(historyIndex);
                }
            }
            return true;
        }
        
        // Handle Backspace
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (!currentInput.isEmpty()) {
                currentInput = currentInput.substring(0, currentInput.length() - 1);
            }
            return true;
        }
        
        // Handle scrolling with Page Up/Page Down
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            scrollOffset = Math.max(0, scrollOffset - getMaxRenderableLines());
            return true;
        }
        
        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            scrollOffset = Math.min(Math.max(0, outputLines.size() - 1), scrollOffset + getMaxRenderableLines());
            return true;
        }
        
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (destinationPickerMode) {
            // Scroll destination list
            ExpeditionDestination[] destinations = ExpeditionDestination.values();
            int scrollAmount = (int) (-verticalAmount);
            destinationScrollOffset = Math.max(0, Math.min(Math.max(0, destinations.length - 5), destinationScrollOffset + scrollAmount));
            return true;
        }
        
        // Scroll the console output
        int scrollAmount = (int) (-verticalAmount * 3);
        scrollOffset = Math.max(0, Math.min(Math.max(0, outputLines.size() - 1), scrollOffset + scrollAmount));
        return true;
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        
        int left = (width - backgroundWidth) / 2;
        int top = (height - backgroundHeight) / 2;
        
        // Check destination picker button clicks
        if (destinationPickerMode) {
            for (DestinationButtonInfo destBtn : destinationButtons) {
                if (mouseX >= destBtn.x && mouseX <= destBtn.x + destBtn.width &&
                    mouseY >= destBtn.y && mouseY <= destBtn.y + destBtn.height) {
                    // Check if player has enough fuel
                    if (storedFuel >= destBtn.destination.getBaseFuelCost()) {
                        sendCommand("launch " + destBtn.destination.getDisplayName());
                        destinationPickerMode = false;
                        playClickSound();
                    } else {
                        // Play error sound
                        if (client != null) {
                            client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.5F));
                        }
                    }
                    return true;
                }
            }
            
            // Check for close button or click outside
            if (mouseX < left || mouseX > left + backgroundWidth || mouseY < top || mouseY > top + backgroundHeight) {
                destinationPickerMode = false;
                return true;
            }
        }
        
        // Check for monitor mode interactions
        if (monitorMode) {
            // If in decision detail view, check for back click (click outside content area)
            if (selectedExpeditionId != null) {
                // Check decision button clicks first
                for (DecisionButtonInfo btn : decisionButtons) {
                    if (mouseX >= btn.x && mouseX <= btn.x + btn.width &&
                        mouseY >= btn.y && mouseY <= btn.y + btn.height) {
                        String choice = btn.isChoiceB ? "b" : "a";
                        sendCommand("decide " + btn.expeditionId + " " + choice);
                        selectedExpeditionId = null;
                        selectedExpeditionData = null;
                        playClickSound();
                        return true;
                    }
                }
                
                // Click anywhere else to go back to monitor list
                selectedExpeditionId = null;
                selectedExpeditionData = null;
                playClickSound();
                return true;
            }
            
            // Check expedition bar clicks (to open detail view)
            for (ExpeditionBarInfo barInfo : expeditionBars) {
                if (mouseX >= barInfo.x && mouseX <= barInfo.x + barInfo.width &&
                    mouseY >= barInfo.y && mouseY <= barInfo.y + barInfo.height) {
                    
                    // Only open detail view for expeditions needing decisions
                    if (barInfo.data.needsDecision) {
                        selectedExpeditionId = barInfo.data.expeditionId;
                        selectedExpeditionData = barInfo.data;
                        
                        // Request detailed info from server
                        BlockPos pos = handler.getBlockEntityPos();
                        if (pos != null) {
                            sendCommand("info " + barInfo.data.expeditionId);
                        }
                        
                        playClickSound();
                        return true;
                    }
                }
            }
            
            // Check decision button clicks (on the progress bars)
            for (DecisionButtonInfo btn : decisionButtons) {
                if (mouseX >= btn.x && mouseX <= btn.x + btn.width &&
                    mouseY >= btn.y && mouseY <= btn.y + btn.height) {
                    String choice = btn.isChoiceB ? "b" : "a";
                    sendCommand("decide " + btn.expeditionId + " " + choice);
                    playClickSound();
                    return true;
                }
            }
        }
        
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    private void playClickSound() {
        if (client != null) {
            client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F));
        }
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (!Character.isISOControl(chr)) {
            currentInput += chr;
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    private void sendCommand(String command) {
        BlockPos pos = handler.getBlockEntityPos();
        if (pos == null || client == null) {
            return;
        }
        
        // Add command to local output immediately for responsiveness
        outputLines.add("> " + command);
        scrollToBottom();
        
        // Send to server
        ClientNetworking.sendExpeditionCommand(pos, command);
    }

    private void scrollToBottom() {
        if (outputLines.isEmpty()) {
            scrollOffset = 0;
            return;
        }
        int maxLines = getMaxRenderableLines();
        int count = 0;
        int index = outputLines.size() - 1;
        while (index >= 0) {
            count += countWrappedLines(outputLines.get(index));
            if (count >= maxLines) {
                break;
            }
            index--;
        }
        scrollOffset = Math.max(0, index);
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int left = (width - backgroundWidth) / 2;
        int top = (height - backgroundHeight) / 2;

        // Dark background with purple tint (expedition theme)
        context.fill(left, top, left + backgroundWidth, top + backgroundHeight, 0xFF0a0012);
        
        if (monitorMode) {
            // Monitor mode - simpler layout for progress bars
            context.fill(left + 5, top + 23, left + backgroundWidth - 5, top + backgroundHeight - 5, 0xFF0d0018);
        } else {
            // Console area with purple border
            context.fill(left + 5, top + 23, left + backgroundWidth - 5, top + 23 + CONSOLE_HEIGHT, 0xFF0d0018);
            
            // Command input area
            context.fill(left + 5, top + backgroundHeight - 35, left + backgroundWidth - 5, top + backgroundHeight - 5, 0xFF0d0018);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Check for auto-refresh
        checkAutoRefresh();
        
        super.render(context, mouseX, mouseY, delta);
        
        int left = (width - backgroundWidth) / 2;
        int top = (height - backgroundHeight) / 2;

        // Draw title
        context.drawText(
            textRenderer, 
            title.copy().formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD), 
            left + titleX, 
            top + titleY, 
            0xBB86FC, 
            true
        );
        
        // Draw fuel indicator
        String fuelText = "Fuel: " + storedFuel + "/10000";
        int fuelWidth = textRenderer.getWidth(fuelText);
        int fuelColor = storedFuel > 2000 ? 0x00FF00 : (storedFuel > 500 ? 0xFFFF00 : 0xFF0000);
        context.drawText(textRenderer, fuelText, left + backgroundWidth - fuelWidth - 10, top + 8, fuelColor, false);

        if (destinationPickerMode) {
            renderDestinationPicker(context, left, top, mouseX, mouseY);
        } else if (monitorMode) {
            renderMonitorMode(context, left, top, delta, mouseX, mouseY);
        } else {
            renderTerminalMode(context, left, top);
        }
    }
    
    private void renderTerminalMode(DrawContext context, int left, int top) {
        // Draw console output with scrolling
        int consoleTop = top + 25;
        int y = consoleTop;
        
        int maxRenderableLines = getMaxRenderableLines();
        int lineHeight = getScaledLineHeight();
        int startLine = Math.min(Math.max(0, scrollOffset), Math.max(0, outputLines.size() - 1));
        int renderedSubLines = 0;
        
        outer:
        for (int i = startLine; i < outputLines.size(); i++) {
            String line = outputLines.get(i);
            
            // Color coding for different line types
            int color = 0xBB86FC; // Default purple
            if (line.startsWith("> ")) {
                color = 0xFFFFFF; // White for commands
            } else if (line.startsWith("Error:") || line.startsWith("DISASTER:")) {
                color = 0xFF5555; // Red for errors
            } else if (line.startsWith("===")) {
                color = 0x03DAC6; // Teal for headers
            } else if (line.startsWith("[!]")) {
                color = 0xFFFF00; // Yellow for warnings
            } else if (line.contains("SUCCESS") || line.contains("Refueled")) {
                color = 0x00FF00; // Green for success
            } else if (line.isEmpty()) {
                y += LINE_HEIGHT;
                continue;
            }
            
            List<OrderedText> wrappedLines = wrapTextLines(line, getConsoleMaxWidth());
            if (wrappedLines.isEmpty()) {
                wrappedLines = Collections.singletonList(Text.literal("").asOrderedText());
            }
            for (OrderedText ordered : wrappedLines) {
                if (renderedSubLines >= maxRenderableLines) {
                    break outer;
                }
                drawScaledOrderedText(context, ordered, left + 10, y, color);
                y += lineHeight;
                renderedSubLines++;
            }
        }
        
        // Draw scroll indicator if needed
        if (outputLines.size() > maxRenderableLines) {
            String scrollInfo = String.format("[%d/%d]", scrollOffset + 1, Math.max(1, outputLines.size() - maxRenderableLines + 1));
            context.drawText(textRenderer, scrollInfo, left + backgroundWidth - 45, consoleTop - 12, 0x808080, false);
        }

        // Draw command prompt and current input
        drawInputLine(context, left + 8, top + backgroundHeight - 28);
    }
    
    private void renderMonitorMode(DrawContext context, int left, int top, float delta, int mouseX, int mouseY) {
        int contentTop = top + 30;
        int centerX = left + backgroundWidth / 2;
        
        // Clear button/bar lists for this frame
        decisionButtons.clear();
        expeditionBars.clear();
        
        // Get expedition data from the world
        List<ExpeditionDisplayData> expeditions = getExpeditionDisplayData();
        
        // Check for sound notifications
        checkForSoundNotifications(expeditions);
        
        // Update selected expedition data if still valid
        if (selectedExpeditionId != null) {
            selectedExpeditionData = null;
            for (ExpeditionDisplayData exp : expeditions) {
                if (exp.expeditionId.equals(selectedExpeditionId)) {
                    selectedExpeditionData = exp;
                    break;
                }
            }
            // If expedition no longer exists or no longer needs decision, clear selection
            if (selectedExpeditionData == null || !selectedExpeditionData.needsDecision) {
                selectedExpeditionId = null;
                selectedExpeditionData = null;
            }
        }
        
        // If we have a selected expedition that needs decision, show detail view
        if (selectedExpeditionData != null && selectedExpeditionData.needsDecision) {
            renderDecisionDetailView(context, left, top, mouseX, mouseY);
            return;
        }
        
        if (expeditions.isEmpty()) {
            // No expeditions message
            String noExp = "No active expeditions";
            int noExpWidth = textRenderer.getWidth(noExp);
            context.drawText(textRenderer, noExp, centerX - noExpWidth / 2, contentTop + 50, 0x808080, false);
            
            String hint = "Press T for terminal, then 'launch' to start";
            int hintWidth = textRenderer.getWidth(hint);
            context.drawText(textRenderer, hint, centerX - hintWidth / 2, contentTop + 65, 0x606060, false);
        } else {
            int y = contentTop;
            for (int i = 0; i < expeditions.size() && i < 5; i++) {
                ExpeditionDisplayData exp = expeditions.get(i);
                renderExpeditionBar(context, left + 50, y, exp, mouseX, mouseY);
                
                // Track bar area for click detection
                expeditionBars.add(new ExpeditionBarInfo(left + 50, y, BAR_WIDTH, BAR_SPACING - 5, exp));
                
                y += BAR_SPACING;
            }
        }
        
        // Render typing notifications at the bottom
        renderTypingNotifications(context, left, top);
        
        // Hint at bottom
        String hint = "Press T for terminal | Click expedition for details";
        context.drawText(textRenderer, hint, left + 10, top + backgroundHeight - 15, 0x505050, false);
    }
    
    private void renderDecisionDetailView(DrawContext context, int left, int top, int mouseX, int mouseY) {
        ExpeditionDisplayData exp = selectedExpeditionData;
        
        // Title
        String title = "=== DECISION REQUIRED ===";
        int titleWidth = textRenderer.getWidth(title);
        context.drawText(textRenderer, title, left + (backgroundWidth - titleWidth) / 2, top + 25, 0xFFAA00, false);
        
        // Expedition info
        String expInfo = exp.destinationName + " (" + exp.expeditionId + ")";
        context.drawText(textRenderer, expInfo, left + 15, top + 42, 0xBB86FC, false);
        
        // Event title
        int contentY = top + 58;
        if (!exp.eventTitle.isEmpty()) {
            context.drawText(textRenderer, exp.eventTitle, left + 15, contentY, 0xFFFFFF, false);
            contentY += 14;
        } else {
            context.drawText(textRenderer, "Event details loading...", left + 15, contentY, 0x808080, false);
            contentY += 14;
            // Request info from server
            BlockPos pos = handler.getBlockEntityPos();
            if (pos != null) {
                sendCommand("info " + exp.expeditionId);
            }
        }
        
        // Event description (word wrap)
        if (!exp.eventDescription.isEmpty()) {
            List<OrderedText> wrapped = textRenderer.wrapLines(Text.literal(exp.eventDescription), backgroundWidth - 30);
            for (int i = 0; i < Math.min(4, wrapped.size()); i++) {
                context.drawText(textRenderer, wrapped.get(i), left + 15, contentY, 0xAAAAAA, false);
                contentY += 11;
            }
        }
        
        contentY += 8;
        
        // Choice buttons
        int btnWidth = 120;
        int btnHeight = 28;
        int btnY = contentY;
        int btnAx = left + 20;
        int btnBx = left + backgroundWidth - btnWidth - 20;
        
        // Choice A button
        boolean hoverA = mouseX >= btnAx && mouseX <= btnAx + btnWidth && mouseY >= btnY && mouseY <= btnY + btnHeight;
        context.fill(btnAx, btnY, btnAx + btnWidth, btnY + btnHeight, hoverA ? 0xFF00AA00 : 0xFF006600);
        context.fill(btnAx, btnY, btnAx + btnWidth, btnY + 1, 0xFF00FF00); // Top highlight
        
        String choiceALabel = "[A] " + (exp.choiceAText.isEmpty() ? "Option A" : truncateString(exp.choiceAText, 14));
        int choiceAWidth = textRenderer.getWidth(choiceALabel);
        context.drawText(textRenderer, choiceALabel, btnAx + (btnWidth - choiceAWidth) / 2, btnY + 10, 0xFFFFFF, true);
        
        // Choice B button  
        boolean hoverB = mouseX >= btnBx && mouseX <= btnBx + btnWidth && mouseY >= btnY && mouseY <= btnY + btnHeight;
        context.fill(btnBx, btnY, btnBx + btnWidth, btnY + btnHeight, hoverB ? 0xFFAA6600 : 0xFF664400);
        context.fill(btnBx, btnY, btnBx + btnWidth, btnY + 1, 0xFFFFAA00); // Top highlight
        
        String choiceBLabel = "[B] " + (exp.choiceBText.isEmpty() ? "Option B" : truncateString(exp.choiceBText, 14));
        int choiceBWidth = textRenderer.getWidth(choiceBLabel);
        context.drawText(textRenderer, choiceBLabel, btnBx + (btnWidth - choiceBWidth) / 2, btnY + 10, 0xFFFFFF, true);
        
        // Store button info for click detection
        decisionButtons.clear();
        decisionButtons.add(new DecisionButtonInfo(btnAx, btnY, btnWidth, btnHeight, exp.expeditionId, false));
        decisionButtons.add(new DecisionButtonInfo(btnBx, btnY, btnWidth, btnHeight, exp.expeditionId, true));
        
        // Back button hint
        context.drawText(textRenderer, "Press ESC or click outside to go back", left + 15, top + backgroundHeight - 15, 0x505050, false);
    }
    
    private String truncateString(String s, int maxLen) {
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen - 2) + "..";
    }
    
    private void renderDestinationPicker(DrawContext context, int left, int top, int mouseX, int mouseY) {
        // Clear destination buttons list for this frame
        destinationButtons.clear();
        
        // Title
        String title = "=== SELECT DESTINATION ===";
        int titleWidth = textRenderer.getWidth(title);
        context.drawText(textRenderer, title, left + (backgroundWidth - titleWidth) / 2, top + 25, 0x03DAC6, false);
        
        // Draw destinations
        ExpeditionDestination[] destinations = ExpeditionDestination.values();
        int visibleCount = Math.min(5, destinations.length);
        int listTop = top + 45;
        int entryHeight = 32;
        
        for (int i = 0; i < visibleCount && destinationScrollOffset + i < destinations.length; i++) {
            int idx = destinationScrollOffset + i;
            ExpeditionDestination dest = destinations[idx];
            int entryY = listTop + i * entryHeight;
            
            boolean isSelected = idx == selectedDestinationIndex;
            boolean isHovered = mouseX >= left + 10 && mouseX <= left + backgroundWidth - 10 &&
                               mouseY >= entryY && mouseY <= entryY + entryHeight - 2;
            boolean canAfford = storedFuel >= dest.getBaseFuelCost();
            
            // Background for selected/hovered
            if (isSelected || isHovered) {
                context.fill(left + 10, entryY, left + backgroundWidth - 10, entryY + entryHeight - 2, 
                    canAfford ? 0x40BB86FC : 0x40FF5555);
            }
            
            // Store button info for click detection
            destinationButtons.add(new DestinationButtonInfo(left + 10, entryY, backgroundWidth - 20, entryHeight - 2, dest));
            
            // Destination name
            int nameColor = canAfford ? (isSelected ? 0xFFFFFF : 0xBB86FC) : 0x808080;
            context.drawText(textRenderer, dest.getDisplayName(), left + 15, entryY + 3, nameColor, false);
            
            // Fuel cost and risk
            String fuelStr = dest.getBaseFuelCost() + " fuel";
            String riskStr = dest.getRiskIndicator();
            String timeStr = dest.getBaseTimeMinutes() + "m";
            String rewardStr = "x" + String.format("%.1f", dest.getRewardMultiplier());
            
            int fuelColor = canAfford ? 0x00FF00 : 0xFF5555;
            context.drawText(textRenderer, fuelStr, left + 15, entryY + 14, fuelColor, false);
            context.drawText(textRenderer, timeStr, left + 80, entryY + 14, 0x808080, false);
            context.drawText(textRenderer, riskStr, left + 120, entryY + 14, 0xFFAA00, false);
            context.drawText(textRenderer, rewardStr, left + 160, entryY + 14, 0x00FFAA, false);
            
            // Launch button on right
            if (canAfford) {
                int btnX = left + backgroundWidth - 60;
                int btnY = entryY + 6;
                boolean btnHovered = mouseX >= btnX && mouseX <= btnX + 45 && mouseY >= btnY && mouseY <= btnY + 16;
                
                context.fill(btnX, btnY, btnX + 45, btnY + 16, btnHovered ? 0xFF00AA00 : 0xFF006600);
                String launchTxt = "LAUNCH";
                int txtW = textRenderer.getWidth(launchTxt);
                context.drawText(textRenderer, launchTxt, btnX + (45 - txtW) / 2, btnY + 4, 0xFFFFFF, false);
            }
        }
        
        // Scroll indicator
        if (destinations.length > 5) {
            String scrollInfo = String.format("Scroll: %d-%d of %d", 
                destinationScrollOffset + 1, 
                Math.min(destinationScrollOffset + 5, destinations.length),
                destinations.length);
            context.drawText(textRenderer, scrollInfo, left + 10, top + backgroundHeight - 30, 0x606060, false);
        }
        
        // Instructions
        String instructions = "Click to launch | ESC to cancel | Arrow keys to navigate";
        context.drawText(textRenderer, instructions, left + 10, top + backgroundHeight - 15, 0x505050, false);
    }
    
    private void renderExpeditionBar(DrawContext context, int x, int y, ExpeditionDisplayData exp, int mouseX, int mouseY) {
        // Destination name and ID
        String nameAndId = exp.destinationName + " (" + exp.expeditionId + ")";
        context.drawText(textRenderer, nameAndId, x, y, 0xBB86FC, false);
        
        // Progress bar background
        int barY = y + 11;
        int barWidth = exp.needsDecision ? BAR_WIDTH - 70 : BAR_WIDTH; // Make room for buttons
        
        context.fill(x, barY, x + barWidth, barY + BAR_HEIGHT, 0xFF1a1a2e);
        
        // Progress bar border
        context.fill(x, barY, x + barWidth, barY + 1, 0xFF3d3d5c);
        context.fill(x, barY + BAR_HEIGHT - 1, x + barWidth, barY + BAR_HEIGHT, 0xFF3d3d5c);
        context.fill(x, barY, x + 1, barY + BAR_HEIGHT, 0xFF3d3d5c);
        context.fill(x + barWidth - 1, barY, x + barWidth, barY + BAR_HEIGHT, 0xFF3d3d5c);
        
        // Progress bar fill
        int fillWidth = (int) (exp.progress * (barWidth - 4));
        int fillColor;
        if (exp.needsDecision) {
            // Pulsing yellow/orange for decision needed
            long pulse = System.currentTimeMillis() % 1000;
            fillColor = pulse < 500 ? 0xFFFFAA00 : 0xFFFF6600;
        } else if (exp.isComplete) {
            fillColor = exp.isSuccess ? 0xFF00CC00 : 0xFFCC0000;
        } else {
            // Purple color for active
            fillColor = 0xFFBB86FC;
        }
        
        if (fillWidth > 0) {
            context.fill(x + 2, barY + 2, x + 2 + fillWidth, barY + BAR_HEIGHT - 2, fillColor);
        }
        
        // Show time remaining in center of bar instead of percentage
        String barText;
        if (exp.needsDecision) {
            barText = "DECISION NEEDED";
        } else if (exp.isComplete) {
            barText = exp.isSuccess ? "CLAIM REWARDS" : "LOST";
        } else {
            barText = exp.timeRemaining;
        }
        int textWidth = textRenderer.getWidth(barText);
        context.drawText(textRenderer, barText, x + (barWidth - textWidth) / 2, barY + 4, 0xFFFFFF, true);
        
        // Render decision buttons if needed
        if (exp.needsDecision) {
            int btnAx = x + barWidth + 5;
            int btnBx = x + barWidth + 40;
            int btnY = barY + 1;
            
            // Button A
            boolean hoverA = mouseX >= btnAx && mouseX <= btnAx + BUTTON_WIDTH && 
                            mouseY >= btnY && mouseY <= btnY + BUTTON_HEIGHT;
            context.fill(btnAx, btnY, btnAx + BUTTON_WIDTH, btnY + BUTTON_HEIGHT, 
                hoverA ? 0xFF00AA00 : 0xFF006600);
            context.drawText(textRenderer, "A", btnAx + 11, btnY + 3, 0xFFFFFF, true);
            
            // Button B
            boolean hoverB = mouseX >= btnBx && mouseX <= btnBx + BUTTON_WIDTH && 
                            mouseY >= btnY && mouseY <= btnY + BUTTON_HEIGHT;
            context.fill(btnBx, btnY, btnBx + BUTTON_WIDTH, btnY + BUTTON_HEIGHT, 
                hoverB ? 0xFFAA6600 : 0xFF664400);
            context.drawText(textRenderer, "B", btnBx + 11, btnY + 3, 0xFFFFFF, true);
            
            // Store button positions for click detection
            decisionButtons.add(new DecisionButtonInfo(btnAx, btnY, BUTTON_WIDTH, BUTTON_HEIGHT, exp.expeditionId, false));
            decisionButtons.add(new DecisionButtonInfo(btnBx, btnY, BUTTON_WIDTH, BUTTON_HEIGHT, exp.expeditionId, true));
        }
        
        // Status indicator on right side (for complete states)
        if (exp.isComplete) {
            String statusText = exp.isSuccess ? "SUCCESS" : "FAILED";
            int statusColor = exp.isSuccess ? 0x00FF00 : 0xFF5555;
            int statusX = x + BAR_WIDTH - textRenderer.getWidth(statusText);
            context.drawText(textRenderer, statusText, statusX, y, statusColor, false);
        }
    }
    
    private void renderTypingNotifications(DrawContext context, int left, int top) {
        if (typingNotifications.isEmpty()) {
            return;
        }
        
        // Update typing animation
        long now = System.currentTimeMillis();
        if (now - lastTypingTime > TYPING_DELAY_MS) {
            lastTypingTime = now;
            typingCharIndex++;
        }
        
        int y = top + backgroundHeight - 55;
        for (int i = 0; i < typingNotifications.size() && i < 2; i++) {
            String notification = typingNotifications.get(i);
            String display;
            if (i == 0) {
                // Currently typing this one
                display = notification.substring(0, Math.min(typingCharIndex, notification.length()));
                if (typingCharIndex < notification.length()) {
                    display += "_";
                }
            } else {
                // Already typed
                display = notification;
            }
            
            context.drawText(textRenderer, display, left + 10, y, 0xFFFF00, false);
            y += 12;
        }
        
        // Remove completed notifications after a delay
        if (!typingNotifications.isEmpty() && typingCharIndex > typingNotifications.get(0).length() + 30) {
            typingNotifications.remove(0);
            typingCharIndex = 0;
        }
    }
    
    
    // Data class for displaying expedition info
    private record ExpeditionDisplayData(
        String expeditionId,
        String destinationName,
        double progress,
        String timeRemaining,
        boolean needsDecision,
        boolean isComplete,
        boolean isSuccess,
        String eventTitle,
        String eventDescription,
        String choiceAText,
        String choiceBText
    ) {}
    
    private List<ExpeditionDisplayData> getExpeditionDisplayData() {
        // Use a map to deduplicate by expedition ID (keep latest entry)
        java.util.Map<String, ExpeditionDisplayData> expeditionMap = new java.util.LinkedHashMap<>();
        
        // Parse expedition data from output lines
        // Format from status command:
        // "  [ ] Dust Belt Alpha - 2:30"
        // "      ID: EXP-BA118FF7"
        
        String currentDestination = null;
        String currentTime = null;
        boolean needsDecision = false;
        boolean isComplete = false;
        boolean isSuccess = false;
        double progress = 0.0;
        
        // Only look at the last ~50 lines to avoid old stale data
        int startIdx = Math.max(0, outputLines.size() - 50);
        
        for (int i = startIdx; i < outputLines.size(); i++) {
            String line = outputLines.get(i);
            String trimmed = line.trim();
            
            // Look for active expedition entries with [ ] or [!] at start
            if (trimmed.startsWith("[ ]") || trimmed.startsWith("[!]")) {
                needsDecision = trimmed.startsWith("[!]");
                isComplete = false;
                isSuccess = false;
                
                // Extract after the bracket: "Dust Belt Alpha - 2:30"
                String afterBracket = trimmed.substring(4).trim();
                int dashIdx = afterBracket.indexOf(" - ");
                if (dashIdx > 0) {
                    currentDestination = afterBracket.substring(0, dashIdx).trim();
                    String remainder = afterBracket.substring(dashIdx + 3).trim();
                    
                    // Check if it's a time (like "2:30") or status
                    if (remainder.matches("\\d+:\\d+")) {
                        currentTime = remainder;
                        // Calculate progress from time based on destination
                        try {
                            String[] parts = remainder.split(":");
                            int mins = Integer.parseInt(parts[0]);
                            int secs = Integer.parseInt(parts[1]);
                            int remainingSecs = mins * 60 + secs;
                            
                            // Estimate total time based on destination name
                            int estimatedTotalSecs = getEstimatedTotalTime(currentDestination);
                            
                            // Progress = elapsed / total = (total - remaining) / total = 1 - remaining/total
                            progress = Math.max(0.0, Math.min(1.0, 1.0 - ((double)remainingSecs / estimatedTotalSecs)));
                        } catch (NumberFormatException e) {
                            progress = 0.5;
                        }
                    } else if (remainder.contains("DECISION")) {
                        needsDecision = true;
                        currentTime = "AWAITING";
                        progress = 0.5;
                    } else {
                        currentTime = remainder;
                        progress = 0.5;
                    }
                }
            }
            // Look for completed expedition lines
            else if (trimmed.startsWith("[OK]") || trimmed.startsWith("[X]")) {
                isComplete = true;
                isSuccess = trimmed.startsWith("[OK]");
                progress = 1.0;
                needsDecision = false;
                
                String afterBracket = trimmed.substring(4).trim();
                int dashIdx = afterBracket.indexOf(" - ID:");
                if (dashIdx > 0) {
                    currentDestination = afterBracket.substring(0, dashIdx).trim();
                    int idIdx = afterBracket.indexOf("ID:");
                    if (idIdx >= 0) {
                        String currentId = afterBracket.substring(idIdx + 3).trim();
                        int spaceIdx = currentId.indexOf(" ");
                        if (spaceIdx > 0) currentId = currentId.substring(0, spaceIdx);
                        
                        expeditionMap.put(currentId, new ExpeditionDisplayData(
                            currentId,
                            currentDestination,
                            progress,
                            isSuccess ? "COMPLETE" : "FAILED",
                            false,
                            true,
                            isSuccess,
                            "", "", "", ""
                        ));
                        currentDestination = null;
                    }
                }
            }
            // Look for ID line that follows active expeditions
            else if (trimmed.startsWith("ID:") && currentDestination != null) {
                String currentId = trimmed.substring(3).trim();
                int spaceIdx = currentId.indexOf(" ");
                if (spaceIdx > 0) {
                    currentId = currentId.substring(0, spaceIdx);
                }
                
                // Try to find event info for this expedition if decision is needed
                String eventTitle = "";
                String eventDesc = "";
                String choiceA = "";
                String choiceB = "";
                
                if (needsDecision) {
                    // Look for event info in output lines (from 'info' or 'pending' command)
                    EventInfo eventInfo = parseEventInfo(currentId);
                    eventTitle = eventInfo.title;
                    eventDesc = eventInfo.description;
                    choiceA = eventInfo.choiceA;
                    choiceB = eventInfo.choiceB;
                }
                
                // Add/update expedition entry (deduplicates by ID)
                expeditionMap.put(currentId, new ExpeditionDisplayData(
                    currentId,
                    currentDestination,
                    progress,
                    currentTime != null ? currentTime : "...",
                    needsDecision,
                    isComplete,
                    isSuccess,
                    eventTitle,
                    eventDesc,
                    choiceA,
                    choiceB
                ));
                
                // Reset for next entry
                currentDestination = null;
                currentTime = null;
                needsDecision = false;
                isComplete = false;
                isSuccess = false;
                progress = 0.0;
            }
        }
        
        return new ArrayList<>(expeditionMap.values());
    }
    
    // Helper record for event parsing
    private record EventInfo(String title, String description, String choiceA, String choiceB) {}
    
    /**
     * Parse event info for a specific expedition from output lines
     */
    private EventInfo parseEventInfo(String expeditionId) {
        String title = "";
        String description = "";
        String choiceA = "";
        String choiceB = "";
        
        boolean foundExpedition = false;
        boolean inEventSection = false;
        StringBuilder descBuilder = new StringBuilder();
        
        // Look through output lines for event info matching this expedition
        for (int i = 0; i < outputLines.size(); i++) {
            String line = outputLines.get(i);
            String trimmed = line.trim();
            
            // Look for the expedition ID
            if (trimmed.contains(expeditionId)) {
                foundExpedition = true;
            }
            
            // Look for pending event section
            if (foundExpedition && trimmed.equals("=== PENDING EVENT ===")) {
                inEventSection = true;
                // Next line is the title
                if (i + 1 < outputLines.size()) {
                    title = outputLines.get(i + 1).trim();
                }
                continue;
            }
            
            // Parse event content after we found the section
            if (inEventSection) {
                if (trimmed.startsWith("Event:")) {
                    title = trimmed.substring(6).trim();
                } else if (trimmed.startsWith("[A]")) {
                    choiceA = trimmed.substring(3).trim();
                } else if (trimmed.startsWith("[B]")) {
                    choiceB = trimmed.substring(3).trim();
                } else if (trimmed.startsWith("Options:") || trimmed.startsWith("Use:") || trimmed.startsWith("---")) {
                    // End of description section
                } else if (trimmed.isEmpty() && !descBuilder.isEmpty()) {
                    // Empty line after description
                } else if (!title.isEmpty() && choiceA.isEmpty() && !trimmed.startsWith("ID:") && !trimmed.startsWith("Destination:")) {
                    // This is part of the description
                    if (!descBuilder.isEmpty()) descBuilder.append(" ");
                    descBuilder.append(trimmed);
                }
                
                // If we have all parts, stop
                if (!choiceA.isEmpty() && !choiceB.isEmpty()) {
                    break;
                }
            }
        }
        
        description = descBuilder.toString();
        return new EventInfo(title, description, choiceA, choiceB);
    }
    
    /**
     * Estimate total expedition time in seconds based on destination name
     */
    private int getEstimatedTotalTime(String destinationName) {
        if (destinationName == null) return 180;
        
        // Match destination to its base time (in seconds)
        return switch (destinationName.toUpperCase().replace(" ", "_")) {
            case "DUST_BELT_ALPHA" -> 3 * 60;   // 3 min
            case "IRON_RIDGE" -> 5 * 60;        // 5 min
            case "CRYSTALLINE_DRIFT" -> 6 * 60; // 6 min
            case "HOLLOW_MOON_REMNANT" -> 6 * 60;
            case "SULFUR_VENTS" -> 7 * 60;
            case "DARKSIDE_CLUSTER" -> 8 * 60;
            case "WRECKAGE_FIELD" -> 9 * 60;
            case "CRIMSON_ANOMALY" -> 10 * 60;
            case "VOID_EDGE_BELT" -> 11 * 60;
            case "THE_MAW" -> 12 * 60;
            default -> 5 * 60; // Default 5 min
        };
    }
    
    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        // Override to prevent drawing slot labels
    }
    
    private int getScaledLineHeight() {
        if (textRenderer == null) {
            return LINE_HEIGHT;
        }
        return Math.max(7, Math.round(textRenderer.fontHeight * TEXT_SCALE) + 1);
    }
    
    private int getAvailableConsoleHeight() {
        int lineHeight = getScaledLineHeight();
        return Math.max(lineHeight, backgroundHeight - 67 - lineHeight);
    }
    
    private int getMaxRenderableLines() {
        int lineHeight = getScaledLineHeight();
        return Math.max(1, getAvailableConsoleHeight() / lineHeight);
    }
    
    private int getConsoleMaxWidth() {
        return backgroundWidth - 20;
    }
    
    private List<OrderedText> wrapTextLines(String line, int maxWidth) {
        if (line == null || line.isEmpty()) {
            return Collections.emptyList();
        }
        if (textRenderer == null) {
            return Collections.singletonList(Text.literal(line).asOrderedText());
        }
        int wrapWidth = (int)Math.max(1, maxWidth / TEXT_SCALE);
        return textRenderer.wrapLines(Text.literal(line), wrapWidth);
    }
    
    private void drawScaledOrderedText(DrawContext context, OrderedText text, int x, int y, int color) {
        if (text == null) {
            return;
        }
        context.getMatrices().push();
        context.getMatrices().translate(x, y, 0);
        context.getMatrices().scale(TEXT_SCALE, TEXT_SCALE, 1.0f);
        context.drawText(textRenderer, text, 0, 0, color, false);
        context.getMatrices().pop();
    }
    
    private void drawInputLine(DrawContext context, int x, int y) {
        long blink = (System.currentTimeMillis() / 500) % 2;
        String prompt = "> " + currentInput + (blink == 0 ? "_" : "");
        drawScaledOrderedText(context, Text.literal(prompt).asOrderedText(), x, y, 0xBB86FC);
    }
    
    private int countWrappedLines(String line) {
        int maxWidth = getConsoleMaxWidth();
        List<OrderedText> wrapped = wrapTextLines(line, maxWidth);
        return Math.max(1, wrapped.size());
    }

    /**
     * Update the output lines and fuel from the server
     */
    public void updateFromServer(List<String> lines, int fuel) {
        this.outputLines = new ArrayList<>(lines);
        this.storedFuel = fuel;
        scrollToBottom();
        
        // Check for pending decisions in output and add notifications
        checkForPendingDecisions(lines);
    }
    
    /**
     * Update the output lines, fuel, and monitor mode from the server
     */
    public void updateFromServer(List<String> lines, int fuel, boolean monitor) {
        this.outputLines = new ArrayList<>(lines);
        this.storedFuel = fuel;
        this.monitorMode = monitor;
        handler.setMonitorMode(monitor);
        scrollToBottom();
        
        // Check for pending decisions in output and add notifications
        checkForPendingDecisions(lines);
    }
    
    private void checkForPendingDecisions(List<String> lines) {
        // Look for decision-needed indicators and add typing notifications
        for (String line : lines) {
            if (line.contains("[!]")) {
                // Extract expedition ID if present
                String expId = extractExpeditionId(line);
                if (expId != null && !notifiedExpeditions.contains(expId)) {
                    // Only add typing notification if in monitor mode
                    if (monitorMode) {
                        addTypingNotification(">>> EXPEDITION " + expId + " REQUIRES YOUR DECISION <<<");
                    }
                }
            }
        }
    }
    
    private String extractExpeditionId(String line) {
        // Look for expedition IDs in format EXP-XXXXXXXX
        int idx = line.indexOf("EXP-");
        if (idx >= 0 && idx + 12 <= line.length()) {
            return line.substring(idx, idx + 12);
        }
        // Also check for just ID: prefix
        idx = line.indexOf("ID: ");
        if (idx >= 0) {
            int end = line.indexOf(" ", idx + 4);
            if (end < 0) end = line.length();
            return line.substring(idx + 4, Math.min(end, idx + 16));
        }
        return null;
    }
    
    /**
     * Add a notification to be typed out in monitor mode
     */
    public void addTypingNotification(String message) {
        if (!typingNotifications.contains(message)) {
            typingNotifications.add(message);
            if (typingNotifications.size() == 1) {
                typingCharIndex = 0;
                lastTypingTime = System.currentTimeMillis();
            }
        }
    }
    
    /**
     * Set expedition display data from server sync
     */
    public void setExpeditionData(List<ExpeditionDisplayData> data) {
        // This would be called when we sync expedition data from server
        // For now, expeditions are parsed from output lines
    }
}

