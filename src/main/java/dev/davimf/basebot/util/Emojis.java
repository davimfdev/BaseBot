package dev.davimf.basebot.util;

import net.dv8tion.jda.api.entities.emoji.ApplicationEmoji;
import net.dv8tion.jda.api.entities.emoji.Emoji;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Custom-emoji set, served from the bot's <b>application emojis</b> (not a guild). The names
 * below are the application-emoji names and also the {@code resources/emojis/<name>.png} file
 * names; {@link EmojiRegistry} uploads/syncs them on startup and fills this registry.
 *
 * <p><b>House rule:</b> every bot-authored message uses these custom emojis — never a raw
 * Unicode emoji. Reference by name: {@link #of(String, String)} for text (the Unicode argument
 * is only a fallback shown until the PNG is uploaded) and {@link #button(String)} for
 * {@code Button.withEmoji(...)}. Resolving by name means the per-app emoji id is never hard-coded.
 */
public final class Emojis {

    private Emojis() {}

    private static final Map<String, ApplicationEmoji> REGISTRY = new ConcurrentHashMap<>();

    // Names = application-emoji names = resources/emojis/<name>.png. Keep in sync with manifest.txt.

    // core / UI
    public static final String GEAR = "gear";
    public static final String WRENCH = "wrench";
    public static final String SEARCH = "search";
    public static final String LIST = "list";
    public static final String EDIT = "edit";
    public static final String NOTE = "note";
    public static final String INFO = "info";
    public static final String WARN = "warn";
    public static final String PLUS = "plus";
    public static final String MINUS = "minus";
    // Legacy concepts without a dedicated PNG → mapped to the closest custom emoji.
    public static final String COMMAND = "message";

    // items / state
    public static final String TRASH = "trash";
    public static final String RECYCLE = "recycle";
    public static final String LINK = "link";
    public static final String PIN = "pin";
    public static final String ATTACHMENT = "attachment";
    public static final String LOCATION = "location";
    public static final String BELL = "bell";
    public static final String BELL_OFF = "bell_off";
    public static final String BOLT = "bolt";
    public static final String PALETTE = "palette";
    public static final String STAR = "star";
    public static final String HEART = "heart";
    public static final String CHECK_YES = "checkyes";
    public static final String CHECK_NO = "checkno";
    public static final String DOT = "online";
    public static final String SEND = "message";
    public static final String COMMENT = "message";

    // time / navigation
    public static final String HOURGLASS = "hourglass";
    public static final String TIMER = "timer";
    public static final String CLOCK = "clock";
    public static final String CALENDAR = "calendar";
    public static final String ARROW = "arrow";
    public static final String ARROW_LEFT = "arrow_left";
    public static final String ARROW_UP = "arrow_up";
    public static final String ARROW_DOWN = "arrow_down";
    public static final String ARROW_MOVE = "arrow_move";
    public static final String CHEVRON_DOWN = "chevron_down";
    public static final String ONLINE = "online";
    public static final String OFFLINE = "offline";

    // moderation
    public static final String SHIELD = "shield";
    public static final String BAN = "ban";
    public static final String KICK = "kick";
    public static final String SKULL = "skull";
    public static final String SCALES = "scales";
    public static final String SWORDS = "swords";
    public static final String BROOM = "broom";
    public static final String NUKE = "nuke";
    public static final String SLOWMODE = "slowmode";
    public static final String LOCK = "lock";
    public static final String UNLOCK = "unlock";

    // permissions / identity
    public static final String PERMS = "perms";
    public static final String KEY = "key";
    public static final String ID = "id";
    public static final String IDCARD = "idcard";
    public static final String MEMBER = "member";
    public static final String MEMBERS = "members";
    public static final String RECRUIT = "recruit";
    public static final String HANDSHAKE = "handshake";
    public static final String ROLES = "roles";
    public static final String NICKNAME = "nickname";
    public static final String RANK = "rank";
    public static final String TROPHY = "trophy";

    // voice / media
    public static final String VOICE = "volume";
    public static final String VOLUME = "volume";
    public static final String VOLUME_LOW = "volume_low";
    public static final String MUTE = "mute";
    public static final String MIC = "mic";
    public static final String MIC_MUTED = "mic_muted";
    public static final String HEADPHONES = "headphones";
    public static final String VIDEO = "video";
    public static final String CAMERA = "camera";
    public static final String STREAM = "stream";
    public static final String MEGAPHONE = "megaphone";
    public static final String DM = "dm";
    public static final String MESSAGE = "message";

    // server / channels
    public static final String SERVER = "server";
    public static final String SERVER_ICON = "server_icon";
    public static final String BANNER = "banner";
    public static final String THREAD = "thread";
    public static final String FOLDER = "folder";
    public static final String FOLDER_OPEN = "folder_open";
    public static final String HASH = "hash";
    public static final String CHANNEL = "channel";
    public static final String EMOJI_ADD = "emoji_add";
    public static final String EMOJI_REMOVE = "emoji_remove";
    public static final String JOIN = "join";
    public static final String LEAVE = "leave";
    public static final String LOADING = "hourglass";

    // economy / finance
    public static final String MONEY = "money";
    public static final String CASH = "cash";
    public static final String EXPENSE = "expense";
    public static final String BANK = "bank";
    public static final String BUDGET = "budget";
    public static final String STATS = "stats";
    public static final String GROWTH = "growth";
    public static final String RECEIPT = "receipt";
    public static final String ABACUS = "abacus";
    public static final String SALES = "sales";
    public static final String PRODUCT = "product";
    public static final String GIFT = "gift";

    // facs / misc
    public static final String WEAPON = "swords";
    public static final String FARM = "farm";
    public static final String SPROUT = "sprout";
    public static final String BOOST = "boost";
    public static final String TARGET = "target";
    public static final String GAME = "game";
    public static final String TICKET = "ticket";
    public static final String FINISH = "finish";
    public static final String CALL = "call";
    public static final String CALL_END = "call_end";
    public static final String GEM = "gem";
    public static final String COMPASS = "compass";
    public static final String HIGHLIGHT = "highlight";

    /** Replaces the whole registry (initial load of existing application emojis). */
    static void load(Map<String, ApplicationEmoji> emojis) {
        REGISTRY.clear();
        REGISTRY.putAll(emojis);
    }

    /** Adds/updates a single emoji (after it is created asynchronously). */
    static void put(String name, ApplicationEmoji emoji) {
        REGISTRY.put(name, emoji);
    }

    /** The resolved application emoji for a name, or {@code null} if not uploaded yet. */
    public static ApplicationEmoji get(String name) {
        return REGISTRY.get(name);
    }

    /** Formatted mention ({@code <:name:id>}) for use in text, or {@code fallback}
     *  (e.g. a Unicode emoji) while the PNG hasn't been uploaded. */
    public static String of(String name, String fallback) {
        ApplicationEmoji e = REGISTRY.get(name);
        return e != null ? e.getFormatted() : fallback;
    }

    /** Emoji for {@code Button.withEmoji(...)}; {@code null} until uploaded (button shows
     *  its label only — {@code withEmoji(null)} is valid). */
    public static Emoji button(String name) {
        return REGISTRY.get(name);
    }
}
