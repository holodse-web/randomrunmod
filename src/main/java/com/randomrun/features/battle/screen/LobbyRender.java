package com.randomrun.features.battle.screen;

import com.randomrun.ui.util.InterfaceStyle;

import com.randomrun.features.battle.manager.BattleRoom;
import com.randomrun.features.battle.screen.LobbyLogic.LobbyUpdates.ConnectionStatus;
import com.randomrun.features.speedrun.ItemDifficulty;
import com.randomrun.infrastructure.RandomRunMod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;
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
        public static int BG_DISABLED() { return InterfaceStyle.isClassic() ? 0xFF383833 : 0x80000000; }
        public static int BG_PANEL() { return InterfaceStyle.isClassic() ? 0xFF494943 : 0xCC000000; }

        public static int BORDER_NORMAL() { return 0xFF000000 | com.randomrun.ui.util.Theme.current().buttonHover; }
        public static int BORDER_HOVERED() { return 0xFF000000 | com.randomrun.ui.util.Theme.current().overlay; }
        public static final int BORDER_DISABLED = 0xFF555555;

        public static final int TEXT_WHITE = 0xFFFFFF;
        public static final int TEXT_GRAY = 0xAAAAAA;
        public static final int TEXT_WARNING = 0xFFAA00;
        public static final int TEXT_ERROR = 0xFF5555;
        public static final int TEXT_SUCCESS = 0x55FF55;
        public static final int TEXT_YELLOW = 0xFFFF55;

        public static int BAR_BG() { return InterfaceStyle.isClassic() ? 0xFF292926 : 0xFF2a2a3c; }
        public static int BAR_FULL() { return InterfaceStyle.isClassic() ? 0xFFA8D477 : 0xFF00FF00; }
        public static int BAR_PARTIAL() { return 0xFF000000 | com.randomrun.ui.util.Theme.current().buttonHover; }

        public static int SLOT_BG() { return InterfaceStyle.isClassic() ? 0xFF55554D : 0x40FFFFFF; }
        public static int SLOT_BORDER() { return InterfaceStyle.isClassic() ? 0xFFA0A095 : 0x80FFFFFF; }

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

    static final class ItemGlitchEffect {
        private static final long INTERVAL_MS = 400;
        private static final String FALLBACK_ITEM = "minecraft:barrier";

        private final List<String> itemIds = new ArrayList<>();
        private long lastUpdateTime;
        private int currentIndex;

        ItemGlitchEffect(boolean allowUnobtainable) {
            try {
                for (Item item : ItemDifficulty.getAllItems(allowUnobtainable)) {
                    itemIds.add(BuiltInRegistries.ITEM.getKey(item).toString());
                }
                Collections.shuffle(itemIds);
            } catch (Exception error) {
                RandomRunMod.LOGGER.error("[Lobby] Item animation initialization failed", error);
                itemIds.add(FALLBACK_ITEM);
            }
        }

        String getCurrentItemId() {
            if (itemIds.isEmpty()) return FALLBACK_ITEM;

            long now = System.currentTimeMillis();
            if (now - lastUpdateTime > INTERVAL_MS) {
                currentIndex = (currentIndex + 1) % itemIds.size();
                lastUpdateTime = now;
            }
            return itemIds.get(currentIndex);
        }
    }

    public static class ConnectionStatusRenderer {
        public static void extractRenderState(GuiGraphicsExtractor context, Font textRenderer,
                                ConnectionStatus status,
                                int x, int y) {
            String statusText = getStatusText(status);
            int color = getStatusColor(status);
            String icon = getStatusIcon(status);

            String fullText = icon + " " + statusText;
            com.randomrun.ui.util.GuiText.text(context, textRenderer, Component.literal(fullText), x, y, color);
        }

        private static String getStatusText(ConnectionStatus status) {
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

        private static int getStatusColor(ConnectionStatus status) {
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

        private static String getStatusIcon(ConnectionStatus status) {
            switch (status) {
                case CONNECTED:
                    if (InterfaceStyle.isClassic()) {
                        return "■";
                    } else {
                        return "●";
                    }
                case CONNECTING:
                    if (InterfaceStyle.isClassic()) {
                        return "…";
                    } else {
                        return "◐";
                    }
                case DISCONNECTED:
                    if (InterfaceStyle.isClassic()) {
                        return "□";
                    } else {
                        return "○";
                    }
                case ERROR:
                    if (InterfaceStyle.isClassic()) {
                        return "×";
                    } else {
                        return "✖";
                    }
                default:
                    return "…";
            }
        }
    }

    public static class RoomListRenderer {
        private static final int TEXT_X = 50;

        private record RoomTag(String key, int classicColor, int color, Predicate<BattleRoom> visible) {}

        private static final List<RoomTag> TAGS = List.of(
            new RoomTag("randomrun.lobby.tag.shared", 0xC9C7BC, 0xD4D4D8, BattleRoom::isSharedWorld),
            new RoomTag("randomrun.lobby.tag.separate", 0xC9C7BC, 0xD4D4D8, room -> !room.isSharedWorld()),
            new RoomTag("randomrun.lobby.tag.streamer", 0xE6A2E8, 0xE879F9, BattleRoom::hasStreamerMode),
            new RoomTag("randomrun.room.voice.tag", 0xA8D477, 0x4ADE80, BattleRoom::isVoice),
            new RoomTag("randomrun.lobby.tag.upgrader", 0x86C9D1, 0x67E8F9, BattleRoom::isUpgrader),
            new RoomTag("randomrun.lobby.tag.random_drop", 0xE5C56B, 0xFACC15, BattleRoom::isRandomDrop)
        );

        public static void renderRoomEntry(GuiGraphicsExtractor context, Font font, BattleRoom room,
                                           ItemStack iconStack, int left, int top, int width, int height, boolean hovered) {
            boolean classic = InterfaceStyle.isClassic();
            renderIcon(context, iconStack, left, top, height);
            int rightEdge = left + width - 10;
            int infoLeft = renderPlayers(context, font, room, rightEdge, top, hovered);

            String host = room.getHost() != null && !room.getHost().isEmpty() ? room.getHost() : "?";
            int x = left + TEXT_X;
            com.randomrun.ui.util.Previews.Heads.draw(context, host, x, top + 8, 9, 0);
            x += 13;
            String title = com.randomrun.ui.util.Ui.ellipsize(font, com.randomrun.infrastructure.util.LanguageManager.translate("randomrun.lobby.room_title", host), infoLeft - x - 16);
            com.randomrun.ui.util.GuiText.text(context, font, title, x, top + 9, room.isJoinable() ? 0xFFFFFFFF : 0xFFB4B4AE, true);
            if (room.isPrivate()) com.randomrun.ui.util.Icons.LOCK.draw(context, x + font.width(title) + 5, top + 9, classic ? 0xFFFFD56B : 0xFFFACC15);

            int chipX = left + TEXT_X;
            int chipY = top + 23;
            for (RoomTag tag : TAGS) {
                if (!tag.visible().test(room)) continue;
                Component text = com.randomrun.infrastructure.util.LanguageManager.text(tag.key());
                int color = classic ? tag.classicColor() : tag.color();
                if (chipX + font.width(text) + 8 > infoLeft - 6) break;
                int bg = classic ? 0xFF2E2E2A : com.randomrun.ui.util.Ui.argb(color, 0.16f);
                chipX += com.randomrun.ui.util.Ui.chip(context, font, text, chipX, chipY, bg, 0xFF000000 | color) + 4;
            }
        }

        private static int renderPlayers(GuiGraphicsExtractor context, Font font, BattleRoom room, int right, int top, boolean hovered) {
            boolean classic = InterfaceStyle.isClassic();
            int players = room.getPlayerCount();
            int max = Math.max(1, room.getMaxPlayers());
            String count = players + "/" + max;
            int countX = right - font.width(count);
            com.randomrun.ui.util.GuiText.text(context, font, count, countX, top + 9, 0xFFFFFFFF, true);
            com.randomrun.ui.util.Icons.PEOPLE.draw(context, countX - com.randomrun.ui.util.Icons.PEOPLE.width - 4, top + 9, classic ? 0xFFB8B8AA : 0xFFA1A1AA);

            int accent = classic ? 0xFFA8D477 : 0xFF000000 | com.randomrun.ui.util.Theme.current().accent;
            int pip = 5, gap = 2;
            int pipsWidth = max * pip + (max - 1) * gap;
            int pipX = right - pipsWidth;
            for (int i = 0; i < max; i++) {
                int px = pipX + i * (pip + gap);
                context.fill(px, top + 21, px + pip, top + 21 + pip, i < players ? accent : (classic ? 0xFF292926 : 0x40FFFFFF));
            }

            String key;
            int color;
            String playing = null;
            if (room.getStatus() != BattleRoom.RoomStatus.WAITING) {
                key = "randomrun.battle.status.in_game"; color = classic ? 0xFFDD8D7C : 0xFFF87171;
                long start = room.getServerStartTime();
                if (start > 0) {
                    long minutes = Math.max(0, (com.randomrun.network.RealtimeClient.getInstance().serverNow() - start) / 60000);
                    playing = minutes < 60 ? com.randomrun.infrastructure.util.LanguageManager.translate("randomrun.lobby.in_game_min", Math.max(1, minutes))
                        : com.randomrun.infrastructure.util.LanguageManager.translate("randomrun.lobby.in_game_hours", minutes / 60, minutes % 60);
                }
            }
            else if (players >= max) { key = "randomrun.lobby.full"; color = classic ? 0xFF9A978D : 0xFF71717A; }
            else { key = "randomrun.lobby.join"; color = hovered ? (classic ? 0xFFFFFFA0 : 0xFF4ADE80) : (classic ? 0xFFB8B8AA : 0xFFA1A1AA); }
            String status = playing != null ? playing : com.randomrun.infrastructure.util.LanguageManager.translate(key);
            boolean join = key.equals("randomrun.lobby.join");
            int statusWidth = font.width(status) + (join ? 7 : 0);
            int statusX = right - statusWidth;
            com.randomrun.ui.util.GuiText.text(context, font, status, statusX, top + 30, color, false);
            if (join) com.randomrun.ui.util.Icons.CHEVRON_RIGHT.draw(context, right - 4, top + 31, color);
            return Math.min(countX - com.randomrun.ui.util.Icons.PEOPLE.width - 4, Math.min(pipX, statusX));
        }

        private static void renderIcon(GuiGraphicsExtractor context, ItemStack iconStack, int left, int top, int height) {
            int size = Math.min(36, height - 8), margin = (height - size) / 2;
            int x0 = left + margin + 1, y0 = top + margin;
            if (InterfaceStyle.isClassic()) {
                com.randomrun.ui.util.Ui.slot(context, x0, y0, x0 + size, y0 + size, 1f);
            }
            if (!iconStack.isEmpty()) {
                com.randomrun.ui.util.Previews.ItemPreviewRenderer.extractInSlot(context, iconStack,
                    x0, y0, x0 + size, y0 + size, InterfaceStyle.isClassic() ? 4 : 2);
            }
        }

        public static ItemStack createRoomIcon(String itemId, ItemGlitchEffect glitchEffect) {
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
