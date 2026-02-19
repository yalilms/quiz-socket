#!/bin/bash
while true; do
  java -cp app.jar org.hlanz.quiz.servidor.ServidorQuiz
  echo "[i] ServidorQuiz terminó, reiniciando en 3s..."
  sleep 3
done &

while true; do
  java -cp app.jar org.hlanz.quiz.ssl.ServidorQuizSSL
  echo "[i] ServidorQuizSSL terminó, reiniciando en 3s..."
  sleep 3
done &

wait
