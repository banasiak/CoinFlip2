package com.banasiak.coinflip.settings

import android.net.Uri
import androidx.compose.ui.unit.IntRect
import androidx.lifecycle.ViewModelStore
import app.cash.turbine.test
import com.banasiak.coinflip.MainDispatcherRule
import com.banasiak.coinflip.R
import com.banasiak.coinflip.common.Coin
import com.banasiak.coinflip.common.CustomCoin
import com.banasiak.coinflip.common.Stats
import com.banasiak.coinflip.util.CoinImage
import com.banasiak.coinflip.util.CustomCoinStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeEmpty
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeFalse
import org.amshove.kluent.shouldBeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherRule::class)
class SettingsViewModelTests {
  private val settings: SettingsManager = mockk(relaxed = true)
  private val customCoins: CustomCoinStore = mockk(relaxed = true)

  private fun viewModel(): SettingsViewModel = SettingsViewModel(settings, customCoins)

  @Test
  fun initial_state_is_loaded_from_the_settings_manager() =
    runTest {
      every { settings.coinPrefix } returns "jfk"
      every { settings.animationEnabled } returns false
      every { settings.shakeEnabled } returns false
      every { settings.soundEnabled } returns false
      every { settings.textEnabled } returns false
      every { settings.vibrateEnabled } returns false
      every { settings.showStats } returns false
      every { settings.showQuickReset } returns true
      every { settings.customHeadsText } returns "CROWN"
      every { settings.customTailsText } returns "SHIP"
      every { settings.dynamicColorsEnabled } returns true
      every { settings.secureRandom } returns true
      every { settings.force } returns ShakeForce.HIGH
      every { settings.showStreak } returns true
      every { settings.customCoinRim } returns false
      every { settings.loadStats() } returns
        Stats(
          counts = mapOf(Coin.Value.HEADS to 7L, Coin.Value.TAILS to 5L),
          records = mapOf(Coin.Value.HEADS to 4L, Coin.Value.TAILS to 3L)
        )

      val vm = viewModel()

      vm.stateFlow.value shouldBeEqualTo
        SettingsState(
          coin = "jfk",
          animate = false,
          shake = false,
          sound = false,
          text = false,
          vibrate = false,
          stats = false,
          streak = true,
          quickReset = true,
          customHeads = "CROWN",
          customTails = "SHIP",
          dynamic = true,
          secureRandom = true,
          force = ShakeForce.HIGH,
          customRim = false,
          // both coins are read on load, so an empty one is an entry rather than an absence
          custom = CustomCoin.entries.associateWith { CustomCoinState() },
          flipCount = 12,
          headsRecord = 4,
          tailsRecord = 3
        )
    }

  @Test
  fun set_animate_persists_and_updates_state() =
    runTest {
      val vm = viewModel()
      vm.postAction(SettingsAction.SetAnimate(false))

      verify { settings.update(Setting.ANIMATE, false) }
      vm.stateFlow.value.animate shouldBeEqualTo false
    }

  @Test
  fun set_coin_persists_and_updates_state() =
    runTest {
      val vm = viewModel()
      vm.postAction(SettingsAction.SetCoin("jfk"))

      verify { settings.update(Setting.COIN, "jfk") }
      vm.stateFlow.value.coin shouldBeEqualTo "jfk"
    }

  @Test
  fun set_shake_persists_and_updates_state() =
    runTest {
      val vm = viewModel()
      vm.postAction(SettingsAction.SetShake(false))

      verify { settings.update(Setting.SHAKE, false) }
      vm.stateFlow.value.shake shouldBeEqualTo false
    }

  @Test
  fun set_sound_persists_and_updates_state() =
    runTest {
      val vm = viewModel()
      vm.postAction(SettingsAction.SetSound(false))

      verify { settings.update(Setting.SOUND, false) }
      vm.stateFlow.value.sound shouldBeEqualTo false
    }

