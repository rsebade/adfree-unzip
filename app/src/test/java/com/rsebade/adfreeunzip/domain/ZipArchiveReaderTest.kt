package com.rsebade.adfreeunzip.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.concurrent.CancellationException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipArchiveReaderTest {
    private val reader = ZipArchiveReader()

    @Test
    fun `list returns files and folders in archive order`() {
        val zip = zipOf("docs/" to null, "docs/a.txt" to "hello", "b.txt" to "hi")

        val result = reader.list { ByteArrayInputStream(zip) }

        // Sizes are null: ZipOutputStream records them after the data, so a streaming read can't see them.
        assertEquals(
            ArchiveResult.Success(
                listOf(
                    ArchiveEntry(listOf("docs"), isDirectory = true, size = null),
                    ArchiveEntry(listOf("docs", "a.txt"), isDirectory = false, size = null),
                    ArchiveEntry(listOf("b.txt"), isDirectory = false, size = null),
                ),
            ),
            result,
        )
    }

    @Test
    fun `extract writes file contents into the target`() {
        val zip = zipOf("docs/" to null, "docs/a.txt" to "hello", "b.txt" to "hi")
        val target = FakeTarget()

        val result = reader.extract({ ByteArrayInputStream(zip) }, target)

        assertEquals(ArchiveResult.Success(ExtractionSummary(2, 1, emptyList())), result)
        assertEquals(mapOf("docs/a.txt" to "hello", "b.txt" to "hi"), target.files())
        assertEquals(listOf("docs"), target.directories)
    }

    @Test
    fun `extract skips entries that would escape the target folder`() {
        val zip = zipOf("../evil.sh" to "rm -rf", "a/../../evil2.sh" to "x", "ok.txt" to "fine")
        val target = FakeTarget()

        val result = reader.extract({ ByteArrayInputStream(zip) }, target)

        assertEquals(
            ArchiveResult.Success(ExtractionSummary(1, 0, listOf("../evil.sh", "a/../../evil2.sh"))),
            result,
        )
        assertEquals(mapOf("ok.txt" to "fine"), target.files())
    }

    @Test
    fun `sanitizePath normalizes slashes and drops dot segments`() {
        assertEquals(listOf("a", "b.txt"), ZipArchiveReader.sanitizePath("/a/./b.txt"))
        assertEquals(listOf("a", "b.txt"), ZipArchiveReader.sanitizePath("a\\b.txt"))
        assertNull(ZipArchiveReader.sanitizePath("a/../../b.txt"))
        assertNull(ZipArchiveReader.sanitizePath("/"))
    }

    @Test
    fun `list reports NoEntries for data that is not a zip`() {
        val result = reader.list { ByteArrayInputStream("just text".toByteArray()) }

        assertEquals(ArchiveResult.Failure(ArchiveError.NoEntries), result)
    }

    @Test
    fun `extract reports Corrupted for a truncated zip`() {
        val zip = zipOf("a.txt" to "x".repeat(10_000))
        val truncated = zip.copyOf(zip.size / 2)

        val result = reader.extract({ ByteArrayInputStream(truncated) }, FakeTarget())

        assertEquals(ArchiveResult.Failure(ArchiveError.Corrupted), result)
    }

    @Test
    fun `list falls back to IBM437 for names that are not UTF-8`() {
        val zip = zipOf("café.txt" to "x", charset = Charset.forName("IBM437"))

        val result = reader.list { ByteArrayInputStream(zip) }

        assertEquals(listOf("café.txt"), (result as ArchiveResult.Success).value.map { it.name })
    }

    @Test
    fun `extract reports Io when the target cannot be written`() {
        val zip = zipOf("a.txt" to "x")
        val target =
            object : ExtractionTarget {
                override fun createDirectory(path: List<String>) = Unit

                override fun openFile(path: List<String>): OutputStream = throw java.io.IOException("disk full")

                override fun deleteFile(path: List<String>) = Unit
            }

        val result = reader.extract({ ByteArrayInputStream(zip) }, target)

        assertEquals(ArchiveResult.Failure(ArchiveError.Io("disk full")), result)
    }

    @Test
    fun `extract reports entries written so far`() {
        val zip = zipOf("docs/" to null, "docs/a.txt" to "hello", "b.txt" to "hi")
        val counts = mutableListOf<Int>()

        reader.extract({ ByteArrayInputStream(zip) }, FakeTarget()) { counts += it }

        assertEquals(listOf(1, 2, 3), counts.distinct())
    }

    @Test
    fun `cancelling mid-file keeps finished files and deletes the partial one`() {
        val zip = zipOf("done.txt" to "complete", "big.bin" to "x".repeat(300_000))
        val target = FakeTarget()
        var chunksIntoBigFile = 0

        val thrown =
            assertThrows(CancellationException::class.java) {
                reader.extract({ ByteArrayInputStream(zip) }, target) { written ->
                    if (written == 1 && ++chunksIntoBigFile > 2) throw CancellationException("cancelled")
                }
            }

        assertEquals("cancelled", thrown.message)
        assertEquals(mapOf("done.txt" to "complete"), target.files())
    }

    private fun zipOf(
        vararg entries: Pair<String, String?>,
        charset: Charset = Charsets.UTF_8,
    ): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes, charset).use { zip ->
            for ((name, content) in entries) {
                zip.putNextEntry(ZipEntry(name))
                content?.let { zip.write(it.toByteArray()) }
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    private class FakeTarget : ExtractionTarget {
        val directories = mutableListOf<String>()
        private val written = linkedMapOf<String, ByteArrayOutputStream>()

        fun files(): Map<String, String> = written.mapValues { it.value.toString(Charsets.UTF_8) }

        override fun createDirectory(path: List<String>) {
            directories += path.joinToString("/")
        }

        override fun openFile(path: List<String>): OutputStream =
            ByteArrayOutputStream().also {
                written[path.joinToString("/")] =
                    it
            }

        override fun deleteFile(path: List<String>) {
            written.remove(path.joinToString("/"))
        }
    }
}
