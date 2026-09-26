package dev.terrorify.tools.fabricdbug.control;

import net.minecraft.client.KeyMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;

/** Optional reflection bridge to Zoomify's private zoom key, avoiding a hard jar dependency. */
public final class ZoomifyBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger("fabricdbug");
    private static final String ZOOMIFY = "dev.isxander.zoomify.Zoomify";

    private ZoomifyBridge() {}

    public static boolean setZooming(boolean enabled) {
        try {
            Class<?> type = Class.forName(ZOOMIFY);
            Field keyField = type.getDeclaredField("zoomKey");
            keyField.setAccessible(true);
            KeyMapping key = (KeyMapping) keyField.get(null);
            key.setDown(enabled);
            Field zooming = type.getDeclaredField("zooming");
            zooming.setAccessible(true);
            zooming.setBoolean(null, enabled);
            Field secondaryZooming = type.getDeclaredField("secondaryZooming");
            secondaryZooming.setAccessible(true);
            secondaryZooming.setBoolean(null, false);
            return true;
        } catch (ReflectiveOperationException | ClassCastException error) {
            LOGGER.warn("FabricDBug zoom command ignored: Zoomify is not installed or changed its API");
            return false;
        }
    }
}
