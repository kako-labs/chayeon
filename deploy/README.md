# 심사용 서버 배포

팀이 운영할 심사용 서버를 위한 구성입니다. 실제 공개 서버의 가동 여부나 Docker 검증 완료를 뜻하지 않습니다. Docker Engine과 Compose v2, Python 3, 서버를 가리키는 도메인의 DNS A/AAAA, 외부에서 연결 가능한 TCP 80·443이 필요합니다. 도메인 없이 로컬 자체 서명 HTTPS로 확인하려면 아래 검증 스크립트를 사용합니다.

```sh
cp deploy/env.example deploy/.env
# deploy/.env의 CHAYEON_DOMAIN을 운영 도메인으로 수정
docker compose --env-file deploy/.env -f deploy/docker-compose.yml up -d --build
docker compose --env-file deploy/.env -f deploy/docker-compose.yml logs --tail=100
```

Caddy가 인증서를 자동 발급·갱신하며 HTTP와 인증 WebSocket을 서버로 전달합니다. 서버 포트는 외부에 게시하지 않습니다. `X-Chayeon-Client-IP`를 Caddy가 덮어쓰므로 서버의 신뢰 프록시 설정을 유지합니다. 다른 프록시로 교체하면 이 헤더의 신뢰 경계를 함께 검토해야 합니다. Caddy 접근 로그는 켜지 않습니다. 토큰·연락처·명령 본문·음성·좌표를 로그에 추가하지 마세요.

공간은 기본 `default`, 공간 정원은 기본 활성 사용자 50명입니다. 앱에는 공간 코드와 계정 선택이 없습니다. 설치별 비밀 값이 서버 계정을 식별하므로 앱 데이터를 지우거나 재설치하면 기존 계정을 복구할 수 없습니다. 별도 공간은 허용 목록에 등록해야 하며, 로컬 HTTP 검증용 서버는 `CHAYEON_ALLOWED_ROOMS='*'`로 실행합니다. 입장 IP 30회/분, HTTP IP 600회/분·인증된 계정 300회/분, WebSocket 시도 IP 60회/분·인증된 계정 20회/분이 기본 제한입니다.

Compose의 `server_data` 볼륨을 `/data`에 연결합니다. 서버는 `CHAYEON_DATA_DIR`이 있을 때만 원자적 파일 저장을 사용하며 파일 권한은 0600입니다. 볼륨은 서버 UID 10001의 쓰기가 가능해야 합니다. 환경 변수를 생략한 직접 실행은 메모리 저장입니다. `CHAYEON_MAX_ROOMS`는 기본 64, `CHAYEON_IDLE_ROOM_MILLIS`는 기본 1800000(30분)이며 env 파일에서 조정할 수 있습니다. 프로필·동의·관계·연락처 등 개인 데이터를 포함하는 저장소이므로 운영자 접근을 제한하세요. 원시 차량 신호와 음성은 저장하지 않습니다. 재시작 시 활성 통화를 종료하며 끝난 연결은 되살리지 않습니다. 유효한 대기 요청은 원래 만료 시각까지 유지됩니다. 저장 파일에는 bearer token 원문 대신 digest를 저장합니다. 유휴 공간은 메모리·영속 저장 모두 계정·관계·기록과 최대 공간 수 점유를 유지하고 토큰만 만료합니다. 로그아웃하거나 유휴 기한을 넘긴 계정은 사용자 정원을 차지하지 않으며, 같은 설치가 돌아오면 빈자리가 있을 때 기존 데이터를 유지한 채 재입장합니다. 영속 저장은 계정별 마지막 인증 시각도 저장하므로 재시작이 활동 기한을 늘리지 않습니다. 이전 저장 파일처럼 활동 시각이 없는 계정은 자리를 예약하지 않고 다음 인증이나 재입장 때 빈자리를 확인합니다. 허용 목록에서 제외한 공간 파일은 삭제하지 않고 로드만 생략합니다.

