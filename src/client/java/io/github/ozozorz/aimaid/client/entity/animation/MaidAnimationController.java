package io.github.ozozorz.aimaid.client.entity.animation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import com.google.common.collect.MapMaker;
import io.github.ozozorz.aimaid.client.entity.state.AiMaidEntityRenderState;
import io.github.ozozorz.aimaid.entity.AiMaidEntity;
import io.github.ozozorz.aimaid.entity.animation.MaidAnimationAction;
import net.minecraft.client.model.HumanoidModel.ArmPose;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.SwingAnimationType;

/** 每只客户端实体一份状态；value 不持有实体，不阻止卸载回收。 */
public final class MaidAnimationController {
    // Entity.equals 比较网络 ID；weakKeys 使用对象身份，重连/重追踪不能复用旧实例的轨道。
    private static final Map<AiMaidEntity, MaidAnimationController> CONTROLLERS = new MapMaker().weakKeys().makeMap();
    private final Map<Key, Track> tracks = new LinkedHashMap<>();
    private int lastTick = Integer.MIN_VALUE;
    private int swingSequence;
    private float previousSwing;
    private boolean swingSuppressed;
    private float run, oldRun, air, oldAir, rising, oldRising, water, oldWater;
    private Identifier active = MaidAnimationAction.NONE;

    private record Key(Identifier id, int sequence, HumanoidArm arm) {}
    private record Selection(Key key, float elapsed, int priority, float fade) {}
    private static final class Track {
        final Key key;
        final float fade;
        float weight, oldWeight, elapsed, useTicks, chargeTicks;
        Track(Selection selection) { key = selection.key; fade = selection.fade; elapsed = selection.elapsed; }
    }

    public static MaidAnimationFrame extract(AiMaidEntity maid, AiMaidEntityRenderState state, float partialTick) {
        return CONTROLLERS.computeIfAbsent(maid, ignored -> new MaidAnimationController()).frame(maid, state, partialTick);
    }

    private MaidAnimationFrame frame(AiMaidEntity maid, AiMaidEntityRenderState state, float partial) {
        boolean blocked = state.deathTime > 0 || state.hasPose(Pose.SLEEPING) || state.isAutoSpinAttack;
        Selection selected = choose(maid, state, partial);
        if (blocked) { selected = null; tracks.clear(); active = MaidAnimationAction.NONE; }
        if (lastTick != maid.tickCount) {
            boolean first = lastTick == Integer.MIN_VALUE;
            int delta = first ? 1 : Mth.clamp(maid.tickCount - lastTick, 1, 20);
            lastTick = maid.tickCount;
            float ground = maid.onGround() && !state.isInWater && !state.isPassenger ? 1 : 0;
            // 这里是原版平滑后的实际位移量，不是 WALK_TARGET 的速度倍率。
            // 当前女仆低速闲逛约 0.24，正常追赶约 0.40；按实际步速渐变。
            float targetRun = ground * Mth.clamp((state.walkAnimationSpeed - 0.28F) / 0.17F, 0, 1);
            float targetAir = !maid.onGround() && !state.isInWater && !state.isPassenger && !state.isFallFlying ? 1 : 0;
            float targetRise = maid.getDeltaMovement().y > 0.025 ? 1 : 0;
            oldRun = run; oldAir = air; oldRising = rising; oldWater = water;
            run = damp(run, targetRun, delta, 4); air = damp(air, targetAir, delta, 3);
            rising = damp(rising, targetRise, delta, 2); water = damp(water, state.isInWater ? 1 : 0, delta, 4);
            if (first) { oldRun = run = targetRun; oldAir = air = targetAir; oldRising = rising = targetRise; oldWater = water = state.isInWater ? 1 : 0; }
            if (selected != null && !tracks.containsKey(selected.key)) {
                if (tracks.size() >= 4) tracks.remove(tracks.keySet().iterator().next());
                Track track = new Track(selected);
                if (first) track.weight = track.oldWeight = 1;
                tracks.put(selected.key, track);
            }
            for (Track track : tracks.values()) {
                boolean current = selected != null && track.key.equals(selected.key);
                track.oldWeight = track.weight;
                track.weight = damp(track.weight, current ? 1 : 0, delta, track.fade);
                if (current) {
                    track.elapsed = selected.elapsed;
                    track.useTicks = state.ticksUsingItem;
                    track.chargeTicks = Math.max(1, state.maxCrossbowChargeDuration);
                } else {
                    track.elapsed += delta;
                }
            }
            tracks.values().removeIf(track -> track.weight < 0.005F && track.oldWeight < 0.005F);
            active = selected == null ? MaidAnimationAction.NONE : selected.key.id;
            previousSwing = state.attackTime;
        }
        // 相同 tick 的第二次提取（包括背包预览）只采样，不能再推进过渡。
        var layers = new ArrayList<MaidAnimationFrame.Layer>();
        float sum = 0;
        for (Track track : tracks.values()) sum += Mth.lerp(partial, track.oldWeight, track.weight);
        for (Track track : tracks.values()) {
            float time = track.elapsed + partial;
            if (selected != null && selected.key.equals(track.key)) time = selected.elapsed;
            layers.add(new MaidAnimationFrame.Layer(track.key.id,
                    Mth.lerp(partial, track.oldWeight, track.weight) / Math.max(1, sum), time, track.key.arm,
                    track.useTicks, track.chargeTicks));
        }
        float hurt = maid.hurtTime > 0 && state.deathTime <= 0
                ? Mth.sin((1 - Mth.clamp((maid.hurtTime - partial) / Math.max(1F, maid.hurtDuration), 0, 1)) * Mth.PI) : 0;
        return new MaidAnimationFrame(Mth.lerp(partial, oldRun, run), Mth.lerp(partial, oldAir, air),
                Mth.lerp(partial, oldRising, rising), Mth.lerp(partial, oldWater, water), hurt,
                (maid.getUUID().hashCode() & 255) * 0.19F, active, layers);
    }

