package com.bossrush.entity.boss;

/**
 * 0: SLEEPING       - AI disabled, wakes on nearby player / first hit
 * 1: BASE_ATTACKS   - the 3 phase-1 attacks
 * 2: ARENA_CHANGE   - arena expands once, gains additional attacks
 * 3: DYING          - 5s scripted death sequence, then a block-safe explosion
 */
public enum TrueIronGolemPhase {
    SLEEPING,
    BASE_ATTACKS,
    ARENA_CHANGE,
    DYING
}