| 환경 변수 / 제한 | 기본값 | 동작 |
|---|---|---|
| `CHAYEON_ALLOWED_ROOMS` | `default` | 쉼표로 구분한 허용 공간. 알 수 없는 공간은 `Restricted`. `*`는 모든 유효한 공간 코드 허용으로 로컬 검증에만 사용 |
| `CHAYEON_ROOM_CAPACITY` | `50` | 삭제·로그아웃되지 않고 유휴 기한 안에 인증 요청 또는 heartbeat가 있었던 계정만 좌석 점유. 복귀도 빈자리 필요 |
| `CHAYEON_IDLE_ROOM_MILLIS` | `1800000` | 30분 무활동 계정의 좌석 반환. 데이터는 보존하며 파일 저장 시 재시작 후에도 같은 기준 적용 |
| `CHAYEON_MAX_ROOMS` | `64` | 로드하는 허용 공간의 최대 개수. 유휴 공간도 데이터 보존을 위해 이 한도에 포함 |
| 요청 제한 테이블 | IP·계정 각각 최대 `4096`개 | 미등록 토큰은 IP 예산만 사용. 계정 예산은 인증된 공간·계정 기준이며 테이블이 차면 가장 오래 사용하지 않은 항목을 교체 |

백업은 서버를 중지한 뒤 Docker 볼륨을 운영 환경의 암호화된 백업 대상으로 복사하고 서버를 다시 시작합니다. 백업 복원에는 같은 볼륨 소유권을 유지하세요. 계정 삭제 후 오래된 백업 복원은 삭제 데이터를 되살릴 수 있으므로 백업 보관·폐기도 함께 관리해야 합니다. 다음 명령은 서버와 Caddy 인증서를 포함한 **모든 볼륨 데이터를 영구 삭제**하므로 초기화가 필요한 경우에만 직접 실행합니다.

```sh
docker compose --env-file deploy/.env -f deploy/docker-compose.yml down --volumes
```

로컬 검증은 별도 Compose 프로젝트를 빌드·실행하고 Caddy 자체 CA를 해당 검증 프로세스에서만 신뢰하고 HTTPS를 통한 health, 프로필·동의·연결 명령, 두 참가자의 성공한 WebSocket 101 업그레이드를 확인한 뒤 자체 볼륨을 정리합니다. 운영 프로젝트에는 사용하지 마세요. 로컬 override는 `localhost`와 `tls internal`을 사용하며 검증 스크립트가 Caddy CA를 임시 파일로 받아 검증 프로세스에서만 신뢰합니다. Android 릴리스에는 임의 CA 우회가 없습니다.

```sh
./deploy/verify.sh
CHAYEON_SERVER_URL=https://judging.example.org python3 scripts/dev.py release
```

운영 상태는 실제 도메인으로 다음 두 경로를 확인합니다.

```sh
curl --fail https://judging.example.org/health
curl --fail https://judging.example.org/ready
./gradlew :app:assembleRelease -Pchayeon.demo=true -Pchayeon.serverUrl=https://judging.example.org
```

`judging.example.org`는 예시이며 실제 도메인으로 바꿉니다. 직접 Gradle 릴리스는 프로젝트의 로컬 서명 설정이 필요하므로 제출용 복사·서명·감사까지 수행하려면 위 `scripts/dev.py release` 경로를 사용하세요.

APK의 URL은 빌드 시 고정됩니다. 심사용 실제 HTTPS 주소로 바꿔 빌드하고 `apk/chayeon-release.apk`를 설치합니다. 주소를 생략하면 네트워크 없이 SOLO 프로필부터 시작합니다. 로컬 에뮬레이터 검증은 debug URL과 adb reverse를 사용하는 `python3 scripts/dev.py e2e --server` 경로를 따릅니다. Docker daemon이 없는 환경에서는 배포 검증을 실행할 수 없으며 셸/Python 정적 검사만으로 운영 준비가 검증되지는 않습니다.

## Cloudflare 터널로 운영할 때

Caddy 대신 Cloudflare 터널이 HTTPS와 WebSocket을 처리하게 할 수도 있어요. 서버 컨테이너만 띄우고 포트는 서버 안에서만 열어요.

```sh
docker build -f server/Dockerfile -t chayeon-server:local .
docker run --rm --name chayeon-server -p 127.0.0.1:18080:8080 \
  -e CHAYEON_TRUST_PROXY=true -e CHAYEON_CLIENT_IP_HEADER=CF-Connecting-IP \
  -e CHAYEON_DATA_DIR=/data -v chayeon-data:/data chayeon-server:local
```

터널 설정의 `ingress`는 운영 주소를 `http://127.0.0.1:18080`으로 보냅니다. Cloudflare가 `CF-Connecting-IP`를 덮어쓰므로 요청 제한이 사용자별로 나뉘어요. 서버 포트를 외부에 직접 열면 이 헤더를 믿으면 안 됩니다.

`cloudflared tunnel route dns`는 기본 설정 파일에 다른 터널이 적혀 있으면 그 터널로 주소를 연결할 수 있어요. 터널 ID를 직접 적고, 필요하면 `--overwrite-dns`로 바로잡으세요.