  @Test
  fun set_text_persists_and_updates_state() =
    runTest {
      val vm = viewModel()
      vm.postAction(SettingsAction.SetText(false))

      verify { settings.update(Setting.TEXT, false) }
      vm.stateFlow.value.text shouldBeEqualTo false
    }

  @Test
  fun set_vibrate_persists_and_updates_state() =
    runTest {
      val vm = viewModel()
      vm.postAction(SettingsAction.SetVibrate(false))

      verify { settings.update(Setting.VIBRATE, false) }
      vm.stateFlow.value.vibrate shouldBeEqualTo false
    }

  @Test
  fun set_stats_persists_and_updates_state() =
    runTest {
      val vm = viewModel()
      vm.postAction(SettingsAction.SetStats(false))

      verify { settings.update(Setting.STATS, false) }
      vm.stateFlow.value.stats shouldBeEqualTo false
    }

  @Test
  fun set_quick_reset_persists_and_updates_state() =
    runTest {
      val vm = viewModel()
      vm.postAction(SettingsAction.SetQuickReset(true))

      verify { settings.update(Setting.QUICK_RESET, true) }
      vm.stateFlow.value.quickReset shouldBeEqualTo true
    }

  @Test
  fun set_secure_random_persists_and_updates_state() =
    runTest {
      val vm = viewModel()
      vm.postAction(SettingsAction.SetSecureRandom(true))

      verify { settings.update(Setting.SECURE_RANDOM, true) }
      vm.stateFlow.value.secureRandom shouldBeEqualTo true
    }

  @Test
  fun set_custom_heads_persists_and_updates_state() =
    runTest {
      val vm = viewModel()
      vm.postAction(SettingsAction.SetCustomHeads("CROWN"))

      verify { settings.update(Setting.CUSTOM_HEADS_TEXT, "CROWN") }
      vm.stateFlow.value.customHeads shouldBeEqualTo "CROWN"
    }

  @Test
  fun set_custom_tails_persists_and_updates_state() =
    runTest {
      val vm = viewModel()
      vm.postAction(SettingsAction.SetCustomTails("SHIP"))

      verify { settings.update(Setting.CUSTOM_TAILS_TEXT, "SHIP") }
      vm.stateFlow.value.customTails shouldBeEqualTo "SHIP"
    }

  @Test
  fun set_force_persists_and_updates_state() =
    runTest {
      val vm = viewModel()
      vm.postAction(SettingsAction.SetForce(ShakeForce.HIGH))

      verify { settings.update(Setting.FORCE, ShakeForce.HIGH) }
      vm.stateFlow.value.force shouldBeEqualTo ShakeForce.HIGH
    }

  @Test
  fun toggling_dynamic_colors_requests_restart_on_back() =
    runTest {
      val vm = viewModel()

      vm.effectFlow.test {
        vm.postAction(SettingsAction.SetDynamic(true))
        awaitItem() shouldBeEqualTo SettingsEffect.EnableRestartOnBack
      }

      verify { settings.update(Setting.DYNAMIC, true) }
      vm.stateFlow.value.dynamic shouldBeEqualTo true
    }

  @Test
  fun reset_stats_emits_undo_snackbar_and_can_be_undone() =
    runTest {
      val previous =
        Stats(
          counts = mapOf(Coin.Value.HEADS to 7L, Coin.Value.TAILS to 2L),
          records = mapOf(Coin.Value.HEADS to 5L, Coin.Value.TAILS to 2L),
          streakValue = Coin.Value.HEADS,
          streak = 3L
        )
      every { settings.loadStats() } returns previous

      val vm = viewModel()

      vm.effectFlow.test {
        vm.postAction(SettingsAction.ResetStats)
        awaitItem() shouldBeEqualTo
          SettingsEffect.ShowSnackbar(
            message = R.string.stats_reset_message,
            actionLabel = R.string.undo,
            action = SettingsAction.UndoResetStats
          )
      }
      verify { settings.resetStats() }

      // the records go with the counts, and both come back together
      vm.stateFlow.value.flipCount shouldBeEqualTo 0L
      vm.stateFlow.value.headsRecord shouldBeEqualTo 0L
      vm.stateFlow.value.tailsRecord shouldBeEqualTo 0L

      vm.postAction(SettingsAction.UndoResetStats)
      verify { settings.persistStats(previous) }
      vm.stateFlow.value.flipCount shouldBeEqualTo 9L
      vm.stateFlow.value.headsRecord shouldBeEqualTo 5L
      vm.stateFlow.value.tailsRecord shouldBeEqualTo 2L
    }

