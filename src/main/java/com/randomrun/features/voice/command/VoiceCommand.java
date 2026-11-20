package com.randomrun.features.voice.command;





public sealed interface VoiceCommand {
    enum Dir { FORWARD, BACK, LEFT, RIGHT }
    enum Cardinal { NORTH, EAST, SOUTH, WEST }


    record Start() implements VoiceCommand {}

    record Stop() implements VoiceCommand {}

    record Move(Dir dir, boolean sprint, int blocks) implements VoiceCommand {}
    record Sprint(boolean on) implements VoiceCommand {}
    record Jump(boolean hold, int times) implements VoiceCommand {}

    record Swim(String mode) implements VoiceCommand {}
    record JumpOff() implements VoiceCommand {}
    record Sneak(boolean on) implements VoiceCommand {}

    record Turn(float degrees) implements VoiceCommand {}

    record Pitch(float degrees, boolean relative) implements VoiceCommand {}
    record Face(Cardinal cardinal) implements VoiceCommand {}
    record Aim(String target) implements VoiceCommand {}

    record Hit(String target, boolean all) implements VoiceCommand {}
    record Use(String target) implements VoiceCommand {}

    record Place(String item, String tier, String target, int count, String words) implements VoiceCommand {}
    record Eat() implements VoiceCommand {}
    record Select(String item, String tier, int slot) implements VoiceCommand {}

    record Get(String item, String tier, int count) implements VoiceCommand {}
    record Inventory(boolean open) implements VoiceCommand {}
    record Craft(String item, String tier, int count, boolean all) implements VoiceCommand {}
    record Drop(String item, boolean all) implements VoiceCommand {}

    record Approach(String target, boolean little) implements VoiceCommand {}

    record Pickup(boolean all) implements VoiceCommand {}

    record Repeat(int times) implements VoiceCommand {}

    record Refine(String dir) implements VoiceCommand {}

    record NewSeed() implements VoiceCommand {}
    record UpgraderOpen(boolean open) implements VoiceCommand {}

    record UpgraderChance(double ratio) implements VoiceCommand {}
    record UpgraderTarget(String item, String tier, String target, String words, int count) implements VoiceCommand {}
    record UpgraderRoll() implements VoiceCommand {}
    record UpgraderClear() implements VoiceCommand {}
    record UpgraderSkip() implements VoiceCommand {}
}
