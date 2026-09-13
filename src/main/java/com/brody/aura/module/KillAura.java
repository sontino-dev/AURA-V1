package com.brody.aura.module;

import com.brody.aura.KillAuraMod;
import com.brody.aura.rotation.RotationManager;
import com.brody.aura.target.TargetManager;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.MaceItem;
import net.minecraft.item.SwordItem;
import net.minecraft.item.TridentItem;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * KillAura main module. The heart of the mod.
 *
 * Technique sources (researched):
 * - ThunderHack Aura    : DVD-logo legit aim point, smartCrit decision tree,
 *                         shieldBreaker axe-swap, dropSprint/returnSprint packets,
 *                         attackTickLimit, grim raytrace gate
 * - VCore Aura          : Track/Grim/Snap rotation handlers with GCD quantization,
 *                         pitch acceleration, lockTarget, projectile override,
 *                         durability sort
 * - Catalyst KillAura   : cooldown-synced autoDelay concept
 *
 * Combined + extended with the AI layer (adaptive cps/rotation + hit trainer).
 */
public class KillAura extends Module {

    public enum RotationMode { TRACK, GRIM, SNAP, NONE }
    public enum TimingMode { COOLDOWN, CPS }

    private final TargetManager targets = new TargetManager();

    public Entity currentTargetEntity;
    private int hitTicks = 0;
    private boolean sprintWasActive = false;
    private int postDelayTicks = 0;
    private double lastCpsUsed;
    private double lastRotUsed;

    // attack outcome tracking (for AI)
    private int ticksSinceAttack = -1;
    private boolean awaitingDamageConfirm = false;

    public KillAura() {
        super("KillAura", "AI-driven automatic melee attack system");
    }

    public Entity currentTarget() {
        return currentTargetEntity;
    }

    /** Ticks left before the next attack is allowed (VulcanMode planner uses it). */
    public int ticksUntilNextAttack() {
        return Math.max(0, hitTicks);
    }

    private VulcanMode getVulcan() {
        Module m = KillAuraMod.MODULES.get("VulcanMode");
        return m instanceof VulcanMode vm ? vm : null;
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) {
            reset();
            return;
        }

        // pause conditions
        if (mc.player.isUsingItem() && isConsuming(mc)) {
            return;
        }

        if (KillAuraMod.AI.isFlaggedRecently() && KillAuraMod.CONFIG.vulcanSafe) {
            // hard backoff window after a flag
            hitTicks = Math.max(hitTicks, 3);
        }

        doAuraLogic(mc);
        hitTicks--;
        postDelayTicks = Math.max(0, postDelayTicks - 1);

        // AI attack outcome window
        if (awaitingDamageConfirm) {
            ticksSinceAttack++;
            if (ticksSinceAttack > 8) {
                awaitingDamageConfirm = false;
                KillAuraMod.AI.onHitMiss();
            }
        }

