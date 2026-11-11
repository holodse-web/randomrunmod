package com.randomrun.features.battle.screen.room;




import com.randomrun.ui.widget.styled.Buttons.ButtonDefault;
import com.randomrun.ui.widget.styled.Buttons.ButtonRainbow;
import com.randomrun.ui.util.Previews.ItemPreviewRenderer;
import com.randomrun.ui.util.Previews.PreviewItems;
import com.randomrun.features.battle.screen.lobby.LobbyRender.CodeAnimator;
import com.randomrun.features.battle.screen.lobby.LobbyRender.ItemAnimationController;
import com.randomrun.features.battle.manager.BattleManager;
import com.randomrun.features.battle.manager.BattleRoom;
import com.randomrun.features.battle.manager.GameMode;
import com.randomrun.features.battle.screen.widget.PlayerSkinWidget;
import com.randomrun.features.challenges.classic.data.ItemDifficulty;
import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.infrastructure.util.IdCompressor;
import com.randomrun.infrastructure.util.LanguageManager;
import com.randomrun.network.BackendSession;
import com.randomrun.network.PendingInvites;
import com.randomrun.network.SocialService;
import com.randomrun.ui.overlay.LoadingCurtain;
import com.randomrun.ui.screen.main.AbstractRandomRunScreen;
import com.randomrun.ui.screen.main.MainModScreen;
import com.randomrun.ui.theme.Theme;
import com.randomrun.ui.util.GuiText;
import com.randomrun.ui.util.Icons;
import com.randomrun.ui.util.Ui;
import com.randomrun.ui.widget.ItemRoulette;
import com.randomrun.ui.widget.social.FriendsPanel;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.Items;

public class RoomWaitingScreen extends AbstractRandomRunScreen {
    private static final float ITEM_SCALE = 70f;
    private static final int ITEM_Y = 56;
    private static final int ITEM_NAME_Y = 94;
    private static final int CARD_TOP = -66;
    private static final int CARD_BOTTOM = 96;
    private static final int KICK_SIZE = 12;
    private static final int SIDE_PANEL_WIDTH = 160;

    private final Screen parent;
    private final String roomCode;
    private final boolean isHost;
    private final Map<String, PlayerSkinWidget> playerWidgets = new HashMap<>();
    private final ItemAnimationController itemAnimator = new ItemAnimationController();
    private final CodeAnimator codeAnimator;
    private final Set<String> pendingKicks = new HashSet<>();

    private ItemRoulette roulette;
    private String lastTarget;
    private boolean selectingItem;
    private ButtonRainbow startGameButton;
    private String controlState = "";
    private FriendsPanel invitePanel;
    private long openTime;

    public RoomWaitingScreen(Screen parent, String roomCode, boolean isHost) {
        super(LanguageManager.text("randomrun.battle.waiting"));
        this.parent = parent;
        this.roomCode = roomCode;
        this.isHost = isHost;
        this.codeAnimator = new CodeAnimator(roomCode);
        BattleRoom room = BattleManager.getInstance().getCurrentRoom();
        this.lastTarget = room != null ? room.getTargetItem() : null;
    }

    private boolean showInvitePanel() {
        return RandomRunMod.getInstance().getConfig().isOnlineMode() && BackendSession.isAuthenticated() && width >= 560;
    }

    private int centerX() {
        return showInvitePanel() ? (width - SIDE_PANEL_WIDTH - 10) / 2 : width / 2;
    }

