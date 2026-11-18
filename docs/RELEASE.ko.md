# 정식 서명 APK 빌드

## 서명 키

- 서명 키와 비밀번호는 저장소에 두지 않는다. 빌드할 때 환경 변수로 넘긴다.
- 배포판 인증서 SHA-256: `A1:76:D9:17:96:52:C1:33:FF:9E:4F:75:77:6A:2D:D9:07:83:27:7E:77:C7:46:CC:35:18:15:C3:90:4E:F5:DC`
  내려받은 APK가 배포판인지 확인할 때 이 값과 비교한다.
- 같은 키로 서명한 APK만 기존 설치 위에 덮어 깔린다. 공개 전 시험판(debug 서명)과 직접 빌드한 APK는 서명이 달라, 기존 앱을 지운 뒤 설치해야 한다.

## 빌드

1. `app/build.gradle.kts`의 `versionCode`(이전보다 크게)와 `versionName`을 올린다.
2. 환경 변수 세 개를 준다. 없으면 release는 서명 없이 빌드된다.

   | 변수 | 값 |
   | --- | --- |
   | `RELEASE_STORE_FILE` | 키 저장소(PKCS12) 파일 경로. 저장소 밖에 둔다. |
   | `RELEASE_STORE_PASSWORD` | 키 저장소 비밀번호(키 비밀번호와 같다) |
   | `RELEASE_KEY_ALIAS` | 키 별칭 |

3. `./gradlew testDebugUnitTest assembleRelease`
4. 서명과 버전을 확인한다.

   ```shell
   apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
   aapt2 dump badging app/build/outputs/apk/release/app-release.apk
   sha256sum app/build/outputs/apk/release/app-release.apk
   ```

5. `CHANGELOG.md`에 바뀐 점을 적는다. 배포할 때 APK의 SHA-256을 함께 안내한다.
