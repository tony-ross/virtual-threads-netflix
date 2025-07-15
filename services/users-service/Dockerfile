FROM openjdk:24-jdk-slim

WORKDIR /app

COPY build/libs/users-service-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8082

ENTRYPOINT ["java", "-XX:+UseVirtualThreads", "-jar", "app.jar"]