    @Override
    protected void init() {
        super.init();
        if (openTime == 0) openTime = System.currentTimeMillis();
        startGameButton = null;
        int centerX = centerX();
        BattleRoom room = BattleManager.getInstance().getCurrentRoom();
        controlState = roomControlState(room);

        if (isHost && room != null) {
            int playerCount = room.getPlayers() != null ? room.getPlayers().size() : 1;
            boolean roomFull = playerCount >= room.getMaxPlayers();
            if (playerCount >= 2 && !room.hasItemSelected() && roulette == null) {
                ButtonRainbow random = addRenderableWidget(new ButtonRainbow(centerX - 100, height - 55, 200, 20,
                    LanguageManager.text("randomrun.battle.random_item"), button -> startRandomItemSelection(), 0, 0.12f));
                random.active = !selectingItem;
            }
            if (room.hasItemSelected() && roomFull && roulette == null) {
                startGameButton = addRenderableWidget(new ButtonRainbow(centerX - 100, height - 55, 200, 20,
                    LanguageManager.text("randomrun.battle.start_game"), button -> startGame(), 1, 0.15f));
                startGameButton.active = !BattleManager.getInstance().isLoadingTriggered();
            }
        }

        addRenderableWidget(new ButtonDefault(centerX - 100, height - 30, 200, 20,
            LanguageManager.text("randomrun.battle.leave_room"), button -> onClose(), 2, 0.2f));

        invitePanel = null;
        if (showInvitePanel()) {
            invitePanel = new FriendsPanel(this, FriendsPanel.Context.ROOM, null);
            invitePanel.layout(width - SIDE_PANEL_WIDTH - 10, 34, width - 10, height - 40, this::addRenderableWidget);
            sendPendingInvites();
        }
    }

    private void sendPendingInvites() {
        for (String name : PendingInvites.drain()) {
            SocialService.getInstance().invite(name).exceptionally(error -> null);
        }
    }

    private void startGame() {
        if (BattleManager.getInstance().isLoadingTriggered() || roulette != null) return;
        if (startGameButton != null) startGameButton.active = false;
        LoadingCurtain.close(LoadingCurtain.Mode.HOST, () -> BattleManager.getInstance().setStatusLoading());
    }

    private void startRandomItemSelection() {
        if (roulette != null || selectingItem) return;
        selectingItem = true;
        BattleManager.getInstance().randomSelectItemInRoom(roomCode).whenComplete((item, error) ->
            Minecraft.getInstance().execute(() -> {
                selectingItem = false;
                if (error != null) RandomRunMod.LOGGER.error("[RoomWaiting] Не удалось выбрать предмет", error);
                if (Minecraft.getInstance().gui.screen() == this) rebuildWidgets();
            }));
    }

    private static Item resolveItem(String compressed) {
        if (compressed == null || compressed.isEmpty()) return null;
        try {
            Identifier id = Identifier.tryParse(IdCompressor.decompress(compressed));
            Item item = id == null ? null : BuiltInRegistries.ITEM.getValue(id);
            return item == null || item == Items.AIR ? null : item;
        } catch (RuntimeException error) {
            return null;
        }
    }

    @Override
    public void tick() {
        super.tick();
        BattleRoom room = BattleManager.getInstance().getCurrentRoom();
        if (room == null || !roomCode.equals(room.getRoomCode())) {
            Minecraft.getInstance().gui.setScreen(parent);
            return;
        }
        String target = room.getTargetItem();
        if ((lastTarget == null || lastTarget.isEmpty()) && target != null && !target.isEmpty()) {
            Item item = resolveItem(target);
            if (item != null) {
                roulette = new ItemRoulette(ItemDifficulty.getAllItems(false), item, ItemRoulette.seedFor(roomCode + ":" + target));
                rebuildWidgets();
            }
        }
        lastTarget = target;
        if (startGameButton != null) startGameButton.active = !BattleManager.getInstance().isLoadingTriggered();
        if (roulette != null && roulette.isComplete()) {
            roulette = null;
            rebuildWidgets();
            return;
        }
        String state = roomControlState(room);
        if (roulette == null && !state.equals(controlState)) rebuildWidgets();
    }

