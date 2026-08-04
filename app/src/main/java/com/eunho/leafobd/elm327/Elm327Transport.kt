package com.eunho.leafobd.elm327

/**
 * ELM327 어댑터와 바이트를 주고받는 통로.
 *
 * 이 인터페이스 덕분에 상위 계층(Elm327Client, ObdService, ViewModel)은
 * 실제 Bluetooth 소켓인지 테스트용 모의 장치인지 알 필요가 없다.
 *
 * 구현체는 [BluetoothElm327Transport] 와 [FakeElm327Transport] 두 가지다.
 *
 * 모든 함수는 호출자가 `Dispatchers.IO` 에서 실행한다고 가정한다.
 * 메인 스레드에서 직접 호출하지 않는다.
 */
interface Elm327Transport {

    /** 화면과 로그에 표시할 설명 (예: "Bluetooth: V-LINK", "모의 어댑터"). */
    val description: String

    /** 모의 데이터 여부. UI에 "모의 데이터" 배지를 띄우는 데 쓴다. */
    val simulated: Boolean

    /** 연결되어 있으면 true. */
    val isOpen: Boolean

    /** 통로를 연다. 이미 열려 있으면 아무것도 하지 않는다. */
    @Throws(Exception::class)
    fun open()

    /** 바이트를 보낸다. */
    @Throws(Exception::class)
    fun write(data: ByteArray)

    /**
     * 최대 [buffer] 크기만큼 읽는다.
     *
     * 구현체는 **블로킹하지 않아야 한다.** 지금 당장 읽을 데이터가 없으면 0을 돌려준다.
     * 블로킹 읽기를 쓰면 코루틴 타임아웃으로 취소할 수 없어 앱이 멈출 수 있기 때문이다.
     * 대기는 [Elm327Client] 가 취소 가능한 `delay` 로 처리한다.
     *
     * @return 읽은 바이트 수. 지금 읽을 것이 없으면 0. 통로가 닫혔으면 -1.
     */
    @Throws(Exception::class)
    fun read(buffer: ByteArray): Int

    /** 통로를 닫는다. 예외를 밖으로 던지지 않는다. */
    fun close()
}
