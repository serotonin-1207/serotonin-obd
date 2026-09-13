package com.eunho.leafobd.log

import com.eunho.leafobd.data.DiagnosticKnowledge

/** Only exact, validated code keys and bundled editorial content may enter exported notes. */
object CodeReportNotes {
    fun create(codes: List<String>): String = buildString {
        appendLine("\n코드 해설·서비스센터 확인 항목")
        appendLine("보고서 생성 시 앱 해설 ${DiagnosticKnowledge.VERSION} 기준 · 측정 당시의 제조사 판정이 아닙니다.")
        appendLine("차량별 적용 미검증 · 아래 설명은 수신 코드와 문자열이 일치하는 참고 자료입니다.")
        val keys = codes.map(DiagnosticKnowledge::normalize).filter(DiagnosticKnowledge::validCode).distinct()
        if (keys.isEmpty()) appendLine("해설을 연결할 수신 코드 없음 · 정상 판정 아님")
        keys.forEach { code ->
            val entry = DiagnosticKnowledge.entries.firstOrNull { it.code == code }
            appendLine("\n$code")
            if (entry == null) {
                appendLine("등록된 해설 근거 미확보 · 서비스센터에 전체 코드·ECU·상태 기준 해석을 요청하세요.")
            } else {
                appendLine(entry.title)
                appendLine("코드 범위: ${entry.codeScope.label}")
                appendLine("대응 단계: ${entry.urgency.label}")
                appendLine("운행 안내: ${entry.drivingAdvice}")
                appendLine(entry.explanation)
                appendLine("검증 상태: ${entry.verification}")
                appendLine("적용 범위: ${entry.sourceScope}")
                appendLine("확인 항목: ${entry.evidence}")
                entry.nextChecks.forEachIndexed { index, check -> appendLine("${index + 1}. $check") }
                if (entry.ownerChecks.isNotEmpty()) appendLine("사용자 확인: ${entry.ownerChecks.joinToString(" · ")}")
                if (entry.technicianHandoff.isNotEmpty()) appendLine("정비사 전달: ${entry.technicianHandoff.joinToString(" · ")}")
                appendLine("삭제 전 보존: ${entry.beforeClear.joinToString(" · ")}")
                if (entry.limitations.isNotBlank()) appendLine(entry.limitations)
                appendLine("근거 대조일: ${entry.reviewedAt}")
                if (entry.sourceUrl.isNotBlank()) appendLine("출처: ${entry.sourceUrl}")
            }
        }
    }
}