    private String roomControlState(BattleRoom room) {
        return room == null ? "" : room.getPlayers() + ":" + room.getMaxPlayers() + ":"
            + room.getTargetItem() + ":" + room.hasItemSelected() + ":" + room.getStatus();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        BattleRoom room = BattleManager.getInstance().getCurrentRoom();
        if (room == null) {
            Minecraft.getInstance().gui.setScreen(new MainModScreen(new TitleScreen()));
            return;
        }
        float fade = Math.min(1f, (System.currentTimeMillis() - openTime) / 300f);
        int centerX = centerX();
        int centerY = height / 2;
        long elapsed = codeAnimator.getElapsedTime();
        itemAnimator.update(elapsed, delta);

        renderHeader(context, room, centerX, mouseX, mouseY);

        if (room.getPlayers() != null) renderPlayers(context, room, centerX, centerY, mouseX, mouseY, fade);

        if (roulette == null) renderTargetItem(context, room, centerX);
        renderStatus(context, room, centerX);
        if (invitePanel != null) {
            Ui.panel(context, width - SIDE_PANEL_WIDTH - 10, 34, width - 10, height - 40, fade);
        }

        super.extractRenderState(context, mouseX, mouseY, delta);

        if (invitePanel != null) invitePanel.extract(context, mouseX, mouseY, fade);
        if (roulette != null) roulette.extract(context, centerX, ITEM_Y, Math.min(320, width - 40 - (invitePanel != null ? SIDE_PANEL_WIDTH : 0)), 40);
    }

    private void renderHeader(GuiGraphicsExtractor context, BattleRoom room, int centerX, int mouseX, int mouseY) {
        Theme theme = Theme.current();
        Component code = LanguageManager.text("randomrun.battle.room_code_display", codeAnimator.getAnimatedCode());
        int[] bounds = codeBounds(centerX);
        boolean hover = Ui.inside(mouseX, mouseY, bounds[0], bounds[1], bounds[2] - bounds[0], bounds[3] - bounds[1]);
        context.fill(bounds[0], bounds[1], bounds[2], bounds[3], Ui.argb(hover ? theme.buttonHover : theme.buttonBase, 0.85f));
        Ui.border(context, bounds[0], bounds[1], bounds[2], bounds[3], 1, 0xFF000000 | theme.border);
        GuiText.text(context, font, code, bounds[0] + 6, bounds[1] + 4, 0xFFFFD700, true);
        if (hover) context.setTooltipForNextFrame(font, LanguageManager.text("randomrun.battle.copy_code"), mouseX, mouseY);

        int players = room.getPlayers() != null ? room.getPlayers().size() : 1;
        String count = players + "/" + room.getMaxPlayers();
        int chipX = bounds[2] + 6;
        chipX += Ui.chip(context, font, Component.literal("👥 " + count), chipX, bounds[1] + 2, 0x99000000, 0xFFFFFFFF) + 4;
        if (room.isPrivate()) chipX += Ui.chip(context, font, LanguageManager.text("randomrun.battle.private"), chipX, bounds[1] + 2, 0x997F1D1D, 0xFFFCA5A5) + 4;
        if (room.isUpgrader()) chipX += Ui.chip(context, font, Component.literal("RRM Upgrader"), chipX, bounds[1] + 2, 0x99155E75, 0xFF67E8F9) + 4;
        if (room.getGameMode() != GameMode.DEFAULT) Ui.chip(context, font, Component.literal(room.getGameMode().getDisplayName()), chipX, bounds[1] + 2, 0x99713F12, 0xFFFDE68A);
        if (room.isUpgrader() || !room.getGameMode().countsForLeaderboard()) {
            GuiText.centeredText(context, font, LanguageManager.text("randomrun.battle.no_leaderboard"), centerX, 26, 0xFF8A8A93);
        }
    }

    private int[] codeBounds(int centerX) {
        Component code = LanguageManager.text("randomrun.battle.room_code_display", codeAnimator.getAnimatedCode());
        int width = font.width(code) + 12;
        int x = centerX - width / 2 - 30;
        return new int[] {x, 8, x + width, 24};
    }

