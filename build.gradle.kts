// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

/*
 * 한글 경로 대응.
 *
 * 이 프로젝트는 "C:\python\leaf EV 프로젝트" 처럼 한글과 공백이 든 경로에 있다.
 * `assembleDebug` 는 문제없이 동작하지만, Gradle 이 단위 테스트를 실행할 때 쓰는
 * 워커 프로세스가 비ASCII 경로가 섞인 클래스패스를 해석하지 못해
 * 모든 테스트 클래스가 ClassNotFoundException 으로 실패한다.
 *
 * 테스트 클래스패스 항목은 전부 `build/` 폴더와 Gradle 캐시 안에 있으므로,
 * **빌드 산출물 폴더만 ASCII 경로로 옮기면** 소스는 그대로 두고 문제를 없앨 수 있다.
 *
 * 경로가 이미 ASCII 라면 아무것도 바꾸지 않는다(기본 `build/` 폴더를 그대로 쓴다).
 */
val projectPathIsAscii: Boolean = rootDir.absolutePath.all { it.code in 0x20..0x7E }

if (!projectPathIsAscii) {
    // 프로젝트 바로 옆(예: C:\python\leafobd-build)에 둔다.
    // 상위 폴더까지 한글이면 어쩔 수 없이 임시 폴더를 쓴다.
    val parent = rootDir.parentFile
    val parentIsAscii = parent != null && parent.absolutePath.all { it.code in 0x20..0x7E }
    val asciiBuildRoot =
        if (parentIsAscii) File(parent, "leafobd-build")
        else File(System.getProperty("java.io.tmpdir"), "leafobd-build")
    logger.lifecycle("한글 경로가 감지되어 빌드 출력 폴더를 옮깁니다: $asciiBuildRoot")
    allprojects {
        // ":app" -> "app", ":" -> "root"
        val safeName = path.trim(':').replace(':', '-').ifEmpty { "root" }
        layout.buildDirectory.set(File(asciiBuildRoot, safeName))
    }
}
