package com.banasiak.coinflip.settings

import android.content.SharedPreferences
import android.content.SharedPreferences.Editor
import com.banasiak.coinflip.common.Coin
import com.banasiak.coinflip.common.CoinType
import timber.log.Timber

/**
 * One preference: its key, its default, and how it reads and writes itself.
 *
 * These were an enum with an `Any?` default back when this wrapped the preference-XML inflater and
 * every value arrived as a String or a Boolean. Kotlin enums cannot take a type parameter, so that
 * shape forced a cast at each of the forty-odd use sites, left [SettingsManager.update] checking
 * types at runtime, and gave the Long-valued settings no way through it at all. Carrying the type
 * here retires all three.
 */
sealed class Setting<T>(val key: String, val default: T) {
  abstract fun read(prefs: SharedPreferences): T

  /**
   * Stages this setting into [editor] without flushing it. The caller owns the transaction, which
   * is what lets `persistStats` write six values in one `apply` and `validateSchema` clear and
   * restamp the schema in a single synchronous `commit`.
   */
  abstract fun write(editor: Editor, value: T)

  class BooleanSetting(key: String, default: Boolean) : Setting<Boolean>(key, default) {
    override fun read(prefs: SharedPreferences): Boolean = prefs.getBoolean(key, default)

    override fun write(editor: Editor, value: Boolean) {
      editor.putBoolean(key, value)
    }
  }

  class StringSetting(key: String, default: String) : Setting<String>(key, default) {
    override fun read(prefs: SharedPreferences): String = prefs.getString(key, default) ?: default

    override fun write(editor: Editor, value: String) {
      editor.putString(key, value)
    }
  }

  /**
   * Kept apart from [StringSetting] so the settings that cannot be null never need unwrapping.
   *
   * Writing null *removes* the key rather than storing one -- the platform's own behavior, and
   * what lets a cleared custom label fall back to the localized default rather than to a blank.
   */
  class OptionalStringSetting(key: String) : Setting<String?>(key, null) {
    override fun read(prefs: SharedPreferences): String? = prefs.getString(key, null)

    override fun write(editor: Editor, value: String?) {
      editor.putString(key, value)
    }
  }

  class LongSetting(key: String, default: Long) : Setting<Long>(key, default) {
    override fun read(prefs: SharedPreferences): Long = prefs.getLong(key, default)

    override fun write(editor: Editor, value: Long) {
      editor.putLong(key, value)
    }
  }

  class IntSetting(key: String, default: Int) : Setting<Int>(key, default) {
    override fun read(prefs: SharedPreferences): Int = prefs.getInt(key, default)

    override fun write(editor: Editor, value: Int) {
      editor.putInt(key, value)
    }
  }

  /**
   * A number the old preference inflater could only write as a string. Reading it back as a Long
   * would throw on every install that already has one, so the encoding stays and lives here rather
   * than at the call site -- including the fallback, since a non-numeric value must not crash.
   */
  class LongAsStringSetting(key: String, default: Long) : Setting<Long>(key, default) {
    override fun read(prefs: SharedPreferences): Long = prefs.getString(key, null)?.toLongOrNull() ?: default

    override fun write(editor: Editor, value: Long) {
      editor.putString(key, value.toString())
    }
  }

  /**
   * The platform hands back the very set it is holding and forbids mutating it, so both directions
   * copy: what is read cannot be written through, and what is written cannot change underneath.
   */
  class StringSetSetting(key: String, default: Set<String> = emptySet()) : Setting<Set<String>>(key, default) {
    /**
     * [Setting.FAVORITES] held a delimited string before it held a set, and the schema version did
     * not move with it, so a store written by an older build still has one under this key.
     * `getStringSet` casts without checking and throws on it, which killed the settings screen as
     * it opened. The stale value reads as [default] instead, and the first write puts a real set
     * in its place. Favorites never shipped, so there is nothing here worth migrating -- only a
     * crash worth not having.
     */
    override fun read(prefs: SharedPreferences): Set<String> =
      try {
        prefs.getStringSet(key, null)?.toSet() ?: default
      } catch (e: ClassCastException) {
        Timber.w(e, "Discarding a $key written before it was a string set")
        default
      }

