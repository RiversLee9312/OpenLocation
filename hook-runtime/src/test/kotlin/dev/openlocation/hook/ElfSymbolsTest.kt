package dev.openlocation.hook

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class ElfSymbolsTest {
    @Test
    fun miniDebugNobitsMayBeLargerThanItsContainer() {
        val b = ByteBuffer.allocate(320).order(ByteOrder.LITTLE_ENDIAN)
        b.putInt(0, 0x464c457f)
        b.put(4, 2)
        b.put(5, 1)
        b.putShort(18, 183)
        b.putLong(40, 64)
        b.putShort(58, 64)
        b.putShort(60, 3)
        b.putShort(62, 1)
        b.putInt(128, 1)
        b.putInt(132, 3)
        b.putLong(152, 256)
        b.putLong(160, 17)
        b.putInt(192, 11)
        b.putInt(196, 8)
        b.putLong(216, 0)
        b.putLong(224, 4 * 1024 * 1024L)
        "\u0000.shstrtab\u0000.text\u0000".toByteArray().copyInto(b.array(), 256)
        val path = Files.createTempFile("openlocation-elf", ".so")
        try {
            Files.write(path, b.array())
            assertTrue(ElfSymbols.read(path.toString()).isEmpty())
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsAnUnrelatedArchitecture() {
        val path = Files.createTempFile("openlocation-invalid", ".so")
        try {
            Files.write(path, ByteArray(64))
            ElfSymbols.read(path.toString())
        } finally {
            Files.deleteIfExists(path)
        }
    }
}