  @Test
  fun a_picked_image_is_held_for_the_crop_dialog() =
    runTest {
      val vm = viewModel()
      val uri = mockk<Uri>()

      vm.postAction(SettingsAction.PickedCustomImage(uri, CustomCoin.Face.HEADS))

      vm.stateFlow.value.pendingCrop shouldBeEqualTo PendingCrop(uri, CustomCoin.Face.HEADS)
    }

  @Test
  fun dismissing_the_crop_clears_the_pending_image() =
    runTest {
      val vm = viewModel()
      vm.postAction(SettingsAction.PickedCustomImage(mockk<Uri>(), CustomCoin.Face.TAILS))

      vm.postAction(SettingsAction.DismissCustomCrop)

      vm.stateFlow.value.pendingCrop shouldBeEqualTo null
    }

  @Test
  fun an_unreadable_image_clears_the_crop_and_says_so() =
    runTest {
      val vm = viewModel()
      vm.postAction(SettingsAction.PickedCustomImage(mockk<Uri>(), CustomCoin.Face.HEADS))

      vm.effectFlow.test {
        vm.postAction(SettingsAction.CustomImageFailed)
        awaitItem() shouldBeEqualTo SettingsEffect.ShowSnackbar(R.string.coin_crop_failed)
      }

      vm.stateFlow.value.pendingCrop shouldBeEqualTo null
    }

  @Test
  fun replacing_a_face_moves_the_revision_though_the_face_set_is_unchanged() =
    runTest {
      // the thumbnails key on this. Replacing an image leaves the face set byte-for-byte identical,
      // so without the revision moving, the dialog goes on drawing the picture it already cached --
      // and it stays composed underneath the photo picker and the crop, so it never gets a fresh one
      val both = setOf(CustomCoin.Face.HEADS, CustomCoin.Face.TAILS)
      every { customCoins.storedFaces(CustomCoin.PHOTO) } returns both
      every { customCoins.revision(CustomCoin.PHOTO) } returns 1_000L
      coEvery { customCoins.save(any(), any(), any(), any()) } returns true
      val vm = viewModel()
      vm.stateFlow.value.stateFor(CustomCoin.PHOTO).revision shouldBeEqualTo 1_000L

      every { customCoins.revision(CustomCoin.PHOTO) } returns 2_000L
      vm.postAction(SettingsAction.PickedCustomImage(mockk<Uri>(), CustomCoin.Face.HEADS))
      vm.postAction(SettingsAction.CropCustomImage(IntRect(0, 0, 10, 10), CoinImage.Orientation.UPRIGHT))
      advanceUntilIdle()

      vm.stateFlow.value.stateFor(CustomCoin.PHOTO).faces shouldBeEqualTo both
      vm.stateFlow.value.stateFor(CustomCoin.PHOTO).revision shouldBeEqualTo 2_000L
    }

