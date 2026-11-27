# 🔐 카톡 한눈에 고정 서명 설정 (최초 1회)

## 목적
이 앱은 이제 고정 ID `com.kyubang.kakaofocus.stable`을 사용합니다.
앞으로는 새 버전마다 `versionCode`만 **더 큰 숫자**로 올리고
**항상 동일한 서명키**로 만든 APK를 설치하면 기존 앱 위에 업데이트됩니다.

## 기존 앱과 차이
카톡 한눈에 9까지는 GitHub Actions가 매번 임시 디버그 키로 서명했습니다.
그 디버그 키는 보관되지 않았기 때문에 이미 설치된 9버전에
새 서명키의 APK를 **직접 업데이트 설치할 수 없습니다**.
'카톡 한눈에 정식'을 **한 번만 새로 설치**해야 합니다.
9버전의 '위젯 꾸미기 → 설정 백업'에서 ZIP을 저장한 뒤
새 정식 앱에서 복원하면 톡방 이름·테마·차단 시간·위젯 사진을 옮길 수 있습니다.
시스템 권한은 다시 허용해야 합니다.

## 보안 및 보관: 매우 중요
ChatGPT에서 따로 전달한 두 **개인 전용 파일**을 소중히 보관하세요.
- `KakaoFocus_StableSigningKey_PRIVATE.p12`: 실제 개인 서명키가 들어 있는 PKCS#12 키 저장소
- `KakaoFocus_GitHubSecret_PRIVATE.txt`: 비밀번호와 암호화되지 않은 Base64 인코딩 키를 담은 JSON 한 줄

이 두 파일은 **공개 GitHub 저장소, Git 커밋, 채팅, 온라인 게시물 등에 업로드하지 마세요**.
Base64는 보안 암호화가 아닙니다. GitHub Actions의 **Repository secret**
값으로만 `.txt` 내용 전체를 등록합니다.

## 사용자가 GitHub에서 해야 할 한 번의 설정
1. 휴대폰이나 PC에서 https://github.com/ddogy1212/kakaotalk/settings/secrets/actions 열기
2. **New repository secret** 누르기
3. Name: `KAKAO_SIGNING_BUNDLE` (철자 정확히)
4. Secret: `KakaoFocus_GitHubSecret_PRIVATE.txt` 파일 안의 **전체 한 줄을 복사·붙여넣기**
5. **Add secret** 눌러 저장
6. https://github.com/ddogy1212/kakaotalk/actions/workflows/build-signed-release.yml 열기
7. **Run workflow** → `main` → 실행
8. 성공한 작업의 Artifacts에서 **`kakaofocus-STABLE-SIGNED-APK`** ZIP 다운로드
9. 압축을 풀어 `app-release.apk`를 설치

※ 이 설정은 ChatGPT GitHub 연결에 저장소 비밀값 수정 권한이 없어
사용자만 할 수 있습니다. **Secret 등록 전에는 영구 서명된 APK를 만들 수 없습니다.**

## 앞으로의 업데이트
- `app/build.gradle`의 **`applicationId` 절대 변경하지 않기**
- `versionCode`는 이전 설치보다 높게 (10 → 11 → 12 ...)
- 같은 `KAKAO_SIGNING_BUNDLE`을 삭제하거나 바꾸지 않기
- 새 코드가 main에 반영되면 `Build permanently signed update APK`가 자동 빌드
- **이 워크플로의 아티팩트만 설치**. 이름에 `debug`가 들어간 APK는 업데이트용으로 사용하지 않기
- 정식 앱을 최초 1회 설치한 후부터는 동일 앱 ID·인증서·더 높은 versionCode로 기존 앱을 업데이트 가능
- 새 APK를 설치해도 앱 데이터와 위젯 설정이 일반적으로 유지되지만, 백업 ZIP도 별도 보관 권장

## 서명키 인증서 확인
오직 이 공개 SHA-256 인증서 지문이 포함된 빌드만 업로드하도록 구성했습니다.

`FB:74:DF:2D:46:A6:F9:C0:C1:40:54:7E:5F:A1:FE:CF:B2:6C:09:F8:D1:7F:E1:3E:37:5A:B4:38:D0:01:7A:E4`

공개 지문은 비밀번호나 개인키가 아니며 공개해도 됩니다.

## 안전장치
- 개인키/비밀번호는 공개 저장소에 커밋하지 않습니다.
- 비밀값은 GitHub Actions 실행 중 일시적으로 복원되며 빌드 후 임시 파일을 삭제합니다.
- APK 서명이 누락되거나, 인증서 지문/고정 앱 ID가 다르면 작업이 실패하고 아티팩트가 업로드되지 않습니다.
- 키 파일과 비밀번호를 모두 잃어버리면 같은 인증서로 업데이트할 수 없습니다.
