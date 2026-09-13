# 공개 오류코드 데이터 팩 배포

앱은 GitHub의 `knowledge-pack-manifest.json`과 `public-data/diagnostic_knowledge_pack.json`만 내려받습니다. 진단 기록, VIN, Bluetooth 주소 또는 차량 프로필은 요청에 포함하지 않습니다.

## 서명 키

현재 공개키와 짝을 이루는 개인키는 저장소 밖의 다음 파일입니다.

`C:\python\leafobd-build\knowledge-pack-signing-private.pk8`

공개키 파일은 같은 경로의 `.pub` 파일입니다. 개인키가 없으면 기존 앱이 받아들일 수 있는 새 데이터 팩을 만들 수 없으므로 암호화된 별도 매체에 백업해야 합니다. 개인키와 `keystore.properties`, `*.jks`는 Git에 올리지 않습니다.

## 배포 파일 생성

1. `app/src/main/assets/diagnostic_knowledge_pack.json`을 수정하고 데이터 검사를 통과시킵니다.
2. 이전 값보다 큰 리비전과 필요한 최소 앱 `versionCode`를 정합니다.
3. 프로젝트 루트에서 다음 명령을 실행합니다. 아래 예시의 리비전은 `3`, 최소 앱 버전은 `2`입니다.

```powershell
New-Item -ItemType Directory -Force -Path 'tools\build' | Out-Null
& 'C:\Program Files\Android\Android Studio\jbr\bin\javac.exe' -d 'tools\build' 'tools\SignKnowledgePack.java'
& 'C:\Program Files\Android\Android Studio\jbr\bin\java.exe' -cp 'tools\build' SignKnowledgePack 'app\src\main\assets\diagnostic_knowledge_pack.json' 'public-data\diagnostic_knowledge_pack.json' 'knowledge-pack-manifest.json' 'app\src\main\assets\diagnostic_knowledge_pack_manifest.json' 'C:\python\leafobd-build\knowledge-pack-signing-private.pk8' '3' '2'
```

4. 단위 테스트와 Android 기기 테스트를 실행합니다.
5. JSON 데이터 팩, 두 매니페스트, 변경 기록을 같은 커밋으로 게시합니다.

서명은 리비전, 팩 버전, SHA-256과 데이터 팩 원본 바이트를 함께 보호합니다. 앱은 고정 GitHub 주소, 최대 크기, 최소 앱 버전, 서명, 해시, JSON 스키마, 출처 원장을 모두 확인합니다. 어느 하나라도 실패하면 설치된 팩을 건드리지 않습니다.
