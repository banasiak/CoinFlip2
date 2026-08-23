package com.banasiak.coinflip

import android.content.SharedPreferences

/**
 * An in-memory stand-in for [SharedPreferences]. Mocking the interface only proves which editor
 * calls were made; this reproduces the platform semantics the production code actually leans on,
 * so a test can assert what the store ends up holding:
 *
 *  - `putString(key, null)` removes the key rather than storing a null
 *  - `clear()` is applied before any puts queued on the same editor, whatever order they were called in
 *  - a getter whose key holds a different type throws [ClassCastException], as the real one does
 */
class FakeSharedPreferences(initial: Map<String, Any> = emptyMap()) : SharedPreferences {
  val values: MutableMap<String, Any> = LinkedHashMap(initial)

  /** How many editors were flushed with `commit()` rather than `apply()`. */
  var commitCount = 0
    private set

  var applyCount = 0
    private set

  private val listeners = mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()

  override fun getAll(): MutableMap<String, *> = values

  override fun getString(key: String, defValue: String?): String? = read(key) as String? ?: defValue

  override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? {
    @Suppress("UNCHECKED_CAST")
    return read(key) as MutableSet<String>? ?: defValues
  }

  override fun getInt(key: String, defValue: Int): Int = read(key) as Int? ?: defValue

  override fun getLong(key: String, defValue: Long): Long = read(key) as Long? ?: defValue

  override fun getFloat(key: String, defValue: Float): Float = read(key) as Float? ?: defValue

  override fun getBoolean(key: String, defValue: Boolean): Boolean = read(key) as Boolean? ?: defValue

  override fun contains(key: String): Boolean = values.containsKey(key)

  override fun edit(): SharedPreferences.Editor = FakeEditor()

  override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
    listeners += listener
  }

  override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
    listeners -= listener
  }

  private fun read(key: String): Any? = values[key]

  private inner class FakeEditor : SharedPreferences.Editor {
    // a null entry marks a pending removal, which is also how the platform models putString(key, null)
    private val pending = LinkedHashMap<String, Any?>()
    private var clearRequested = false

    override fun putString(key: String, value: String?) = stage(key, value)

    override fun putStringSet(key: String, values: MutableSet<String>?) = stage(key, values)

    override fun putInt(key: String, value: Int) = stage(key, value)

    override fun putLong(key: String, value: Long) = stage(key, value)

    override fun putFloat(key: String, value: Float) = stage(key, value)

    override fun putBoolean(key: String, value: Boolean) = stage(key, value)

    override fun remove(key: String) = stage(key, null)

    override fun clear(): SharedPreferences.Editor {
      clearRequested = true
      return this
    }

    override fun commit(): Boolean {
      commitCount++
      flush()
      return true
    }

    override fun apply() {
      applyCount++
      flush()
    }

    private fun stage(key: String, value: Any?): SharedPreferences.Editor {
      pending[key] = value
      return this
    }

    private fun flush() {
      val touched = if (clearRequested) values.keys + pending.keys else pending.keys
      if (clearRequested) values.clear()
      pending.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
      touched.toList().forEach { key -> listeners.forEach { it.onSharedPreferenceChanged(this@FakeSharedPreferences, key) } }
    }
  }
}