package org.hlanz.quiz.modelo;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class Pregunta {
    private String pregunta;
    private String opcionA;
    private String opcionB;
    private String opcionC;
    private String opcionD;
    private char respuestaCorrecta; // A, B, C o D

    public Pregunta(String pregunta, String opcionA, String opcionB, String opcionC, String opcionD, char respuestaCorrecta) {
        this.pregunta = pregunta;
        this.opcionA = opcionA;
        this.opcionB = opcionB;
        this.opcionC = opcionC;
        this.opcionD = opcionD;
        this.respuestaCorrecta = Character.toUpperCase(respuestaCorrecta);
    }

    // Parsear linea del Blooket CSV (separado por comas)
    // Formato: numero,pregunta,opcion1,opcion2,opcion3,opcion4,tiempoLimite,respuestaCorrecta(1-4)
    public static Pregunta fromBlooketCSV(String linea) {
        String[] partes = linea.split(",");
        if (partes.length < 8) {
            throw new IllegalArgumentException("Linea CSV invalida: " + linea);
        }

        // La respuesta correcta viene como numero 1-4, convertir a letra A-D
        int numRespuesta = Integer.parseInt(partes[7].trim());
        char letraRespuesta = (char) ('A' + numRespuesta - 1); // 1->A, 2->B, 3->C, 4->D

        return new Pregunta(
                partes[1].trim(),  // pregunta
                partes[2].trim(),  // opcion A (Answer 1)
                partes[3].trim(),  // opcion B (Answer 2)
                partes[4].trim(),  // opcion C (Answer 3)
                partes[5].trim(),  // opcion D (Answer 4)
                letraRespuesta
        );
    }

    // Cargar lista de preguntas desde InputStream (fichero local o FTP)
    // Formato Blooket: primera linea titulo, segunda linea cabecera, luego datos
    public static List<Pregunta> cargarDesdeCSV(InputStream inputStream) throws IOException {
        List<Pregunta> preguntas = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String linea;
            int numLinea = 0;
            while ((linea = reader.readLine()) != null) {
                numLinea++;
                // Saltar las 2 primeras lineas (titulo + cabecera)
                if (numLinea <= 2) continue;
                if (!linea.trim().isEmpty()) {
                    try {
                        preguntas.add(fromBlooketCSV(linea));
                    } catch (Exception e) {
                        System.out.println("[WARN] Error parseando linea " + numLinea + ": " + e.getMessage());
                    }
                }
            }
        }
        return preguntas;
    }

    public boolean esCorrecta(char respuesta) {
        return Character.toUpperCase(respuesta) == respuestaCorrecta;
    }

    public String getPregunta() { return pregunta; }
    public String getOpcionA() { return opcionA; }
    public String getOpcionB() { return opcionB; }
    public String getOpcionC() { return opcionC; }
    public String getOpcionD() { return opcionD; }
    public char getRespuestaCorrecta() { return respuestaCorrecta; }
}
