package com.example.kpkn.data.importer

import com.example.kpkn.domain.training.importer.ImportSheet
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.math.MathContext
import java.nio.charset.StandardCharsets

/** Lector acotado de libros Excel 97–2003 (BIFF8) guardados en un contenedor OLE/CFB. */
internal object Biff8Reader {
    private const val INVALID_XLS = "No es un libro de Excel (.xls) válido."
    private const val ENCRYPTED_XLS = "Este libro de Excel está protegido. Guárdalo sin contraseña y vuelve a intentarlo."
    private const val TOO_MANY_STRINGS = "Este libro contiene demasiado texto para leerlo en el teléfono. Guárdalo como .xlsx y vuelve a intentarlo."
    private const val CFB_SIGNATURE = "\u00D0\u00CF\u0011\u00E0\u00A1\u00B1\u001A\u00E1"
    private const val END_OF_CHAIN = 0xFFFF_FFFEL
    private const val FREE_SECTOR = 0xFFFF_FFFFL
    private const val FAT_SECTOR = 0xFFFF_FFFDL
    private const val DIFAT_SECTOR = 0xFFFF_FFFCL
    private const val BOF = 0x0809
    private const val EOF = 0x000A
    private const val FILEPASS = 0x002F
    private const val BOUNDSHEET8 = 0x0085
    private const val SST = 0x00FC
    private const val CONTINUE = 0x003C
    private const val LABEL = 0x0204
    private const val NUMBER = 0x0203
    private const val RK = 0x027E
    private const val MULRK = 0x00BD
    private const val LABELSST = 0x00FD
    private const val BOOLERR = 0x0205
    private const val FORMULA = 0x0006
    private const val STRING = 0x0207
    private const val FORMAT = 0x041E
    private const val XF = 0x00E0
    private const val WORKSHEET_SUBSTREAM = 0x0010
    private const val WORKBOOK_GLOBALS_SUBSTREAM = 0x0005
    private const val MAX_SHARED_STRINGS = 100_000
    private const val MAX_SHARED_REFERENCES = ImportLimits.MAX_ROWS * ImportLimits.MAX_COLUMNS * ImportLimits.MAX_SHEETS

    /** Lee las hojas visibles, manteniendo los topes que aplican los lectores xlsx y ods. */
    fun read(bytes: ByteArray): List<ImportSheet> {
        if (bytes.size > ImportLimits.MAX_FILE_BYTES) throw ImportFileException(ImportFileReader.TOO_LARGE_MESSAGE)
        if (!bytes.hasOleSignature()) throw ImportFileException(INVALID_XLS)
        try {
            val compound = CompoundFile(bytes)
            val workbook = compound.workbookStream() ?: throw ImportFileException(ImportFileReader.OLD_OFFICE_MESSAGE)
            return WorkbookStream(workbook).read()
        } catch (error: ImportFileException) {
            throw error
        } catch (_: Exception) {
            throw ImportFileException(INVALID_XLS)
        }
    }

    private fun ByteArray.hasOleSignature(): Boolean =
        size >= CFB_SIGNATURE.length && CFB_SIGNATURE.indices.all { this[it] == CFB_SIGNATURE[it].code.toByte() }

    /** Estructura OLE: sigue FAT y MiniFAT con ciclos y tamaños acotados por el archivo recibido. */
    private class CompoundFile(private val bytes: ByteArray) {
        private val sectorSize: Int
        private val miniSectorSize: Int
        private val sectorCount: Int
        private val miniStreamCutoff: Long
        private val firstDirectorySector: Long
        private val firstMiniFatSector: Long
        private val miniFatSectorCount: Int
        private val fat: LongArray

