package com.randomrun.battle.screen;

import com.randomrun.battle.BattleManager;
import com.randomrun.ui.screen.main.AbstractRandomRunScreen;
import com.randomrun.ui.widget.styled.ButtonRainbow;
import com.randomrun.ui.widget.styled.SliderDefault;
import com.randomrun.ui.widget.styled.TextFieldStyled;
import com.randomrun.ui.widget.styled.ButtonDefault;
import com.randomrun.ui.widget.RevealAnimationWidget;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import net.minecraft.text.Text;
public class PrivateHostScreenItemReveal extends AbstractRandomRunScreen {
    private final Screen parent;
    
    private final Item targetItem;
    
    private RevealAnimationWidget revealWidget;
    
    private int maxPlayers = 2;
    public PrivateHostScreenItemReveal(Screen parent, Item item) {
        super(Text.translatable("randomrun.battle.create_room.title"));
        this.parent = parent;
        this.targetItem = item;
    
    }
    private TextFieldStyled passwordField;
    private int modeIndex = 0; // 0: Separate, 1: Shared (e4mc), 2: Shared (Radmin)

    private ButtonDefault modeButton;
    // Выбор провайдера (e4mc / WorldHost)
    // private ButtonDefault providerButton;
    // private String selectedProvider = "WORLD_HOST"; // WORLD_HOST, E4MC
    // private boolean isE4mcInstalled = false;

    // private boolean isWorldHostInstalled = false;
    @Override
    protected void init() {
        
        int centerX = width / 2;
        int buttonY = height / 2 + 120;
        
        this.revealWidget = new RevealAnimationWidget(centerX, height / 2 - 80, new ItemStack(targetItem));

        // Слайдер игроков
        addDrawableChild(new SliderDefault(
            centerX - 100, buttonY - 75,
            200, 20,
            Text.translatable("randomrun.battle.players_count", maxPlayers),
            (maxPlayers - 2) / 4.0, 
            value -> {
                PrivateHostScreenItemReveal.this.maxPlayers = 2 + (int)Math.round(value * 4);
            },
            0.15f
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Text.translatable("randomrun.battle.players_count", PrivateHostScreenItemReveal.this.maxPlayers));
            }
        });

        // Кнопка режима
        modeButton = new ButtonDefault(
            centerX - 100, buttonY - 50,
            200, 20,
            getModeText(),
            button -> {
                modeIndex = (modeIndex + 1) % 3;
                modeButton.setMessage(getModeText());
            },
            1, 0.12f
        );
        addDrawableChild(modeButton);

        // Поле пароля (Стиль StyledButton2)
        passwordField = new TextFieldStyled(textRenderer, centerX - 100, buttonY - 25, 200, 20, Text.translatable("randomrun.battle.password_optional"), 0.05f);
        passwordField.setMaxLength(32);
        passwordField.setCenteredPlaceholder(Text.translatable("randomrun.battle.password_placeholder"));
        addDrawableChild(passwordField);

        // Кнопка создания комнаты
        addDrawableChild(new ButtonRainbow(
            centerX - 100, buttonY,
            200, 20,
            Text.translatable("randomrun.battle.create_room"),
            button -> createRoom(),
            0, 0.12f
        ));
        
        // Кнопка назад
        addDrawableChild(new ButtonDefault(
            centerX - 100, height - 30, 
            200, 20,
            Text.translatable("randomrun.button.back"),
            button -> close()
        ));
    }
    
    private Text getModeText() {
        if (modeIndex == 0) return Text.translatable("randomrun.battle.separate_worlds");
        if (modeIndex == 1) return Text.translatable("randomrun.battle.shared_world");
        return Text.translatable("randomrun.battle.shared_world_radmin");
    }
    
    private void createRoom() {
        if (targetItem == null) return;
        
        // Проверка E4MC для Shared World (e4mc)
        if (modeIndex == 1) {
            boolean isE4mcLoaded = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("e4mc_minecraft") 
                                || net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("e4mc");
            
            if (!isE4mcLoaded) {
                try {
                    Class.forName("link.e4mc.E4mcClient");
                    isE4mcLoaded = true;
                } catch (ClassNotFoundException e) {
                }
            }
            
            if (!isE4mcLoaded) {
                 if (MinecraftClient.getInstance().player != null) {
                     MinecraftClient.getInstance().player.sendMessage(Text.translatable("randomrun.error.e4mc_not_installed"), false);
                 }
                 errorMessage = Text.translatable("randomrun.error.shared_world_requires_e4mc").getString();
                 errorTime = System.currentTimeMillis();
                 return;
            }
        }
        
        String playerName = MinecraftClient.getInstance().getSession().getUsername();
        String password = passwordField.getText();
        boolean isPrivate = !password.isEmpty();
        boolean isSharedWorld = (modeIndex != 0);
        String creationMode = (modeIndex == 0) ? "sw" : ((modeIndex == 1) ? "e4" : "rv");
        
        BattleManager.getInstance().createRoom(playerName, targetItem, isSharedWorld, isPrivate, password, maxPlayers, creationMode).thenAccept(roomCode -> {
            if (roomCode != null) {
                MinecraftClient.getInstance().execute(() -> {
                    // Переход к экрану ожидания
                    // Мы не передаем режим Radmin сюда явно, но BattleWaitingScreen сможет узнать его 
                    // через поле isSharedWorld и будущий флаг "radmin" (если мы добавим его в BattleRoom)
                    // Или просто передадим параметр в BattleWaitingScreen
                    MinecraftClient.getInstance().setScreen(new BattleWaitingScreen(parent, roomCode, true, modeIndex == 2));
                });
            } else {
                MinecraftClient.getInstance().execute(() -> {
                    errorMessage = "Не удалось создать комнату";
                    errorTime = System.currentTimeMillis();
                });
            }
        });
    }
    
    // Отображение сообщения об ошибке
    private String errorMessage = null;
    private long errorTime = 0;
    
    // Выбор провайдера (e4mc / WorldHost)
    private ButtonDefault providerButton;
    private String selectedProvider = "WORLD_HOST"; // WORLD_HOST, E4MC
    private boolean isE4mcInstalled = false;
    private boolean isWorldHostInstalled = false;

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (revealWidget != null && revealWidget.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (revealWidget != null && revealWidget.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (revealWidget != null && revealWidget.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderGradientBackground(context);
        
        if (revealWidget != null) {
            revealWidget.render(context, mouseX, mouseY, delta);
        }
        
        // Сначала рендерим все стандартные виджеты (кнопки, слайдеры и т.д.)
        super.render(context, mouseX, mouseY, delta);
        
        String itemName = targetItem.getName().getString();
        context.drawCenteredTextWithShadow(textRenderer, itemName, width / 2, height / 2 + 15, 0xFFFFFF);
        
        // Показать сообщение об ошибке, если оно есть и недавнее (< 5 секунд)
        if (errorMessage != null && System.currentTimeMillis() - errorTime < 5000) {
            context.drawCenteredTextWithShadow(textRenderer, errorMessage, width / 2, height - 80, 0xFF5555);
        }
        
        context.drawCenteredTextWithShadow(textRenderer, 
            Text.translatable("randomrun.drag_to_rotate"), 
            width / 2, height / 2 + 25, 0x666666); 
    }
    
    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }
    
    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }
}
