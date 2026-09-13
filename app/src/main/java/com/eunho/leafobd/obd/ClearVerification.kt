package com.eunho.leafobd.obd

object ClearVerification {
    fun identity(code: DtcCode) = Triple(code.ecu, code.code, code.status)
    fun complete(reads: List<DtcReadOutcome>, before: List<DtcCode> = emptyList()): Boolean =
        reads.map { it.mode }.toSet() == ObdMode.entries.toSet() && reads.all {
            it.log.success && it.result.status == ObdResponseStatus.OK && !it.result.truncated
        } && before.all { code ->
            code.ecu == null || reads.filter { it.mode.command == code.source.command }.any { read ->
                ObdFrameParser.parse(com.eunho.leafobd.util.ResponseText.dataLines(read.log.rawResponse, read.mode.command), true)
                    .any { response -> response.ecuId == code.ecu && response.complete }
            }
        }
}