    override fun write(editor: Editor, value: Set<String>) {
      editor.putStringSet(key, value.toMutableSet())
    }
  }

  /**
   * An enum held under a string of its own rather than an ordinal, so reordering the constants
   * cannot silently repoint what is already on disk. [encode] names one; reading scans [entries]
   * for it, and anything unrecognized reads back as [default] -- the one place the fallback lives.
   */
  class EnumSetting<E : Enum<E>>(
    key: String,
    default: E,
    private val entries: List<E>,
    private val encode: (E) -> String
  ) : Setting<E>(key, default) {
    override fun read(prefs: SharedPreferences): E =
      prefs.getString(key, null)?.let { stored -> entries.firstOrNull { encode(it) == stored } } ?: default

    override fun write(editor: Editor, value: E) {
      editor.putString(key, encode(value))
    }
  }

  companion object {
    val COIN = StringSetting("coin", CoinType.GEORGE_WASHINGTON.prefix)
    val CUSTOM_HEADS_TEXT = OptionalStringSetting("customHeadsText")
    val CUSTOM_TAILS_TEXT = OptionalStringSetting("customTailsText")
    val ANIMATE = BooleanSetting("animate", true)
    val SHAKE = BooleanSetting("shake", true)
    val SOUND = BooleanSetting("sound", true)
    val STATS = BooleanSetting("stats", true)
    val TEXT = BooleanSetting("text", true)
    val VIBRATE = BooleanSetting("vibrate", true)

    val DIAGNOSTICS = LongAsStringSetting("diagnostics", 100_000L)
    val DYNAMIC = BooleanSetting("dynamic", false)
    val QUICK_RESET = BooleanSetting("quickReset", false)
    val FORCE = EnumSetting("force", ShakeForce.MEDIUM, ShakeForce.entries) { it.stored }
    val SECURE_RANDOM = BooleanSetting("secureRandom", false)
    val FAVORITES = StringSetSetting("favoriteCoins")

    val CUSTOM_COIN_RIM = BooleanSetting("customCoinRim", true)

    // which glyph each emoji face was made from, so the picker reopens on it. Only a convenience:
    // the PNG is the artwork, and the disc behind it follows the prefix, so losing these to a
    // schema wipe costs the preselection and nothing else.
    val EMOJI_HEADS = OptionalStringSetting("emojiHeads")
    val EMOJI_TAILS = OptionalStringSetting("emojiTails")
    val STREAK = BooleanSetting("streak", false)
    val HEADS = LongSetting("headsCount", 0L)
    val TAILS = LongSetting("tailsCount", 0L)
    val HEADS_RECORD = LongSetting("headsRecord", 0L)
    val TAILS_RECORD = LongSetting("tailsRecord", 0L)
    val STREAK_VALUE = EnumSetting("streakValue", Coin.Value.UNKNOWN, Coin.Value.entries) { it.name }
    val STREAK_COUNT = LongSetting("streakCount", 0L)

    // adding a key needs no bump -- validateSchema() only wipes on a mismatch, and an absent key reads its default
    val SCHEMA = IntSetting("schemaVersion", 7) // the old version of the app was '6'
  }
}

/**
 * Reads a setting: `prefs[Setting.ANIMATE]`.
 *
 * Naming a function `get` and marking it `operator` is what makes square-bracket syntax compile --
 * Kotlin's indexed-access convention, the same one behind `map[key]`. It is an *extension* because
 * `SharedPreferences` is a platform interface we cannot add members to, and generic in [T] so the
 * setting's own type comes back out: `prefs[Setting.HEADS]` is a `Long`, with no cast at the call site.
 */
internal operator fun <T> SharedPreferences.get(setting: Setting<T>): T = setting.read(this)

/** The `set` half of the same convention, for use inside `edit { }`: `this[Setting.HEADS] = 7L`. */
internal operator fun <T> Editor.set(setting: Setting<T>, value: T) {
  setting.write(this, value)
}