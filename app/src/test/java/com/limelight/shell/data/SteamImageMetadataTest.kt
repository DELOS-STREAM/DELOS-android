package com.limelight.shell.data

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class SteamImageMetadataTest {
    private fun parse(fields: String) = SgdbImage.parse(JsonParser.parseString("""{"id":8,"url":"https://art.invalid/full.png",$fields}""").asJsonObject, false)!!

    @Test fun malformedOptionalFieldsDoNotHideAnOtherwiseUsableImage() {
        val image = parse(""""thumb":{},"width":"wide","height":[],"score":false,"style":[{}],"author":[],"date":{}""")
        assertEquals(image.url, image.thumb)
        assertEquals(0, image.width)
        assertEquals(0, image.height)
        assertEquals(0, image.score)
        assertNull(image.style)
        assertNull(image.author)
        assertNull(image.date)
        assertNull(image.format)
    }

    @Test fun emptyAndNonPrimitiveStylesAreNotDisplayed() {
        for (style in listOf("[]", "{}", "null", "\"\"", "[\"\"]")) {
            assertNull(parse("\"style\":$style").style)
        }
        assertEquals("alternate", parse(""""style":["alternate","blurred"]""").style)
        assertEquals("blurred", parse(""""style":"blurred"""").style)
    }

    @Test fun dateStringsAcceptSecondsMillisecondsAndOffsetsButNotInvalidValues() {
        fun date(value: String) = SgdbImage.parseDate(JsonParser.parseString(value))
        assertEquals(1_700_000_000L, date("\"1700000000\""))
        assertEquals(1_700_000_000L, date("\"1700000000000\""))
        assertEquals(1_700_000_000L, date("1700000000000"))
        assertEquals(1_700_000_000L, date("1700000000"))
        assertEquals(0L, date("\"1970-01-01T01:00:00+01:00\""))
        for (value in listOf("null", "[]", "{}", "true", "\"yesterday\"", "\"\"")) assertNull(date(value))
        assertNull(SgdbImage.parseDate(null))
        assertEquals(1_700_000_000L, parse(""""created_at":"1700000000000"""").date)
    }

    @Test fun formatLabelsAndVideoPreviewsFollowTheImageActuallyShown() {
        assertNull(parse(""""mime":""""").format)
        assertEquals("JPG", parse(""""mime":"image/jpeg"""").format)
        assertEquals("WEBP", parse(""""mime":"image/webp"""").format)
        val image = parse(""""thumb":"https://art.invalid/preview.WEBM?token=1"""")
        assertEquals(image.url, image.previewUrl)
        val still = parse(""""thumb":"https://art.invalid/preview.png?token=1"""")
        assertEquals(still.thumb, still.previewUrl)
    }
}