        init {
            require(bytes.size >= 512)
            val majorVersion = u16(bytes, 26)
            require(majorVersion == 3 || majorVersion == 4)
            val sectorShift = u16(bytes, 30)
            require(sectorShift == if (majorVersion == 3) 9 else 12)
            sectorSize = 1 shl sectorShift
            val miniSectorShift = u16(bytes, 32)
            require(miniSectorShift == 6)
            miniSectorSize = 1 shl miniSectorShift
            require(bytes.size >= sectorSize && bytes.size % sectorSize == 0)
            require(u16(bytes, 28) == 0xFFFE)
            sectorCount = bytes.size / sectorSize - 1
            require(sectorCount > 0)

            val fatSectorCount = u32(bytes, 44).boundedInt(sectorCount)
            firstDirectorySector = u32(bytes, 48)
            miniStreamCutoff = u32(bytes, 56)
            firstMiniFatSector = u32(bytes, 60)
            miniFatSectorCount = u32(bytes, 64).boundedInt(sectorCount)
            val firstDifatSector = u32(bytes, 68)
            val difatSectorCount = u32(bytes, 72).boundedInt(sectorCount)
            val fatSectorIds = readDifat(firstDifatSector, difatSectorCount, fatSectorCount)
            require(fatSectorIds.size == fatSectorCount)
            val fatEntries = ArrayList<Long>(fatSectorCount * (sectorSize / 4))
            fatSectorIds.forEach { id ->
                val data = sector(id)
                for (offset in data.indices step 4) fatEntries += u32(data, offset)
            }
            fat = fatEntries.toLongArray()
            require(firstDirectorySector < sectorCount && firstDirectorySector != FREE_SECTOR)
            require(miniStreamCutoff > 0)
        }

        fun workbookStream(): ByteArray? {
            val directory = readRegularChain(firstDirectorySector, bytes.size)
            val entries = directoryEntries(directory)
            val workbook = entries.firstOrNull { it.type == 2 && it.name.equals("Workbook", ignoreCase = true) }
                ?: entries.firstOrNull { it.type == 2 && it.name.equals("Book", ignoreCase = true) }
                ?: return null
            val root = entries.firstOrNull { it.type == 5 } ?: throw IllegalArgumentException("Missing OLE root")
            val miniFat = if (miniFatSectorCount == 0) LongArray(0) else {
                require(firstMiniFatSector < sectorCount && firstMiniFatSector != FREE_SECTOR)
                val miniFatBytes = readRegularStream(firstMiniFatSector, miniFatSectorCount * sectorSize)
                LongArray(miniFatBytes.size / 4) { index -> u32(miniFatBytes, index * 4) }
            }
            return if (workbook.size < miniStreamCutoff) {
                val miniStream = readRegularStream(root.startSector, root.size.toIntBounded())
                readMiniStream(workbook, miniStream, miniFat)
            } else {
                readRegularStream(workbook.startSector, workbook.size.toIntBounded())
            }
        }

        private fun readDifat(firstSector: Long, count: Int, expectedFatSectors: Int): List<Long> {
            val result = ArrayList<Long>(expectedFatSectors)
            for (index in 0 until 109) {
                val id = u32(bytes, 76 + index * 4)
                if (id != FREE_SECTOR && id < sectorCount) result += id
                else if (id != FREE_SECTOR && id !in setOf(END_OF_CHAIN, FAT_SECTOR, DIFAT_SECTOR)) {
                    throw IllegalArgumentException("Invalid DIFAT entry")
                }
            }
            var next = firstSector
            val seen = HashSet<Long>()
            repeat(count) {
                require(next < sectorCount && seen.add(next))
                val data = sector(next)
                for (offset in 0 until sectorSize - 4 step 4) {
                    val id = u32(data, offset)
                    if (id != FREE_SECTOR && id < sectorCount) result += id
                    else if (id != FREE_SECTOR && id !in setOf(END_OF_CHAIN, FAT_SECTOR, DIFAT_SECTOR)) {
                        throw IllegalArgumentException("Invalid DIFAT chain")
                    }
                }
                next = u32(data, sectorSize - 4)
            }
            require(count == 0 || next == END_OF_CHAIN)
            require(result.distinct().size == result.size)
            return result.take(expectedFatSectors)
        }