    private Selection choose(AiMaidEntity maid, AiMaidEntityRenderState state, float partial) {
        Selection result = null;
        var action = maid.getAnimationAction();
        var clip = action.isActive(maid.level().getGameTime()) ? MaidAnimations.get(action.id()) : null;
        HumanoidArm usedArm = state.useItemHand == net.minecraft.world.InteractionHand.MAIN_HAND ? state.mainArm : state.mainArm.getOpposite();
        ArmPose usedPose = usedArm == HumanoidArm.RIGHT ? state.rightArmPose : state.leftArmPose;
        if (state.isUsingItem && (usedPose == ArmPose.BOW_AND_ARROW || usedPose == ArmPose.CROSSBOW_CHARGE)) {
            result = new Selection(new Key(usedPose == ArmPose.BOW_AND_ARROW ? MaidAnimations.BOW : MaidAnimations.CROSSBOW_CHARGE,
                    0, usedArm), state.ticksUsingItem, 300, 2);
        } else if (state.rightArmPose == ArmPose.CROSSBOW_HOLD || state.leftArmPose == ArmPose.CROSSBOW_HOLD) {
            result = new Selection(new Key(MaidAnimations.CROSSBOW_HOLD, 0,
                    state.rightArmPose == ArmPose.CROSSBOW_HOLD ? HumanoidArm.RIGHT : HumanoidArm.LEFT), 0, 100, 3);
        }
        if (state.attackTime > 0 && state.swingAnimationType == SwingAnimationType.WHACK) {
            if (lastTick != maid.tickCount && (previousSwing <= 0 || state.attackTime < previousSwing)) {
                swingSequence++;
                swingSuppressed = clip != null && clip.priority() >= 220;
            }
            // 工作中的原版 swing 包不另外播放一次近战，取消工作后也不补播它的尾巴。
            if (!swingSuppressed) result = higher(result, new Selection(new Key(MaidAnimations.MELEE, swingSequence, state.attackArm),
                    state.attackTime * 6, 220, 1));
        }
        if (action.isActive(maid.level().getGameTime())) {
            boolean recoil = action.id().equals(MaidAnimationAction.BOW_RELEASE) || action.id().equals(MaidAnimationAction.CROSSBOW_FIRE);
            if (clip != null || recoil) result = higher(result, new Selection(new Key(action.id(), action.sequence(), state.mainArm),
                    action.elapsed(maid.level().getGameTime(), partial), recoil ? 350 : clip.priority(), recoil ? 1 : clip.transitionTicks()));
        }
        return result;
    }

    private static Selection higher(Selection left, Selection right) { return left == null || right.priority > left.priority ? right : left; }
    private static float damp(float value, float target, int delta, float ticks) {
        return Mth.lerp(1 - (float) Math.exp(-delta / ticks), value, target);
    }
}
