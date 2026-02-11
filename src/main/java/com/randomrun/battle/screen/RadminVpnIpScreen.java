package com.randomrun.battle.screen;

import com.randomrun.battle.BattleManager;
import com.randomrun.battle.BattleRoom;
import com.randomrun.ui.screen.main.AbstractRandomRunScreen;
import com.randomrun.ui.widget.styled.ButtonDefault;
import com.randomrun.ui.widget.styled.TextFieldStyled;
import com.randomrun.main.data.PlayerProfile;
import com.randomrun.ui.screen.main.MainModScreen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

public class RadminVpnIpScreen extends AbstractRandomRunScreen {
    private final Screen parent;
    private final String roomCode;
    private TextFieldStyled ipField;
    private String errorMessage;
    private ButtonDefault saveIpButton;
    private long openTime;

    public RadminVpnIpScreen(Screen parent, String roomCode) {
        super(Text.translatable("randomrun.radmin.ip_input.title"));
        this.parent = parent;
        this.roomCode = roomCode;
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int centerY = height / 2;
        openTime = System.currentTimeMillis();

        // Поле ввода
        ipField = new TextFieldStyled(textRenderer, centerX - 120, centerY - 35, 240, 20,
                Text.translatable("randomrun.radmin.ip_input.placeholder"), 0.06f);
        ipField.setMaxLength(64);
        ipField.setCenteredPlaceholder(Text.translatable("randomrun.radmin.enter_ip"));
        
        // Автозаполнение сохраненного IP
        String savedIp = PlayerProfile.get().getSavedIp();
        if (savedIp != null && !savedIp.isEmpty()) {
            ipField.setText(savedIp);
        }
        
        // Слушатель изменений для обновления текста кнопки
        ipField.setChangedListener(text -> updateSaveButton());
        
        addDrawableChild(ipField);

        // Кнопка сохранения IP
        saveIpButton = new ButtonDefault(
                centerX - 120, centerY - 5,
                240, 20,
                Text.translatable("randomrun.radmin.save_ip"),
                button -> saveIp(),
                0, 0.1f
        );
        addDrawableChild(saveIpButton);
        updateSaveButton(); // Обновить текст кнопки при инициализации

        // Кнопка Старт
        addDrawableChild(new ButtonDefault(
                centerX - 120, centerY + 25,
                240, 20,
                Text.translatable("randomrun.battle.start_game"),
                button -> startGame(),
                1, 0.15f
        ));

        // Кнопка Назад
        addDrawableChild(new ButtonDefault(
                centerX - 120, height - 30,
                240, 20,
                Text.translatable("randomrun.button.back"),
                button -> MinecraftClient.getInstance().setScreen(parent),
                2, 0.2f
        ));
    }
    
    private void updateSaveButton() {
        String currentIp = ipField.getText();
        String savedIp = PlayerProfile.get().getSavedIp();
        
        if (savedIp != null && !savedIp.isEmpty() && savedIp.equals(currentIp)) {
            // Если IP совпадает с сохраненным
            saveIpButton.setMessage(Text.translatable("randomrun.radmin.update_ip"));
            saveIpButton.active = true;
        } else {
            if (savedIp != null && !savedIp.isEmpty()) {
                saveIpButton.setMessage(Text.translatable("randomrun.radmin.update_ip"));
            } else {
                saveIpButton.setMessage(Text.translatable("randomrun.radmin.save_ip"));
            }
            saveIpButton.active = true;
        }
    }
    
    private void saveIp() {
        String ip = ipField.getText().trim();
        if (!ip.isEmpty()) {
            PlayerProfile.get().setSavedIp(ip);
            updateSaveButton();
        }
    }

    private void startGame() {
        String ip = ipField.getText().trim();
        if (!isValidIp(ip)) {
            errorMessage = Text.translatable("randomrun.radmin.invalid_ip").getString();
            return;
        }

        BattleManager.getInstance().setManualServerAddress(ip);

        BattleRoom room = BattleManager.getInstance().getCurrentRoom();
        boolean canStart = false;
        if (room != null) {
            int playerCount = room.getPlayers() != null ? room.getPlayers().size() : 1;
            int maxPlayers = room.getMaxPlayers();
            canStart = playerCount >= maxPlayers;
        }

        if (canStart) {
            BattleManager.getInstance().setStatusLoading();
            MinecraftClient.getInstance().setScreen(new BattleWaitingScreen(parent, roomCode, true));
        } else {
            MinecraftClient.getInstance().setScreen(new BattleWaitingScreen(parent, roomCode, true));
        }
    }

    private boolean isValidIp(String ip) {
        if (ip == null || ip.isEmpty()) return false;
        String[] parts = ip.split(":", 2);
        String host = parts[0].trim();
        String portStr = parts.length > 1 ? parts[1].trim() : null;

        String[] octets = host.split("\\.");
        if (octets.length != 4) return false;
        for (String octet : octets) {
            try {
                int val = Integer.parseInt(octet);
                if (val < 0 || val > 255) return false;
            } catch (NumberFormatException e) {
                return false;
            }
        }

        if (portStr != null && !portStr.isEmpty()) {
            try {
                int port = Integer.parseInt(portStr);
                if (port < 1 || port > 65535) return false;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        
        // Draw semi-transparent background box with border
        int centerX = width / 2;
        int centerY = height / 2;
        int boxWidth = 260;
        int boxHeight = 100; // Covers input field and buttons
        int boxX = centerX - boxWidth / 2;
        int boxY = centerY - 45;
        
        // Background fill (semi-transparent black)
        context.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0x80000000);
        
        // Border (white, 1 pixel)
        int borderColor = 0xFFFFFFFF;
        context.fill(boxX, boxY, boxX + boxWidth, boxY + 1, borderColor); // Top
        context.fill(boxX, boxY + boxHeight - 1, boxX + boxWidth, boxY + boxHeight, borderColor); // Bottom
        context.fill(boxX, boxY, boxX + 1, boxY + boxHeight, borderColor); // Left
        context.fill(boxX + boxWidth - 1, boxY, boxX + boxWidth, boxY + boxHeight, borderColor); // Right
        
        super.render(context, mouseX, mouseY, delta);

        if (errorMessage != null) {
            context.drawCenteredTextWithShadow(textRenderer, errorMessage, width / 2, height / 2 + 40, 0xFF5555);
        }
    }
}
