package com.eunho.leafobd.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothSocket
import com.eunho.leafobd.elm327.Elm327Transport
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * 이미 연결된 [BluetoothSocket] 위에서 동작하는 통로.
 *
 * 소켓 연결 자체는 [BluetoothClassicManager] 가 담당한다.
 * 이 클래스는 연결된 소켓의 스트림만 다룬다.
 *
 * 읽기는 반드시 논블로킹이어야 하므로 `InputStream.available()` 로
 * 지금 읽을 수 있는 양만 읽는다. `read()` 를 그냥 호출하면 데이터가 올 때까지
 * 스레드가 막혀 코루틴 취소와 타임아웃이 동작하지 않는다.
 */
class BluetoothElm327Transport(
    private val socket: BluetoothSocket,
    deviceName: String
) : Elm327Transport {

    override val description: String = "Bluetooth: $deviceName"
    override val simulated: Boolean = false

    private var input: InputStream? = null
    private var output: OutputStream? = null

    @Volatile
    private var closed = false

    override val isOpen: Boolean
        // 호출 전 BluetoothPermissions.hasAll() 로 권한을 확인한 뒤에만 이 통로가 만들어진다.
        @SuppressLint("MissingPermission")
        get() = !closed && socket.isConnected && input != null

    override fun open() {
        if (input != null && output != null) return
        input = socket.inputStream
        output = socket.outputStream
        closed = false
    }

    override fun write(data: ByteArray) {
        val stream = output ?: throw IOException("어댑터 출력 스트림이 준비되지 않았습니다.")
        stream.write(data)
        stream.flush()
    }

    override fun read(buffer: ByteArray): Int {
        if (closed) return -1
        val stream = input ?: return -1

        val available = stream.available()
        if (available <= 0) return 0

        return stream.read(buffer, 0, minOf(available, buffer.size))
    }

    /** 스트림과 소켓을 모두 닫는다. 어떤 예외도 밖으로 내보내지 않는다. */
    override fun close() {
        closed = true
        runCatching { input?.close() }
        runCatching { output?.close() }
        runCatching { socket.close() }
        input = null
        output = null
    }
}
