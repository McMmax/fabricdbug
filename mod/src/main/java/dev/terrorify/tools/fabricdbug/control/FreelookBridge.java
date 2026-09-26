package dev.terrorify.tools.fabricdbug.control;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/** Optional reflection bridge to Celibistrial's Freelook mod, avoiding a hard jar dependency. */
public final class FreelookBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger("fabricdbug");
    private static final String ENTRYPOINT = "freelook.freelook.FreeLookMod";

    private FreelookBridge() {}

    public static boolean start(String perspectiveName) {
        return invoke("startFreeLooking", perspectiveName);
    }

    public static boolean stop() {
        return invoke("stopFreeLooking", null);
    }

    private static boolean invoke(String methodName, String perspectiveName) {
        try {
            Object instance = FabricLoader.getInstance().getEntrypoints("client", ClientModInitializer.class)
                .stream().filter(entry -> entry.getClass().getName().equals(ENTRYPOINT)).findFirst().orElse(null);
            if (instance == null) {
                LOGGER.warn("FabricDBug freelook command ignored: Freelook mod is not installed");
                return false;
            }
            Minecraft client = Minecraft.getInstance();
            Method method;
            Object[] arguments;
            if ("startFreeLooking".equals(methodName)) {
                method = instance.getClass().getDeclaredMethod(methodName, Minecraft.class, CameraType.class);
                CameraType perspective = CameraType.THIRD_PERSON_BACK;
                if (perspectiveName != null && !perspectiveName.isBlank()) {
                    perspective = CameraType.valueOf(perspectiveName.toUpperCase());
                }
                arguments = new Object[] {client, perspective};
            } else {
                method = instance.getClass().getDeclaredMethod(methodName, Minecraft.class);
                arguments = new Object[] {client};
            }
            method.setAccessible(true);
            method.invoke(instance, arguments);
            return true;
        } catch (ReflectiveOperationException | IllegalArgumentException error) {
            LOGGER.warn("FabricDBug could not control Freelook", error);
            return false;
        }
    }
}