        private fun directoryEntries(directory: ByteArray): List<DirectoryEntry> =
            (0 until directory.size / 128).mapNotNull { index ->
                val offset = index * 128
                val type = directory[offset + 66].toInt() and 0xFF
                if (type == 0) return@mapNotNull null
                val nameLength = u16(directory, offset + 64)
                require(nameLength in 2..64 && nameLength % 2 == 0)
                val name = String(directory, offset, nameLength - 2, StandardCharsets.UTF_16LE)
                val streamSize = if (u16(bytes, 26) == 3) u32(directory, offset + 120) else u64(directory, offset + 120)
                require(streamSize <= ImportLimits.MAX_FILE_BYTES)
                DirectoryEntry(
                    name = name,
                    type = type,
                    startSector = u32(directory, offset + 116),
                    size = streamSize,
                )
            }

        private fun readRegularStream(startSector: Long, size: Int): ByteArray {
            if (size == 0) return ByteArray(0)
            require(size in 1..ImportLimits.MAX_FILE_BYTES.toInt())
            val output = ByteArrayOutputStream(size)
            val seen = HashSet<Long>()
            var current = startSector
            while (output.size() < size) {
                require(current < sectorCount && seen.add(current))
                val data = sector(current)
                output.write(data, 0, minOf(data.size, size - output.size()))
                current = fat.getOrElse(current.toInt()) { throw IllegalArgumentException("Invalid FAT chain") }
                if (output.size() < size) require(current != END_OF_CHAIN && current != FREE_SECTOR)
            }
            return output.toByteArray()
        }

        private fun readRegularChain(startSector: Long, maxBytes: Int): ByteArray {
            require(startSector < sectorCount && maxBytes > 0)
            val output = ByteArrayOutputStream()
            val seen = HashSet<Long>()
            var current = startSector
            while (current != END_OF_CHAIN) {
                require(current < sectorCount && seen.add(current))
                require(output.size().toLong() + sectorSize <= maxBytes.toLong())
                val data = sector(current)
                output.write(data)
                current = fat.getOrElse(current.toInt()) { throw IllegalArgumentException("Invalid FAT chain") }
                require(current != FREE_SECTOR)
            }
            return output.toByteArray()
        }

        private fun readMiniStream(entry: DirectoryEntry, rootStream: ByteArray, miniFat: LongArray): ByteArray {
            val size = entry.size.toIntBounded()
            if (size == 0) return ByteArray(0)
            require(miniFat.isNotEmpty() && entry.startSector < miniFat.size)
            val output = ByteArrayOutputStream(size)
            val seen = HashSet<Long>()
            var current = entry.startSector
            while (output.size() < size) {
                require(current < miniFat.size && seen.add(current))
                val offset = current.toInt() * miniSectorSize
                require(offset >= 0 && offset + miniSectorSize <= rootStream.size)
                val count = minOf(miniSectorSize, size - output.size())
                output.write(rootStream, offset, count)
                current = miniFat[current.toInt()]
                if (output.size() < size) require(current != END_OF_CHAIN && current != FREE_SECTOR)
            }
            return output.toByteArray()
        }

        private fun sector(id: Long): ByteArray {
            require(id < sectorCount)
            val start = (id + 1).toInt() * sectorSize
            require(start >= 0 && start + sectorSize <= bytes.size)
            return bytes.copyOfRange(start, start + sectorSize)
        }

        private data class DirectoryEntry(val name: String, val type: Int, val startSector: Long, val size: Long)
    }

    private class WorkbookStream(private val bytes: ByteArray) {
        fun read(): List<ImportSheet> {
            val globals = readGlobals()
            val visibleWorksheets = globals.sheets.filter { it.state == 0 && it.type == 0 }
            if (visibleWorksheets.isEmpty()) return emptyList()
            return visibleWorksheets.take(ImportLimits.MAX_SHEETS).mapIndexed { index, sheet ->
                val rows = readWorksheet(sheet.offset, globals.sharedStrings, globals.formatIds, globals.customFormats)
                val notes = buildList {
                    if (rows.columnsCut) add(TrimNotes.COLUMNS)
                    if (rows.rowsCut) add(TrimNotes.ROWS)
                    if (index == 0 && visibleWorksheets.size > ImportLimits.MAX_SHEETS) {
                        add(TrimNotes.sheets(visibleWorksheets.size))
                    }
                }
                ImportSheet(name = sheet.name.ifBlank { "Hoja ${index + 1}" }, rows = rows.rows, notes = notes)
            }
        }

