package com.randomrun.features.voice.command;

/**
 * Parsed voice commands. Pure data, no Minecraft types, so the parser can be
 * tested without a game. Item and target keys are resolved by the executor.
 */
public sealed interface VoiceCommand {
    enum Dir { FORWARD, BACK, LEFT, RIGHT }
    enum Cardinal { NORTH, EAST, SOUTH, WEST }

    /** Start the run after the countdown freeze ("погнали!"). */
    record Start() implements VoiceCommand {}
    /** Stop everything and clear the queue. */
    record Stop() implements VoiceCommand {}
    /** Keep walking until told to stop, or for {@code blocks} blocks when > 0. */
    record Move(Dir dir, boolean sprint, int blocks) implements VoiceCommand {}
    record Sprint(boolean on) implements VoiceCommand {}
    record Jump(boolean hold, int times) implements VoiceCommand {}
    /** In water: "up" (float to the surface), "down" (dive), "out" (swim to the shore). */
    record Swim(String mode) implements VoiceCommand {}
    record JumpOff() implements VoiceCommand {}
    record Sneak(boolean on) implements VoiceCommand {}
    /** Relative yaw turn in degrees; positive turns right. */
    record Turn(float degrees) implements VoiceCommand {}
    /** Absolute pitch when {@code relative} is false, otherwise a delta. Negative looks up. */
    record Pitch(float degrees, boolean relative) implements VoiceCommand {}
    record Face(Cardinal cardinal) implements VoiceCommand {}
    record Aim(String target) implements VoiceCommand {}
    /** Break the block or hit the mob in the crosshair; with a target, aim first. */
    record Hit(String target, boolean all) implements VoiceCommand {}
    record Use(String target) implements VoiceCommand {}
    /** Place a block, or put items on the upgrader stake when the upgrader is open. */
    record Place(String item, String tier, String target, int count, String words) implements VoiceCommand {}
    record Eat() implements VoiceCommand {}
    record Select(String item, String tier, int slot) implements VoiceCommand {}
    /** Just an item name: take it if we have it, otherwise craft it. */
    record Get(String item, String tier, int count) implements VoiceCommand {}
    record Inventory(boolean open) implements VoiceCommand {}
    record Craft(String item, String tier, int count, boolean all) implements VoiceCommand {}
    record Drop(String item, boolean all) implements VoiceCommand {}
    /** Walk up to a block or mob ("подойди к дереву"), or to the last aimed one when target is null. */
    record Approach(String target, boolean little) implements VoiceCommand {}
    /** Walk over dropped items to pick them up ("подбери"). */
    record Pickup(boolean all) implements VoiceCommand {}
    /** Repeat the previous phrase ("ещё"). */
    record Repeat(int times) implements VoiceCommand {}
    /** Pick another block or mob of the same kind as the last aim: up, down, left, right, far, near, other. */
    record Refine(String dir) implements VoiceCommand {}
    /** Restart the run in a fresh world ("смени сид"). */
    record NewSeed() implements VoiceCommand {}
    record UpgraderOpen(boolean open) implements VoiceCommand {}
    /** Pick an upgrade target worth stake × ratio (30% -> ratio 3.33, x4 -> ratio 4). */
    record UpgraderChance(double ratio) implements VoiceCommand {}
    record UpgraderTarget(String item, String tier, String target, String words, int count) implements VoiceCommand {}
    record UpgraderRoll() implements VoiceCommand {}
    record UpgraderClear() implements VoiceCommand {}
    record UpgraderSkip() implements VoiceCommand {}
}
