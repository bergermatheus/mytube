package app.mytube.tv

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchKeyboardTest {
    @Test fun backspaceRemovesWholeUnicodeCodePoints() {
        assertEquals("", "".dropLastCharacter())
        assertEquals("a", "ab".dropLastCharacter())
        assertEquals("café", "café🚀".dropLastCharacter())
        assertEquals("", "🚀".dropLastCharacter())
    }
}
