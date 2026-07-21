package com.bossrush.entity.boss;

/**
 * 0: DORMANT     - buried/still, AI disabled, wakes on proximity or first hit
 * 1: AWAKENED    - melee swipe + sonic pulse beam
 * 2: CONSUMING   - periodic vortex: pulls entities in while firing at them
 * 3: COLLAPSING  - 5s scripted death sequence, then a block-safe explosion
 */
public enum HollowWardenPhase {
    DORMANT,
    AWAKENED,
    CONSUMING,
    COLLAPSING
}
