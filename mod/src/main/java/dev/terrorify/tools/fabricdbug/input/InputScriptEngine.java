package dev.terrorify.tools.fabricdbug.input;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Applies agent-issued movement/look scripts without changing vanilla physics. */
public final class InputScriptEngine {
    /** Vanilla bindings that can be held by a deterministic debug script. */
    public enum Key { FORWARD, BACK, LEFT, RIGHT, JUMP, SPRINT, SNEAK, ATTACK, USE }
    public record Step(int ticks, Set<Key> keys, Float yaw, Float pitch) {}

    private final Deque<Step> queue = new ArrayDeque<>();
    private Step active;
    private int remaining;

    public synchronized void start(List<Step> steps) {
        stop();
        queue.addAll(steps);
        advance();
    }

    public synchronized void stop() {
        queue.clear();
        active = null;
        remaining = 0;
        releaseAll();
    }

    public synchronized void tick() {
        if (active == null || --remaining > 0) return;
        advance();
    }

    private void advance() {
        releaseAll();
        active = queue.poll();
        if (active == null) return;
        remaining = Math.max(1, active.ticks());
        for (Key key : active.keys()) {
            KeyMapping mapping = keyMappings().get(key);
            if (mapping != null) {
                mapping.setDown(true);
                // Attack/use need an actual click edge, not only the held state.
                if (key == Key.ATTACK || key == Key.USE) KeyMapping.click(mapping.getDefaultKey());
            }
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            if (active.yaw() != null) player.setYRot(active.yaw());
            if (active.pitch() != null) player.setXRot(active.pitch());
        }
    }

    private void releaseAll() {
        for (KeyMapping mapping : keyMappings().values()) mapping.setDown(false);
    }

    private Map<Key, KeyMapping> keyMappings() {
        Options options = Minecraft.getInstance().options;
        Map<Key, KeyMapping> map = new EnumMap<>(Key.class);
        if (options == null) return map;
        map.put(Key.FORWARD, options.keyUp); map.put(Key.BACK, options.keyDown);
        map.put(Key.LEFT, options.keyLeft); map.put(Key.RIGHT, options.keyRight);
        map.put(Key.JUMP, options.keyJump); map.put(Key.SPRINT, options.keySprint);
        map.put(Key.SNEAK, options.keyShift);
        map.put(Key.ATTACK, options.keyAttack);
        map.put(Key.USE, options.keyUse);
        return map;
    }
}
