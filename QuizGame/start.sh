#!/bin/bash
java -cp app.jar org.hlanz.quiz.servidor.ServidorQuiz &
java -cp app.jar org.hlanz.quiz.ssl.ServidorQuizSSL &
wait
