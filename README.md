# 차연

길 위에서 시작되는 인연. 주변 참여자와 서로 동의해 연결하고, 멀어지면 끝나며, 서로 다시 원할 때만 이어지는 차량 앱입니다.

HMG 앱 공모전의 2순위 작품(가칭)입니다. 1순위 작품은 [motion-care](https://github.com/kako-labs/motion-care)입니다.

## 현재 상태

단일 Gradle 모노레포 기반이 준비되어 있습니다. 공통 계약, 디자인 시스템, 기능별 내비게이션 골격과
빈 저장소 구현이 있으며, 실제 만남·서버·차량 수집·시그니처 계산은 각 담당자가 이어서 구현합니다.

JDK 17과 Android SDK 35 환경에서 `python3 scripts/dev.py verify`로 검증합니다.
시작 방법과 작업 규칙은 [개발 안내](CONTRIBUTING.md), 화면별 계약은 [공통 계약](docs/contracts.md)을 확인하세요.

## 문서

- [문서 색인과 링크](docs/README.md)
- [기획서 초안](docs/proposal.md)
- [프로젝트 뼈대 초안](docs/architecture.md)
- [공통 계약과 담당 경계](docs/contracts.md)
- [디자인 시안](docs/design/README.md)
