package com.limelight.shell.data

import java.util.Locale

/**
 * Offline artwork for host utilities; logos intentionally remain the rendered application name. A utility is never looked up
 * on SteamGridDB (Vibepollo's "Virtual Display" was given a Virtual Boy game's cover, owner 2026-11-24).
 *
 * The entries of Sunshine, Apollo, Vibepollo, Vibeshine, Sunshine Foundation and ArtLight (research 2026-11-24, sources in
 * docs/HOST_UTILITIES.md): matched by the host's fixed UUID first, then by the exact name, trimmed (Vibepollo puts 1 to 5 leading
 * spaces before its session controls so they sort first) and lower-cased. Never by "contains": "Desktop Dungeons" is a game.
 */
internal object BundledArtwork {
    enum class Kind(val slug: String) {
        DESKTOP("desktop"), REMOTE_INPUT("remote-input"), REMOTE_MONITOR("remote-monitor"), STEAM_BIG_PICTURE("steam-big-picture"),
        VIRTUAL_DISPLAY("virtual-display"), TERMINATE("terminate"), RESUME("resume"), DISCONNECT("disconnect"),
        PLAYNITE("playnite"), XBOX("xbox");

        fun assetPath(artKind: ArtKind): String? =
            if (artKind == ArtKind.LOGO) null else "artwork/$slug/${artKind.file}.jpg"
    }

    private val BY_UUID = mapOf(
        "8cb5c136-da67-4f99-b4a1-f9cd35005cf4" to Kind.REMOTE_INPUT, // Apollo, Vibepollo, ArtLight
        "9a1c5a25-58fe-40e0-b9aa-7d3f00000006" to Kind.REMOTE_INPUT, // Vibepollo/Vibeshine session control
        "9a1c5a25-58fe-40e0-b9aa-7d3f00000005" to Kind.REMOTE_MONITOR,
        "8902cb19-674a-403d-a587-41b092e900ba" to Kind.VIRTUAL_DISPLAY, // Apollo, Vibepollo, ArtLight
        "eaac6159-089a-46a9-9e24-6436885f6610" to Kind.DESKTOP, // Apollo's "Desktop (fallback)"
        "e16cbe1b-295d-4632-9a76-ec4180c857d3" to Kind.TERMINATE, // Apollo, Vibepollo, ArtLight
        "9a1c5a25-58fe-40e0-b9aa-7d3f00000004" to Kind.TERMINATE,
        "9a1c5a25-58fe-40e0-b9aa-7d3f00000001" to Kind.RESUME,
        "9a1c5a25-58fe-40e0-b9aa-7d3f00000002" to Kind.DISCONNECT, // Disconnect Monitor
        "9a1c5a25-58fe-40e0-b9aa-7d3f00000003" to Kind.DISCONNECT, // Disconnect Input
        // …07 is a duplicate tile of the running game: a real game, looked up as such
    )

    private val BY_NAME = mapOf(
        "desktop" to Kind.DESKTOP, "desktop (fallback)" to Kind.DESKTOP, "low res desktop" to Kind.DESKTOP,
        "big screen" to Kind.DESKTOP, // Sunshine Foundation's launcher
        "virtual display" to Kind.VIRTUAL_DISPLAY,
        "remote input" to Kind.REMOTE_INPUT, "remote monitor" to Kind.REMOTE_MONITOR,
        "terminate" to Kind.TERMINATE, "resume" to Kind.RESUME,
        "disconnect monitor" to Kind.DISCONNECT, "disconnect input" to Kind.DISCONNECT,
        "steam big picture" to Kind.STEAM_BIG_PICTURE, "steam bigpicture" to Kind.STEAM_BIG_PICTURE, "steam" to Kind.STEAM_BIG_PICTURE,
        "playnite (fullscreen)" to Kind.PLAYNITE, "playnite" to Kind.PLAYNITE,
        "xbox game" to Kind.XBOX,
    )

    fun kind(app: App): Kind? {
        app.nvApp.getAppUUID()?.lowercase(Locale.ROOT)?.let { uuid -> BY_UUID[uuid]?.let { return it } }
        return BY_NAME[app.name.trim().lowercase(Locale.ROOT)]
    }

    fun assetPath(app: App, artKind: ArtKind): String? = kind(app)?.assetPath(artKind)
}
