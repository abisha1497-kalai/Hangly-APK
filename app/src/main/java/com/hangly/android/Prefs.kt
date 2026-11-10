package com.hangly.android

import android.content.Context
import android.content.SharedPreferences

/** All user settings, stored in one SharedPreferences file. */
class Prefs(context: Context) {
    companion object {
        const val MAX_CHARMS = 3
    }

    val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("hangly", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = sp.getBoolean("enabled", false)
        set(v) { sp.edit().putBoolean("enabled", v).apply() }

    /** Max width of the pendant in dp. */
    var sizeDp: Int
        get() = sp.getInt("sizeDp", 90)
        set(v) { sp.edit().putInt("sizeDp", v).apply() }

    /** Chain length in dp. */
    var chainDp: Int
        get() = sp.getInt("chainDp", 200)
        set(v) { sp.edit().putInt("chainDp", v).apply() }

    /** Horizontal position of the hook, 0 (left) .. 100 (right). */
    var pivot: Int
        get() = sp.getInt("pivot", 82)
        set(v) { sp.edit().putInt("pivot", v).apply() }

    /** How strongly tilts and shakes move the pendant, 0..100. */
    var motion: Int
        get() = sp.getInt("motion", 50)
        set(v) { sp.edit().putInt("motion", v).apply() }

    var tilt: Boolean
        get() = sp.getBoolean("tilt", true)
        set(v) { sp.edit().putBoolean("tilt", v).apply() }

    /** Chain look; see PendulumView.STYLE_NAMES (0 gold .. 6 mala). */
    var style: Int
        get() = sp.getInt("style", 0)
        set(v) { sp.edit().putInt("style", v).apply() }

    var bootStart: Boolean
        get() = sp.getBoolean("bootStart", false)
        set(v) { sp.edit().putBoolean("bootStart", v).apply() }

    var removeBg: Boolean
        get() = sp.getBoolean("removeBg", true)
        set(v) { sp.edit().putBoolean("removeBg", v).apply() }

    /** How many charms hang from the top of the screen (the "charm bracelet"), 1..3. */
    var charmCount: Int
        get() = sp.getInt("charmCount", 1).coerceIn(1, MAX_CHARMS)
        set(v) { sp.edit().putInt("charmCount", v.coerceIn(1, MAX_CHARMS)).apply() }

    /** What a tap on a charm does: 0 sparkle and swing, 1 next charm, 2 nothing. */
    var tapAction: Int
        get() = sp.getInt("tapAction", 0)
        set(v) { sp.edit().putInt("tapAction", v).apply() }

    /** What a long-press does: 0 open settings, 1 next charm, 2 hide Hangly, 3 nothing. */
    var longAction: Int
        get() = sp.getInt("longAction", 0)
        set(v) { sp.edit().putInt("longAction", v).apply() }

    var haptics: Boolean
        get() = sp.getBoolean("haptics", true)
        set(v) { sp.edit().putBoolean("haptics", v).apply() }

    /**
     * Which pendant hangs in [slot] (0 until MAX_CHARMS): a built-in id from DefaultPendant.IDS,
     * "custom" (the picked photo for that slot) or "emoji:<text>".
     * Slot 0 keeps working for people who already picked a photo before charms existed.
     */
    fun pendantId(slot: Int, hasCustomFile: Boolean): String {
        val saved = sp.getString("pendant_$slot", null)
        if (saved != null) return saved
        if (slot == 0) return if (hasCustomFile) "custom" else "lotus"
        return DefaultPendant.IDS[(slot * 2) % DefaultPendant.IDS.size] // different look per slot
    }

    fun setPendantId(slot: Int, id: String) {
        sp.edit().putString("pendant_$slot", id).apply()
    }

    /** Bumped whenever the pendant image changes, so the overlay reloads it. */
    fun bumpImage() {
        sp.edit().putInt("imgVer", sp.getInt("imgVer", 0) + 1).apply()
    }
}
