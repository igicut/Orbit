package com.example.orbit.data.remote

import com.example.orbit.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** F-37: razlikovanje slike sa servera od lokalne */
class ImageUrlsTest {

    @Test
    fun `server path becomes an absolute url without a double slash`() {
        val model = ImageUrls.model("/images/11111111-2222-3333-4444-555555555555.jpg")
        assertEquals(
            BuildConfig.BASE_URL.trimEnd('/') + "/images/11111111-2222-3333-4444-555555555555.jpg",
            model,
        )
        assertFalse(model.substringAfter("://").contains("//"))
    }

    @Test
    fun `local uris are left alone`() {
        val content = "content://media/external/images/media/42"
        val file = "file:///storage/emulated/0/Android/data/photo.jpg"

        assertEquals(content, ImageUrls.model(content))
        assertEquals(file, ImageUrls.model(file))
        assertFalse(ImageUrls.isStored(content))
        assertFalse(ImageUrls.isStored(file))
    }

    @Test
    fun `only the images prefix counts as stored`() {
        assertTrue(ImageUrls.isStored("/images/a.jpg"))
        assertFalse(ImageUrls.isStored("images/a.jpg"))
        assertFalse(ImageUrls.isStored("http://example.com/images/a.jpg"))
    }
}
