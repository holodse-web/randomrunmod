package com.randomrun.features.battle.screen;

import com.randomrun.features.battle.manager.BattleRoom;
import com.randomrun.features.battle.screen.LobbyLogic.LobbyPollingManager;

import java.util.Random;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class LobbyRender {
    private LobbyRender() {
    }

    public static class UIColors {
        public static int BG_NORMAL() { return 0xFF000000 | com.randomrun.ui.util.Theme.current().panel; }
        public static int BG_HOVERED() { return 0xFF000000 | com.randomrun.ui.util.Theme.current().buttonBase; }
        public static final int BG_DISABLED = 0x80000000;
        public static final int BG_PANEL = 0xCC000000;

        public static int BORDER_NORMAL() { return 0xFF000000 | com.randomrun.ui.util.Theme.current().buttonHover; }
        public static int BORDER_HOVERED() { return 0xFF000000 | com.randomrun.ui.util.Theme.current().overlay; }
        public static final int BORDER_DISABLED = 0xFF555555;

        public static final int TEXT_WHITE = 0xFFFFFF;
        public static final int TEXT_GRAY = 0xAAAAAA;
        public static final int TEXT_WARNING = 0xFFAA00;
        public static final int TEXT_ERROR = 0xFF5555;
        public static final int TEXT_SUCCESS = 0x55FF55;
        public static final int TEXT_YELLOW = 0xFFFF55;

        public static final int BAR_BG = 0xFF2a2a3c;
        public static final int BAR_FULL = 0xFF00FF00;
        public static int BAR_PARTIAL() { return 0xFF000000 | com.randomrun.ui.util.Theme.current().buttonHover; }

        public static final int SLOT_BG = 0x40FFFFFF;
        public static final int SLOT_BORDER = 0x80FFFFFF;

        public static final int PING_EXCELLENT = 0x55FF55;
        public static final int PING_GOOD = 0xFFFF55;
        public static final int PING_BAD = 0xFF5555;
        public static final int PING_ERROR = 0xFF5555;
        public static final int PING_LOADING = 0x808080;
    }

    public static class CodeAnimator {
        private static final long CODE_ANIMATION_DURATION = 2000;
        private static final long DIGIT_REVEAL_INTERVAL = 400;

        private final String roomCode;
        private final long startTime;
        private final Random random = new Random();
        private String cachedCode = null;
        private long lastCacheTime = 0;
        private static final long CACHE_DURATION = 16;

        public CodeAnimator(String roomCode) {
            this.roomCode = roomCode;
            this.startTime = System.currentTimeMillis();
        }

        public String getAnimatedCode() {
            long currentTime = System.currentTimeMillis();

            if (cachedCode != null && currentTime - lastCacheTime < CACHE_DURATION) {
                return cachedCode;
            }

            long elapsed = currentTime - startTime;

            if (elapsed >= CODE_ANIMATION_DURATION) {
                cachedCode = roomCode;
                lastCacheTime = currentTime;
                return roomCode;
            }

            StringBuilder animatedCode = new StringBuilder();
            for (int i = 0; i < roomCode.length(); i++) {
                long digitStartTime = i * DIGIT_REVEAL_INTERVAL;

                if (elapsed < digitStartTime) {
                    animatedCode.append('*');
                } else if (elapsed < digitStartTime + DIGIT_REVEAL_INTERVAL) {
                    long digitElapsed = elapsed - digitStartTime;
                    float progress = (float) digitElapsed / DIGIT_REVEAL_INTERVAL;

                    if (progress < 0.8f) {
                        char randomDigit = (char) ('0' + random.nextInt(10));
                        animatedCode.append(randomDigit);
                    } else {
                        animatedCode.append(roomCode.charAt(i));
                    }
                } else {
                    animatedCode.append(roomCode.charAt(i));
                }
            }

            cachedCode = animatedCode.toString();
            lastCacheTime = currentTime;
            return cachedCode;
        }

        public long getElapsedTime() {
            return System.currentTimeMillis() - startTime;
        }

        public boolean isAnimationComplete() {
            return getElapsedTime() >= CODE_ANIMATION_DURATION;
        }

        public long getAnimationDuration() {
            return CODE_ANIMATION_DURATION;
        }
    }

    public static class ItemAnimationController {
        private static final long RESUME_DELAY = 500;
        private static final float RESUME_ACCELERATION = 0.02f;
        private static final float MAX_ROTATION_SPEED = 2f;
        private static final float MAX_LEVITATION_SPEED = 1f;

        private float rotationY = 0f;
        private float levitationOffset = 0f;
        private float targetLevitationOffset = 0f;
        private long lastDragTime = 0;
        private float rotationSpeed = 0f;
        private float levitationSpeed = 0f;

        private boolean dragging = false;
        private float dragRotationX = 0f;
        private float dragRotationY = 0f;
        private double lastMouseX, lastMouseY;
        private float frozenLevitationOffset = 0f;

        public void update(long elapsed, float delta) {
            long timeSinceLastDrag = System.currentTimeMillis() - lastDragTime;

            if (dragging) {
                rotationSpeed = 0f;
                levitationSpeed = 0f;
                frozenLevitationOffset = levitationOffset;
            } else if (timeSinceLastDrag > RESUME_DELAY) {
                rotationSpeed = Math.min(rotationSpeed + RESUME_ACCELERATION * delta, MAX_ROTATION_SPEED);
                levitationSpeed = Math.min(levitationSpeed + RESUME_ACCELERATION * delta, MAX_LEVITATION_SPEED);

                rotationY += delta * rotationSpeed;
                targetLevitationOffset = (float) Math.sin(elapsed / 500.0) * 5f;
                levitationOffset += (targetLevitationOffset - levitationOffset) * levitationSpeed * delta * 0.1f;
            } else {
                levitationOffset = frozenLevitationOffset;
            }
        }

        public void startDrag(double mouseX, double mouseY) {
            dragging = true;
            lastMouseX = mouseX;
            lastMouseY = mouseY;
        }

        public void updateDrag(double mouseX, double mouseY) {
            if (dragging) {
                dragRotationY -= (float) (mouseX - lastMouseX) * 0.5f;
                dragRotationX -= (float) (mouseY - lastMouseY) * 0.5f;
                dragRotationX = Math.max(-90, Math.min(90, dragRotationX));
                lastMouseX = mouseX;
                lastMouseY = mouseY;
            }
        }

        public void stopDrag() {
            if (dragging) {
                dragging = false;
                lastDragTime = System.currentTimeMillis();
                frozenLevitationOffset = levitationOffset;
            }
        }

        public boolean isDragging() {
            return dragging;
        }

        public float getRotationY() {
            return rotationY;
        }

        public float getDragRotationX() {
            return dragRotationX;
        }

        public float getDragRotationY() {
            return dragRotationY;
        }

        public float getLevitationOffset() {
            return levitationOffset;
        }
    }

    public static class ConnectionStatusRenderer {
        public static void extractRenderState(GuiGraphicsExtractor context, Font textRenderer,
                                LobbyPollingManager.ConnectionStatus status,
                                int x, int y) {
            String statusText = getStatusText(status);
            int color = getStatusColor(status);
            String icon = getStatusIcon(status);

            String fullText = icon + " " + statusText;
            com.randomrun.ui.util.GuiText.text(context, textRenderer, Component.literal(fullText), x, y, color);
        }

        private static String getStatusText(LobbyPollingManager.ConnectionStatus status) {
            switch (status) {
                case CONNECTED:
                    return com.randomrun.infrastructure.util.LanguageManager.translate("randomrun.lobby.status.connected");
                case CONNECTING:
                    return com.randomrun.infrastructure.util.LanguageManager.translate("randomrun.lobby.status.connecting");
                case DISCONNECTED:
                    return com.randomrun.infrastructure.util.LanguageManager.translate("randomrun.lobby.status.disconnected");
                case ERROR:
                    return com.randomrun.infrastructure.util.LanguageManager.translate("randomrun.lobby.status.error");
                default:
                    return com.randomrun.infrastructure.util.LanguageManager.translate("randomrun.lobby.status.unknown");
            }
        }

        private static int getStatusColor(LobbyPollingManager.ConnectionStatus status) {
            switch (status) {
                case CONNECTED:
                    return UIColors.TEXT_SUCCESS;
                case CONNECTING:
                    return UIColors.TEXT_YELLOW;
                case DISCONNECTED:
                    return UIColors.TEXT_GRAY;
                case ERROR:
                    return UIColors.TEXT_ERROR;
                default:
                    return UIColors.TEXT_GRAY;
            }
        }

        private static String getStatusIcon(LobbyPollingManager.ConnectionStatus status) {
            switch (status) {
                case CONNECTED:
                    return "●";
                case CONNECTING:
                    return "◐";
                case DISCONNECTED:
                    return "○";
                case ERROR:
                    return "✖";
                default:
                    return "?";
            }
        }
    }

    public static class RoomListRenderer {
        private static final int ICON_OFFSET = 8;
        private static final int TEXT_X = 50;
        private static final int TEXT_Y_TITLE = 10;
        private static final int TEXT_Y_INFO = 26;
        private static final int LOCK_OFFSET = 20;

        public static void renderRoomEntry(GuiGraphicsExtractor context, Font textRenderer,
                                          BattleRoom room, ItemStack iconStack, String modeText,
                                          int left, int top, int width, int height, boolean hovered) {
            renderIcon(context, iconStack, left, top);

            renderInfo(context, textRenderer, room, modeText, left, top, width);

            if (room.isPrivate()) {
                com.randomrun.ui.util.GuiText.text(context, textRenderer, Component.literal("🔒"),
                    left + width - LOCK_OFFSET, top + TEXT_Y_INFO, UIColors.TEXT_WARNING);
            }
        }

        private static void renderIcon(GuiGraphicsExtractor context, ItemStack iconStack, int left, int top) {
            if (!iconStack.isEmpty()) {
                com.randomrun.ui.util.Previews.ItemPreviewRenderer.extractLargeGui(context, iconStack, left + ICON_OFFSET + 16, top + ICON_OFFSET + 16, 32);
            }
        }

        private static void renderInfo(GuiGraphicsExtractor context, Font textRenderer,
                                       BattleRoom room, String modeText, int left, int top, int width) {
            String host = room.getHost() != null && !room.getHost().isEmpty() ? room.getHost() : "Unknown";
            com.randomrun.ui.util.GuiText.text(context, textRenderer, Component.literal(host + "'s Room"),
                left + TEXT_X, top + TEXT_Y_TITLE, UIColors.TEXT_WHITE);

            if (room.getStatus() != BattleRoom.RoomStatus.WAITING) {
                String statusText = getRuleIndicators(room) + "§c⚠ " + com.randomrun.infrastructure.util.LanguageManager
                    .text("randomrun.battle.status.in_game").getString();
                com.randomrun.ui.util.GuiText.text(context, textRenderer, Component.literal(statusText),
                    left + TEXT_X, top + TEXT_Y_INFO, UIColors.TEXT_WARNING);
            } else {
                int playerCount = room.getPlayers() != null ? room.getPlayers().size() : 0;

                String minigameIndicator = getRuleIndicators(room);

                String info = minigameIndicator + modeText + " | " + playerCount + "/" + room.getMaxPlayers();
                com.randomrun.ui.util.GuiText.text(context, textRenderer, Component.literal(info),
                    left + TEXT_X, top + TEXT_Y_INFO, UIColors.TEXT_GRAY);
            }
        }

        public static String getRuleIndicators(BattleRoom room) {
            return (room.isVoice() ? "§a[" + com.randomrun.infrastructure.util.LanguageManager.translate("randomrun.room.voice.tag") + "] §r" : "")
                + (room.isUpgrader() ? "§b[Upgrader] §r" : "")
                + (room.isRandomDrop() ? "§6[RD] §r" : "");
        }

        public static String getRoomModeText(BattleRoom room) { return com.randomrun.infrastructure.util.LanguageManager.translate(room.isSharedWorld() ? "randomrun.room.world.shared" : "randomrun.room.world.separate"); }

        public static ItemStack createRoomIcon(String itemId, com.randomrun.features.battle.screen.ItemGlitchEffect glitchEffect) {
            if (itemId == null || itemId.isEmpty()) {
                itemId = glitchEffect.getCurrentItemId();
            }
            Identifier id = Identifier.tryParse(itemId);
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return ItemStack.EMPTY;
            Item item = BuiltInRegistries.ITEM.getValue(id);
            return item != null ? com.randomrun.ui.util.Previews.PreviewItems.stack(item) : ItemStack.EMPTY;
        }
    }
}