        private fun readGlobals(): Globals {
            val first = recordAt(0)
            require(first.id == BOF && first.length >= 4)
            if (u16(bytes, first.payloadOffset) != 0x0600 || u16(bytes, first.payloadOffset + 2) != WORKBOOK_GLOBALS_SUBSTREAM) {
                throw ImportFileException(ImportFileReader.OLD_OFFICE_MESSAGE)
            }
            val sheets = mutableListOf<SheetRef>()
            var sharedStrings = emptyList<String>()
            val customFormats = HashMap<Int, String>()
            val formatIds = mutableListOf<Int>()
            var offset = first.nextOffset
            while (offset < bytes.size) {
                val record = recordAt(offset)
                when (record.id) {
                    FILEPASS -> throw ImportFileException(ENCRYPTED_XLS)
                    BOUNDSHEET8 -> sheets += readSheetRef(record)
                    FORMAT -> if (record.length >= 5) {
                        customFormats[u16(bytes, record.payloadOffset)] =
                            readInlineString(bytes, record.payloadOffset + 2, record.length - 2)
                    }
                    XF -> if (record.length >= 4) formatIds += u16(bytes, record.payloadOffset + 2)
                    SST -> {
                        val segments = mutableListOf(payload(record))
                        var nextOffset = record.nextOffset
                        while (nextOffset < bytes.size) {
                            val continuation = recordAt(nextOffset)
                            if (continuation.id != CONTINUE) break
                            segments += payload(continuation)
                            nextOffset = continuation.nextOffset
                        }
                        sharedStrings = SstReader(segments).read()
                        offset = nextOffset
                        continue
                    }
                    EOF -> return Globals(sheets, sharedStrings, formatIds, customFormats)
                }
                offset = record.nextOffset
            }
            throw IllegalArgumentException("Missing workbook globals EOF")
        }

        private fun readSheetRef(record: Record): SheetRef {
            require(record.length >= 8)
            val p = record.payloadOffset
            val streamOffset = u32(bytes, p).toIntBounded()
            val state = bytes[p + 4].toInt() and 0xFF
            val type = bytes[p + 5].toInt() and 0xFF
            val charCount = bytes[p + 6].toInt() and 0xFF
            val flags = bytes[p + 7].toInt() and 0xFF
            val width = if (flags and 1 != 0) 2 else 1
            require(8 + charCount * width <= record.length)
            val name = decodeChars(bytes, p + 8, charCount, width == 2)
            return SheetRef(name, streamOffset, state, type)
        }

