FROM maven:3.9-eclipse-temurin-21

WORKDIR /app

COPY pom.xml ./
# Cache dependencies and startup plugins before copying sources.
RUN mvn -B -ntp test-compile spring-boot:help
COPY src/ ./src/

EXPOSE 8080
CMD ["mvn", "-B", "-ntp", "spring-boot:run"]
