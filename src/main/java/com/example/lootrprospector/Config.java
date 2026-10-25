package com.example.lootrprospector;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(modid = LootrProspector.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.IntValue SCAN_RADIUS = BUILDER
            .comment("Scan radius in blocks.")
            .defineInRange("scanRadius", 64, 8, 128);

    private static final ForgeConfigSpec.IntValue COOLDOWN_TICKS = BUILDER
            .comment("Cooldown between uses, in ticks.")
            .defineInRange("cooldownTicks", 100, 20, 1200);

    private static final ForgeConfigSpec.IntValue HIGHLIGHT_TICKS = BUILDER
            .comment("How long the client keeps the found positions highlighted, in ticks.")
            .defineInRange("highlightTicks", 300, 20, 1200);

    private static final ForgeConfigSpec.IntValue MAX_HIGHLIGHTS = BUILDER
            .comment("Maximum number of positions sent to the client and highlighted.")
            .defineInRange("maxHighlights", 128, 16, 512);

    private static final ForgeConfigSpec.IntValue CHAT_LIST_LIMIT = BUILDER
            .comment("How many nearest coordinates are listed in chat. 0 disables the list.")
            .defineInRange("chatListLimit", 5, 0, 20);

    private static final ForgeConfigSpec.BooleanValue SCAN_MINECARTS = BUILDER
            .comment("Also scan Lootr chest minecarts.")
            .define("scanMinecarts", true);

    private static final ForgeConfigSpec.BooleanValue ENABLE_HIGHLIGHT = BUILDER
            .comment("Send positions to the client for through-wall highlighting.")
            .define("enableHighlight", true);

    private static final ForgeConfigSpec.BooleanValue PLAY_SOUND = BUILDER
            .comment("Play a sound when a scan finishes.")
            .define("playSound", true);

    private static final ForgeConfigSpec.BooleanValue SHOW_UNLOADED_HINT = BUILDER
            .comment("Warn when some chunks in range were not loaded.")
            .define("showUnloadedHint", true);

    private static final ForgeConfigSpec.BooleanValue DEBUG_LOG_SCAN = BUILDER
            .comment("Log scan duration and result counts at INFO level.")
            .define("debugLogScan", false);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    public static int scanRadius = 64;
    public static int cooldownTicks = 100;
    public static int highlightTicks = 300;
    public static int maxHighlights = 128;
    public static int chatListLimit = 5;
    public static boolean scanMinecarts = true;
    public static boolean enableHighlight = true;
    public static boolean playSound = true;
    public static boolean showUnloadedHint = true;
    public static boolean debugLogScan = false;

    private Config() {
    }

    @SubscribeEvent
    static void onConfigChanged(final ModConfigEvent event) {
        scanRadius = SCAN_RADIUS.get();
        cooldownTicks = COOLDOWN_TICKS.get();
        highlightTicks = HIGHLIGHT_TICKS.get();
        maxHighlights = MAX_HIGHLIGHTS.get();
        chatListLimit = CHAT_LIST_LIMIT.get();
        scanMinecarts = SCAN_MINECARTS.get();
        enableHighlight = ENABLE_HIGHLIGHT.get();
        playSound = PLAY_SOUND.get();
        showUnloadedHint = SHOW_UNLOADED_HINT.get();
        debugLogScan = DEBUG_LOG_SCAN.get();
    }
}
