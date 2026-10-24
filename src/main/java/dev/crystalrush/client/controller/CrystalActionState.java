package dev.crystalrush.client.controller;

/**
 * Explicit state enumeration for the CrystalRush action state machine.
 */
public enum CrystalActionState {
    /** Waiting for the player to hold the configured activation key. */
    IDLE,

    /** Inspecting the player's hands and hotbar for End Crystals. */
    CHECKING_INVENTORY,

    /** Switching the selected hotbar slot to a detected End Crystal slot if needed. */
    SELECTING_CRYSTAL,

    /** Validating the current crosshair target (Obsidian/Bedrock base or existing EndCrystalEntity). */
    CHECKING_TARGET,

    /** Waiting for server/world state confirmation after dispatching a single vanilla interaction. */
    WAITING_FOR_GAME_STATE,

    /** World state confirmed; enforcing the configured minimum action interval before next cycle. */
    ACTION_COMPLETED,

    /** Action cycle aborted due to key release, GUI opening, missing crystals, or disconnect. */
    CANCELLED,

    /** Safely restoring the player's previous hotbar slot if not manually changed. */
    RESTORING_SLOT
}
