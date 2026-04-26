FROM eclipse-temurin:25-jdk

WORKDIR /app

COPY . .

RUN javac JavaBackend.java

EXPOSE 10000

CMD ["java", "JavaBackend"]