        private fun readWorksheet(
            streamOffset: Int,
            shared: List<String>,
            formatIds: List<Int>,
            customFormats: Map<Int, String>,
        ): SheetRows {
            val bof = recordAt(streamOffset)
            require(bof.id == BOF && bof.length >= 4)
            if (u16(bytes, bof.payloadOffset + 2) != WORKSHEET_SUBSTREAM) return SheetRows(emptyList())
            val values = HashMap<Int, HashMap<Int, String>>()
            var maxRow = -1
            var maxColumn = -1
            var columnsCut = false
            var rowsCut = false
            var pendingFormulaString: Pair<Int, Int>? = null
            fun put(row: Int, column: Int, value: String) {
                if (value.isBlank()) return
                if (row >= ImportLimits.MAX_ROWS) {
                    rowsCut = true
                    return
                }
                if (column >= ImportLimits.MAX_COLUMNS) {
                    columnsCut = true
                    return
                }
                values.getOrPut(row) { HashMap() }[column] = value
                maxRow = maxOf(maxRow, row)
                maxColumn = maxOf(maxColumn, column)
            }

            var offset = bof.nextOffset
            while (offset < bytes.size) {
                val record = recordAt(offset)
                val p = record.payloadOffset
                when (record.id) {
                    EOF -> break
                    LABELSST -> if (record.length >= 10) {
                        val index = u32(bytes, p + 6).toIntBounded()
                        put(u16(bytes, p), u16(bytes, p + 2), shared.getOrNull(index).orEmpty())
                    }
                    LABEL -> if (record.length >= 9) {
                        val text = readInlineString(bytes, p + 6, record.length - 6)
                        put(u16(bytes, p), u16(bytes, p + 2), text)
                    }
                    NUMBER -> if (record.length >= 14) {
                        val style = u16(bytes, p + 4)
                        put(u16(bytes, p), u16(bytes, p + 2), formattedNumber(readDouble(bytes, p + 6), style, formatIds, customFormats))
                    }
                    RK -> if (record.length >= 10) {
                        val style = u16(bytes, p + 4)
                        put(u16(bytes, p), u16(bytes, p + 2), rkText(u32(bytes, p + 6), style, formatIds, customFormats))
                    }
                    MULRK -> if (record.length >= 12) {
                        val row = u16(bytes, p)
                        val firstColumn = u16(bytes, p + 2)
                        val lastColumn = u16(bytes, p + record.length - 2)
                        val entriesBytes = record.length - 6
                        require(entriesBytes % 6 == 0)
                        val count = entriesBytes / 6
                        require(count > 0 && lastColumn == firstColumn + count - 1)
                        repeat(count) { index ->
                            val entryOffset = p + 4 + index * 6
                            val style = u16(bytes, entryOffset)
                            put(row, firstColumn + index, rkText(u32(bytes, entryOffset + 2), style, formatIds, customFormats))
                        }
                    }
                    BOOLERR -> if (record.length >= 8) {
                        val value = bytes[p + 6].toInt() and 0xFF
                        val isError = bytes[p + 7].toInt() and 0xFF != 0
                        if (!isError) put(u16(bytes, p), u16(bytes, p + 2), if (value == 0) "FALSE" else "TRUE")
                    }
                    FORMULA -> if (record.length >= 14) {
                        val row = u16(bytes, p)
                        val column = u16(bytes, p + 2)
                        val style = u16(bytes, p + 4)
                        val resultType = bytes[p + 6].toInt() and 0xFF
                        val special = u16(bytes, p + 12) == 0xFFFF
                        if (!special) {
                            put(row, column, formattedNumber(readDouble(bytes, p + 6), style, formatIds, customFormats))
                            pendingFormulaString = null
                        } else when (resultType) {
                            0 -> pendingFormulaString = row to column
                            1 -> put(row, column, if (bytes[p + 8].toInt() == 0) "FALSE" else "TRUE")
                            else -> pendingFormulaString = null
                        }
                    }
                    STRING -> pendingFormulaString?.let { (row, column) ->
                        put(row, column, readInlineString(bytes, p, record.length))
                        pendingFormulaString = null
                    }
                }
                offset = record.nextOffset
            }
            val rows = if (maxRow < 0) emptyList() else (0..maxRow).map { row ->
                val cells = values[row]
                List(maxColumn + 1) { column -> cells?.get(column).orEmpty() }
            }
            return SheetRows(rows, columnsCut, rowsCut)
        }

        private fun readInlineString(source: ByteArray, offset: Int, available: Int): String {
            require(available >= 3)
            val length = u16(source, offset)
            val flags = source[offset + 2].toInt() and 0xFF
            val width = if (flags and 1 != 0) 2 else 1
            require(3 + length * width <= available)
            return decodeChars(source, offset + 3, length, width == 2)
        }

        private fun recordAt(offset: Int): Record {
            require(offset >= 0 && offset + 4 <= bytes.size)
            val id = u16(bytes, offset)
            val length = u16(bytes, offset + 2)
            val payloadOffset = offset + 4
            require(payloadOffset + length <= bytes.size)
            return Record(id, payloadOffset, length, payloadOffset + length)
        }

        private fun payload(record: Record): ByteArray =
            bytes.copyOfRange(record.payloadOffset, record.payloadOffset + record.length)

