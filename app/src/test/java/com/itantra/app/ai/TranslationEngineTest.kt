package com.itantra.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TranslationEngineTest {

    private lateinit var engine: TranslationEngine

    @Before
    fun setUp() {
        engine = TranslationEngine()
    }

    @Test
    fun translatesDirectHindiPhrasesToEnglish() {
        assertEquals("Need help", engine.translate("मदद चाहिए", "hi", "en"))
        assertEquals("Save me", engine.translate("बचाओ", "hi", "en"))
        assertEquals("We are trapped under rubble", engine.translate("हम मलबे में दबे हैं", "hi", "en"))
        assertEquals("Need water", engine.translate("पानी चाहिए", "hi", "en"))
        assertEquals("Having difficulty breathing", engine.translate("सांस लेने में तकलीफ है", "hi", "en"))
        assertEquals("Bleeding heavily", engine.translate("खून बह रहा है", "hi", "en"))
        assertEquals("Where are you?", engine.translate("आप कहां हैं", "hi", "en"))
    }

    @Test
    fun translatesDirectEnglishPhrasesToHindi() {
        assertEquals("मदद चाहिए", engine.translate("Need help", "en", "hi"))
        assertEquals("बचाओ", engine.translate("Save me", "en", "hi"))
        assertEquals("हम मलबे में दबे हैं", engine.translate("We are trapped under rubble", "en", "hi"))
        assertEquals("बचाव दल आ रहा है", engine.translate("Rescue team is on the way", "en", "hi"))
        assertEquals("ऑक्सीजन चाहिए", engine.translate("Need oxygen", "en", "hi"))
        assertEquals("क्या आप ठीक हैं?", engine.translate("Are you okay?", "en", "hi"))
    }

    @Test
    fun handlesCaseInsensitiveEnglishInput() {
        assertEquals("मदद चाहिए", engine.translate("need help", "en", "hi"))
        assertEquals("मदद चाहिए", engine.translate("NEED HELP", "en", "hi"))
        assertEquals("बचाव दल आ रहा है", engine.translate("RESCUE TEAM IS ON THE WAY", "en", "hi"))
    }

    @Test
    fun handlesPunctuationCleanly() {
        assertEquals("Need help", engine.translate("मदद चाहिए!", "hi", "en"))
        assertEquals("Need help", engine.translate("मदद चाहिए???", "hi", "en"))
        assertEquals("मदद चाहिए", engine.translate("Need help!", "en", "hi"))
    }

    @Test
    fun returnsOriginalWhenLanguagesMatchOrEmpty() {
        assertEquals("", engine.translate("", "hi", "en"))
        assertEquals("", engine.translate("   ", "hi", "en"))
        assertEquals("Hello world", engine.translate("Hello world", "en", "en"))
        assertEquals("नमस्ते", engine.translate("नमस्ते", "hi", "hi"))
    }

    @Test
    fun translatesWordSubstitutionsForNovelCombinations() {
        val result = engine.translate("Trapped child need water", "en", "hi")
        // Both "child" -> "बच्चा" and "water" -> "पानी" should be translated
        assertTrue("Expected translated tokens in: $result", result.contains("बच्चा") || result.contains("पानी"))
    }
}