    private void renderTargetItem(GuiGraphicsExtractor context, BattleRoom room, int centerX) {
        Item item = resolveItem(room.getTargetItem());
        if (item == null) {
            int size = 44;
            context.fill(centerX - size / 2, ITEM_Y - size / 2, centerX + size / 2, ITEM_Y + size / 2, 0x55000000);
            Ui.border(context, centerX - size / 2, ITEM_Y - size / 2, centerX + size / 2, ITEM_Y + size / 2, 1, 0x80FFFFFF);
            Ui.centeredScaled(context, font, Component.literal("?"), centerX, ITEM_Y - 8, 2f, 0xFFFFFFFF);
            if (selectingItem) GuiText.centeredText(context, font, LanguageManager.text("randomrun.battle.rolling"), centerX, ITEM_NAME_Y, 0xFFFACC15);
            return;
        }
        ItemPreviewRenderer.extract(context, PreviewItems.stack(item), centerX, ITEM_Y + itemAnimator.getLevitationOffset(),
            ITEM_SCALE, itemAnimator.getDragRotationX(), itemAnimator.getRotationY() + itemAnimator.getDragRotationY(), ItemDisplayContext.FIXED);
        GuiText.centeredText(context, font, Component.translatable(item.getDescriptionId()), centerX, ITEM_NAME_Y, 0xFFFFFFFF);
    }

    private void renderStatus(GuiGraphicsExtractor context, BattleRoom room, int centerX) {
        String key;
        int color;
        if (BattleManager.getInstance().isLoadingTriggered() || room.getStatus() == BattleRoom.RoomStatus.LOADING) {
            key = isHost ? "randomrun.battle.status.host_loading" : "randomrun.battle.status.guest_loading";
            color = 0xFF67E8F9;
        } else if (room.getPlayers() == null || room.getPlayers().size() < room.getMaxPlayers()) {
            key = "randomrun.battle.status.waiting_players";
            color = 0xFFA1A1AA;
        } else if (roulette != null) {
            key = "randomrun.battle.rolling";
            color = 0xFFFACC15;
        } else if (!room.hasItemSelected()) {
            key = isHost ? "randomrun.battle.status.choose_item" : "randomrun.battle.status.host_choosing";
            color = 0xFFFACC15;
        } else {
            key = isHost ? "randomrun.battle.status.ready_host" : "randomrun.battle.status.ready_guest";
            color = 0xFF4ADE80;
        }
        Component text = LanguageManager.text(key);
        int y = height - 70;
        int w = font.width(text) + 16;
        context.fill(centerX - w / 2, y - 3, centerX + w / 2, y + 11, 0xAA000000);
        context.fill(centerX - w / 2, y - 3, centerX - w / 2 + 2, y + 11, color);
        GuiText.centeredText(context, font, text, centerX + 1, y, color);
    }

    private int playerSpacing(int maxPlayers) {
        int available = width - 40 - (showInvitePanel() ? SIDE_PANEL_WIDTH + 10 : 0);
        return Math.max(64, Math.min(118, available / Math.max(1, maxPlayers)));
    }

    private int playerX(int index, int maxPlayers) {
        int spacing = playerSpacing(maxPlayers);
        return centerX() - (maxPlayers - 1) * spacing / 2 + index * spacing;
    }

    private int cardWidth(int maxPlayers) {
        return playerSpacing(maxPlayers) - 8;
    }

    private void renderPlayers(GuiGraphicsExtractor context, BattleRoom room, int centerX, int centerY, int mouseX, int mouseY, float fade) {
        List<String> players = room.getPlayers();
        playerWidgets.keySet().removeIf(name -> !players.contains(name));
        for (String player : players) playerWidgets.computeIfAbsent(player, PlayerSkinWidget::new);
        int maxPlayers = room.getMaxPlayers();
        int cardWidth = cardWidth(maxPlayers);
        long now = System.currentTimeMillis();
        for (int i = 0; i < maxPlayers; i++) {
            float appear = Ui.easeOut((now - openTime - i * 60L) / 380f);
            if (appear <= 0f) continue;
            int x = playerX(i, maxPlayers);
            int lift = Math.round((1f - appear) * 14);
            context.pose().pushMatrix();
            context.pose().translate(0, lift);
            if (i < players.size()) renderPlayer(context, room, players.get(i), x, centerY, cardWidth, mouseX, mouseY, appear * fade);
            else renderEmptySlot(context, x, centerY, cardWidth, appear * fade);
            context.pose().popMatrix();
        }
    }

