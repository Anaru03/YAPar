#!/bin/bash
set -e

mkdir -p out/META-INF

echo "Compilando..."
javac -d out -sourcepath src \
    src/Main.java \
    src/parser/*.java \
    src/lexer/*.java \
    src/runtime/*.java

echo "Empacando JAR..."
echo "Main-Class: Main" > out/META-INF/MANIFEST.MF
jar cfm YAPar.jar out/META-INF/MANIFEST.MF -C out .

echo ""
echo "Build completo."
echo "Ejecutar con: java -jar YAPar.jar"