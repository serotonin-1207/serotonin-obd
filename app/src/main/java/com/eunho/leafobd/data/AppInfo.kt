package com.eunho.leafobd.data

/** 제작자·배포 관련 고정 정보. */
object AppInfo {

    const val APP_NAME = "Serotonin 범용 OBD"
    const val AUTHOR = "이은호"
    const val AUTHOR_EMAIL = "serotonin.1207@gmail.com"

    /**
     * 업데이트 정보 파일 위치.
     *
     * GitHub 저장소의 `version.json` 을 raw 로 가리킨다.
     * 배포 저장소를 만든 뒤 아래 URL 의 `사용자명/저장소명` 을 실제 값으로 바꾼다.
     *
     * 형식(version.json):
     * ```json
     * {
     *   "versionCode": 3,
     *   "versionName": "1.2",
     *   "notes": "무엇이 바뀌었는지 한두 줄",
     *   "downloadUrl": "https://.../app-release.apk"
     * }
     * ```
     */
    const val VERSION_JSON_URL =
        "https://raw.githubusercontent.com/serotonin-1207/serotonin-obd/main/version.json"

    /** 사용자가 수동으로 갱신할 때만 읽는 서명된 공개 오류코드 데이터 팩. */
    const val KNOWLEDGE_PACK_MANIFEST_URL =
        "https://raw.githubusercontent.com/serotonin-1207/serotonin-obd/main/knowledge-pack-manifest.json"
    const val KNOWLEDGE_PACK_URL =
        "https://raw.githubusercontent.com/serotonin-1207/serotonin-obd/main/public-data/diagnostic_knowledge_pack.json"

    /** 공개 검증 키(X.509 DER, P-256). 개인 서명 키는 저장소 밖에 보관한다. */
    const val KNOWLEDGE_PACK_PUBLIC_KEY_BASE64 =
        "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE8R/BZbxC6qfBWVJTwi8kULMtnVV4sjsWyUXTWBFXso6aPrHRyXy8iHu4i9hTI3NEx2hTE9E4gim6X2aXnwx3Iw=="

    /** 소스 코드 저장소. 앱 정보 화면에서 안내한다. */
    const val REPO_URL = "https://github.com/serotonin-1207/serotonin-obd"
}