        private data class Record(val id: Int, val payloadOffset: Int, val length: Int, val nextOffset: Int)
        private data class SheetRef(val name: String, val offset: Int, val state: Int, val type: Int)
        private data class Globals(
            val sheets: List<SheetRef>,
            val sharedStrings: List<String>,
            val formatIds: List<Int>,
            val customFormats: Map<Int, String>,
        )
        private data class SheetRows(val rows: List<List<String>>, val columnsCut: Boolean = false, val rowsCut: Boolean = false)
    }

    /** Decodifica la tabla compartida incluso cuando BIFF8 la continúa en varios registros CONTINUE. */
    private class SstReader(private val segments: List<ByteArray>) {
        private var segmentIndex = 0
        private var position = 0

        fun read(): List<String> {
            val totalReferences = readU32()
            val uniqueCount = readU32()
            require(totalReferences <= MAX_SHARED_REFERENCES)
            if (uniqueCount > MAX_SHARED_STRINGS) throw ImportFileException(TOO_MANY_STRINGS)
            val strings = ArrayList<String>(uniqueCount.toInt())
            repeat(uniqueCount.toInt()) {
                val charCount = readU16()
                val flags = readByte()
                val richRunCount = if (flags and 0x08 != 0) readU16() else 0
                val extensionLength = if (flags and 0x04 != 0) readU32().toIntBounded() else 0
                val text = readCharacters(charCount, flags and 1 != 0)
                skip(richRunCount * 4 + extensionLength)
                strings += text
            }
            return strings
        }

        private fun readCharacters(count: Int, initialWide: Boolean): String {
            val output = StringBuilder(count)
            var remaining = count
            var wide = initialWide
            while (remaining > 0) {
                if (!hasBytes()) {
                    require(advanceSegment())
                    wide = readByte() and 1 != 0
                }
                val segment = segments[segmentIndex]
                val available = segment.size - position
                val width = if (wide) 2 else 1
                val completeChars = available / width
                require(completeChars > 0)
                val copiedChars = minOf(remaining, completeChars)
                output.append(decodeChars(segment, position, copiedChars, wide))
                position += copiedChars * width
                remaining -= copiedChars
                if (remaining > 0) {
                    require(advanceSegment())
                    wide = readByte() and 1 != 0
                }
            }
            return output.toString()
        }

        private fun skip(count: Int) {
            require(count >= 0)
            var remaining = count
            while (remaining > 0) {
                if (!hasBytes()) require(advanceSegment())
                val amount = minOf(remaining, segments[segmentIndex].size - position)
                position += amount
                remaining -= amount
            }
        }

        private fun readByte(): Int {
            if (!hasBytes()) require(advanceSegment())
            return segments[segmentIndex][position++].toInt() and 0xFF
        }

        private fun readU16(): Int = readByte() or (readByte() shl 8)

        private fun readU32(): Long = readByte().toLong() or
            (readByte().toLong() shl 8) or
            (readByte().toLong() shl 16) or
            (readByte().toLong() shl 24)

        private fun hasBytes(): Boolean = segmentIndex < segments.size && position < segments[segmentIndex].size

        private fun advanceSegment(): Boolean {
            if (segmentIndex + 1 >= segments.size) return false
            segmentIndex++
            position = 0
            return true
        }
    }

    private enum class NumberKind { PLAIN, PERCENT, TIME }

    private val quotedFormat = Regex("\"[^\"]*\"")
    private val escapedFormat = Regex("\\\\.")
    private val bracketFormat = Regex("\\[[^\\]]*]")
    private val elapsedFormat = Regex("\\[(?:h+|m+|s+)]")

    private fun rkText(value: Long, style: Int, formatIds: List<Int>, customFormats: Map<Int, String>): String {
        val bits = value.toInt()
        val decoded = if (bits and 0x02 != 0) {
            (bits shr 2).toDouble()
        } else {
            val ieeeBits = (value and 0xFFFF_FFFCL) shl 32
            java.lang.Double.longBitsToDouble(ieeeBits)
        }
        return formattedNumber(if (bits and 1 != 0) decoded / 100.0 else decoded, style, formatIds, customFormats)
    }