  @Test
  fun a_failed_save_leaves_the_revision_alone() =
    runTest {
      every { customCoins.revision(CustomCoin.PHOTO) } returns 1_000L
      coEvery { customCoins.save(any(), any(), any(), any()) } returns false
      val vm = viewModel()

      vm.postAction(SettingsAction.PickedCustomImage(mockk<Uri>(), CustomCoin.Face.HEADS))
      vm.postAction(SettingsAction.CropCustomImage(IntRect(0, 0, 10, 10), CoinImage.Orientation.UPRIGHT))
      advanceUntilIdle()

      vm.stateFlow.value.stateFor(CustomCoin.PHOTO).revision shouldBeEqualTo 1_000L
    }

  @Test
  fun set_custom_rim_persists_and_updates_state() =
    runTest {
      val vm = viewModel()
      vm.postAction(SettingsAction.SetCustomRim(false))

      verify { settings.update(Setting.CUSTOM_COIN_RIM, false) }
      vm.stateFlow.value.customRim shouldBeEqualTo false
    }

  @Test
  fun deleting_clears_the_coin_on_screen_but_not_yet_on_disk() =
    runTest {
      // the whole point of deferring: the screen reads as empty while the files are still there,
      // so an undo has something to come back to and nothing has to be renamed aside
      every { customCoins.storedFaces(CustomCoin.PHOTO) } returns setOf(CustomCoin.Face.HEADS, CustomCoin.Face.TAILS)
      every { settings.coinPrefix } returnsMany listOf(CustomCoin.PHOTO.prefix, Setting.COIN.default)
      val vm = viewModel()

      vm.postAction(SettingsAction.DeleteCustomCoin(CustomCoin.PHOTO))

      vm.stateFlow.value.stateFor(CustomCoin.PHOTO).faces.shouldBeEmpty()
      vm.stateFlow.value.coin shouldBeEqualTo Setting.COIN.default
      coVerify(exactly = 0) { customCoins.deleteAll(CustomCoin.PHOTO) }
    }

  @Test
  fun deleting_offers_an_undo_and_a_commit_for_when_the_snackbar_lapses() =
    runTest {
      every { customCoins.storedFaces(CustomCoin.PHOTO) } returns setOf(CustomCoin.Face.HEADS, CustomCoin.Face.TAILS)
      val vm = viewModel()

      vm.effectFlow.test {
        vm.postAction(SettingsAction.DeleteCustomCoin(CustomCoin.PHOTO))
        awaitItem() shouldBeEqualTo
          SettingsEffect.ShowSnackbar(
            message = R.string.settings_item_photo_coin_deleted,
            actionLabel = R.string.undo,
            action = SettingsAction.UndoDeleteCustomCoin(CustomCoin.PHOTO),
            onDismissed = SettingsAction.CommitDeleteCustomCoin(CustomCoin.PHOTO)
          )
      }
    }

  @Test
  fun undoing_a_delete_restores_the_coin_and_never_touches_the_files() =
    runTest {
      val both = setOf(CustomCoin.Face.HEADS, CustomCoin.Face.TAILS)
      every { customCoins.storedFaces(CustomCoin.PHOTO) } returns both
      every { settings.coinPrefix } returnsMany
        listOf(CustomCoin.PHOTO.prefix, Setting.COIN.default, CustomCoin.PHOTO.prefix)
      val vm = viewModel()

      vm.postAction(SettingsAction.DeleteCustomCoin(CustomCoin.PHOTO))
      vm.postAction(SettingsAction.UndoDeleteCustomCoin(CustomCoin.PHOTO))

      vm.stateFlow.value.stateFor(CustomCoin.PHOTO).faces shouldBeEqualTo both
      vm.stateFlow.value.coin shouldBeEqualTo CustomCoin.PHOTO.prefix
      verify { settings.update(Setting.COIN, CustomCoin.PHOTO.prefix) }
      coVerify(exactly = 0) { customCoins.deleteAll(CustomCoin.PHOTO) }
    }