    private void renderCard(GuiGraphicsExtractor context, int x, int centerY, int cardWidth, int accent, float fade, boolean empty) {
        Theme theme = Theme.current();
        int left = x - cardWidth / 2, right = x + cardWidth / 2, top = centerY + CARD_TOP, bottom = centerY + CARD_BOTTOM;
        if (empty) {
            context.fill(left, top, right, bottom, Ui.argb(0x000000, 0.22f * fade));
            int dash = Ui.argb(0xFFFFFF, 0.12f * fade);
            for (int px = left; px < right; px += 6) {
                context.fill(px, top, Math.min(px + 3, right), top + 1, dash);
                context.fill(px, bottom - 1, Math.min(px + 3, right), bottom, dash);
            }
            for (int py = top; py < bottom; py += 6) {
                context.fill(left, py, left + 1, Math.min(py + 3, bottom), dash);
                context.fill(right - 1, py, right, Math.min(py + 3, bottom), dash);
            }
            return;
        }
        context.fillGradient(left, top, right, bottom, Ui.argb(theme.panel, 0.72f * fade), Ui.argb(0x000000, 0.62f * fade));
        context.fillGradient(left, top, right, top + 40, Ui.argb(accent, 0.16f * fade), Ui.argb(accent, 0f));
        Ui.border(context, left, top, right, bottom, 1, Ui.argb(0xFFFFFF, 0.08f * fade));
        context.fill(left, top, right, top + 2, Ui.argb(accent, 0.95f * fade));
        context.fill(left + 1, bottom - 32, right - 1, bottom - 1, Ui.argb(0x000000, 0.35f * fade));
    }

    private void renderEmptySlot(GuiGraphicsExtractor context, int x, int centerY, int cardWidth, float fade) {
        renderCard(context, x, centerY, cardWidth, 0, fade, true);
        float pulse = 0.35f + 0.25f * (float) Math.sin(System.currentTimeMillis() / 400.0);
        Icons.PLUS.drawCentered(context, x - 10, centerY, 20, 16, Ui.argb(0xFFFFFF, pulse * fade));
        GuiText.centeredText(context, font, LanguageManager.text("randomrun.lobby.empty_slot"), x, centerY + CARD_BOTTOM - 22, Ui.argb(0x71717A, fade));
    }

    private void renderPlayer(GuiGraphicsExtractor context, BattleRoom room, String player, int x, int centerY, int cardWidth, int mouseX, int mouseY, float fade) {
        PlayerSkinWidget widget = playerWidgets.get(player);
        if (widget == null) return;
        boolean host = room.isHost(player);
        boolean ready = room.isPlayerReady(player);
        int accent = host ? 0xFFC247 : ready ? 0x4ADE80 : Theme.current().buttonHover;
        renderCard(context, x, centerY, cardWidth, accent, fade, false);
        int size = Math.min(96, Math.round(cardWidth * 1.2f));
        context.enableScissor(x - cardWidth / 2 + 1, centerY + CARD_TOP + 2, x + cardWidth / 2 - 1, centerY + CARD_BOTTOM - 32);
        widget.extractRenderState(context, x, centerY - 2, size,
            Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true));
        context.disableScissor();

        int nameY = centerY + CARD_BOTTOM - 27;
        int crown = host ? Icons.CROWN.width + 3 : 0;
        String name = Ui.ellipsize(font, player, cardWidth - 10 - crown);
        int nameWidth = font.width(name) + crown;
        int nameX = x - nameWidth / 2;
        if (host) Icons.CROWN.draw(context, nameX, nameY + 1, Ui.argb(0xFFC247, fade));
        GuiText.text(context, font, name, nameX + crown, nameY, Ui.argb(host ? 0xFFE7A3 : 0xFFFFFF, fade), true);

        if (ready) {
            int statusY = nameY + 12;
            String status = LanguageManager.translate("randomrun.battle.ready_badge");
            int statusWidth = font.width(status) + 7;
            int statusX = x - statusWidth / 2;
            context.fill(statusX, statusY + 2, statusX + 4, statusY + 6, Ui.argb(0x4ADE80, fade));
            GuiText.text(context, font, status, statusX + 7, statusY, Ui.argb(0x86EFAC, fade), false);
        }

