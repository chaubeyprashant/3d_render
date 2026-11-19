package com.example.a3d_render.project

import com.example.a3d_render.domain.project.DriveDownloadPage
import com.example.a3d_render.domain.project.DriveLinkParser
import com.example.a3d_render.domain.project.ParsedDriveLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveLinkParserTest {

    @Test
    fun parsesFileShareLink() {
        val parsed = DriveLinkParser.parse(
            "  https://drive.google.com/file/d/1AbCDef-ghij_KLMNOP1234567/view?usp=sharing  "
        )
        val file = parsed as ParsedDriveLink.File
        assertEquals("1AbCDef-ghij_KLMNOP1234567", file.id)
        assertNull(file.resourceKey)
        assertNull(parsed.rejectionMessage())
    }

    @Test
    fun keepsResourceKey() {
        val parsed = DriveLinkParser.parse(
            "https://drive.google.com/file/d/1AbCDef-ghij_KLMNOP1234567/view?usp=sharing&resourcekey=0-abc_DEF"
        ) as ParsedDriveLink.File
        assertEquals("0-abc_DEF", parsed.resourceKey)
    }

    @Test
    fun parsesOpenAndUcLinks() {
        val open = DriveLinkParser.parse("https://drive.google.com/open?id=1AbCDef-ghij_KLMNOP1234567")
        val uc = DriveLinkParser.parse("https://drive.google.com/uc?export=download&id=1AbCDef-ghij_KLMNOP1234567")
        assertEquals("1AbCDef-ghij_KLMNOP1234567", (open as ParsedDriveLink.File).id)
        assertEquals("1AbCDef-ghij_KLMNOP1234567", (uc as ParsedDriveLink.File).id)
    }

    @Test
    fun rejectsFolderLinks() {
        val parsed = DriveLinkParser.parse(
            "https://drive.google.com/drive/folders/1AbCDef-ghij_KLMNOP1234567?usp=sharing"
        )
        assertTrue(parsed is ParsedDriveLink.Folder)
        assertTrue(parsed.rejectionMessage()!!.contains("folder"))
    }

    @Test
    fun rejectsOtherHosts() {
        val parsed = DriveLinkParser.parse("https://example.com/file/d/1AbCDef-ghij_KLMNOP1234567/view")
        assertTrue(parsed is ParsedDriveLink.Invalid)
    }

    @Test
    fun readsConfirmForm() {
        val html = """
            <html><form id="download-form" action="https://drive.usercontent.google.com/download" method="get">
              <input type="hidden" name="id" value="FILEID1234567890">
              <input type="hidden" name="export" value="download">
              <input type="hidden" name="confirm" value="t">
              <input type="hidden" name="uuid" value="uuid-value">
            </form></html>
        """.trimIndent()
        val url = DriveDownloadPage.confirmUrl(html, "FILEID1234567890", "0-key")
        assertEquals(
            "https://drive.usercontent.google.com/download?id=FILEID1234567890&export=download&confirm=t&uuid=uuid-value&resourcekey=0-key",
            url
        )
    }

    @Test
    fun privatePageMessageAsksForSharing() {
        val message = DriveDownloadPage.failureMessage("<html>Sign in to Google</html>", 200)
        assertTrue(message.contains("Anyone with the link"))
    }
}