  @Test
  fun the_snackbar_lapsing_is_what_finally_unlinks_the_files() =
    runTest {
      every { customCoins.storedFaces(CustomCoin.PHOTO) } returns setOf(CustomCoin.Face.HEADS, CustomCoin.Face.TAILS)
      val vm = viewModel()

      vm.postAction(SettingsAction.DeleteCustomCoin(CustomCoin.PHOTO))
      every { customCoins.storedFaces(CustomCoin.PHOTO) } returns emptySet()
      vm.postAction(SettingsAction.CommitDeleteCustomCoin(CustomCoin.PHOTO))

      coVerify { customCoins.deleteAll(CustomCoin.PHOTO) }
      vm.stateFlow.value.stateFor(CustomCoin.PHOTO).faces.shouldBeEmpty()
    }

  @Test
  fun a_commit_after_an_undo_does_nothing() =
    runTest {
      // both arrive from the same snackbar, and only one of them may count
      every { customCoins.storedFaces(CustomCoin.PHOTO) } returns setOf(CustomCoin.Face.HEADS, CustomCoin.Face.TAILS)
      val vm = viewModel()

      vm.postAction(SettingsAction.DeleteCustomCoin(CustomCoin.PHOTO))
      vm.postAction(SettingsAction.UndoDeleteCustomCoin(CustomCoin.PHOTO))
      vm.postAction(SettingsAction.CommitDeleteCustomCoin(CustomCoin.PHOTO))

      coVerify(exactly = 0) { customCoins.deleteAll(CustomCoin.PHOTO) }
    }

  @Test
  fun setting_a_new_face_settles_a_delete_that_is_still_waiting_before_writing() =
    runTest {
      // a rotation cancels the snackbar without it reporting either way, so a delete can still be
      // pending here -- and calling it off would leave the un-replaced face on disk to come back
      every { customCoins.storedFaces(CustomCoin.PHOTO) } returns setOf(CustomCoin.Face.HEADS, CustomCoin.Face.TAILS)
      coEvery { customCoins.save(any(), any(), any(), any()) } returns true
      val vm = viewModel()

      vm.postAction(SettingsAction.DeleteCustomCoin(CustomCoin.PHOTO))
      vm.postAction(SettingsAction.PickedCustomImage(mockk<Uri>(), CustomCoin.Face.HEADS))
      // only the replaced face survives the settled delete
      every { customCoins.storedFaces(CustomCoin.PHOTO) } returns setOf(CustomCoin.Face.HEADS)
      vm.postAction(SettingsAction.CropCustomImage(IntRect(0, 0, 10, 10), CoinImage.Orientation.UPRIGHT))
      advanceUntilIdle()

      coVerifyOrder {
        customCoins.deleteAll(CustomCoin.PHOTO)
        customCoins.save(any(), any(), any(), any())
      }
      vm.stateFlow.value.stateFor(CustomCoin.PHOTO).faces shouldBeEqualTo setOf(CustomCoin.Face.HEADS)
      vm.stateFlow.value.stateFor(CustomCoin.PHOTO).ready.shouldBeFalse()

      // and the commit the lost snackbar would have sent finds nothing left to do
      vm.postAction(SettingsAction.CommitDeleteCustomCoin(CustomCoin.PHOTO))
      advanceUntilIdle()
      coVerify(exactly = 1) { customCoins.deleteAll(CustomCoin.PHOTO) }
    }

  @Test
  fun an_emoji_face_is_written_and_moves_that_coins_revision() =
    runTest {
      every { customCoins.storedFaces(CustomCoin.EMOJI) } returns setOf(CustomCoin.Face.HEADS)
      every { customCoins.revision(CustomCoin.EMOJI) } returns 1_000L
      coEvery { customCoins.save(any<String>(), any()) } returns true
      val vm = viewModel()

      every { customCoins.revision(CustomCoin.EMOJI) } returns 2_000L
      vm.postAction(SettingsAction.PickedCustomEmoji("\uD83C\uDF55", CustomCoin.Face.HEADS))
      advanceUntilIdle()

      coVerify { customCoins.save("\uD83C\uDF55", CustomCoin.Face.HEADS) }
      vm.stateFlow.value.stateFor(CustomCoin.EMOJI).revision shouldBeEqualTo 2_000L
    }

