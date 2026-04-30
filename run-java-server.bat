@echo off
javac -cp "lib/*" JavaBackend.java
java --enable-native-access=ALL-UNNAMED -cp ".;lib/*" JavaBackend
