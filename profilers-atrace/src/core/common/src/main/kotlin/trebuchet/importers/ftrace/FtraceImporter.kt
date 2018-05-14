/*
 * Copyright 2017 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package trebuchet.importers.ftrace

import trebuchet.importers.ImportFeedback
import trebuchet.importers.Importer
import trebuchet.importers.ImporterFactory
import trebuchet.io.DataSlice
import trebuchet.io.GenericByteBuffer
import trebuchet.io.StreamingLineReader
import trebuchet.io.StreamingReader
import trebuchet.model.fragments.ModelFragment
import trebuchet.util.contains

class FtraceImporter(val feedback: ImportFeedback) : Importer {
    var foundHeader = false
    val state = FtraceImporterState(feedback)
    val parser = FtraceLine.Parser(state.stringCache)

    // Create captured lambads here to avoid extra kotlin-generated overhead
    private val lineReaderCallback: (DataSlice) -> Unit = this::handleLine
    private val ftraceParserCallback: (FtraceLine) -> Unit = state::importLine

    override fun import(stream: StreamingReader): ModelFragment? {
        val lineReader = StreamingLineReader(1024, stream)
        foundHeader = false
        lineReader.forEachLine(lineReaderCallback)
        return state.finish()
    }

    fun handleLine(line: DataSlice) {
        if (line[0] == '#'.toByte()) {
            foundHeader = true
        } else if (foundHeader) {
            try {
                parser.parseLine(line, ftraceParserCallback)
            } catch (ex: Exception) {
                if (line.toString().isNotBlank()) {
                    feedback.reportImportWarning("Failed to parse: '${line.toString()}'")
                    feedback.reportImportException(ex)
                }
            }
        }
    }

    object Factory : ImporterFactory {
        override fun importerFor(buffer: GenericByteBuffer, feedback: ImportFeedback): Importer? {
            if (buffer.contains("# tracer: nop\n", 1000)) {
                return FtraceImporter(feedback)
            }
            return null
        }
    }
}