    private fun formattedNumber(number: Double, style: Int, formatIds: List<Int>, customFormats: Map<Int, String>): String {
        if (!number.isFinite()) return ""
        val formatId = formatIds.getOrElse(style) { 0 }
        val kind = customFormats[formatId]?.let(::kindOfCode) ?: when {
            formatId == 9 || formatId == 10 -> NumberKind.PERCENT
            formatId in 18..21 || formatId in 45..47 -> NumberKind.TIME
            else -> NumberKind.PLAIN
        }
        return numberText(BigDecimal.valueOf(number).stripTrailingZeros().toPlainString(), kind)
    }

    private fun kindOfCode(code: String): NumberKind {
        val unquoted = code.replace(quotedFormat, "").replace(escapedFormat, "").lowercase()
        val elapsed = bracketFormat.findAll(unquoted).any { elapsedFormat.matches(it.value) }
        val plain = unquoted.replace(bracketFormat, "")
        return when {
            plain.contains('%') -> NumberKind.PERCENT
            plain.contains('y') || plain.contains('d') -> NumberKind.PLAIN
            elapsed || plain.contains('h') || plain.contains('s') -> NumberKind.TIME
            else -> NumberKind.PLAIN
        }
    }

    private fun numberText(raw: String, kind: NumberKind): String = when (kind) {
        NumberKind.PERCENT -> raw.toBigDecimalOrNull()
            ?.movePointRight(2)
            ?.round(MathContext(15))
            ?.stripTrailingZeros()
            ?.toPlainString()
            ?.plus("%") ?: raw
        NumberKind.TIME -> clockText(raw) ?: raw
        NumberKind.PLAIN -> raw
    }

    private fun clockText(raw: String): String? {
        val days = raw.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 } ?: return null
        val seconds = Math.round(days * 86_400.0)
        val remainder = (seconds % 60).toString().padStart(2, '0')
        return if (seconds < 3_600) {
            "${seconds / 60}:$remainder"
        } else {
            "${seconds / 3_600}:${(seconds / 60 % 60).toString().padStart(2, '0')}:$remainder"
        }
    }

    private fun readDouble(bytes: ByteArray, offset: Int): Double {
        require(offset >= 0 && offset + 8 <= bytes.size)
        var bits = 0L
        for (index in 0 until 8) bits = bits or ((bytes[offset + index].toLong() and 0xFFL) shl (index * 8))
        return java.lang.Double.longBitsToDouble(bits)
    }

    private fun decodeChars(bytes: ByteArray, offset: Int, count: Int, wide: Boolean): String {
        val length = count * if (wide) 2 else 1
        require(offset >= 0 && length >= 0 && offset + length <= bytes.size)
        return String(bytes, offset, length, if (wide) StandardCharsets.UTF_16LE else StandardCharsets.ISO_8859_1)
    }

    private fun u16(bytes: ByteArray, offset: Int): Int {
        require(offset >= 0 && offset + 2 <= bytes.size)
        return (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun u32(bytes: ByteArray, offset: Int): Long {
        require(offset >= 0 && offset + 4 <= bytes.size)
        return (bytes[offset].toLong() and 0xFFL) or
            ((bytes[offset + 1].toLong() and 0xFFL) shl 8) or
            ((bytes[offset + 2].toLong() and 0xFFL) shl 16) or
            ((bytes[offset + 3].toLong() and 0xFFL) shl 24)
    }

    private fun u64(bytes: ByteArray, offset: Int): Long {
        val low = u32(bytes, offset)
        val high = u32(bytes, offset + 4)
        require(high == 0L)
        return low
    }

    private fun Long.boundedInt(max: Int): Int {
        require(this in 0..max.toLong())
        return toInt()
    }

    private fun Long.toIntBounded(): Int {
        require(this in 0..ImportLimits.MAX_FILE_BYTES)
        return toInt()
    }
}
