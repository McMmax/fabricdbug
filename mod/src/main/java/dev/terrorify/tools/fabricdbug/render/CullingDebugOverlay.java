package dev.terrorify.tools.fabricdbug.render;

import dev.terrorify.tools.fabricdbug.spacetime.HotPlayerTracker;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.world.phys.Vec3;

/**
 * Draws Hot-authoritative positions only. Colour is deliberately unavailable
 * until {@code RendererExposureOracle} supplies a depth-tested result: finite
 * collision rays are not a valid proxy for rendered exposure under V4.
 */
public final class CullingDebugOverlay {
    private static final int UNAVAILABLE = 0xE8FFD23F;
    private final HotPlayerTracker tracker;

    public CullingDebugOverlay(HotPlayerTracker tracker) { this.tracker = tracker; }

    public void install() {
        LevelRenderEvents.BEFORE_GIZMOS.register(context -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null) return;
            Vec3 camera = context.levelState().cameraRenderState.pos;
            Vec3 origin = hudTracerOrigin(camera);
            DrawableGizmoPrimitives gizmos = new DrawableGizmoPrimitives();
            for (HotPlayerTracker.Position player : tracker.snapshot().values()) {
                if (player.uuid().equalsIgnoreCase(mc.player.getUUID().toString())) continue;
                int color = UNAVAILABLE;
                box(gizmos, player.x(), player.y(), player.z(), color);
                gizmos.addLine(origin, new Vec3(player.x(), player.y() + 0.9, player.z()), color, 2.0f);
            }
            gizmos.submit(context.submitNodeCollector(), context.levelState().cameraRenderState, true);
        });
    }

    private static Vec3 hudTracerOrigin(Vec3 cameraPosition) {
        var camera = Minecraft.getInstance().gameRenderer.mainCamera();
        var forward = camera.forwardVector();
        var up = camera.upVector();
        return cameraPosition.add(forward.x() * .18 - up.x() * .105,
            forward.y() * .18 - up.y() * .105, forward.z() * .18 - up.z() * .105);
    }

    private static void box(DrawableGizmoPrimitives g, double x, double y, double z, int color) {
        double r = .3, h = 1.8;
        Vec3[] bottom = {new Vec3(x-r,y,z-r),new Vec3(x+r,y,z-r),new Vec3(x+r,y,z+r),new Vec3(x-r,y,z+r)};
        Vec3[] top = {new Vec3(x-r,y+h,z-r),new Vec3(x+r,y+h,z-r),new Vec3(x+r,y+h,z+r),new Vec3(x-r,y+h,z+r)};
        for (int i = 0; i < 4; i++) {
            g.addLine(bottom[i], bottom[(i+1)%4], color, 3);
            g.addLine(top[i], top[(i+1)%4], color, 3);
            g.addLine(bottom[i], top[i], color, 3);
        }
    }
}
