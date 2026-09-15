package com.serkantkn.zunelauncher.data.model

/** One app as the sources page shows it: the app itself plus everything the row needs. */
data class SocialSource(
    val app: AppInfo,
    val kind: SocialKind?,
    val isOn: Boolean,
    /** The app has been sending things that look social but has not been let in. */
    val isSuggested: Boolean,
    /** How many of its messages the hub is holding. */
    val waiting: Int
)

/** What kind of social app a notification came from. */
enum class SocialKind {
    /** Somebody wrote to you: WhatsApp, Telegram, Messenger, Signal. */
    MESSAGING,

    /** Something happened on a feed: Instagram, X, Reddit, LinkedIn. */
    NETWORK
}

/**
 * Which apps belong in the Social hub.
 *
 * The hub is for what people say to each other, not for everything the phone has to announce. A
 * battery warning, a delivery, a system update and a game asking you to come back are all
 * notifications, and none of them belong in a place called "social".
 *
 * Android cannot answer "is this a social app?" — a notification's category is a hint the app fills
 * in and most of them get it wrong — so the answer starts from a list of the apps people actually
 * mean. The list is only a starting point: whatever it gets wrong, the hub's own sources page lets
 * the user add or remove by hand, and that choice always wins.
 */
object SocialApps {

    /** The apps the hub switches itself on for, the first time it is opened. */
    val CATALOGUE: Map<String, SocialKind> = buildMap {
        // ── Messaging ──
        put("com.whatsapp", SocialKind.MESSAGING)
        put("com.whatsapp.w4b", SocialKind.MESSAGING)
        put("org.telegram.messenger", SocialKind.MESSAGING)
        put("org.telegram.messenger.web", SocialKind.MESSAGING)
        put("org.thunderdog.challegram", SocialKind.MESSAGING)
        put("com.facebook.orca", SocialKind.MESSAGING)
        put("com.facebook.mlite", SocialKind.MESSAGING)
        put("org.thoughtcrime.securesms", SocialKind.MESSAGING)
        put("com.discord", SocialKind.MESSAGING)
        put("com.viber.voip", SocialKind.MESSAGING)
        put("jp.naver.line.android", SocialKind.MESSAGING)
        put("com.skype.raider", SocialKind.MESSAGING)
        put("com.tencent.mm", SocialKind.MESSAGING)
        put("com.kakao.talk", SocialKind.MESSAGING)
        put("com.imo.android.imoim", SocialKind.MESSAGING)
        put("com.google.android.apps.dynamite", SocialKind.MESSAGING)
        put("com.Slack", SocialKind.MESSAGING)
        put("com.microsoft.teams", SocialKind.MESSAGING)
        put("im.vector.app", SocialKind.MESSAGING)
        put("com.bbm", SocialKind.MESSAGING)

        // ── Networks ──
        put("com.instagram.android", SocialKind.NETWORK)
        put("com.instagram.lite", SocialKind.NETWORK)
        put("com.instagram.barcelona", SocialKind.NETWORK)
        put("com.facebook.katana", SocialKind.NETWORK)
        put("com.facebook.lite", SocialKind.NETWORK)
        put("com.twitter.android", SocialKind.NETWORK)
        put("com.snapchat.android", SocialKind.NETWORK)
        put("com.zhiliaoapp.musically", SocialKind.NETWORK)
        put("com.ss.android.ugc.trill", SocialKind.NETWORK)
        put("com.reddit.frontpage", SocialKind.NETWORK)
        put("com.linkedin.android", SocialKind.NETWORK)
        put("com.pinterest", SocialKind.NETWORK)
        put("com.tumblr", SocialKind.NETWORK)
        put("tv.twitch.android.app", SocialKind.NETWORK)
        put("xyz.blueskyweb.app", SocialKind.NETWORK)
        put("org.joinmastodon.android", SocialKind.NETWORK)
        put("com.vkontakte.android", SocialKind.NETWORK)
        put("com.sina.weibo", SocialKind.NETWORK)
        put("com.clubhouse.app", SocialKind.NETWORK)
    }

    fun isKnown(packageName: String): Boolean = packageName in CATALOGUE

    fun kindOf(packageName: String): SocialKind? = CATALOGUE[packageName]

    /**
     * What the hub listens to before anybody has said otherwise: every app from the catalogue that
     * is actually on the phone. An empty answer is honest — it means the hub has nothing to show
     * until the user names a source themselves.
     */
    fun defaultSources(installedPackages: Collection<String>): Set<String> =
        installedPackages.filterTo(LinkedHashSet()) { it in CATALOGUE }
}

/**
 * Whether a notification is one person saying something to another, rather than an app talking
 * about itself.
 *
 * Even inside a social app most notifications are not messages: "backing up your chats", "you have
 * 12 new updates", a media player, an upload in progress. Android marks those in ways that can be
 * read — an ongoing flag, a category, a progress bar — and this is where they are turned away.
 *
 * [category] is the notification's own category, [isOngoing] its ongoing flag, [isGroupSummary]
 * whether it is the folded-up header of several others, and [hasProgress] whether it carries a
 * progress bar.
 */
fun isSocialWorthy(
    category: String?,
    isOngoing: Boolean,
    isGroupSummary: Boolean,
    hasProgress: Boolean,
    hasText: Boolean
): Boolean {
    if (!hasText) return false
    if (isOngoing || isGroupSummary || hasProgress) return false
    return category !in NOISE_CATEGORIES
}

/**
 * Categories that are never somebody talking. Left deliberately short: an app that names no
 * category at all is given the benefit of the doubt, because most of them do not name one and
 * refusing those would empty the hub.
 */
private val NOISE_CATEGORIES = setOf(
    "progress",
    "service",
    "transport",
    "sys",
    "err",
    "promo",
    "recommendation",
    "status",
    "navigation",
    "alarm",
    "call"
)

/**
 * Whether an app the user has not chosen looks like it might belong in the hub.
 *
 * Used only to offer a suggestion on the sources page — never to let anything in by itself. An app
 * that says its notification is a message or a social event, and is not already known, is worth
 * putting in front of the user once.
 */
fun looksSocial(category: String?): Boolean = category == "msg" || category == "social"
