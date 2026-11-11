package com.randomrun.ui.widget.social;


import com.randomrun.features.battle.manager.BattleSupport.RoomJoinResult;
import com.randomrun.features.battle.manager.BattleSupport.RoomJoiner;
import com.randomrun.features.battle.manager.BattleManager;
import com.randomrun.infrastructure.util.IdCompressor;
import com.randomrun.infrastructure.util.LanguageManager;
import com.randomrun.network.BackendSession;
import com.randomrun.network.SocialService;
import com.randomrun.ui.theme.Theme;
import com.randomrun.ui.util.GuiText;
import com.randomrun.ui.util.Heads;
import com.randomrun.ui.util.Icons;
import com.randomrun.ui.util.Ui;
import com.randomrun.ui.widget.styled.TextFieldStyled;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public final class FriendsPanel {
    public enum Tab { FRIENDS, REQUESTS, INVITES }
    public enum Context { MENU, ROOM }

    private static final int ROW = 24;
    private static final int ICON = 14;
    private static Tab lastTab = Tab.FRIENDS;

    private final Screen screen;
    private final Context context;
    private final Runnable onInviteFromMenu;
    private int left, top, right, bottom;
    private Tab tab = lastTab;
    private TextFieldStyled nameField;
    private double scroll;
    private String feedback;
    private boolean feedbackError;
    private long feedbackTime;
    private boolean busy;
    private long rowsShownAt = System.currentTimeMillis();
    private final List<Hit> hits = new ArrayList<>();

    private record Hit(int x, int y, int w, int h, Runnable action) {}
    private record Colors(int base, int hover, int accent, int title) {}

    private Colors colors() {
        if (context == Context.MENU) return new Colors(0x1A5C1A, 0x2D8F2D, 0x4ADE80, 0x4ADE80);
        Theme theme = Theme.current();
        return new Colors(theme.buttonBase, theme.buttonHover, theme.accent, theme.accent);
    }

    public FriendsPanel(Screen screen, Context context, Runnable onInviteFromMenu) {
        this.screen = screen;
        this.context = context;
        this.onInviteFromMenu = onInviteFromMenu;
    }

    public void layout(int left, int top, int right, int bottom, Consumer<AbstractWidget> addWidget) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        Font font = Minecraft.getInstance().font;
        nameField = new TextFieldStyled(font, left + 6, contentTop(), right - left - 32, 16, LanguageManager.text("randomrun.friends.add.placeholder"), 0f) {
            @Override
            public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
                if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_RETURN || event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_NUMPADENTER) {
                    sendRequest();
                    return true;
                }
                return super.keyPressed(event);
            }
        };
        Colors colors = colors();
        nameField.setColors(colors.base(), colors.hover());
        nameField.setBorderColor(colors.hover());
        nameField.setMaxLength(16);
        nameField.setResponder(value -> {
            String clean = value.replaceAll("[^A-Za-z0-9_]", "");
            if (!clean.equals(value)) nameField.setValue(clean);
        });
        nameField.setCenteredPlaceholder(LanguageManager.text("randomrun.friends.add.placeholder"));
        nameField.visible = tab == Tab.REQUESTS;
        addWidget.accept(nameField);
        SocialService.getInstance().refresh();
    }

    private int contentTop() {
        return top + 42;
    }

    private void setTab(Tab value) {
        if (tab == value) return;
        tab = value;
        lastTab = value;
        scroll = 0;
        rowsShownAt = System.currentTimeMillis();
        if (nameField != null) {
            nameField.visible = tab == Tab.REQUESTS;
            if (!nameField.visible) nameField.setFocused(false);
        }
        Ui.click();
    }

    public void extract(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float fade) {
        hits.clear();
        Font font = Minecraft.getInstance().font;
        Colors theme = colors();
        SocialService.Snapshot data = SocialService.getInstance().snapshot();

        Component title = LanguageManager.text("randomrun.friends.title");
        GuiText.text(graphics, font, title, left + 8, top + 8, Ui.argb(theme.title(), fade), true);
        String online = LanguageManager.translate("randomrun.friends.online_count", data.onlineCount(), data.friends().size());
        int onlineX = right - 8 - font.width(online);
        graphics.fill(onlineX - 8, top + 10, onlineX - 4, top + 14, Ui.argb(0x4ADE80, fade));
        GuiText.text(graphics, font, online, onlineX, top + 8, Ui.argb(0xA1A1AA, fade), false);

        if (!BackendSession.isAuthenticated()) {
            GuiText.centeredText(graphics, font, LanguageManager.text("randomrun.friends.offline"), (left + right) / 2, top + 50, Ui.argb(0xA1A1AA, fade));
            return;
        }

        int tabY = top + 22;
        int tabW = (right - left - 12) / 3;
        drawTab(graphics, font, Tab.FRIENDS, LanguageManager.translate("randomrun.friends.tab.friends"), 0, left + 6, tabY, tabW, mouseX, mouseY, theme, fade);
        drawTab(graphics, font, Tab.REQUESTS, LanguageManager.translate("randomrun.friends.tab.requests"), data.incoming().size(), left + 6 + tabW, tabY, tabW, mouseX, mouseY, theme, fade);
        drawTab(graphics, font, Tab.INVITES, LanguageManager.translate("randomrun.friends.tab.invites"), data.invites().size(), left + 6 + tabW * 2, tabY, right - left - 12 - tabW * 2, mouseX, mouseY, theme, fade);

        int listTop = tab == Tab.REQUESTS ? contentTop() + 22 : contentTop();
        int listBottom = bottom - 26;
        if (tab == Tab.REQUESTS) {
            boolean hover = Ui.inside(mouseX, mouseY, right - 24, contentTop(), 18, 16);
            graphics.fill(right - 24, contentTop(), right - 6, contentTop() + 16, Ui.argb(hover ? theme.hover() : theme.base(), fade));
            Ui.border(graphics, right - 24, contentTop(), right - 6, contentTop() + 16, 1, Ui.argb(theme.hover(), fade));
            Icons.PLUS.drawCentered(graphics, right - 24, contentTop(), 18, 16, Ui.argb(0xFFFFFF, fade));
            hits.add(new Hit(right - 24, contentTop(), 18, 16, this::sendRequest));
        }

        graphics.enableScissor(left + 2, listTop, right - 2, listBottom);
        List<Row> rows = rows(data);
        int contentHeight = rows.size() * ROW;
        double maxScroll = Math.max(0, contentHeight - (listBottom - listTop));
        scroll = Math.clamp(scroll, 0, maxScroll);
        int y = listTop - (int) scroll;
        long now = System.currentTimeMillis();
        for (int index = 0; index < rows.size(); index++) {
            if (y + ROW >= listTop && y <= listBottom) {
                float appear = Ui.easeOut((now - rowsShownAt - index * 45L) / 300f);
                if (appear > 0f) {
                    graphics.pose().pushMatrix();
                    graphics.pose().translate((1f - appear) * 18f, 0);
                    rows.get(index).draw(graphics, font, y, mouseX, mouseY, listTop, listBottom, fade * appear);
                    graphics.pose().popMatrix();
                }
            }
            y += ROW;
        }
        if (rows.isEmpty()) {
            String key = switch (tab) {
                case FRIENDS -> "randomrun.friends.empty.friends";
                case REQUESTS -> "randomrun.friends.empty.requests";
                case INVITES -> "randomrun.friends.empty.invites";
            };
            int textY = listTop + 8;
            for (var line : font.split(LanguageManager.text(key), right - left - 20)) {
                GuiText.centeredText(graphics, font, line, (left + right) / 2, textY, Ui.argb(0x8A8A93, fade));
                textY += 10;
            }
        }
        graphics.disableScissor();
        if (maxScroll > 0) {
            int trackH = listBottom - listTop;
            int thumbH = Math.max(12, (int) (trackH * (trackH / (double) contentHeight)));
            int thumbY = listTop + (int) ((trackH - thumbH) * (scroll / maxScroll));
            graphics.fill(right - 4, listTop, right - 2, listBottom, Ui.argb(0xFFFFFF, 0.08f * fade));
            graphics.fill(right - 4, thumbY, right - 2, thumbY + thumbH, Ui.argb(theme.accent(), fade));
        }

        if (feedback != null) {
            long age = System.currentTimeMillis() - feedbackTime;
            if (age > 3500) feedback = null;
            else {
                float alpha = fade * Math.min(1f, (3500 - age) / 500f);
                String text = Ui.ellipsize(font, feedback, right - left - 12);
                GuiText.centeredText(graphics, font, text, (left + right) / 2, bottom - 22 - 10, Ui.argb(feedbackError ? 0xF87171 : 0x4ADE80, alpha));
            }
        }
    }

    private void drawTab(GuiGraphicsExtractor graphics, Font font, Tab value, String label, int badge, int x, int y, int w, int mouseX, int mouseY, Colors theme, float fade) {
        boolean active = tab == value;
        boolean hover = Ui.inside(mouseX, mouseY, x, y, w, 14);
        graphics.fill(x, y, x + w - 1, y + 14, Ui.argb(active ? theme.hover() : hover ? theme.base() : 0x000000, (active ? 0.85f : hover ? 0.7f : 0.35f) * fade));
        if (active) graphics.fill(x, y + 13, x + w - 1, y + 14, Ui.argb(theme.accent(), fade));
        String text = Ui.ellipsize(font, label, w - (badge > 0 ? 14 : 4));
        GuiText.centeredText(graphics, font, text, x + (w - (badge > 0 ? 8 : 0)) / 2, y + 3, Ui.argb(active ? 0xFFFFFF : 0xC4C4CC, fade));
        if (badge > 0) {
            int bx = x + w - 10;
            graphics.fill(bx - 1, y + 2, bx + 8, y + 11, Ui.argb(0xEF4444, fade));
            GuiText.centeredText(graphics, font, badge > 9 ? "9" : String.valueOf(badge), bx + 4, y + 3, Ui.argb(0xFFFFFF, fade));
        }
        hits.add(new Hit(x, y, w, 14, () -> setTab(value)));
    }

    private interface Row {
        void draw(GuiGraphicsExtractor graphics, Font font, int y, int mouseX, int mouseY, int clipTop, int clipBottom, float fade);
    }

    private List<Row> rows(SocialService.Snapshot data) {
        List<Row> rows = new ArrayList<>();
        switch (tab) {
            case FRIENDS -> {
                for (SocialService.Friend friend : data.friends()) rows.add((g, f, y, mx, my, ct, cb, fade) -> drawFriend(g, f, friend, y, mx, my, ct, cb, fade));
            }
            case REQUESTS -> {
                for (String name : data.incoming()) rows.add((g, f, y, mx, my, ct, cb, fade) -> drawRequest(g, f, name, true, y, mx, my, ct, cb, fade));
                for (String name : data.outgoing()) rows.add((g, f, y, mx, my, ct, cb, fade) -> drawRequest(g, f, name, false, y, mx, my, ct, cb, fade));
            }
            case INVITES -> {
                for (SocialService.Invite invite : data.invites()) rows.add((g, f, y, mx, my, ct, cb, fade) -> drawInvite(g, f, invite, y, mx, my, ct, cb, fade));
            }
        }
        return rows;
    }

    private void rowBackground(GuiGraphicsExtractor graphics, int y, boolean hover, float fade) {
        if (hover) graphics.fill(left + 4, y + 1, right - 6, y + ROW - 1, Ui.argb(0xFFFFFF, 0.07f * fade));
    }

    private void drawFriend(GuiGraphicsExtractor graphics, Font font, SocialService.Friend friend, int y, int mouseX, int mouseY, int clipTop, int clipBottom, float fade) {
        boolean hover = Ui.inside(mouseX, mouseY, left + 4, y, right - left - 10, ROW) && mouseY >= clipTop && mouseY < clipBottom;
        rowBackground(graphics, y, hover, fade);
        Heads.draw(graphics, friend.name(), left + 8, y + 4, 16, friend.online() ? Ui.argb(0x4ADE80, fade) : Ui.argb(0x3F3F46, fade));
        if (!friend.online()) graphics.fill(left + 8, y + 4, left + 24, y + 20, Ui.argb(0x000000, 0.5f * fade));
        int textX = left + 28;
        List<Runnable> actions = new ArrayList<>();
        List<Icons> icons = new ArrayList<>();
        List<String> tips = new ArrayList<>();
        if (friend.online()) {
            boolean canInvite = context == Context.MENU || BattleManager.getInstance().getCurrentRoom() != null;
            boolean inMyRoom = BattleManager.getInstance().getCurrentRoom() != null && friend.room() != null
                && friend.room().equals(BattleManager.getInstance().getCurrentRoomId());
            if (canInvite && !inMyRoom && friend.activity() != SocialService.Activity.BATTLE) {
                icons.add(SocialService.getInstance().recentlyInvited(friend.name()) ? Icons.CHECK : Icons.PLUS);
                tips.add("randomrun.friends.action.invite");
                actions.add(() -> inviteFriend(friend.name()));
            }
            if (context == Context.MENU && friend.activity() == SocialService.Activity.ROOM && friend.room() != null && !friend.roomPrivate() && friend.roomPlayers() < friend.roomMax()) {
                icons.add(Icons.ARROW);
                tips.add("randomrun.friends.action.join");
                actions.add(() -> joinRoom(friend.room()));
            }
        }
        if (hover) {
            icons.add(0, Icons.CROSS);
            tips.add(0, "randomrun.friends.action.remove");
            actions.add(0, () -> removeFriend(friend.name()));
        }
        int iconsWidth = drawIcons(graphics, font, icons, tips, actions, y, mouseX, mouseY, clipTop, clipBottom, hover, fade);
        int maxText = right - textX - 8 - iconsWidth;
        GuiText.text(graphics, font, Ui.ellipsize(font, friend.name(), maxText), textX, y + 3, Ui.argb(friend.online() ? 0xFFFFFF : 0x8A8A93, fade), false);
        String status = status(friend);
        GuiText.text(graphics, font, Ui.ellipsize(font, status, maxText), textX, y + 13, Ui.argb(statusColor(friend), fade), false);
    }

    private int drawIcons(GuiGraphicsExtractor graphics, Font font, List<Icons> icons, List<String> tips, List<Runnable> actions, int y, int mouseX, int mouseY, int clipTop, int clipBottom, boolean rowHover, float fade) {
        Colors theme = colors();
        int x = right - 8;
        int used = 0;
        for (int i = icons.size() - 1; i >= 0; i--) {
            x -= ICON;
            int iy = y + (ROW - ICON) / 2;
            boolean hover = Ui.inside(mouseX, mouseY, x, iy, ICON, ICON) && mouseY >= clipTop && mouseY < clipBottom;
            Icons icon = icons.get(i);
            int base = icon == Icons.CROSS ? 0x7F1D1D : icon == Icons.CHECK ? 0x14532D : theme.base();
            int hi = icon == Icons.CROSS ? 0xDC2626 : icon == Icons.CHECK ? 0x16A34A : theme.hover();
            graphics.fill(x, iy, x + ICON, iy + ICON, Ui.argb(hover ? hi : base, (rowHover || hover ? 1f : 0.75f) * fade));
            icon.drawCentered(graphics, x, iy, ICON, ICON, Ui.argb(0xFFFFFF, fade));
            if (hover) graphics.setTooltipForNextFrame(font, LanguageManager.text(tips.get(i)), mouseX, mouseY);
            if (iy >= clipTop - ICON && iy < clipBottom) hits.add(new Hit(x, Math.max(iy, clipTop), ICON, Math.min(iy + ICON, clipBottom) - Math.max(iy, clipTop), actions.get(i)));
            x -= 3;
            used += ICON + 3;
        }
        return used;
    }

    private void drawRequest(GuiGraphicsExtractor graphics, Font font, String name, boolean incoming, int y, int mouseX, int mouseY, int clipTop, int clipBottom, float fade) {
        boolean hover = Ui.inside(mouseX, mouseY, left + 4, y, right - left - 10, ROW) && mouseY >= clipTop && mouseY < clipBottom;
        rowBackground(graphics, y, hover, fade);
        Heads.draw(graphics, name, left + 8, y + 4, 16, Ui.argb(incoming ? 0xFACC15 : 0x52525B, fade));
        List<Icons> icons = new ArrayList<>();
        List<String> tips = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        if (incoming) {
            icons.add(Icons.CHECK);
            tips.add("randomrun.friends.action.accept");
            actions.add(() -> run(SocialService.getInstance().accept(name), "randomrun.friends.accepted", name));
        }
        icons.add(Icons.CROSS);
        tips.add(incoming ? "randomrun.friends.action.decline" : "randomrun.friends.action.cancel");
        actions.add(() -> run(SocialService.getInstance().decline(name), null, name));
        int used = drawIcons(graphics, font, icons, tips, actions, y, mouseX, mouseY, clipTop, clipBottom, hover, fade);
        int maxText = right - left - 36 - used;
        GuiText.text(graphics, font, Ui.ellipsize(font, name, maxText), left + 28, y + 3, Ui.argb(0xFFFFFF, fade), false);
        GuiText.text(graphics, font, LanguageManager.text(incoming ? "randomrun.friends.request.incoming" : "randomrun.friends.request.outgoing"), left + 28, y + 13, Ui.argb(incoming ? 0xFACC15 : 0x8A8A93, fade), false);
    }

    private void drawInvite(GuiGraphicsExtractor graphics, Font font, SocialService.Invite invite, int y, int mouseX, int mouseY, int clipTop, int clipBottom, float fade) {
        boolean hover = Ui.inside(mouseX, mouseY, left + 4, y, right - left - 10, ROW) && mouseY >= clipTop && mouseY < clipBottom;
        rowBackground(graphics, y, hover, fade);
        Heads.draw(graphics, invite.from(), left + 8, y + 4, 16, Ui.argb(colors().accent(), fade));
        List<Icons> icons = List.of(Icons.CHECK, Icons.CROSS);
        List<String> tips = List.of("randomrun.friends.action.join", "randomrun.friends.action.decline");
        List<Runnable> actions = List.of(() -> acceptInvite(invite), () -> run(SocialService.getInstance().declineInvite(invite.from()), null, invite.from()));
        int used = drawIcons(graphics, font, icons, tips, actions, y, mouseX, mouseY, clipTop, clipBottom, hover, fade);
        int maxText = right - left - 36 - used;
        GuiText.text(graphics, font, Ui.ellipsize(font, invite.from(), maxText), left + 28, y + 3, Ui.argb(0xFFFFFF, fade), false);
        GuiText.text(graphics, font, Ui.ellipsize(font, LanguageManager.translate("randomrun.friends.invite.room", invite.code()), maxText), left + 28, y + 13, Ui.argb(colors().accent(), fade), false);
    }

    public static String status(SocialService.Friend friend) {
        if (!friend.online()) return LanguageManager.translate("randomrun.friends.status.offline");
        String item = itemName(friend.target());
        return switch (friend.activity()) {
            case SOLO -> item != null ? LanguageManager.translate("randomrun.friends.status.solo", item) : LanguageManager.translate("randomrun.friends.status.solo_plain");
            case ROOM -> LanguageManager.translate("randomrun.friends.status.room", friend.roomPlayers(), friend.roomMax());
            case BATTLE -> item != null ? LanguageManager.translate("randomrun.friends.status.battle", item) : LanguageManager.translate("randomrun.friends.status.battle_plain");
            case LOBBY -> LanguageManager.translate("randomrun.friends.status.lobby");
            default -> LanguageManager.translate("randomrun.friends.status.menu");
        };
    }

    private static int statusColor(SocialService.Friend friend) {
        if (!friend.online()) return 0x6B6B74;
        return switch (friend.activity()) {
            case SOLO -> 0x67E8F9;
            case ROOM -> 0xFACC15;
            case BATTLE -> 0xF87171;
            default -> 0x86EFAC;
        };
    }

    private static String itemName(String id) {
        if (id == null || id.isEmpty()) return null;
        try {
            String full = id.contains(":") ? id : IdCompressor.decompress(id);
            if (full != null && !full.contains(":")) full = "minecraft:" + full;
            Identifier identifier = full == null ? null : Identifier.tryParse(full);
            if (identifier == null) return null;
            Item item = BuiltInRegistries.ITEM.getValue(identifier);
            return item == null || item == Items.AIR ? null : Component.translatable(item.getDescriptionId()).getString();
        } catch (RuntimeException error) {
            return null;
        }
    }

    private String pendingRemove;
    private long pendingRemoveTime;

    private void removeFriend(String name) {
        long now = System.currentTimeMillis();
        if (!name.equals(pendingRemove) || now - pendingRemoveTime > 3000) {
            pendingRemove = name;
            pendingRemoveTime = now;
            showFeedback(LanguageManager.translate("randomrun.friends.remove_confirm", name), true);
            return;
        }
        pendingRemove = null;
        run(SocialService.getInstance().remove(name), "randomrun.friends.removed", name);
    }

    private void inviteFriend(String name) {
        if (context == Context.MENU && BattleManager.getInstance().getCurrentRoom() == null) {
            com.randomrun.network.PendingInvites.add(name);
            if (onInviteFromMenu != null) onInviteFromMenu.run();
            return;
        }
        run(SocialService.getInstance().invite(name), "randomrun.friends.invited", name);
    }

    private void joinRoom(String code) {
        if (busy || RoomJoiner.isJoining()) return;
        busy = true;
        RoomJoiner.join(code, screen).whenComplete((result, error) -> Minecraft.getInstance().execute(() -> {
            busy = false;
            if (result == null || !result.joined()) showJoinError(result);
        }));
    }

    private void acceptInvite(SocialService.Invite invite) {
        if (busy || RoomJoiner.isJoining()) return;
        if (BattleManager.getInstance().getCurrentRoom() != null) {
            showFeedback(LanguageManager.translate("randomrun.friends.error.in_room"), true);
            return;
        }
        joinRoom(invite.code());
    }

    private void showJoinError(RoomJoinResult result) {
        RoomJoinResult reason = result == null ? RoomJoinResult.CONNECTION_FAILED : result;
        String key = reason.messageKey() == null ? "randomrun.friends.error.generic" : reason.messageKey();
        showFeedback(LanguageManager.translate(key), true);
    }

    private void sendRequest() {
        if (nameField == null) return;
        String name = nameField.getValue().trim();
        if (!name.matches("[A-Za-z0-9_]{3,16}")) {
            showFeedback(LanguageManager.translate("randomrun.friends.error.not_found"), true);
            return;
        }
        nameField.setValue("");
        run(SocialService.getInstance().request(name), "randomrun.friends.requested", name);
    }

    private void run(CompletableFuture<Void> future, String successKey, String name) {
        busy = true;
        future.whenComplete((ignored, error) -> Minecraft.getInstance().execute(() -> {
            busy = false;
            if (error != null) {
                showFeedback(LanguageManager.translate(SocialService.errorKey(error)), true);
                Ui.playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.8f, 0.6f);
            } else if (successKey != null) {
                showFeedback(LanguageManager.translate(successKey, name), false);
                Ui.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.6f, 0.4f);
            }
        }));
    }

    private void showFeedback(String text, boolean error) {
        feedback = text;
        feedbackError = error;
        feedbackTime = System.currentTimeMillis();
    }

    public boolean mouseClicked(double mouseX, double mouseY) {
        if (!Ui.inside(mouseX, mouseY, left, top, right - left, bottom - top)) return false;
        for (Hit hit : List.copyOf(hits)) {
            if (Ui.inside(mouseX, mouseY, hit.x(), hit.y(), hit.w(), hit.h())) {
                if (!busy) hit.action().run();
                return true;
            }
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (!Ui.inside(mouseX, mouseY, left, top, right - left, bottom - top)) return false;
        scroll -= amount * ROW;
        return true;
    }
}
