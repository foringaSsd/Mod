package com.bossrush.entity.boss;

/**
 * No sleeping phase this time — it's aggressive from the moment it spawns.
 * 0: BASE_ATTACKS - Molten Slam (melee) + Flame Wave (cone AOE)
 * 1: EMPOWERED    - adds Reinforcements (real Piglin Brutes) + Meteor Shower (lava hazards)
 * 2: ENRAGED      - same kit, all cooldowns shortened
 */
public enum BastionWarlordPhase {
    BASE_ATTACKS,
    EMPOWERED,
    ENRAGED
}
