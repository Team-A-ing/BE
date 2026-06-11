FROM gradle:8-jdk21 AS build
WORKDIR /app
COPY . .
RUN gradle clean build -x check -x test -Pproduction --no-daemon

FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y ffmpeg && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /app/build/libs/readb-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
