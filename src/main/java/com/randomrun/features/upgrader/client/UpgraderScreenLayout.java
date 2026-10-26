package com.randomrun.features.upgrader.client;

public record UpgraderScreenLayout(int windowX, int windowY, int windowWidth, int windowHeight,
                                   int contentX, int contentWidth, int contentTop, int slotSize,
                                   int leftWidth, int rightWidth, int lowerY, int lowerHeight,
                                   int heroHeight) {
    public static UpgraderScreenLayout fit(int screenWidth, int screenHeight) {
        int width = Math.min(Math.min(620, screenWidth - 32), Math.max(288, (int) (screenWidth * .82)));
        int height = Math.min(Math.min(310, screenHeight - 20), Math.max(220, (int) (screenHeight * .84)));
        int windowX = (screenWidth - width) / 2;
        int windowY = (screenHeight - height) / 2;
        int contentWidth = width - 12;
        int contentTop = windowY + 26;
        int slotSize = Math.max(16, Math.min(22,
            Math.min((contentWidth / 2 - 16) / 9, (height - 100) / 9)));
        int lowerHeight = slotSize * 4 + 30;
        int lowerY = windowY + height - 14 - lowerHeight;
        int heroHeight = lowerY - contentTop - 34;
        int leftWidth = Math.max(slotSize * 9 + 8, (contentWidth - 8) / 2);
        int rightWidth = contentWidth - leftWidth - 8;
        return new UpgraderScreenLayout(windowX, windowY, width, height, windowX + 6, contentWidth,
            contentTop, slotSize, leftWidth, rightWidth, lowerY, lowerHeight, heroHeight);
    }

    public int inventoryX() {
        return contentX + (leftWidth - slotSize * 9) / 2;
    }

    public int targetColumns() {
        return Math.max(1, Math.min(9, (rightWidth - 16) / slotSize));
    }

    public int targetX() {
        return contentX + leftWidth + 8 + (rightWidth - targetColumns() * slotSize) / 2;
    }

    public int gridY() { return lowerY + 23; }
    public int actionY() { return contentTop + heroHeight + 4; }
    public int actionHeight() { return 26; }
    public int sideWidth() { return (contentWidth - 16) / 3; }
    public int centerWidth() { return contentWidth - sideWidth() * 2 - 16; }
    public int centerX() { return contentX + sideWidth() + 8; }
    public int targetPanelX() { return centerX() + centerWidth() + 8; }
    public int wheelRadius() { return Math.max(16, Math.min(centerWidth() / 2 - 10, heroHeight / 2 - 9)); }
    public int presetX(int column) { return targetPanelX() + (sideWidth() - 4) * column / 3 + 2 * column; }
    public int presetWidth(int column) {
        return (sideWidth() - 4) * (column + 1) / 3 - (sideWidth() - 4) * column / 3;
    }
}