  @Test
  fun the_glyph_is_remembered_so_the_picker_reopens_on_it() =
    runTest {
      every { customCoins.storedFaces(CustomCoin.EMOJI) } returns setOf(CustomCoin.Face.HEADS)
      every { settings.emojiFaces } returns mapOf(CustomCoin.Face.HEADS to "\uD83C\uDF55")
      coEvery { customCoins.save(any<String>(), any()) } returns true
      val vm = viewModel()

      vm.postAction(SettingsAction.PickedCustomEmoji("\uD83C\uDF55", CustomCoin.Face.HEADS))
      advanceUntilIdle()

      verify { settings.setEmojiFace(CustomCoin.Face.HEADS, "\uD83C\uDF55") }
      vm.stateFlow.value.stateFor(CustomCoin.EMOJI).emoji shouldBeEqualTo
        mapOf(CustomCoin.Face.HEADS to "\uD83C\uDF55")
    }

  @Test
  fun a_failed_emoji_save_says_so_and_records_nothing() =
    runTest {
      coEvery { customCoins.save(any<String>(), any()) } returns false
      val vm = viewModel()

      vm.effectFlow.test {
        vm.postAction(SettingsAction.PickedCustomEmoji("\uD83C\uDF55", CustomCoin.Face.HEADS))
        advanceUntilIdle()
        awaitItem() shouldBeEqualTo
          SettingsEffect.ShowSnackbar(R.string.settings_item_custom_coin_save_failed)
      }

      verify(exactly = 0) { settings.setEmojiFace(any(), any()) }
    }

  @Test
  fun setting_an_emoji_face_settles_a_delete_that_is_still_waiting_before_writing() =
    runTest {
      // the same order the photo path depends on: called off instead of run, the face that is not
      // being replaced survives on disk and rebuilds the coin the user had just deleted
      every { customCoins.storedFaces(CustomCoin.EMOJI) } returns
        setOf(CustomCoin.Face.HEADS, CustomCoin.Face.TAILS)
      coEvery { customCoins.save(any<String>(), any()) } returns true
      val vm = viewModel()

      vm.postAction(SettingsAction.DeleteCustomCoin(CustomCoin.EMOJI))
      every { customCoins.storedFaces(CustomCoin.EMOJI) } returns setOf(CustomCoin.Face.HEADS)
      vm.postAction(SettingsAction.PickedCustomEmoji("\uD83C\uDF55", CustomCoin.Face.HEADS))
      advanceUntilIdle()

      coVerifyOrder {
        customCoins.deleteAll(CustomCoin.EMOJI)
        customCoins.save(any<String>(), any())
      }
      vm.stateFlow.value.stateFor(CustomCoin.EMOJI).ready.shouldBeFalse()
    }

  @Test
  fun a_pending_delete_on_one_coin_is_not_settled_by_a_write_to_the_other() =
    runTest {
      // they share a directory and a snackbar mechanism; only the coin that was written may settle
      every { customCoins.storedFaces(CustomCoin.PHOTO) } returns
        setOf(CustomCoin.Face.HEADS, CustomCoin.Face.TAILS)
      coEvery { customCoins.save(any<String>(), any()) } returns true
      val vm = viewModel()

      vm.postAction(SettingsAction.DeleteCustomCoin(CustomCoin.PHOTO))
      vm.postAction(SettingsAction.PickedCustomEmoji("\uD83C\uDF55", CustomCoin.Face.HEADS))
      advanceUntilIdle()

      coVerify(exactly = 0) { customCoins.deleteAll(CustomCoin.PHOTO) }

      // and the photo coin's own undo still has something to come back to
      vm.postAction(SettingsAction.UndoDeleteCustomCoin(CustomCoin.PHOTO))
      vm.stateFlow.value.stateFor(CustomCoin.PHOTO).ready.shouldBeTrue()
    }

