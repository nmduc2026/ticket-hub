# syntax=docker/dockerfile:1
# Một Dockerfile cho cả hai ứng dụng backend; chọn ứng dụng bằng --build-arg MODULE=app|gateway.
#   docker build -f backend/Dockerfile --build-arg MODULE=app -t tickethub/app backend
# Multi-stage: build bằng JDK, tách layered JAR, chạy trên distroless non-root (overview.md §19.4).

ARG MODULE=app

FROM eclipse-temurin:21.0.9_10-jdk AS build
ARG MODULE
WORKDIR /workspace
# Chép pom trước để lớp phụ thuộc Maven được cache khi chỉ đổi mã nguồn.
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY app/pom.xml app/pom.xml
COPY gateway/pom.xml gateway/pom.xml
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -pl ${MODULE} -am dependency:go-offline
COPY app/src app/src
COPY gateway/src gateway/src
# Test và Spotless đã chạy ở job CI riêng; image chỉ đóng gói.
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -q -pl ${MODULE} -am -DskipTests -Dspotless.check.skip=true package \
    && java -Djarmode=tools -jar ${MODULE}/target/${MODULE}-*-SNAPSHOT.jar extract --layers --launcher --destination /layers

FROM gcr.io/distroless/java21-debian12:nonroot
WORKDIR /app
COPY --from=build /layers/dependencies/ ./
COPY --from=build /layers/spring-boot-loader/ ./
COPY --from=build /layers/snapshot-dependencies/ ./
COPY --from=build /layers/application/ ./
USER nonroot
# Chỉ đặt giới hạn bộ nhớ theo container; mọi cấu hình khác (URL, mật khẩu) qua biến môi trường.
ENTRYPOINT ["/usr/bin/java", "-XX:MaxRAMPercentage=75", "org.springframework.boot.loader.launch.JarLauncher"]
