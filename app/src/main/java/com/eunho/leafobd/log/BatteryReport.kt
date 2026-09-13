package com.eunho.leafobd.log

import com.eunho.leafobd.data.BatterySnapshot
import java.util.Locale

data class BatteryReport(val summary: String, val demo: Boolean, val charts: List<BatteryReportChart>) {
    companion object {
        fun from(data: BatterySnapshot): BatteryReport {
            require(data.expectedChannels in 1..512 && data.channels.size <= data.expectedChannels)
            require(data.channels.map { it.id }.distinct().size == data.channels.size && data.channels.all { it.id in 1..data.expectedChannels })
            require(data.temperatureSensors.size <= 512)
            fun number(value: Double?, min: Double, max: Double) = value?.takeIf { it.isFinite() && it in min..max }
            fun format(value: Double?, digits: Int = 2) = value?.let { String.format(Locale.ROOT, "%.${digits}f", it) } ?: "미확인"
            val byId = data.channels.associateBy { it.id }
            val voltages = (1..data.expectedChannels).map { it to number(byId[it]?.volts, 0.0, 6.0) }
            val values = voltages.mapNotNull { it.second }
            val spread = if (values.size == data.expectedChannels) (values.max() - values.min()) * 1000 else null
            val charts = mutableListOf<BatteryReportChart>()
            voltages.chunked(64).forEach { charts.add(BatteryReportChart("셀 전압 채널", "V", it)) }
            if (data.temperatureSensors.isNotEmpty()) data.temperatureSensors.mapIndexed { i, sensor -> (i + 1) to number(sensor.second, -80.0, 150.0) }
                .chunked(32).forEach { charts.add(BatteryReportChart("별도 온도 센서 · 수신 순서", "°C", it)) }
            val channelTemperatures = data.channels.filter { it.celsius != null }.sortedBy { it.id }.map { it.id to number(it.celsius, -80.0, 150.0) }
            channelTemperatures.chunked(32).forEach { charts.add(BatteryReportChart("채널에 기록된 온도 · 별도 센서와 구분", "°C", it)) }
            val summary = buildString {
                appendLine("배터리 측정 보고서")
                appendLine(if (data.demo) "가상 예시 · 실제 차량 측정 아님" else "앱에서 열린 단일 시점 기록 · 측정 정확성 미인증")
                appendLine("측정 시각: ${runCatching { java.time.OffsetDateTime.parse(data.capturedAt).toString() }.getOrDefault("미확인")}")
                appendLine("차량명·VIN·장치 주소·출처 자유 입력·센서 이름은 포함하지 않았습니다.")
                appendLine("대상 차량과 원본 출처는 정비소에서 별도로 확인하세요. 다른 진단이나 배터리 기록을 합치지 않았습니다.")
                appendLine("\nSOC 충전 잔량: ${format(number(data.soc, 0.0, 100.0), 1)} %")
                appendLine("현재 남아 있는 충전량의 비율입니다. 배터리 열화 정도를 뜻하지 않습니다.")
                appendLine("SOH BMS 보고 건강도: ${format(number(data.reportedSoh, 0.0, 100.0))} %")
                appendLine("BMS는 배터리 관리 장치입니다. SOH는 내부 추정값이며 별도 용량 시험 결과가 아닙니다.")
                appendLine("\n전압 수신: ${values.size}/${data.expectedChannels}채널")
                appendLine("수신값 최저/최고: ${format(values.minOrNull(), 3)} / ${format(values.maxOrNull(), 3)} V")
                appendLine("전체 전압 편차: ${format(spread, 1)} mV")
                appendLine("편차는 같은 시각의 최고·최저 차이입니다. 1,000 mV = 1 V. 채널 누락 시 전체 편차를 계산하지 않습니다.")
                appendLine("\n온도: 별도 센서 ${data.temperatureSensors.size}개 / 채널 온도 ${channelTemperatures.size}개")
                if (data.temperatureSensors.isEmpty() && channelTemperatures.isEmpty()) appendLine("온도 기록 없음 · 정상 온도로 간주하지 않습니다.")
                appendLine("센서 번호는 수신 순서이며 실제 배터리 내부 위치를 뜻하지 않습니다.")
                appendLine("\n측정 조건 확인: 정차·충전 여부 / 직전 주행 / 외기온 / 경고등과 증상")
                appendLine("위 조건은 이 기록에서 검증할 수 없습니다. 그래프의 차이만으로 열화나 부품 교체를 판단하지 마세요.")
                appendLine("그래프는 수신 범위에 맞춘 확대 눈금입니다. 페이지마다 눈금이 다를 수 있습니다.")
                appendLine("이 보고서는 오류코드 조회·삭제 결과와 운행 가능 판정을 포함하지 않습니다.")
            }
            return BatteryReport(summary, data.demo, charts)
        }
    }
}

data class BatteryReportChart(val title: String, val unit: String, val points: List<Pair<Int, Double?>>)
