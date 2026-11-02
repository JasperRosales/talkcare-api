# Build: docker build -t talkcare-api .
# Run:   docker run -p 4000:4000 --env-file .env -v talkcare-uploads:/data/uploads talkcare-api
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 talkcare && mkdir -p /data/uploads && chown talkcare /data/uploads
COPY --from=build /src/target/talkcare-api.jar app.jar
USER talkcare
ENV PORT=4000 UPLOAD_DIR=/data/uploads
EXPOSE 4000
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
