FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app

# Copy Maven wrapper and pom.xml
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Copy the source code
COPY src ./src

# Build the application. The cache mount keeps ~/.m2 (dependencies and the Maven
# distribution the wrapper downloads) between builds and across all services, so
# libraries are fetched once instead of on every image build.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B clean package -DskipTests

# ---- Runtime Image ----
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]