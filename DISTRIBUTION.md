# DISTRIBUTION.md — 배포 절차

> 제작자: 이은호 (serotonin.1207@gmail.com)
> 앱: Serotonin 범용 OBD (패키지 `com.eunho.leafobd`)

새 버전을 내고 지인들에게 배포하는 전체 순서다.

---

## 준비 (최초 1회)

### 1. 서명 키스토어 만들기

프로젝트 폴더에서 실행한다. 비밀번호는 직접 정한다.

```bash
"C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -v -keystore serotonin-obd.jks -alias serotonin -keyalg RSA -keysize 2048 -validity 10000
```

`keystore.properties.example` 을 복사해 `keystore.properties` 로 만들고 값을 채운다.
`keystore.properties` 와 `.jks` 는 `.gitignore` 에 있어 GitHub 에 올라가지 않는다.

> ⚠️ **키스토어와 비밀번호를 잃어버리면 같은 앱으로 업데이트할 수 없다.** 반드시 백업한다.

### 2. GitHub 저장소 만들기

1. GitHub 에서 새 저장소 생성 (예: `serotonin-1207/serotonin-obd`)
2. 저장소 이름을 정하면 `app/src/main/java/com/eunho/leafobd/data/AppInfo.kt` 의
   `VERSION_JSON_URL`, `REPO_URL` 을 실제 주소로 바꾼다.
3. 코드 업로드:

```bash
cd "C:\python\leaf EV 프로젝트"
git init
git add .
git commit -m "첫 공개 버전"
git branch -M main
git remote add origin https://github.com/사용자명/저장소명.git
git push -u origin main
```

---

## 새 버전 낼 때마다 (반복)

### 1. 버전 올리기

`app/build.gradle.kts` 에서 `versionCode` 를 1 올리고 `versionName` 을 바꾼다.

```kotlin
versionCode = 3       // 이전보다 1 크게
versionName = "1.2"
```

### 2. 빌드와 검증

```bash
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleRelease
```

release APK: `C:\python\leafobd-build\app\outputs\apk\release\app-release.apk`

### 3. GitHub Releases 에 APK 올리기

1. GitHub 저장소 → **Releases** → **Draft a new release**
2. 태그: `v1.2` (versionName 과 맞춤)
3. `app-release.apk` 를 첨부
4. Publish

### 4. version.json 갱신

저장소 루트의 `version.json` 을 새 버전에 맞게 고치고 push 한다.

```json
{
  "versionCode": 3,
  "versionName": "1.2",
  "notes": "무엇이 바뀌었는지 한두 줄",
  "downloadUrl": "https://github.com/사용자명/저장소명/releases/latest"
}
```

이 파일이 갱신되면, 앱을 켠 지인들에게 자동으로 **"새 버전이 있습니다"** 안내가 뜬다.
안내를 누르면 브라우저로 다운로드 페이지가 열린다. (앱이 직접 설치하지는 않는다)

### 5. 구글 드라이브에도 올리기 (선택)

GitHub 가 익숙하지 않은 지인을 위해 `app-release.apk` 를 구글 드라이브에도 올리고
공유 링크를 카카오톡으로 보낼 수 있다. 이때 링크 권한을 **"링크가 있는 모든 사용자"** 로 둔다.

---

## 지인에게 전달할 것

- `app-release.apk` (또는 GitHub/드라이브 링크)
- [INSTALL.md](INSTALL.md) — 설치·사용 안내문. 그대로 전달하면 된다.

## 업데이트 확인이 어떻게 동작하나

- 앱은 켜질 때 `version.json` 의 `versionCode` 하나만 읽는다.
- 그 값이 설치된 버전보다 크면 홈 화면에 안내가 뜬다.
- **진단 데이터(오류코드·로그·VIN)는 어디로도 전송되지 않는다.**
- 설정 → 업데이트 → "새 버전 확인" 을 끄면 인터넷을 전혀 쓰지 않는다.
