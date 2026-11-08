package com.example

import com.example.live.ZoyaState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun testZoyaStateTransitions() {
    val states = listOf(ZoyaState.IDLE, ZoyaState.LISTENING, ZoyaState.THINKING, ZoyaState.SPEAKING)
    assertEquals(4, states.size)
    assertEquals("LISTENING", ZoyaState.LISTENING.name)
    assertEquals("SPEAKING", ZoyaState.SPEAKING.name)
  }

  @Test
  fun testForegroundServiceCompanionInitialState() {
    assertEquals(ZoyaState.IDLE, ZoyaForegroundService.currentState.value)
    assertEquals(false, ZoyaForegroundService.isRunning.value)
    assertEquals("Offline", ZoyaForegroundService.statusText.value)
  }
}
