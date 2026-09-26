package dev.terrorify.tools.fabricdbug.control;

import com.google.gson.JsonArray;
import dev.terrorify.tools.fabricdbug.spacetime.SpacetimeClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.nio.file.Path;
import java.util.UUID;

/** Uses Minecraft's native render-target capture, never the desktop framebuffer. */
public final class ScreenshotCapture {
    private ScreenshotCapture() {}

    public static Path capture(SpacetimeClient client, String targetUuid) {
        Minecraft minecraft = Minecraft.getInstance();
        String filename = "fabricdbug-" + UUID.randomUUID() + ".png";
        Path path = minecraft.gameDirectory.toPath().resolve("screenshots").resolve(filename);
        Screenshot.grab(minecraft.gameDirectory, filename, minecraft.gameRenderer.mainRenderTarget(), 1,
            ignored -> report(client, targetUuid, path, "complete"));
        report(client, targetUuid, path, "scheduled");
        return path;
    }

    private static void report(SpacetimeClient client, String targetUuid, Path path, String status) {
        JsonArray args = new JsonArray();
        args.add(targetUuid == null ? "" : targetUuid);
        args.add("screenshot");
        String escaped = path.toString().replace("\\", "\\\\").replace("\"", "\\\"");
        args.add("{\"status\":\"" + status + "\",\"path\":\"" + escaped + "\"}");
        client.callReducerAsync("report_fabricdbug_event", args);
    }
}
