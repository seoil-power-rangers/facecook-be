# build/libs/*.jar은 CI에서 네이티브(x86) 환경으로 미리 빌드해서 넘겨준다.
# (Gradle 빌드를 여기서 하면 arm64 크로스빌드 시 QEMU 에뮬레이션 때문에
#  같은 빌드가 7분 넘게 걸린다 - 실제로 겪은 문제, PR에서 분리함)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
