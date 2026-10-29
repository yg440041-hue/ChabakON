# 차박ON (ChabakON)

**버튼 한 번으로 차를 '안전하게' 침실로 바꿔 주는 차박 원스톱 앱**

현대자동차그룹 차량용 앱 개발 공모전(Pleos Playground) 출품작입니다.
안전 점검 → 단계별 시트 평탄화 → 잠자리 모드(공조·환기·취침등·잠금) → 밤샘 배터리 관리 → 아침 운전 자세 복귀까지 한 흐름으로 제공합니다.

- 시연 영상(에뮬레이터 실제 동작, 약 2분 19초): https://youtu.be/2QtC7dmzokQ

---

## APK 파일 위치

- 이 저장소의 **Releases** 페이지(https://github.com/yg440041-hue/ChabakON/releases/tag/v0.2.0) → **v0.2.0** → `KimIro_ChabakON_release.apk` (GitHub 웹 업로드 용량 초과로 Releases에 등록)
- release 빌드이며, 바로 설치·실행할 수 있도록 서명되어 있습니다.

---

## 실행 방법

### 방법 1. APK로 바로 실행
1. Pleos Connect Emulator(v2.1.0, API 34)를 실행합니다.
2. Releases에서 받은 APK를 에뮬레이터 화면으로 끌어다 놓거나, 아래 명령으로 설치합니다.
   ```
   adb install -r KimIro_ChabakON_release.apk
   ```
3. 앱 목록(하단 독)에서 **차박ON**을 실행합니다.

### 방법 2. 소스로 빌드
1. Android Studio > **Open** > 이 저장소 폴더(`ChabakON`) 선택
2. Gradle Sync가 끝날 때까지 기다립니다. (처음에는 수 분)
3. 상단 기기 선택에서 **Pleos Connect v2.1.0** 에뮬레이터 선택 > **▶ Run**

> 프로젝트 경로에 한글이 없는 위치(예: `C:\dev\ChabakON`)를 권장합니다.

---

## 테스트 환경 및 필요한 설정

| 항목 | 내용 |
|---|---|
| Android Studio | 2026.2 이상 |
| 에뮬레이터 | Pleos Connect Emulator v2.1.0 (Android API 34, Automotive, RAM 4GB, Cold boot) |
| Pleos SDK | Vehicle SDK 2.3.2, SpeechToText SDK 3.3.1 (`ai.pleos.playground:Vehicle`, `ai.pleos.playground:SpeechToText`, 공개 저장소 nexus-playground.pleos.ai) |
| 빌드 | minSdk 28, compileSdk 35, Kotlin 2.0, Jetpack Compose |

### 에뮬레이터 이미지 추가
Android Studio > Tools > SDK Manager > **SDK Update Sites** > `+`
- Name: `Pleos Connect System Image`
- URL(Windows/Linux): `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml`

이후 SDK Platforms 탭에서 API 34의 Pleos Connect v2.1.0 System Image를 설치하고, Device Manager에서 Automotive AVD를 만듭니다.

### 차량 상태 바꾸기 (Virtual Console)
```
adb forward tcp:8088 tcp:8088
```
브라우저에서 `http://127.0.0.1:8088/` 접속 → 2열 착석(Convenience > Seat Occupancy), 문 열림, 기어, 브레이크 페달 등을 바꿔 가며 시험합니다.

### 테스트 팁
- **어린이 보호 잠금 PIN 기본값: `0000`** (상단 'PIN 설정'에서 변경·끄기 가능)
- PIN 초기화: 브레이크 페달을 밟은 상태(Virtual Console > Motion > 브레이크 페달 > 0 초과)에서 'PIN을 잊었어요' → '5초간 누르기'
- 에뮬레이터 기본값은 2열에 사람이 앉아 있는 상태라 시작 버튼이 꺼져 있습니다. Virtual Console에서 2열 좌석을 비워 주세요.
- 음성 정지 시험: 에뮬레이터 Extended Controls > Microphone > **Enable Host Microphone Access** 를 켜고, 평탄화 중에 "멈춰"라고 또렷하게 말합니다.
- 상단 '로그' 칩: 모든 SDK 호출 결과 확인

---

## 주요 기능

- **안전 점검 6가지**: 2열 비어 있음(착석 센서), 안전벨트 해제, 기어 P, 옆문 닫힘(트렁크는 열려도 됨), 어린이 보호 잠금, 좌석 사이·발밑 비어 있음(눈으로 확인 후 [확인했어요]). 센서 값이 없는 항목은 사용자 확인으로 대체
- **3D 좌석 평면도**: 2열 좌석별 상태 색 표시, 움직이는 시트 빨간 깜박임
- **세이프 오토 평탄화**: 3초 카운트다운 + 음성 안내, 단계별 진행 막대(앞좌석 앞뒤 → 등받이 → 헤드레스트 → 2열 등받이)
  - '여기까지' → 그 자리에서 멈춤, 같은 자리 버튼이 '계속하기'로 바뀜, '이 위치로 다음 단계'로 확정
  - 즉시 정지: 화면 아무 곳 터치 / 정지 버튼 / 문 열림 / 2열 착석 감지 / **음성("멈춰", "정지", "그만", "잠깐")**
  - **끼임 감지**: 움직이던 시트가 목표 위치의 90% 전에 멈추면 즉시 정지(평탄화·원위치·복귀 모두)
  - **작동 중 경고음**: 시트가 움직이는 동안 약 1초 간격으로 경고음
  - 처음 위치 저장(앱 재실행 후에도 유지), 이미 평탄화된 경우 시작 버튼 비활성
- **잠자리 모드**: 전체 선택 + 항목별 온오프(공조, 2열 창문 환기, 화면 취침등, 2열 시트 열선, 도어 잠금), 켜진 상태에서 바꾸면 즉시 반영, 설정 저장
- **배터리 가디언**: 하한선까지 버틸 시간 표시, 하한선 근처에서 풍량 자동 감소
- **굿모닝 복귀**: 차박 모드 후에만 활성화, 기상 알람·일출 화면, 출발 전 안전 점검 후 평탄화 전 위치로 단계별 복귀
- **어린이 보호 잠금**: PIN 해제 후 5분 유지, 작동 완료 시 자동 재잠금, PIN 설정에서 PIN 변경·잠금 끄기(현재 PIN 필요), 브레이크 페달 + 5초 누르기로 PIN 초기화

## 사용한 Pleos SDK
- **Seat**: 착석 감지, 안전벨트, 앞뒤 이동·등받이·헤드레스트 이동(MoveControl FORWARD/BACKWARD/STOP)과 위치 리스너, 시트 메모리, 2열 열선
- **Door**: 도어·테일게이트 열림 상태, 도어 잠금
- **HVAC / Window**: 공조 전원·온도·풍량, 2열 창문 환기
- **Gear / Brake / EV Battery**: 기어 P, 브레이크 페달 상태, 배터리 잔량
- 각 도메인의 `check...Capabilities`로 차종별 지원 기능과 영역을 판별
- **SpeechToText (ON_DEVICE)**: 시트 작동 중에만 음성 인식을 켜고, 정지 단어를 들으면 즉시 정지. 에뮬레이터에서는 앱이 녹음한 PCM을 `sendAudio()`로 전달

## 알려진 제한 및 다음 계획
- 앰비언트 조명 밝기 제어: 현재 SDK 공개 API에 설정 함수가 없어 화면 취침등으로 대체
- SDK 2.3.2 에뮬레이터에서 착석 값이 VHAL 표준(1=비어 있음)과 반대로 해석되어 앱에서 보정
- 에뮬레이터에는 한국어 TTS 엔진이 없어 카운트다운 안내 음성이 들리지 않을 수 있습니다(실차·TTS 엔진 설치 기기에서는 정상)
- 다음 계획: 실차 마이크 환경 음성 인식률 검증, 차박 장소 도우미(FusedLocation / NaviHelper)

## 출처 및 라이선스
- 차량 3D 모델: "2022 Mahindra Scorpio-N" by BHP3D (CC BY 4.0), 지붕 절단·색상 변경 후 렌더링 (`CAR_MODEL_CREDIT.txt`)
- 글꼴: 나눔스퀘어라운드 (SIL Open Font License 1.1, `NanumSquareRound_LICENSE_OFL.txt`)
- 이 저장소의 소스 코드와 디자인의 저작권은 제작자에게 있습니다. 공모전 심사 목적의 열람·실행 외 무단 복제·사용을 금합니다. (`LICENSE`)
