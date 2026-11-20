package com.randomrun.ui.widget;

import com.randomrun.features.battle.manager.BattleManager;
import com.randomrun.features.battle.manager.BattleSupport.RoomJoinResult;
import com.randomrun.features.battle.manager.BattleSupport.RoomJoiner;
import com.randomrun.infrastructure.util.IdCompressor;
import com.randomrun.infrastructure.util.LanguageManager;
import com.randomrun.network.BackendSession;
import com.randomrun.network.SocialService;
import com.randomrun.ui.util.GuiText;
import com.randomrun.ui.util.Icons;
import com.randomrun.ui.util.Previews.Heads;
import com.randomrun.ui.util.Theme;
import com.randomrun.ui.util.InterfaceStyle;
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
    public enum Tab { CHAT, FRIENDS, REQUESTS }
    public enum Context { MENU, ROOM }

    private static final int ROW = 24;
    private static final int ICON = 14;
    private static Tab lastTab = Tab.CHAT;
    private static final int CHAT_LINE = 10, CHAT_HEADER = 12, CHAT_GAP = 3;
    private static final int[] NAME_COLORS = {0x93C5FD, 0xF9A8D4, 0xFCD34D, 0xA5B4FC, 0x5EEAD4, 0xFDBA74, 0xC4B5FD, 0xBEF264};
    private static final int[] NAME_COLORS_CLASSIC = {0x9CB4D1, 0xD1A0B6, 0xD9C27E, 0xA9ADD0, 0x8FC6BC, 0xD6AA86, 0xB7A9D6, 0xB5CC86};
    private static final java.time.format.DateTimeFormatter CHAT_TIME = java.time.format.DateTimeFormatter.ofPattern("HH:mm");

    private final Screen screen;
    private final Context context;
    private final Runnable onInviteFromMenu;
    private int left, top, right, bottom;
    private Tab tab = lastTab;
    private TextFieldStyled nameField, chatField;
    private double scroll, chatScroll;
    private record ChatLine(com.randomrun.network.ChatService.Message message, boolean header, net.minecraft.util.FormattedCharSequence text, int height, boolean mention) {}
    private List<ChatLine> chatLines = List.of();
    private int chatVersion = -1, chatWidth = -1, chatHeight;
    private long newMessagesAt;

    private static String feedback;
    private static boolean feedbackError;
    private static long feedbackTime;
    private boolean busy;
    private long rowsShownAt = System.currentTimeMillis();
    private final List<Hit> hits = new ArrayList<>();

    private record Hit(int x, int y, int w, int h, Runnable action) {}
    private record Colors(int base, int hover, int accent, int title) {}

    private Colors colors() {
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
        nameField = new TextFieldStyled(font, left + 4, contentTop(), fieldWidth(), 16, LanguageManager.text("randomrun.friends.add.placeholder"), 0f) {
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
        chatField = new TextFieldStyled(font, left + 4, chatFieldY(), fieldWidth(), 16, LanguageManager.text("randomrun.chat.placeholder"), 0f) {
            @Override
            public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
                if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_RETURN || event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_NUMPADENTER) {
                    sendChat();
                    return true;
                }
                return super.keyPressed(event);
            }
        };
        chatField.setColors(colors.base(), colors.hover());
        chatField.setBorderColor(colors.hover());
        chatField.setMaxLength(com.randomrun.network.ChatService.MAX_LENGTH);
        chatField.setCenteredPlaceholder(LanguageManager.text("randomrun.chat.placeholder"));
        chatField.visible = tab == Tab.CHAT && BackendSession.isAuthenticated();
        addWidget.accept(chatField);
        SocialService.getInstance().refresh();
    }

    private int fieldWidth() {
        return right - left - 8 - 22;
    }

    private Runnable expandHandler;
    private boolean expanded;

    public void setExpandHandler(Runnable handler, boolean expanded) {
        this.expandHandler = handler;
        this.expanded = expanded;
    }

    public void bounds(int left, int top, int right, int bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        if (nameField != null) {
            nameField.setX(left + 4);
            nameField.setY(contentTop());
            nameField.setWidth(fieldWidth());
        }
        if (chatField != null) {
            chatField.setX(left + 4);
            chatField.setY(chatFieldY());
            chatField.setWidth(fieldWidth());
        }
    }

    public void moveTo(int x, boolean shown, boolean interactive) {
        int dx = x - left;
        left += dx;
        right += dx;
        if (nameField != null) {
            nameField.setX(nameField.getX() + dx);
            nameField.visible = shown && tab == Tab.REQUESTS;
            nameField.active = interactive;
            if (!interactive) nameField.setFocused(false);
        }
        if (chatField != null) {
            chatField.setX(chatField.getX() + dx);
            chatField.visible = shown && tab == Tab.CHAT && BackendSession.isAuthenticated();
            chatField.active = interactive;
            if (!interactive) chatField.setFocused(false);
        }
        if (!interactive) hits.clear();
    }

    private int footer() {
        return context == Context.MENU ? 26 : 4;
    }

    private int contentTop() {
        return top + 42;
    }

    private int chatFieldY() {
        return bottom - footer() - 18;
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
        if (chatField != null) {
            chatField.visible = tab == Tab.CHAT;
            if (!chatField.visible) chatField.setFocused(false);
        }
        chatScroll = 0;
        Ui.click();
    }

    public void extract(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float fade) {
        hits.clear();
        Font font = Minecraft.getInstance().font;
        Colors theme = colors();
        SocialService.Snapshot data = SocialService.getInstance().snapshot();

        Component title = LanguageManager.text("randomrun.friends.title");
        GuiText.text(graphics, font, title, left + 8, top + 8, Ui.argb(theme.title(), fade), true);
        if (expandHandler != null) {
            Icons icon = expanded ? Icons.COLLAPSE : Icons.EXPAND;
            int ix = right - 8 - icon.width, iy = top + 8;
            boolean hover = Ui.inside(mouseX, mouseY, ix - 3, iy - 3, icon.width + 6, icon.height + 6);
            icon.draw(graphics, ix, iy, Ui.argb(hover ? 0xFFFFFF : (InterfaceStyle.isClassic() ? 0xB8B8AA : theme.title()), fade));
            if (hover) graphics.requestCursor(com.mojang.blaze3d.platform.cursor.CursorTypes.POINTING_HAND);
            hits.add(new Hit(ix - 3, iy - 3, icon.width + 6, icon.height + 6, () -> { Ui.click(); expandHandler.run(); }));
        }

        if (!BackendSession.isAuthenticated()) {
            GuiText.centeredText(graphics, font, LanguageManager.text("randomrun.friends.offline"), (left + right) / 2, top + 50, Ui.argb((InterfaceStyle.isClassic() ? 0xB8B8AA : 0xA1A1AA), fade));
            return;
        }

        int tabY = top + 22;
        int tabW = (right - left - 8) / 3;
        var chat = com.randomrun.network.ChatService.getInstance();
        if (tab == Tab.CHAT) chat.markSeen();
        drawTab(graphics, font, Tab.CHAT, LanguageManager.translate("randomrun.chat.tab"), tab == Tab.CHAT ? 0 : chat.unread(), left + 4, tabY, tabW, mouseX, mouseY, theme, fade);
        drawTab(graphics, font, Tab.FRIENDS, LanguageManager.translate("randomrun.friends.tab.friends"), data.invites().size(), left + 4 + tabW, tabY, tabW, mouseX, mouseY, theme, fade);
        drawTab(graphics, font, Tab.REQUESTS, LanguageManager.translate("randomrun.friends.tab.requests"), data.incoming().size(), left + 4 + tabW * 2, tabY, right - left - 8 - tabW * 2, mouseX, mouseY, theme, fade);
        if (chatField != null && chatField.visible != (tab == Tab.CHAT)) chatField.visible = tab == Tab.CHAT;
        if (tab == Tab.CHAT) {
            extractChat(graphics, font, mouseX, mouseY, theme, fade);
            if (feedback != null) {
                long age = System.currentTimeMillis() - feedbackTime;
                if (age > 3500) feedback = null;
                else drawFeedback(graphics, font, fade * Math.min(1f, (3500 - age) / 500f));
            }
            return;
        }

        int listTop = tab == Tab.REQUESTS ? contentTop() + 22 : contentTop();
        int listBottom = bottom - footer();
        if (tab == Tab.REQUESTS) {
            boolean hover = Ui.inside(mouseX, mouseY, right - 24, contentTop(), 20, 16);
            if (InterfaceStyle.isClassic()) {
                Ui.bevel(graphics, right - 24, contentTop(), right - 4, contentTop() + 16, hover ? theme.hover() : theme.base(), fade, false);
                if (hover) Ui.border(graphics, right - 24, contentTop(), right - 4, contentTop() + 16, 1, Ui.argb(0xFFFFA0, fade));
            } else {
                graphics.fill(right - 24, contentTop(), right - 4, contentTop() + 16, Ui.argb(hover ? theme.hover() : theme.base(), fade));
                Ui.border(graphics, right - 24, contentTop(), right - 4, contentTop() + 16, 1, Ui.argb(theme.hover(), fade));
            }
            Icons.PLUS.drawCentered(graphics, right - 24, contentTop(), 20, 16, Ui.argb(0xFFFFFF, fade));
            hits.add(new Hit(right - 24, contentTop(), 20, 16, this::sendRequest));
        }

        if (InterfaceStyle.isClassic()) Ui.slot(graphics, left + 4, listTop - 1, right - 4, listBottom + 1, fade);
        graphics.enableScissor(left + 2, listTop, right - 2, listBottom);
        List<Row> rows = rows(data);
        int contentHeight = rows.size() * ROW;
        double maxScroll = Math.max(0, contentHeight - (listBottom - listTop));
        scroll = Math.clamp(scroll, 0, maxScroll);
        int y = listTop - (int) scroll;
        long now = System.currentTimeMillis();
        for (int index = 0; index < rows.size(); index++) {
            if (y + ROW >= listTop && y <= listBottom) {
                float appear = InterfaceStyle.isClassic() ? 1f : Ui.easeOut((now - rowsShownAt - index * 45L) / 300f);
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
                case CHAT -> "randomrun.chat.empty";
            };
            int textY = listTop + 8;
            for (var line : font.split(LanguageManager.text(key), right - left - 20)) {
                GuiText.centeredText(graphics, font, line, (left + right) / 2, textY, Ui.argb((InterfaceStyle.isClassic() ? 0xAAAA9E : 0x8A8A93), fade));
                textY += 10;
            }
        }
        graphics.disableScissor();
        if (maxScroll > 0) {
            int trackH = listBottom - listTop;
            int thumbH = Math.max(12, (int) (trackH * (trackH / (double) contentHeight)));
            int thumbY = listTop + (int) ((trackH - thumbH) * (scroll / maxScroll));
            if (InterfaceStyle.isClassic()) {
                graphics.fill(right - 4, listTop, right - 2, listBottom, Ui.argb(0x181815, fade));
                Ui.bevel(graphics, right - 4, thumbY, right - 1, thumbY + thumbH, 0x97978D, fade, false);
            } else {
                graphics.fill(right - 4, listTop, right - 2, listBottom, Ui.argb(0xFFFFFF, 0.08f * fade));
                graphics.fill(right - 4, thumbY, right - 2, thumbY + thumbH, Ui.argb(theme.accent(), fade));
            }
        }

        if (feedback != null) {
            long age = System.currentTimeMillis() - feedbackTime;
            if (age > 3500) feedback = null;
            else drawFeedback(graphics, font, fade * Math.min(1f, (3500 - age) / 500f));
        }
    }

    private static final String CROSS = "\u00D7";

    private int markupWidth(Font font, String text) {
        int width = 0;
        for (String part : text.split(CROSS, -1)) width += font.width(part);
        return width + (text.split(CROSS, -1).length - 1) * (Icons.SMALL_CROSS.width + 2);
    }

    private List<String> wrap(Font font, String text, int maxWidth) {
        List<String> lines = new java.util.ArrayList<>();
        String line = "";
        for (String word : text.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && markupWidth(font, candidate) > maxWidth) {
                lines.add(line);
                line = word;
            } else line = candidate;
        }
        if (!line.isEmpty()) lines.add(line);
        return lines;
    }

    private void drawFeedback(GuiGraphicsExtractor graphics, Font font, float alpha) {
        int maxWidth = right - left - 12;
        List<String> lines = wrap(font, feedback, maxWidth);
        int height = lines.size() * 10 + 6;
        int y = bottom + 4 + height <= screen.height - 2 ? bottom + 4 : bottom - footer() - height;
        int color = Ui.argb(feedbackError ? (InterfaceStyle.isClassic() ? 0xDD8D7C : 0xF87171) : (InterfaceStyle.isClassic() ? 0xA5CA7C : 0x4ADE80), alpha);
        if (InterfaceStyle.isClassic()) Ui.bevel(graphics, left, y, right, y + height, 0x2B2B27, alpha, true);
        else {
            graphics.fill(left, y, right, y + height, Ui.argb(0x000000, 0.75f * alpha));
            graphics.fill(left, y, left + 2, y + height, color);
        }
        int textY = y + 4;
        for (String line : lines) {
            int x = (left + right - markupWidth(font, line)) / 2;
            String[] parts = line.split(CROSS, -1);
            for (int i = 0; i < parts.length; i++) {
                GuiText.text(graphics, font, parts[i], x, textY, color, false);
                x += font.width(parts[i]);
                if (i < parts.length - 1) {
                    Icons.SMALL_CROSS.draw(graphics, x + 1, textY, color);
                    x += Icons.SMALL_CROSS.width + 2;
                }
            }
            textY += 10;
        }
    }

    private void drawTab(GuiGraphicsExtractor graphics, Font font, Tab value, String label, int badge, int x, int y, int w, int mouseX, int mouseY, Colors theme, float fade) {
        boolean active = tab == value;
        boolean hover = Ui.inside(mouseX, mouseY, x, y, w, 14);
        if (InterfaceStyle.isClassic()) {
            Ui.bevel(graphics, x, y, x + w - 1, y + 14, active ? 0x36362F : hover ? theme.hover() : theme.base(), fade, active);
            if (active || hover) Ui.border(graphics, x, y, x + w - 1, y + 14, 1, Ui.argb(active ? theme.accent() : 0xFFFFA0, fade));
        } else {
            graphics.fill(x, y, x + w - 1, y + 14, Ui.argb(active ? theme.hover() : hover ? theme.base() : 0x000000, (active ? 0.85f : hover ? 0.7f : 0.35f) * fade));
            if (active) graphics.fill(x, y + 13, x + w - 1, y + 14, Ui.argb(theme.accent(), fade));
        }
        String text = Ui.ellipsize(font, label, w - (badge > 0 ? 14 : 4));
        GuiText.centeredText(graphics, font, text, x + (w - (badge > 0 ? 8 : 0)) / 2, y + 3, Ui.argb(active ? 0xFFFFFF : (InterfaceStyle.isClassic() ? 0xCECEC0 : 0xC4C4CC), fade));
        if (badge > 0) {
            int bx = x + w - 10;
            graphics.fill(bx - 1, y + 2, bx + 8, y + 11, Ui.argb((InterfaceStyle.isClassic() ? 0x965C4D : 0xEF4444), fade));
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
            case CHAT -> {}
        }
        return rows;
    }

    private void rowBackground(GuiGraphicsExtractor graphics, int y, boolean hover, float fade) {
        if (InterfaceStyle.isClassic()) {
            if (hover) graphics.fill(left + 6, y + 1, right - 7, y + ROW - 1, Ui.argb(0x68685D, 0.55f * fade));
            graphics.fill(left + 7, y + ROW - 1, right - 8, y + ROW, Ui.argb(0x77776A, 0.16f * fade));
        } else {
            if (hover) graphics.fill(left + 4, y + 1, right - 6, y + ROW - 1, Ui.argb(0xFFFFFF, 0.07f * fade));
        }
    }

    private void drawFriend(GuiGraphicsExtractor graphics, Font font, SocialService.Friend friend, int y, int mouseX, int mouseY, int clipTop, int clipBottom, float fade) {
        boolean hover = Ui.inside(mouseX, mouseY, left + 4, y, right - left - 10, ROW) && mouseY >= clipTop && mouseY < clipBottom;
        rowBackground(graphics, y, hover, fade);
        Heads.draw(graphics, friend.name(), left + 8, y + 4, 16, friend.online() ? Ui.argb((InterfaceStyle.isClassic() ? 0xA5CA7C : 0x4ADE80), fade) : Ui.argb(0x3F3F46, fade));
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
            SocialService.Invite invite = inviteFrom(friend.name());
            if (context == Context.MENU && (invite != null || friend.activity() == SocialService.Activity.ROOM && friend.room() != null && !friend.roomPrivate() && friend.roomPlayers() < friend.roomMax())) {
                icons.add(Icons.ARROW);
                tips.add("randomrun.friends.action.join");
                actions.add(() -> { if (invite != null) acceptInvite(invite); else joinRoom(friend.room()); });
            }
        }
        if (hover) {
            icons.add(0, Icons.CROSS);
            tips.add(0, "randomrun.friends.action.remove");
            actions.add(0, () -> removeFriend(friend.name()));
        }
        int iconsWidth = drawIcons(graphics, font, icons, tips, actions, y, mouseX, mouseY, clipTop, clipBottom, hover, fade);
        int maxText = right - textX - 8 - iconsWidth;
        GuiText.text(graphics, font, Ui.ellipsize(font, friend.name(), maxText), textX, y + 3, Ui.argb(friend.online() ? 0xFFFFFF : (InterfaceStyle.isClassic() ? 0xAAAA9E : 0x8A8A93), fade), false);
        SocialService.Invite invite = friend.online() ? inviteFrom(friend.name()) : null;
        String status = invite != null ? LanguageManager.translate("randomrun.friends.invite.room", invite.code()) : status(friend);
        GuiText.text(graphics, font, Ui.ellipsize(font, status, maxText), textX, y + 13, Ui.argb(invite != null ? colors().accent() : statusColor(friend), fade), false);
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
            if (InterfaceStyle.isClassic()) {
                int base = icon == Icons.CROSS ? 0x6F4840 : icon == Icons.CHECK ? 0x4C633C : theme.base();
                int hi = icon == Icons.CROSS ? 0x916459 : icon == Icons.CHECK ? 0x6D8755 : theme.hover();
                Ui.bevel(graphics, x, iy, x + ICON, iy + ICON, hover ? hi : base, fade, false);
                if (hover) Ui.border(graphics, x, iy, x + ICON, iy + ICON, 1, Ui.argb(0xFFFFA0, fade));
            } else {
                int base = icon == Icons.CROSS ? 0x7F1D1D : icon == Icons.CHECK ? 0x14532D : theme.base();
                int hi = icon == Icons.CROSS ? 0xDC2626 : icon == Icons.CHECK ? 0x16A34A : theme.hover();
                graphics.fill(x, iy, x + ICON, iy + ICON, Ui.argb(hover ? hi : base, (rowHover || hover ? 1f : 0.75f) * fade));
            }
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
        Heads.draw(graphics, name, left + 8, y + 4, 16, Ui.argb(incoming ? (InterfaceStyle.isClassic() ? 0xDCCA82 : 0xFACC15) : 0x52525B, fade));
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
        GuiText.text(graphics, font, LanguageManager.text(incoming ? "randomrun.friends.request.incoming" : "randomrun.friends.request.outgoing"), left + 28, y + 13, Ui.argb(incoming ? (InterfaceStyle.isClassic() ? 0xDCCA82 : 0xFACC15) : (InterfaceStyle.isClassic() ? 0xAAAA9E : 0x8A8A93), fade), false);
    }

    private static SocialService.Invite inviteFrom(String name) {
        for (SocialService.Invite invite : SocialService.getInstance().snapshot().invites()) if (invite.from().equalsIgnoreCase(name)) return invite;
        return null;
    }

    private void extractChat(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, Colors theme, float fade) {
        var chat = com.randomrun.network.ChatService.getInstance();
        chat.ensureHistory();
        int areaTop = contentTop(), areaBottom = chatFieldY() - 4;
        int areaLeft = left + 6, areaRight = right - 8;
        boolean classic = InterfaceStyle.isClassic();
        if (classic) Ui.slot(graphics, left + 4, areaTop - 1, right - 4, areaBottom + 1, fade);
        else graphics.fill(left + 4, areaTop - 1, right - 4, areaBottom + 1, Ui.argb(0x000000, 0.22f * fade));
        rebuildChat(font, chat, areaRight - areaLeft - 6);
        int areaHeight = areaBottom - areaTop - 4;
        double maxScroll = Math.max(0, chatHeight - areaHeight);
        chatScroll = Math.clamp(chatScroll, 0, maxScroll);

        if (chatLines.isEmpty()) {
            String key = !chat.loaded() || !com.randomrun.network.RealtimeClient.getInstance().isConnected() ? "randomrun.chat.connecting" : "randomrun.chat.empty";
            int textY = (areaTop + areaBottom) / 2 - 8;
            if (key.equals("randomrun.chat.connecting")) Ui.spinner(graphics, (left + right) / 2, textY - 8, 5, theme.accent());
            for (var line : font.split(LanguageManager.text(key), right - left - 24)) {
                GuiText.centeredText(graphics, font, line, (left + right) / 2, textY, Ui.argb(classic ? 0xAAAA9E : 0x8A8A93, fade));
                textY += 10;
            }
        } else {
            graphics.enableScissor(areaLeft, areaTop, areaRight, areaBottom);
            long now = System.currentTimeMillis();
            String me = BackendSession.playerName();
            int y = areaBottom - 2 + (int) chatScroll;
            for (int index = chatLines.size() - 1; index >= 0 && y > areaTop; index--) {
                ChatLine line = chatLines.get(index);
                y -= line.height();
                if (y > areaBottom) continue;
                var message = line.message();
                float appear = classic ? 1f : Ui.easeOut((now - message.receivedAt()) / 220f);
                float alpha = fade * (0.25f + 0.75f * appear);
                int shift = Math.round((1f - appear) * 8f);
                if (line.header()) {
                    int headY = y + 2;
                    Heads.draw(graphics, message.name(), areaLeft + 2 + shift, headY, 8, 0);
                    int nameColor = nameColor(message.name(), me);
                    int nameX = areaLeft + 13 + shift;
                    String name = Ui.ellipsize(font, message.name(), areaRight - nameX - 34);
                    boolean hover = !message.name().equalsIgnoreCase(me) && Ui.inside(mouseX, mouseY, nameX, headY, font.width(name), 9) && mouseY >= areaTop && mouseY < areaBottom;
                    GuiText.text(graphics, font, name, nameX, headY + 1, Ui.argb(hover ? 0xFFFFFF : nameColor, alpha), true);
                    if (hover) {
                        graphics.fill(nameX, headY + 10, nameX + font.width(name), headY + 11, Ui.argb(nameColor, alpha));
                        graphics.requestCursor(com.mojang.blaze3d.platform.cursor.CursorTypes.POINTING_HAND);
                    }
                    if (!message.name().equalsIgnoreCase(me)) hits.add(new Hit(nameX, Math.max(headY, areaTop), font.width(name), 9, () -> reply(message.name())));
                    String time = java.time.Instant.ofEpochMilli(message.at()).atZone(java.time.ZoneId.systemDefault()).format(CHAT_TIME);
                    GuiText.text(graphics, font, time, areaRight - 3 - font.width(time), headY + 1, Ui.argb(classic ? 0x77776A : 0x52525B, alpha), false);
                } else {
                    if (line.mention()) graphics.fill(areaLeft + 1, y, areaLeft + 3, y + CHAT_LINE, Ui.argb(classic ? 0xDCCA82 : 0xFACC15, alpha));
                    int color = line.mention() ? (classic ? 0xFFEFD9A0 : 0xFFFDE68A) : (classic ? 0xFFC8C8BC : 0xFFBFBFC8);
                    graphics.text(font, line.text(), areaLeft + 13 + shift, y + 1, Ui.argb(color & 0xFFFFFF, alpha), false);
                }
            }
            graphics.disableScissor();
            if (maxScroll > 0) {
                int trackH = areaBottom - areaTop;
                int thumbH = Math.max(10, (int) (trackH * (areaHeight / (double) chatHeight)));
                int thumbY = areaTop + (int) ((trackH - thumbH) * (1 - chatScroll / maxScroll));
                graphics.fill(right - 7, thumbY, right - 5, thumbY + thumbH, Ui.argb(theme.accent(), 0.8f * fade));
            }
            if (chatScroll > 0 && newMessagesAt > 0) {
                Component text = LanguageManager.text("randomrun.chat.new_messages");
                int w = font.width(text) + 12, x = (left + right - w) / 2, py = areaBottom - 16;
                boolean hover = Ui.inside(mouseX, mouseY, x, py, w, 13);
                graphics.fill(x, py, x + w, py + 13, Ui.argb(hover ? theme.hover() : theme.base(), 0.95f * fade));
                Ui.border(graphics, x, py, x + w, py + 13, 1, Ui.argb(theme.accent(), fade));
                GuiText.centeredText(graphics, font, text, x + w / 2, py + 3, Ui.argb(0xFFFFFF, fade));
                if (hover) graphics.requestCursor(com.mojang.blaze3d.platform.cursor.CursorTypes.POINTING_HAND);
                hits.add(new Hit(x, py, w, 13, () -> { chatScroll = 0; newMessagesAt = 0; }));
            } else if (chatScroll == 0) newMessagesAt = 0;
        }

        int fieldY = chatFieldY();
        boolean hover = Ui.inside(mouseX, mouseY, right - 24, fieldY, 20, 16);
        boolean ready = chatField != null && !chatField.getValue().isBlank();
        if (classic) {
            Ui.bevel(graphics, right - 24, fieldY, right - 4, fieldY + 16, hover ? theme.hover() : theme.base(), fade, false);
            if (hover) Ui.border(graphics, right - 24, fieldY, right - 4, fieldY + 16, 1, Ui.argb(0xFFFFA0, fade));
        } else {
            graphics.fill(right - 24, fieldY, right - 4, fieldY + 16, Ui.argb(ready ? theme.hover() : theme.base(), (hover ? 1f : 0.85f) * fade));
            Ui.border(graphics, right - 24, fieldY, right - 4, fieldY + 16, 1, Ui.argb(ready ? theme.accent() : theme.hover(), fade));
        }
        Icons.ARROW.drawCentered(graphics, right - 24, fieldY, 20, 16, Ui.argb(ready ? 0xFFFFFF : 0xA1A1AA, fade));
        if (hover) graphics.requestCursor(com.mojang.blaze3d.platform.cursor.CursorTypes.POINTING_HAND);
        hits.add(new Hit(right - 24, fieldY, 20, 16, this::sendChat));
        if (chatField != null && chatField.isFocused()) {
            String counter = chatField.getValue().length() + "/" + com.randomrun.network.ChatService.MAX_LENGTH;
            if (chatField.getValue().length() > com.randomrun.network.ChatService.MAX_LENGTH - 30)
                GuiText.text(graphics, font, counter, right - 26 - font.width(counter), fieldY - 10, Ui.argb(classic ? 0x9A978D : 0x71717A, fade), false);
        }
        String error = chat.error();
        if (error != null) {
            Component text = LanguageManager.text(error);
            int w = font.width(text) + 10, x = (left + right - w) / 2, ey = areaBottom - 15;
            graphics.fill(x, ey, x + w, ey + 13, Ui.argb(0x000000, 0.85f * fade));
            graphics.fill(x, ey, x + 2, ey + 13, Ui.argb(classic ? 0xDD8D7C : 0xF87171, fade));
            GuiText.text(graphics, font, text, x + 6, ey + 3, Ui.argb(classic ? 0xDD8D7C : 0xF87171, fade), false);
        }
    }

    private void rebuildChat(Font font, com.randomrun.network.ChatService chat, int width) {
        int version = chat.version();
        if (version == chatVersion && width == chatWidth) return;
        int before = chatHeight;
        boolean grew = version != chatVersion && chatVersion >= 0;
        chatVersion = version;
        chatWidth = width;
        String me = BackendSession.playerName();
        List<ChatLine> lines = new ArrayList<>();
        int height = 0;
        com.randomrun.network.ChatService.Message previous = null;
        for (var message : chat.messages()) {
            boolean header = previous == null || !previous.name().equalsIgnoreCase(message.name()) || message.at() - previous.at() > 180000;
            if (header) {
                lines.add(new ChatLine(message, true, null, CHAT_HEADER + (previous == null ? 0 : CHAT_GAP), false));
                height += CHAT_HEADER + (previous == null ? 0 : CHAT_GAP);
            }
            boolean mention = !message.name().equalsIgnoreCase(me) && com.randomrun.network.ChatService.mentions(message.text(), me);
            for (var part : font.split(Component.literal(message.text()), Math.max(20, width - 12))) {
                lines.add(new ChatLine(message, false, part, CHAT_LINE, mention));
                height += CHAT_LINE;
            }
            previous = message;
        }
        chatLines = lines;
        chatHeight = height;
        if (grew && chatScroll > 0 && height > before) {
            chatScroll += height - before;
            newMessagesAt = System.currentTimeMillis();
        }
    }

    private int nameColor(String name, String me) {
        if (name.equalsIgnoreCase(me)) return InterfaceStyle.isClassic() ? 0x8DD5DD : 0x67E8F9;
        for (SocialService.Friend friend : SocialService.getInstance().snapshot().friends()) {
            if (friend.name().equalsIgnoreCase(name)) return InterfaceStyle.isClassic() ? 0xA5CA7C : 0x4ADE80;
        }
        int[] palette = InterfaceStyle.isClassic() ? NAME_COLORS_CLASSIC : NAME_COLORS;
        return palette[Math.floorMod(name.toLowerCase(java.util.Locale.ROOT).hashCode(), palette.length)];
    }

    private void reply(String name) {
        if (chatField == null) return;
        String value = chatField.getValue();
        String prefix = name + ", ";
        if (!value.startsWith(prefix)) chatField.setValue(prefix + value);
        chatField.setFocused(true);
        screen.setFocused(chatField);
        chatField.moveCursorToEnd(false);
        Ui.click();
    }

    private void sendChat() {
        if (chatField == null) return;
        String value = chatField.getValue();
        if (value.isBlank()) return;
        if (com.randomrun.network.ChatService.getInstance().send(value)) {
            chatField.setValue("");
            chatScroll = 0;
            Ui.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.8f, 0.3f);
        }
    }

    public static String status(SocialService.Friend friend) {
        if (!friend.online()) return friend.seen() > 0
            ? LanguageManager.translate("randomrun.friends.status.seen", ago(friend.seen()))
            : LanguageManager.translate("randomrun.friends.status.offline");
        String item = itemName(friend.target());
        return switch (friend.activity()) {
            case SOLO -> item != null ? LanguageManager.translate("randomrun.friends.status.solo", item) : LanguageManager.translate("randomrun.friends.status.solo_plain");
            case ROOM -> LanguageManager.translate("randomrun.friends.status.room", friend.roomPlayers(), friend.roomMax());
            case BATTLE -> item != null ? LanguageManager.translate("randomrun.friends.status.battle", item) : LanguageManager.translate("randomrun.friends.status.battle_plain");
            case LOBBY -> LanguageManager.translate("randomrun.friends.status.lobby");
            default -> LanguageManager.translate("randomrun.friends.status.menu");
        };
    }

    private static String ago(long seen) {
        long minutes = Math.max(0, (com.randomrun.network.RealtimeClient.getInstance().serverNow() - seen) / 60000);
        if (minutes < 1) return LanguageManager.translate("randomrun.time.just_now");
        if (minutes < 60) return LanguageManager.translate("randomrun.time.minutes_ago", minutes);
        if (minutes < 1440) return LanguageManager.translate("randomrun.time.hours_ago", minutes / 60);
        return LanguageManager.translate("randomrun.time.days_ago", minutes / 1440);
    }

    private static int statusColor(SocialService.Friend friend) {
        if (!friend.online()) return (InterfaceStyle.isClassic() ? 0x9A9A8D : 0x6B6B74);
        return switch (friend.activity()) {
            case SOLO -> (InterfaceStyle.isClassic() ? 0x9CBDBB : 0x67E8F9);
            case ROOM -> (InterfaceStyle.isClassic() ? 0xDCCA82 : 0xFACC15);
            case BATTLE -> (InterfaceStyle.isClassic() ? 0xDD8D7C : 0xF87171);
            default -> (InterfaceStyle.isClassic() ? 0xA5CA7C : 0x86EFAC);
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
            com.randomrun.network.SocialService.PendingInvites.add(name);
            if (onInviteFromMenu != null) onInviteFromMenu.run();
            return;
        }
        run(SocialService.getInstance().invite(name), "randomrun.friends.invited", name);
    }

    private void joinRoom(String code) {
        if (busy || RoomJoiner.isJoining()) return;
        busy = true;
        com.randomrun.features.streamer.StreamerScreen.lookupRoom(code, room -> {
            busy = false;
            if (Minecraft.getInstance().gui.screen() == screen)
                com.randomrun.features.streamer.StreamerScreen.askForRoom(screen, room, () -> sendJoin(code));
        }, reason -> {
            busy = false;
            showJoinError(reason);
        });
    }

    private void sendJoin(String code) {
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
        if (tab == Tab.CHAT) chatScroll += amount * CHAT_LINE * 2;
        else scroll -= amount * ROW;
        return true;
    }
}
