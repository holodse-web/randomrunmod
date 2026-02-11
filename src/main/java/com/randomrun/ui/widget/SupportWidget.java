package com.randomrun.ui.widget;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.randomrun.battle.FirebaseClient;
import com.randomrun.main.RandomRunMod;
import com.randomrun.ui.screen.main.MainModScreen;
import com.randomrun.ui.widget.styled.ButtonDefault;
import com.randomrun.ui.widget.styled.ButtonMenu;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.Widget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class SupportWidget implements Widget, Drawable, Element, Selectable {
    private final MinecraftClient client;
    private final int screenWidth;
    private final int screenHeight;
    private final Runnable onClose;
    
    private boolean visible = false;
    private float fadeProgress = 0f;
    private long openTime;
    private static final float FADE_SPEED = 0.1f;
    
    // Updated dimensions
    private static final int POPUP_WIDTH = 500;
    private static final int POPUP_HEIGHT = 220;
    
    private final List<ClickableWidget> buttons = new ArrayList<>();
    
    // Donation List
    private List<DonationEntry> donations = new ArrayList<>();
    private float scrollOffset = 0f;
    private static final float SCROLL_SPEED = 0.5f; // Pixels per frame
    private boolean isLoading = false;
    
    public SupportWidget(MinecraftClient client, int width, int height, Runnable onClose) {
        this.client = client;
        this.screenWidth = width;
        this.screenHeight = height;
        this.onClose = onClose;
        initButtons();
    }

    private boolean focused = false;

    @Override
    public void setFocused(boolean focused) {
        this.focused = focused;
    }

    @Override
    public boolean isFocused() {
        return focused;
    }

    @Override
    public void forEachChild(Consumer<ClickableWidget> consumer) {
        buttons.forEach(consumer);
    }

    @Override
    public int getX() { return 0; }
    @Override
    public int getY() { return 0; }
    @Override
    public int getWidth() { return screenWidth; }
    @Override
    public int getHeight() { return screenHeight; }
    @Override
    public void setX(int x) {}
    @Override
    public void setY(int y) {}
    @Override
    public SelectionType getType() {
        return SelectionType.NONE;
    }

    @Override
    public void appendNarrations(NarrationMessageBuilder builder) {
        // No narration for container
    }

    @Override
    public ScreenRect getNavigationFocus() { return Element.super.getNavigationFocus(); }
    
    private void initButtons() {
        buttons.clear();
        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;
        
        int leftSectionCenter = centerX - POPUP_WIDTH / 4;
        
        int startY = centerY - 50;
        int spacing = 35;
        int btnWidth = 180;
        int btnHeight = 25;
        
        // Monobank Button
        buttons.add(new ButtonMenu(
            leftSectionCenter - btnWidth / 2, startY,
            btnWidth, btnHeight,
            Text.translatable("randomrun.support.monobank"),
            button -> openLink("https://send.monobank.ua/jar/3TGJE5dA6c"),
            0, 0.1f
        ));
        
        // DonatePay Button
        buttons.add(new ButtonMenu(
            leftSectionCenter - btnWidth / 2, startY + spacing,
            btnWidth, btnHeight,
            Text.translatable("randomrun.support.donatepay"),
            button -> openLink("https://donatepay.ru/don/rrm"),
            1, 0.15f
        ));
        
        // Crypto TON Button
        String tonAddress = "UQDsYDDCiRtZOsWJjkCNkQR1At8sCi4c8G4BwfZSguUgSfW-";
        buttons.add(new ButtonMenu(
            leftSectionCenter - btnWidth / 2, startY + spacing * 2,
            btnWidth, btnHeight,
            Text.translatable("randomrun.support.ton"),
            button -> {
                client.keyboard.setClipboard(tonAddress);
                if (client.player != null) {
                    client.player.sendMessage(Text.translatable("randomrun.support.ton_copied"), true);
                } else {
                    client.getToastManager().add(new net.minecraft.client.toast.SystemToast(
                        net.minecraft.client.toast.SystemToast.Type.PERIODIC_NOTIFICATION, 
                        Text.translatable("randomrun.support.ton_copied"), 
                        Text.literal(tonAddress.substring(0, 8) + "...")
                    ));
                }
            },
            2, 0.2f
        ));
        
        // Close Button (Left side as well)
        buttons.add(new ButtonDefault(
            leftSectionCenter - 80, centerY + POPUP_HEIGHT / 2 - 30,
            160, 20,
            Text.translatable("randomrun.button.back"),
            button -> close(),
            3, 0.25f
        ));
    }
    
    public void show() {
        visible = true;
        openTime = System.currentTimeMillis();
        fadeProgress = 0f;
        scrollOffset = 0f;
        fetchDonations();
    }
    
    private void fetchDonations() {
        isLoading = true;
        donations.clear();
        
        CompletableFuture.runAsync(() -> {
            try {
                // Fetch from /donations path
                JsonElement element = FirebaseClient.getInstance().get("/donations").join();
                
                List<DonationEntry> fetched = new ArrayList<>();
                
                if (element != null) {
                    if (element.isJsonObject()) {
                        JsonObject obj = element.getAsJsonObject();
                        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                            if (entry.getValue().isJsonObject()) {
                                addDonationFromJson(entry.getValue().getAsJsonObject(), fetched);
                            }
                        }
                    } else if (element.isJsonArray()) {
                        com.google.gson.JsonArray arr = element.getAsJsonArray();
                        for (JsonElement e : arr) {
                            if (e != null && e.isJsonObject()) {
                                addDonationFromJson(e.getAsJsonObject(), fetched);
                            }
                        }
                    }
                }
                
                // Update list on main thread
                MinecraftClient.getInstance().execute(() -> {
                    donations.addAll(fetched);
                    isLoading = false;
                });
            } catch (Exception e) {
                RandomRunMod.LOGGER.error("Failed to fetch donations", e);
                MinecraftClient.getInstance().execute(() -> isLoading = false);
            }
        });
    }

    private void addDonationFromJson(JsonObject d, List<DonationEntry> list) {
        String name = d.has("n") ? d.get("n").getAsString() : "Unknown";
        String crypto = d.has("c") ? d.get("c").getAsString() : "";
        String amount = d.has("a") ? d.get("a").getAsString() : "";
        list.add(new DonationEntry(name, crypto, amount));
    }
    
    public void close() {
        visible = false;
        if (onClose != null) onClose.run();
    }
    
    public boolean isVisible() {
        return visible;
    }
    
    private void openLink(String url) {
        try {
            Util.getOperatingSystem().open(new URI(url));
        } catch (Exception e) {
            RandomRunMod.LOGGER.error("Failed to open link: " + url, e);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!visible) return;
        
        long elapsed = System.currentTimeMillis() - openTime;
        fadeProgress = Math.min(1.0f, elapsed / 1000.0f * (1.0f / FADE_SPEED));
        
        // Dark Overlay
        context.fill(0, 0, screenWidth, screenHeight, (int)(200 * fadeProgress) << 24);
        
        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;
        int left = centerX - POPUP_WIDTH / 2;
        int top = centerY - POPUP_HEIGHT / 2;
        int right = centerX + POPUP_WIDTH / 2;
        int bottom = centerY + POPUP_HEIGHT / 2;
        
        // Popup Container
        int bgAlpha = (int) (240 * fadeProgress); 
        int bgColor = 0x1a0a2e; 
        context.fill(left, top, right, bottom, (bgAlpha << 24) | bgColor);
        
        // Animated Border
        MainModScreen.renderAnimatedBorder(context, left, top, right, bottom, 2);
        
        // Title (Left Side)
        context.getMatrices().push();
        float scale = 1.2f;
        // Position title above left section
        context.getMatrices().translate(centerX - POPUP_WIDTH / 4, top + 25, 0);
        context.getMatrices().scale(scale, scale, 1f);
        context.drawCenteredTextWithShadow(client.textRenderer, Text.translatable("randomrun.screen.support.title"), 0, 0, 0xFFFFFF);
        context.getMatrices().pop();
        
        // Divider Line (Center)
        // Semi-transparent gray, not touching top/bottom
        int dividerColor = (int)(100 * fadeProgress) << 24 | 0x888888;
        int dividerTop = top + 20;
        int dividerBottom = bottom - 20;
        context.fill(centerX - 1, dividerTop, centerX + 1, dividerBottom, dividerColor);
        
        // Render buttons
        for (ClickableWidget button : buttons) {
            button.render(context, mouseX, mouseY, delta);
        }
        
        // Right Side: Donations
        renderDonations(context, centerX, top, right, bottom, delta);
    }
    
    private void renderDonations(DrawContext context, int startX, int startY, int endX, int endY, float delta) {
        int contentWidth = endX - startX;
        int contentHeight = endY - startY - 40; // Margins
        int renderTop = startY + 20;
        int renderBottom = endY - 20;
        
        // Title for Donations
        context.drawCenteredTextWithShadow(client.textRenderer, 
            Text.translatable("randomrun.support.top_supporters"), 
            startX + contentWidth / 2, startY + 10, 0xFFAA00);
            
        if (isLoading) {
            context.drawCenteredTextWithShadow(client.textRenderer, 
                Text.translatable("randomrun.support.loading"), 
                startX + contentWidth / 2, startY + contentHeight / 2, 0xAAAAAA);
            return;
        }
        
        if (donations.isEmpty()) {
            context.drawCenteredTextWithShadow(client.textRenderer, 
                Text.translatable("randomrun.support.no_data"), 
                startX + contentWidth / 2, startY + contentHeight / 2, 0xAAAAAA);
            return;
        }
        
        // Scroll Logic (Top to Bottom)
        // scrollOffset increases -> items move down
        scrollOffset += delta * SCROLL_SPEED;
        
        int lineHeight = 15;
        float totalListHeight = donations.size() * lineHeight;
        
        // Scissor Test
        double scale = client.getWindow().getScaleFactor();
        int scissorX = (int) (startX * scale);
        int scissorY = (int) ((client.getWindow().getScaledHeight() - renderBottom) * scale);
        int scissorW = (int) (contentWidth * scale);
        int scissorH = (int) ((renderBottom - renderTop) * scale);
        
        com.mojang.blaze3d.systems.RenderSystem.enableScissor(scissorX, scissorY, scissorW, scissorH);
        
        // Reset offset if it goes too far
        // For continuous scrolling, we can render items with modulo arithmetic
        
        float startRenderY = renderTop + 20; // Some padding below title
        
        for (int i = 0; i < donations.size(); i++) {
            DonationEntry entry = donations.get(i);
            
            // Calculate Y position
            // Items start above and move down
            float yPos = startRenderY + (i * lineHeight) + scrollOffset;
            
            // Wrap logic
            // If item goes below bottom, move it to top?
            // Actually, usually "credits" move UP (bottom -> top). 
            // But user said "сверху вниз" (Top -> Down).
            // If Top -> Down, items appear at Top and move to Bottom.
            // So y increases.
            
            // We want cyclic scrolling.
            // Effective Y = (BaseY + Offset) % CycleHeight
            // But we need to handle the window.
            
            // Let's assume we render the list repeatedly or just wrap indices.
            // Simpler: Just render the list and if it's visible, draw it.
            // For looping:
            // y = (i * lineHeight + scrollOffset) % totalListHeight
            // But this will make them jump.
            
            // Better: 
            float relativeY = (i * lineHeight + scrollOffset) % (Math.max(totalListHeight, renderBottom - renderTop + 50));
            // Adjust so it flows smoothly
            // If relativeY is too large, it might need to be wrapped to appear at top?
            // No, if scrolling Top->Down, things move DOWN.
            // So they should start at negative Y and move to positive Y.
            
            // Let's stick to simple scrolling for now:
            // Just move down. If scrollOffset > totalHeight, reset?
            // "по кругу" means loop.
            
            // Let's implement modulo based position
            float containerHeight = renderBottom - renderTop;
            float cycleHeight = Math.max(totalListHeight, containerHeight);
            
            // Position relative to top
            float itemY = (i * lineHeight + scrollOffset) % cycleHeight;
            
            // If we are scrolling down, itemY increases.
            // If itemY > containerHeight, it disappears. 
            // But we want it to reappear at top.
            // Wait, standard modulo logic: 0..cycleHeight.
            
            // Render at (renderTop + itemY)
            // Also render at (renderTop + itemY - cycleHeight) to cover the top edge transition
            
            drawDonationItem(context, entry, startX + contentWidth / 2, renderTop + itemY, renderTop, renderBottom);
            drawDonationItem(context, entry, startX + contentWidth / 2, renderTop + itemY - cycleHeight, renderTop, renderBottom);
        }
        
        com.mojang.blaze3d.systems.RenderSystem.disableScissor();
    }
    
    private void drawDonationItem(DrawContext context, DonationEntry entry, int centerX, float y, int topLimit, int bottomLimit) {
        if (y < topLimit - 10 || y > bottomLimit) return;
        
        String text = entry.toString();
        int color = 0xFFFFFF;
        
        // Fade out at edges
        float alpha = 1.0f;
        float distToEdge = Math.min(y - topLimit, bottomLimit - y);
        if (distToEdge < 20) {
            alpha = Math.max(0, distToEdge / 20f);
        }
        
        int alphaInt = (int)(alpha * 255);
        int finalColor = (alphaInt << 24) | (color & 0x00FFFFFF);
        
        context.drawCenteredTextWithShadow(client.textRenderer, text, centerX, (int)y, finalColor);
    }
    
    // Mouse input handling for buttons
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!visible) return false;
        
        // Close if clicked outside
        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;
        if (mouseX < centerX - POPUP_WIDTH / 2 || mouseX > centerX + POPUP_WIDTH / 2 ||
            mouseY < centerY - POPUP_HEIGHT / 2 || mouseY > centerY + POPUP_HEIGHT / 2) {
            close();
            return true;
        }
        
        for (ClickableWidget widget : buttons) {
            if (widget.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    private static class DonationEntry {
        String name;
        String crypto;
        String amount;
        
        DonationEntry(String name, String crypto, String amount) {
            this.name = name;
            this.crypto = crypto;
            this.amount = amount;
        }
        
        @Override
        public String toString() {
            // Format: Name/Crypto - Amount ❤
            StringBuilder sb = new StringBuilder();
            sb.append(name);
            if (crypto != null && !crypto.isEmpty()) {
                sb.append("/").append(crypto);
            }
            sb.append(" - ").append(amount).append(" §c❤");
            return sb.toString();
        }
    }
}
