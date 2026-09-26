package dev.terrorify.tools.fabricdbug.spacetime;

/** Runtime configuration for the direct SpacetimeDB debug feed. */
public final class SpacetimeConfig {
    private SpacetimeConfig() {}

    public static final boolean ENABLED = Boolean.parseBoolean(
        System.getProperty("fabricdbug.spacetime.enabled", "true"));
    public static final String URI = System.getProperty(
        "fabricdbug.spacetime.uri", "http://127.0.0.1:13002");
    public static final String HOT_DB = System.getProperty(
        "fabricdbug.spacetime.hotDb", "stomzydevhot");
    public static final int POSITION_POLL_TICKS = Integer.parseInt(
        System.getProperty("fabricdbug.spacetime.positionPollTicks", "5"));
    public static final int COMMAND_POLL_TICKS = Integer.parseInt(
        System.getProperty("fabricdbug.spacetime.commandPollTicks", "5"));
}
