# Use the official Eclipse Temurin JDK 25 image as the base
FROM eclipse-temurin:25-jdk

# Set working directory
WORKDIR /app

# Copy Maven wrapper and pom.xml
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Download dependencies
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the application
RUN ./mvnw clean package -DskipTests -B

# Expose port
EXPOSE 8081

# Run the application
ENTRYPOINT ["java", "-jar", "target/OTP-Login-0.0.1-SNAPSHOT.jar"]