package dev.openlocation.hook

import java.io.ByteArrayInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.tukaani.xz.XZInputStream

/** Discover symbols from the installed ELF, including its optional GNU mini debug image. */
internal object ElfSymbols {
    private class Elf(val bytes: ByteArray) {
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        init {
            require(
                bytes.size >= 64 &&
                    b.getInt(0) == 0x464c457f &&
                    bytes[4] == 2.toByte() &&
                    bytes[5] == 1.toByte()
            )
            require(b.getShort(18).toInt() == 183) { "Only AArch64 ELF is supported" }
        }

        data class Section(
            val name: Int,
            val type: Int,
            val offset: Int,
            val size: Int,
            val link: Int,
            val entry: Int,
        )

        private fun int64(offset: Int) =
            b.getLong(offset).also { require(it in 0..bytes.size.toLong()) }.toInt()

        val sections = run {
            val start = int64(40)
            val stride = b.getShort(58).toInt() and 65535
            val count = b.getShort(60).toInt() and 65535
            require(
                stride >= 64 &&
                    count <= 8192 &&
                    start.toLong() + stride.toLong() * count <= bytes.size
            )
            List(count) { i ->
                val p = start + i * stride
                val type = b.getInt(p + 4)
                val size =
                    b.getLong(p + 32).also { require(it in 0..Int.MAX_VALUE.toLong()) }.toInt()
                val offset = int64(p + 24)
                require(type == 8 || offset.toLong() + size <= bytes.size)
                Section(
                    b.getInt(p),
                    type,
                    offset,
                    size,
                    b.getInt(p + 40),
                    b.getLong(p + 56).toInt(),
                )
            }
        }

        fun content(s: Section) = bytes.copyOfRange(s.offset, s.offset + s.size)

        fun string(s: Section, index: Int): String {
            require(index in 0 until s.size)
            val p = s.offset + index
            val end =
                (p until s.offset + s.size).firstOrNull { bytes[it] == 0.toByte() }
                    ?: error("Unterminated ELF string")
            return bytes.copyOfRange(p, end).toString(Charsets.UTF_8)
        }

        fun symbols(): Map<String, Long> = buildMap {
            sections
                .filter { it.type == 2 || it.type == 11 }
                .forEach { s ->
                    require(s.entry >= 24 && s.size / s.entry <= 200000)
                    val strings = sections[s.link]
                    repeat(s.size / s.entry) { i ->
                        val p = s.offset + i * s.entry
                        if (b.getShort(p + 6).toInt() != 0 && (bytes[p + 4].toInt() and 15) == 2) {
                            val name = string(strings, b.getInt(p))
                            val value = b.getLong(p + 8)
                            if (value > 0) put(name, value)
                        }
                    }
                }
        }

        fun debug(): ByteArray? {
            val names = sections[b.getShort(62).toInt() and 65535]
            val section =
                sections.firstOrNull { string(names, it.name) == ".gnu_debugdata" } ?: return null
            return XZInputStream(ByteArrayInputStream(content(section)), 16384).use {
                it.readNBytes(16 * 1024 * 1024 + 1).also { data ->
                    require(data.size <= 16 * 1024 * 1024)
                }
            }
        }
    }

    fun read(path: String): Map<String, Long> {
        val file = File(path)
        require(file.length() in 64..32 * 1024 * 1024)
        val elf = Elf(file.readBytes())
        return elf.symbols() + (elf.debug()?.let { Elf(it).symbols() } ?: emptyMap())
    }
}