        if (currentTargetEntity != null) {
            KillAuraMod.AI.feedTarget(currentTargetEntity);
        }
    }

    private void doAuraLogic(MinecraftClient mc) {
        if (!hasWeapon(mc)) {
            currentTargetEntity = null;
            return;
        }

        // update target
        Entity candidate = targets.updateTarget(mc, KillAuraMod.CONFIG.reach + KillAuraMod.CONFIG.wallRange, KillAuraMod.CONFIG.fov);
        currentTargetEntity = candidate;

        if (candidate == null) {
            KillAuraMod.ROTATIONS.reset(mc);
            return;
        }

        // VulcanMode adaptive layer (CatLean-inspired)
        VulcanMode vulcan = getVulcan();
        boolean vulcanOn = vulcan != null && vulcan.isEnabled() && KillAuraMod.CONFIG.vulcanSafe;
        if (vulcanOn) vulcan.onTargetTick(mc, candidate);

        // per-attack reach: CatLean randomized reach when VulcanMode is on
        double effectiveReach = vulcanOn ? vulcan.rollAttackReach(mc) : KillAuraMod.CONFIG.reach;

        // auto sprint reset (w-tap) - anti-metronome share when VulcanMode on
        boolean doReset = vulcanOn ? vulcan.rollSprintReset() : KillAuraMod.CONFIG.sprintReset;
        if (KillAuraMod.CONFIG.autoSprint && doReset && candidate instanceof LivingEntity) {
            handleSprintReset(mc);
        }

        // rotation
        RotationManager.Mode rotMode = getRotationMode();
        double effectiveRot = KillAuraMod.AI.effectiveRotSpeed();
        lastRotUsed = effectiveRot > 0 ? effectiveRot : KillAuraMod.CONFIG.rotationSpeed;

        boolean readyForCrit = autoCrit(mc);
        boolean rotationReady = KillAuraMod.ROTATIONS.rotate(mc, candidate, rotMode, readyForCrit,
                effectiveReach, KillAuraMod.CONFIG.wallRange, true);

        // AI attack outcome tracking snapshot
        double effectiveCps = KillAuraMod.AI.effectiveCps();
        lastCpsUsed = effectiveCps;

        boolean gateOpen = vulcanOn ? vulcan.gateAttack(mc, candidate) : KillAuraMod.ANTICHEAT.gateAttack(mc);
        boolean readyForAttack = readyForCrit && (rotationReady || skipRayTraceCheck(mc)) && hitTicks <= 0 && postDelayTicks <= 0 && gateOpen;

        if (!readyForAttack) return;
        if (!isTargetInAttackRange(mc, candidate, effectiveReach)) return;

        // shield breaker: swap axe, hit, swap back
        if (KillAuraMod.CONFIG.hvhShieldBreaker && shieldBreaker(mc)) return;

        boolean[] state = preAttack(mc, doReset);
        boolean attacked = false;
        if (!(candidate instanceof PlayerEntity pl && pl.isUsingItem()
                && pl.getOffHandStack().getItem() == Items.SHIELD)) {
            KillAuraMod.ANTICHEAT.sendPreAttackMove(mc);
            attack(mc, candidate);
            KillAuraMod.ANTICHEAT.sendPostAttackMove(mc);
            attacked = true;
        }
        postAttack(mc, state[0], state[1], doReset);
        if (attacked && vulcanOn) vulcan.onAttacked();

        // smart feint: occasionally pause attacks to bait parry/shield
        if (KillAuraMod.CONFIG.hvhSmartFeint && Math.random() < KillAuraMod.CONFIG.hvhFeintChance) {
            hitTicks = 6 + (int) (Math.random() * 5);
        }

        // AI snapshot for trainer
        KillAuraMod.AI.trainer.snapshot(lastCpsUsed, lastRotUsed);
        if (attacked) {
            awaitingDamageConfirm = true;
            ticksSinceAttack = 0;
        }
    }

    public void notifyDamageConfirmed() {
        if (awaitingDamageConfirm) {
            awaitingDamageConfirm = false;
            KillAuraMod.AI.onHitSuccess();
        }
    }

    private RotationManager.Mode getRotationMode() {
        return RotationManager.Mode.TRACK;
    }

    private boolean hasWeapon(MinecraftClient mc) {
        Item item = mc.player.getMainHandStack().getItem();
        return item instanceof SwordItem || item instanceof AxeItem
                || item instanceof TridentItem || item instanceof MaceItem;
    }

    private boolean skipRayTraceCheck(MinecraftClient mc) {
        // mirror VCore skipRayTraceCheck: when standing under a low ceiling raytrace
        // to the head fails, allow the attack
        return mc.world.getBlockCollisions(mc.player,
                mc.player.getBoundingBox().expand(-0.25, 0.0, -0.25).offset(0.0, 1.0, 0.0)).iterator().hasNext();
    }

    /**
     * Smart crit decision tree - ThunderHack/VCore style.
     * Decides whether this attack should be a jumping crit.
     */
    private boolean autoCrit(MinecraftClient mc) {
        if (!KillAuraMod.CONFIG.autoJumpCrit) return true;

        boolean reasonForSkipCrit =
                mc.player.getAbilities().flying
                || mc.player.isFallFlying()
                || mc.player.hasStatusEffect(StatusEffects.BLINDNESS)
                || mc.player.hasStatusEffect(StatusEffects.SLOW_FALLING)
                || isInWeb(mc);

        if (getAttackCooldown(mc) < 0.9f) return false;

        if (mc.player.isInLava() || mc.player.isSubmergedInWater()) return true;
        if (isAboveWater(mc)) return true;

        if (mc.player.fallDistance > 1.0f && mc.player.fallDistance < 1.14f) return false;

        if (reasonForSkipCrit) return true;

        return !mc.player.isOnGround() && mc.player.fallDistance > 0.0f;
    }

    private boolean isInWeb(MinecraftClient mc) {
        Vec3d pos = mc.player.getPos();
        BlockPos bp = BlockPos.ofFloored(pos);
        return mc.world.getBlockState(bp).getBlock() == Blocks.COBWEB
                || mc.world.getBlockState(bp.up()).getBlock() == Blocks.COBWEB;
    }

    private boolean isAboveWater(MinecraftClient mc) {
        return mc.world.getBlockState(BlockPos.ofFloored(mc.player.getPos().add(0.0, -0.4, 0.0))).getBlock() == Blocks.WATER;
    }

    private float getAttackCooldown(MinecraftClient mc) {
        return MathHelper.clamp(mc.player.getAttackCooldownProgress(0.0f), 0.0f, 1.0f);
    }

    private boolean isTargetInAttackRange(MinecraftClient mc, Entity target, double range) {
        Vec3d eyePos = mc.player.getEyePos();
        var box = target.getBoundingBox();
        Vec3d closest = new Vec3d(
                MathHelper.clamp(eyePos.x, box.minX, box.maxX),
                MathHelper.clamp(eyePos.y, box.minY, box.maxY),
                MathHelper.clamp(eyePos.z, box.minZ, box.maxZ)
        );
        double softCap = Math.min(range, KillAuraMod.CONFIG.maxReachSoft);
        return eyePos.squaredDistanceTo(closest) <= softCap * softCap;
    }

    private void attack(MinecraftClient mc, Entity target) {
        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);
        mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
        hitTicks = computeHitTicks();

        if (KillAuraMod.CONFIG.sprintReset) {
            handleSprintReset(mc);
        }

    }

    private int computeHitTicks() {
        // VulcanMode profile CPS scaling (LEGIT slows down, RAGE speeds up)
        double mul = 1.0;
        VulcanMode vulcan = getVulcan();
        if (vulcan != null && vulcan.isEnabled() && KillAuraMod.CONFIG.vulcanSafe) {
            mul = vulcan.cpsMultiplier();
        }
        if (KillAuraMod.CONFIG.aiEnabled && KillAuraMod.CONFIG.aiAdaptiveCps) {
            double cps = KillAuraMod.AI.effectiveCps() * mul;
            return Math.max(2, (int) Math.round(20.0 / Math.max(2.0, cps)));
        }
        double cps = (KillAuraMod.CONFIG.minCps + Math.random() * (KillAuraMod.CONFIG.maxCps - KillAuraMod.CONFIG.minCps)) * mul;
        return Math.max(2, (int) Math.round(20.0 / Math.max(2.0, cps)));
    }

    /**
     * Sprint reset before hit (w-tap packet technique) - keeps sprint damage bonus
     * and confuses reach checks that track sprint state.
     */
    private void handleSprintReset(MinecraftClient mc) {
        if (mc.player.isSprinting() && !sprintWasActive) {
            sprintWasActive = true;
            if (KillAuraMod.CONFIG.vulcanSafe) {
                // only drop sprint when actually striking, restored in postAttack
            }
        }
    }

    private boolean[] preAttack(MinecraftClient mc, boolean doReset) {
        boolean blocking = mc.player.isUsingItem() && mc.player.getActiveItem().getItem().getUseAction(mc.player.getActiveItem()) == UseAction.BLOCK;
        if (blocking) {
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, Direction.DOWN));
        }

        boolean sprint = mc.player.isSprinting();
        if (sprint && doReset) {
            disableSprint(mc);
        }

        return new boolean[]{blocking, sprint};
    }

    private void postAttack(MinecraftClient mc, boolean wasBlocking, boolean wasSprinting, boolean doReset) {
        if (wasSprinting && doReset) {
            enableSprint(mc);
        }
        if (wasBlocking) {
            mc.getNetworkHandler().sendPacket(new PlayerInteractItemC2SPacket(
                    Hand.OFF_HAND,
                    mc.player.getInventory().selectedSlot,
                    KillAuraMod.ROTATIONS.getRotationYaw(),
                    KillAuraMod.ROTATIONS.getRotationPitch()));
        }
        // random post-attack delay (humanization)
        if (KillAuraMod.CONFIG.randomPostDelay) {
            postDelayTicks = KillAuraMod.CONFIG.minPostDelayTicks
                    + (int) (Math.random() * (KillAuraMod.CONFIG.maxPostDelayTicks - KillAuraMod.CONFIG.minPostDelayTicks + 1));
        }
        sprintWasActive = false;
    }

    private void disableSprint(MinecraftClient mc) {
        mc.player.setSprinting(false);
        mc.options.sprintKey.setPressed(false);
        mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
    }

    private void enableSprint(MinecraftClient mc) {
        mc.player.setSprinting(true);
        mc.options.sprintKey.setPressed(true);
        mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_SPRINTING));
    }

    /**
     * Shield breaker - ThunderHack/VCore technique.
     * Target shielding? Swap to axe (silent), hit to break the shield, swap back.
     */
    private boolean shieldBreaker(MinecraftClient mc) {
        if (!(currentTargetEntity instanceof PlayerEntity)) return false;
        PlayerEntity target = (PlayerEntity) currentTargetEntity;
        if (!target.isUsingItem()) return false;
        if (target.getOffHandStack().getItem() != Items.SHIELD
                && target.getMainHandStack().getItem() != Items.SHIELD) return false;

        int axeSlot = findAxe(mc);
        if (axeSlot == -1) return false;

        if (axeSlot >= 9) {
            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, axeSlot,
                    mc.player.getInventory().selectedSlot, SlotActionType.SWAP, mc.player);
            mc.getNetworkHandler().sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
            mc.interactionManager.attackEntity(mc.player, target);
            mc.player.swingHand(Hand.MAIN_HAND);
            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, axeSlot,
                    mc.player.getInventory().selectedSlot, SlotActionType.SWAP, mc.player);
            mc.getNetworkHandler().sendPacket(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
        } else {
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(axeSlot));
            mc.interactionManager.attackEntity(mc.player, target);
            mc.player.swingHand(Hand.MAIN_HAND);
            mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(mc.player.getInventory().selectedSlot));
        }

        hitTicks = 10;
        return true;
    }

    private int findAxe(MinecraftClient mc) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() instanceof AxeItem) return i;
        }
        // check inventory
        for (int i = 9; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() instanceof AxeItem) return i;
        }
        return -1;
    }

    private boolean isConsuming(MinecraftClient mc) {
        if (mc.player.isUsingItem()) {
            UseAction action = mc.player.getActiveItem().getUseAction();
            return action == UseAction.EAT || action == UseAction.DRINK;
        }
        return false;
    }

    private void reset() {
        currentTargetEntity = null;
        hitTicks = 0;
        awaitingDamageConfirm = false;
        targets.clear();
    }

    @Override
    public void onEnable() {
        hitTicks = 0;
        postDelayTicks = 0;
    }

    @Override
    public void onDisable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        currentTargetEntity = null;
        targets.clear();
        if (mc.player != null) {
            KillAuraMod.ROTATIONS.reset(mc);
        }
    }
}
