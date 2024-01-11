package com.banasiak.coinflip

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule : BeforeEachCallback, AfterEachCallback {
  override fun beforeEach(junitContext: ExtensionContext?) {
    Dispatchers.setMain(UnconfinedTestDispatcher())
  }

  override fun afterEach(junitContext: ExtensionContext?) {
    Dispatchers.resetMain()
  }
}