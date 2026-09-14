#!/bin/sh
# EC2 수동 배포 스크립트.
#
# GitHub Actions 가 계정 수준에서 막혀 있을 때(Actions has been disabled for this user)
# .github/workflows/deploy.yml 의 'EC2 에 배포' 단계를 손으로 돌리기 위한 것이다.
# 이미지는 로컬에서 만들어 Docker Hub 에 올려 둔 것을 내려받는다.
#
# 쓰는 법 (EC2 안에서):
#   curl -sLO https://raw.githubusercontent.com/JuHyungKim-kjh/CinePick/main/deploy.sh
#   sh deploy.sh
#
# 되돌리기 — 커밋 SHA 를 인자로 준다:
#   sh deploy.sh f9d0858f523051c5a66561828895fc47d7f43b73

set -e

TAG="${1:-latest}"
IMAGE="kjh0300/cinepick:$TAG"

echo "==> 배포할 이미지: $IMAGE"

# docker 를 sudo 없이 쓸 수 있는지 확인해 둔다 (ubuntu 가 docker 그룹에 없으면 sudo 가 필요하다)
if docker info >/dev/null 2>&1; then
  DOCKER="docker"
else
  echo "    docker 를 직접 쓸 수 없어 sudo 로 실행한다"
  DOCKER="sudo docker"
fi

echo "==> 이미지를 내려받는다 (30초~2분)"
$DOCKER pull "$IMAGE"

# 첫 배포에는 지울 컨테이너가 없으므로 실패를 무시한다
echo "==> 돌고 있는 컨테이너를 정리한다"
$DOCKER stop cinepick 2>/dev/null || true
$DOCKER rm cinepick 2>/dev/null || true

echo "==> 새 컨테이너를 띄운다"
$DOCKER run -d \
  --name cinepick \
  --restart unless-stopped \
  --network host \
  --log-opt max-size=10m --log-opt max-file=3 \
  --env-file ~/cinepick.env \
  "$IMAGE"

# 배포를 반복하면 옛 이미지가 쌓여 디스크를 채운다
echo "==> 옛 이미지를 정리한다"
$DOCKER image prune -f >/dev/null

# 컨테이너가 떴다고 서비스가 되는 것은 아니다. 실제로 응답하는지 확인한다.
echo "==> 준비될 때까지 기다린다 (최대 150초)"
i=1
while [ "$i" -le 30 ]; do
  if curl -sf http://127.0.0.1:8080/system/status 2>/dev/null | grep -q '"ready":true'; then
    echo ""
    echo "  ===================="
    echo "   배포 성공"
    echo "  ===================="
    curl -s http://127.0.0.1:8080/system/status
    echo ""
    echo ""
    echo "  브라우저에서 확인: http://15.164.221.135/"
    exit 0
  fi
  printf "."
  sleep 5
  i=$((i + 1))
done

echo ""
echo "  150초 안에 준비되지 않았다. 로그를 확인한다:"
echo ""
$DOCKER logs --tail 50 cinepick
exit 1
