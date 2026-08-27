# ------------------------------------------------------------------
# 1단계: 빌드. JDK 로 jar 를 만들고 여기서 끝낸다.
# 빌드 도구는 실행에 필요 없으므로 2단계로 넘기지 않는다(이미지가 수백 MB 가벼워진다).
# ------------------------------------------------------------------
FROM eclipse-temurin:17-jdk AS build

WORKDIR /app

# 의존성 목록만 먼저 복사한다. 소스만 고쳤을 때 의존성 내려받기를 건너뛰기 위한 것으로,
# 이 순서를 지켜야 도커 레이어 캐시가 산다.
COPY gradle gradle
COPY gradlew build.gradle settings.gradle ./

# Windows 에서 만든 gradlew 는 줄바꿈이 CRLF 라 리눅스에서 "bad interpreter" 로 죽는다
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew
RUN ./gradlew dependencies --no-daemon > /dev/null 2>&1 || true

COPY src src
RUN ./gradlew bootJar --no-daemon -x test

# ------------------------------------------------------------------
# 2단계: 실행. JRE + Chrome 만 담는다.
# ------------------------------------------------------------------
FROM eclipse-temurin:17-jre

# MovieCrawlerService 가 실제 Chrome 을 띄우므로 브라우저가 이미지 안에 있어야 한다.
# fonts-nanum 은 한글 페이지(CGV·롯데·메가박스)가 네모로 깨지지 않게 하기 위한 것이다.
RUN apt-get update \
    && apt-get install -y --no-install-recommends wget gnupg ca-certificates fonts-nanum tzdata \
    && wget -q -O /tmp/chrome.deb https://dl.google.com/linux/direct/google-chrome-stable_current_amd64.deb \
    && apt-get install -y --no-install-recommends /tmp/chrome.deb \
    && rm -f /tmp/chrome.deb \
    && rm -rf /var/lib/apt/lists/*

# 크롤링한 개봉일·상영시간을 한국 시간으로 읽어야 한다
ENV TZ=Asia/Seoul

WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar

# t2.small(2GB)에서 MySQL·Chrome 과 함께 돌아가는 것을 전제로 잡은 값이다.
# 힙을 열어두면 크롤링 중 Chrome 이 메모리를 요구할 때 서로 밀어내다 OOM 으로 죽는다.
ENV JAVA_OPTS="-Xmx640m -XX:+UseSerialGC"

EXPOSE 8080

# exec 형태로 감싸야 java 가 PID 1 이 되어 docker stop 의 종료 신호를 직접 받는다
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
