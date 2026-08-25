package com.banasiak.coinflip.extensions

import androidx.lifecycle.SavedStateHandle
import com.banasiak.coinflip.main.MainState
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldBeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SavedStateTests {
  private val handle: SavedStateHandle = mockk(relaxed = true)

  @Test
  fun `state is written under the key every screen shares`() {
    // the literal is spelled out here on purpose: changing it silently orphans state saved by an
    // installed build across a process death
    val state = MainState(headsCount = 7)

    handle.save(state)

    verify(exactly = 1) { handle.set("state", state) }
  }

  @Test
  fun `state that cannot be parcelled is rejected instead of being dropped silently`() {
    val error = assertThrows<IllegalArgumentException> { handle.save("not parcelable") }

    error.message!!.contains("Parcelable").shouldBeTrue()
    verify(exactly = 0) { handle.set(any(), any<String>()) }
  }

  @Test
  fun `state is read back from the same key`() {
    val state = MainState(headsCount = 7)
    every { handle.get<MainState>("state") } returns state

    handle.restore<MainState>() shouldBeEqualTo state
  }

  @Test
  fun `an empty handle restores nothing`() {
    every { handle.get<MainState>("state") } returns null

    handle.restore<MainState>().shouldBeNull()
  }
}