package com.bossrush.command;

import com.bossrush.entity.ModEntities;
import com.bossrush.entity.boss.AbstractBossEntity;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.EntityType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * /bossrush debug mode <true|false>
 * /bossrush debug attack <boss> <attack> <target>
 * /bossrush debug list <boss>
 *
 * Debug mode is off by default and gates the "attack" subcommand — the
 * "mode" and "list" subcommands always work so you can check things
 * without turning it on. Requires permission level 2 (same as most
 * vanilla admin commands).
 *
 * Example:
 *   /bossrush debug mode true
 *   /bossrush debug attack ttig projectile Steve
 *   -> "[TTIG] uses Projectile Attack (target: Steve)"
 *
 * Boss ids currently registered: ttig, warden, familiar, sovereign,
 * warlord. Only TTIG has an attack wired up right now (its ranged
 * barrage, id "projectile") — see GolemRangedBarrageGoal#debugForceFire
 * and TrueIronGolemEntity#initGoals for the pattern to follow when
 * wiring up more.
 */
public class BossRushCommand {
    public static boolean debugMode = false;

    private static final Map<String, EntityType<? extends AbstractBossEntity>> BOSS_IDS = new LinkedHashMap<>();

    static {
        BOSS_IDS.put("ttig", ModEntities.TRUE_IRON_GOLEM);
        BOSS_IDS.put("warden", ModEntities.HOLLOW_WARDEN);
        BOSS_IDS.put("familiar", ModEntities.END_FAMILIAR);
        BOSS_IDS.put("sovereign", ModEntities.SHULKER_SOVEREIGN);
        BOSS_IDS.put("warlord", ModEntities.BASTION_WARLORD);
    }

    private static final SuggestionProvider<ServerCommandSource> BOSS_SUGGESTIONS = (ctx, builder) -> {
        for (String id : BOSS_IDS.keySet()) builder.suggest(id);
        return builder.buildFuture();
    };

    private static final SuggestionProvider<ServerCommandSource> ATTACK_SUGGESTIONS = (ctx, builder) -> {
        try {
            String bossId = StringArgumentType.getString(ctx, "boss");
            EntityType<? extends AbstractBossEntity> type = BOSS_IDS.get(bossId.toLowerCase());
            if (type != null) {
                AbstractBossEntity boss = findNearestBoss(ctx.getSource(), type);
                if (boss != null) {
                    for (String id : boss.getDebugAttacks().keySet()) builder.suggest(id);
                }
            }
        } catch (IllegalArgumentException ignored) {
            // "boss" argument not filled in yet — no suggestions to offer
        }
        return builder.buildFuture();
    };

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal("bossrush")
                        .requires(source -> source.hasPermissionLevel(2))
                        .then(CommandManager.literal("debug")
                                .then(CommandManager.literal("mode")
                                        .then(CommandManager.argument("value", BoolArgumentType.bool())
                                                .executes(BossRushCommand::setDebugMode)))
                                .then(CommandManager.literal("attack")
                                        .then(CommandManager.argument("boss", StringArgumentType.word())
                                                .suggests(BOSS_SUGGESTIONS)
                                                .then(CommandManager.argument("attack", StringArgumentType.word())
                                                        .suggests(ATTACK_SUGGESTIONS)
                                                        .then(CommandManager.argument("target", EntityArgumentType.player())
                                                                .executes(BossRushCommand::runDebugAttack)))))
                                .then(CommandManager.literal("list")
                                        .then(CommandManager.argument("boss", StringArgumentType.word())
                                                .suggests(BOSS_SUGGESTIONS)
                                                .executes(BossRushCommand::listAttacks))))));
    }

    private static int setDebugMode(CommandContext<ServerCommandSource> ctx) {
        boolean value = BoolArgumentType.getBool(ctx, "value");
        debugMode = value;
        ctx.getSource().sendFeedback(() -> Text.literal("Boss Rush debug mode: " + (value ? "ON" : "OFF")), true);
        return 1;
    }

    private static int runDebugAttack(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerCommandSource source = ctx.getSource();

        if (!debugMode) {
            source.sendError(Text.literal("Debug mode is off. Enable it with /bossrush debug mode true"));
            return 0;
        }

        String bossId = StringArgumentType.getString(ctx, "boss").toLowerCase();
        String attackId = StringArgumentType.getString(ctx, "attack").toLowerCase();
        ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "target");

        EntityType<? extends AbstractBossEntity> type = BOSS_IDS.get(bossId);
        if (type == null) {
            source.sendError(Text.literal("Unknown boss id '" + bossId + "'. Try: " + String.join(", ", BOSS_IDS.keySet())));
            return 0;
        }

        AbstractBossEntity boss = findNearestBoss(source, type);
        if (boss == null) {
            source.sendError(Text.literal("No " + bossId + " found within 100 blocks. Summon one first."));
            return 0;
        }

        AbstractBossEntity.DebugAttack attack = boss.getDebugAttacks().get(attackId);
        if (attack == null) {
            String available = boss.getDebugAttacks().isEmpty()
                    ? "(none wired up yet)"
                    : String.join(", ", boss.getDebugAttacks().keySet());
            source.sendError(Text.literal("Unknown attack '" + attackId + "' for " + bossId + ". Available: " + available));
            return 0;
        }

        attack.trigger().accept(target);

        String message = "[" + bossId.toUpperCase() + "] uses " + attack.displayName()
                + " (target: " + target.getName().getString() + ")";
        source.sendFeedback(() -> Text.literal(message), true);
        return 1;
    }

    private static int listAttacks(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        String bossId = StringArgumentType.getString(ctx, "boss").toLowerCase();

        EntityType<? extends AbstractBossEntity> type = BOSS_IDS.get(bossId);
        if (type == null) {
            source.sendError(Text.literal("Unknown boss id '" + bossId + "'. Try: " + String.join(", ", BOSS_IDS.keySet())));
            return 0;
        }

        AbstractBossEntity boss = findNearestBoss(source, type);
        if (boss == null) {
            source.sendError(Text.literal("No " + bossId + " found within 100 blocks."));
            return 0;
        }

        String available = boss.getDebugAttacks().isEmpty()
                ? "(none wired up yet)"
                : String.join(", ", boss.getDebugAttacks().keySet());
        source.sendFeedback(() -> Text.literal("Attacks for " + bossId + ": " + available), false);
        return 1;
    }

    private static AbstractBossEntity findNearestBoss(ServerCommandSource source, EntityType<? extends AbstractBossEntity> type) {
        if (!(source.getWorld() instanceof ServerWorld world)) return null;
        Vec3d pos = source.getPosition();
        Box box = new Box(pos.x - 100, pos.y - 100, pos.z - 100, pos.x + 100, pos.y + 100, pos.z + 100);

        List<AbstractBossEntity> candidates = world.getEntitiesByClass(
                AbstractBossEntity.class, box, e -> e.getType() == type);

        AbstractBossEntity nearest = null;
        double bestDistSq = Double.MAX_VALUE;
        for (AbstractBossEntity b : candidates) {
            double distSq = b.squaredDistanceTo(pos.x, pos.y, pos.z);
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                nearest = b;
            }
        }
        return nearest;
    }
}
