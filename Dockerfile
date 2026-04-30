FROM eclipse-temurin:25-jdk

WORKDIR /app

COPY . .

ADD https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/3.46.1.0/sqlite-jdbc-3.46.1.0.jar /app/lib/sqlite-jdbc.jar
ADD https://repo1.maven.org/maven2/org/slf4j/slf4j-api/2.0.13/slf4j-api-2.0.13.jar /app/lib/slf4j-api.jar
ADD https://repo1.maven.org/maven2/org/slf4j/slf4j-simple/2.0.13/slf4j-simple-2.0.13.jar /app/lib/slf4j-simple.jar

RUN javac -cp "lib/*" JavaBackend.java

EXPOSE 10000

CMD ["sh", "-c", "java --enable-native-access=ALL-UNNAMED -cp '.:lib/*' JavaBackend"]