  @Test
  fun deleting_one_coin_leaves_the_other_selected() =
    runTest {
      // the selection is a single value, and only the coin that owned it may reset it
      every { customCoins.storedFaces(CustomCoin.EMOJI) } returns
        setOf(CustomCoin.Face.HEADS, CustomCoin.Face.TAILS)
      every { settings.coinPrefix } returns CustomCoin.PHOTO.prefix
      val vm = viewModel()

      vm.postAction(SettingsAction.DeleteCustomCoin(CustomCoin.EMOJI))

      verify(exactly = 0) { settings.update(Setting.COIN, Setting.COIN.default) }
      vm.stateFlow.value.coin shouldBeEqualTo CustomCoin.PHOTO.prefix
    }

  @Test
  fun leaving_settings_settles_a_delete_on_each_coin_that_was_waiting() =
    runTest {
      CustomCoin.entries.forEach {
        every { customCoins.storedFaces(it) } returns setOf(CustomCoin.Face.HEADS, CustomCoin.Face.TAILS)
      }
      val vm = viewModel()
      val store = ViewModelStore().apply { put("settings", vm) }

      CustomCoin.entries.forEach { vm.postAction(SettingsAction.DeleteCustomCoin(it)) }
      store.clear()

      CustomCoin.entries.forEach { verify { customCoins.deleteAllDetached(it) } }
    }

  @Test
  fun deleting_with_nothing_set_does_nothing_at_all() =
    runTest {
      every { customCoins.storedFaces(CustomCoin.PHOTO) } returns emptySet()
      val vm = viewModel()

      vm.effectFlow.test {
        vm.postAction(SettingsAction.DeleteCustomCoin(CustomCoin.PHOTO))
        expectNoEvents()
      }
      coVerify(exactly = 0) { customCoins.deleteAll(CustomCoin.PHOTO) }
    }

  @Test
  fun leaving_settings_settles_a_delete_that_was_still_waiting() =
    runTest {
      // the snackbar goes with the screen, so a pending delete would otherwise hang forever and
      // quietly undo itself. Walking away is a decision too.
      every { customCoins.storedFaces(CustomCoin.PHOTO) } returns setOf(CustomCoin.Face.HEADS, CustomCoin.Face.TAILS)
      val vm = viewModel()
      val store = ViewModelStore().apply { put("settings", vm) }

      vm.postAction(SettingsAction.DeleteCustomCoin(CustomCoin.PHOTO))
      store.clear()

      verify { customCoins.deleteAllDetached(CustomCoin.PHOTO) }
    }

  @Test
  fun leaving_settings_with_no_delete_pending_touches_nothing() =
    runTest {
      val vm = viewModel()
      val store = ViewModelStore().apply { put("settings", vm) }

      store.clear()

      verify(exactly = 0) { customCoins.deleteAllDetached(CustomCoin.PHOTO) }
    }

  @Test
  fun the_turn_and_flip_the_user_chose_reach_the_store() =
    runTest {
      // the crop rect is in the coordinates of the adjusted image, so saving has to arrive at the
      // same one; dropping the adjustment here would crop the wrong part of the photo
      val turned = CoinImage.Orientation(degrees = 90f, mirrored = true)
      coEvery { customCoins.save(any(), any(), any(), any()) } returns true
      val vm = viewModel()

      vm.postAction(SettingsAction.PickedCustomImage(mockk<Uri>(), CustomCoin.Face.HEADS))
      vm.postAction(SettingsAction.CropCustomImage(IntRect(1, 2, 3, 4), turned))
      advanceUntilIdle()

      coVerify { customCoins.save(any(), CustomCoin.Face.HEADS, IntRect(1, 2, 3, 4), turned) }
    }
}