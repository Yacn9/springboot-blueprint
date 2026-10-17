# syntax=docker/dockerfile:1
#
# The application image. It builds from source, so `docker build .` needs
# nothing but Docker; the result runs as a non-root user from Spring Boot's
# layers, so a code change rebuilds only the last, smallest layer.

FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY . .
RUN ./gradlew :app:bootJar --no-daemon --console=plain \
 && cp "$(ls app/build/libs/*.jar | grep -v -- '-plain.jar')" application.jar \
 && java -Djarmode=tools -jar application.jar extract --layers --destination extracted

FROM eclipse-temurin:25-jre
RUN useradd --system --uid 1001 spring
USER spring
WORKDIR /application
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "application.jar"]