        if (isHost && !host) {
            int kx = x + cardWidth / 2 - KICK_SIZE - 3, ky = centerY + CARD_TOP + 5;
            boolean hover = Ui.inside(mouseX, mouseY, kx, ky, KICK_SIZE, KICK_SIZE);
            context.fill(kx, ky, kx + KICK_SIZE, ky + KICK_SIZE, Ui.argb(hover ? 0xDC2626 : 0x7F1D1D, (hover ? 1f : 0.8f) * fade));
            Icons.CROSS.drawCentered(context, kx, ky, KICK_SIZE, KICK_SIZE, Ui.argb(0xFFFFFF, fade));
            if (hover) context.setTooltipForNextFrame(font, LanguageManager.text("randomrun.battle.kick"), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        if (invitePanel != null && invitePanel.mouseClicked(mouseX, mouseY)) return true;
        int[] bounds = codeBounds(centerX());
        if (Ui.inside(mouseX, mouseY, bounds[0], bounds[1], bounds[2] - bounds[0], bounds[3] - bounds[1])) {
            Minecraft.getInstance().keyboardHandler.setClipboard(roomCode);
            com.randomrun.ui.widget.overlay.CustomNotification.show(LanguageManager.translate("randomrun.battle.code_copied", roomCode));
            Ui.click();
            return true;
        }
        if (isHost && handleKickClick(mouseX, mouseY)) return true;
        int centerX = centerX();
        if (roulette == null && mouseX >= centerX - 40 && mouseX <= centerX + 40 && mouseY >= ITEM_Y - 35 && mouseY <= ITEM_Y + 35) {
            itemAnimator.startDrag(mouseX, mouseY);
            return true;
        }
        for (PlayerSkinWidget widget : playerWidgets.values()) {
            if (widget.mouseClicked(event, doubleClick)) return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private boolean handleKickClick(double mouseX, double mouseY) {
        BattleRoom room = BattleManager.getInstance().getCurrentRoom();
        if (room == null || room.getPlayers() == null) return false;
        List<String> players = room.getPlayers();
        int maxPlayers = room.getMaxPlayers();
        int cardWidth = cardWidth(maxPlayers);
        for (int i = 0; i < players.size(); i++) {
            String player = players.get(i);
            if (room.isHost(player)) continue;
            int x = playerX(i, maxPlayers);
            int kx = x + cardWidth / 2 - KICK_SIZE - 3, ky = height / 2 + CARD_TOP + 5;
            if (Ui.inside(mouseX, mouseY, kx, ky, KICK_SIZE, KICK_SIZE)) {
                if (!pendingKicks.add(player)) return true;
                BattleManager.getInstance().kickPlayer(roomCode, player).whenComplete((success, error) ->
                    Minecraft.getInstance().execute(() -> {
                        pendingKicks.remove(player);
                        if (Minecraft.getInstance().gui.screen() != this) return;
                        if (error != null) RandomRunMod.LOGGER.error("[RoomWaiting] Ошибка исключения игрока", error);
                        else if (Boolean.TRUE.equals(success)) rebuildWidgets();
                    }));
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        for (PlayerSkinWidget widget : playerWidgets.values()) widget.mouseReleased();
        if (event.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) itemAnimator.stopDrag();
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double deltaX, double deltaY) {
        if (itemAnimator.isDragging()) {
            itemAnimator.updateDrag(event.x(), event.y());
            return true;
        }
        for (PlayerSkinWidget widget : playerWidgets.values()) {
            if (widget.mouseDragged(event.x(), event.y(), deltaX)) return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (invitePanel != null && invitePanel.mouseScrolled(mouseX, mouseY, verticalAmount)) return true;
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void onClose() {
        playerWidgets.clear();
        if (LoadingCurtain.isCovering()) LoadingCurtain.abort();
        BattleManager.getInstance().leaveRoom();
        Minecraft.getInstance().gui.setScreen(parent);
    }
}
