package org.hlanz.quiz.servidor;

import org.hlanz.quiz.modelo.Pregunta;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class ServidorQuiz {
    private static final int PUERTO = 8080;
    private static final int MAX_CLIENTES = 10;
    private static final int TIEMPO_RESPUESTA_SEG = 15; // segundos para responder

    private static Set<ManejadorClienteQuiz> jugadores = ConcurrentHashMap.newKeySet();
    private static List<Pregunta> preguntas;

    public static void main(String[] args) {
        ExecutorService pool = Executors.newFixedThreadPool(MAX_CLIENTES);

        // Cargar preguntas desde CSV local
        preguntas = cargarPreguntas();
        if (preguntas.isEmpty()) {
            System.out.println("[ERROR] No se pudieron cargar preguntas. Saliendo...");
            return;
        }
        System.out.println("=== SERVIDOR QUIZ ===");
        System.out.println("Protocolo: HTTP/1.1 sobre TCP");
        System.out.println("Puerto: " + PUERTO);
        System.out.println("Preguntas cargadas: " + preguntas.size());
        System.out.println("Esperando jugadores...");
        System.out.println("Escribe START cuando todos esten conectados.\n");

        // Hilo para aceptar conexiones
        Thread hiloConexiones = new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(PUERTO)) {
                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    ManejadorClienteQuiz manejador = new ManejadorClienteQuiz(clientSocket);
                    pool.execute(manejador);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        hiloConexiones.setDaemon(true);
        hiloConexiones.start();

        // Hilo principal: espera START del admin
        Scanner scanner = new Scanner(System.in);
        while (true) {
            String input = scanner.nextLine().trim();
            if (input.equalsIgnoreCase("START") && !jugadores.isEmpty()) {
                break;
            }
            if (jugadores.isEmpty()) {
                System.out.println("[!] No hay jugadores conectados todavia.");
            } else {
                System.out.println("[i] Jugadores conectados: " + jugadores.size() + ". Escribe START para empezar.");
            }
        }

        // Empezar el juego
        System.out.println("\n=== EMPIEZA EL QUIZ ===\n");
        iniciarJuego();
        pool.shutdown();
        scanner.close();
    }

    private static void iniciarJuego() {
        for (int i = 0; i < preguntas.size(); i++) {
            Pregunta pregunta = preguntas.get(i);

            // Preparar nueva ronda
            for (ManejadorClienteQuiz jugador : jugadores) {
                jugador.nuevaRonda();
            }

            System.out.println("--- Pregunta " + (i + 1) + "/" + preguntas.size() + " ---");
            System.out.println(pregunta.getPregunta());

            // Enviar pregunta con las 4 opciones en el body de una respuesta HTTP
            String bodyPregunta = pregunta.getPregunta() + "\n"
                    + "A:" + pregunta.getOpcionA() + "\n"
                    + "B:" + pregunta.getOpcionB() + "\n"
                    + "C:" + pregunta.getOpcionC() + "\n"
                    + "D:" + pregunta.getOpcionD();
            broadcastHttp("QUESTION", bodyPregunta);

            // Registrar momento de envio
            long tiempoInicio = System.currentTimeMillis();

            // Esperar respuestas (max TIEMPO_RESPUESTA_SEG segundos)
            long limite = tiempoInicio + (TIEMPO_RESPUESTA_SEG * 1000L);
            while (System.currentTimeMillis() < limite) {
                boolean todosRespondieron = true;
                for (ManejadorClienteQuiz jugador : jugadores) {
                    if (jugador.estaConectado() && !jugador.haRespondido()) {
                        todosRespondieron = false;
                        break;
                    }
                }
                if (todosRespondieron) break;

                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            // Calcular puntos
            calcularPuntos(pregunta, tiempoInicio);

            // Enviar respuesta correcta
            broadcastHttp("RESULT", String.valueOf(pregunta.getRespuestaCorrecta()));

            // Enviar ranking
            String ranking = generarRanking();
            broadcastHttp("RANKING", ranking);
            System.out.println("Ranking: " + ranking + "\n");

            // Enviar NEXT si no es la ultima pregunta
            if (i < preguntas.size() - 1) {
                broadcastHttp("NEXT", "Siguiente pregunta...");
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        // Fin del juego
        System.out.println("=== FIN DEL QUIZ ===");
        System.out.println("Ranking final: " + generarRanking());
        broadcastHttp("END", generarRanking());
    }

    private static void calcularPuntos(Pregunta pregunta, long tiempoInicio) {
        for (ManejadorClienteQuiz jugador : jugadores) {
            if (!jugador.estaConectado()) continue;

            if (jugador.haRespondido() && pregunta.esCorrecta(jugador.getRespuesta())) {
                long ms = jugador.getTiempoRespuesta() - tiempoInicio;
                int puntos = Math.max(100, 1000 - (int)(ms / 10));
                jugador.sumarPuntos(puntos);
                System.out.println("  " + jugador.getNombreUsuario() + ": CORRECTO (" + ms + "ms) -> +" + puntos + "pts");
            } else if (jugador.haRespondido()) {
                System.out.println("  " + jugador.getNombreUsuario() + ": INCORRECTO (respondio " + jugador.getRespuesta() + ")");
            } else {
                System.out.println("  " + jugador.getNombreUsuario() + ": NO RESPONDIO");
            }
        }
    }

    private static String generarRanking() {
        List<ManejadorClienteQuiz> lista = new ArrayList<>(jugadores);
        lista.sort((a, b) -> b.getPuntuacionTotal() - a.getPuntuacionTotal());

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lista.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append((i + 1)).append(".")
              .append(lista.get(i).getNombreUsuario())
              .append("(").append(lista.get(i).getPuntuacionTotal()).append("pts)");
        }
        return sb.toString();
    }

    private static List<Pregunta> cargarPreguntas() {
        // Intentar cargar desde FTP primero
        List<Pregunta> preguntasFtp = cargarDesdeFTP();
        if (!preguntasFtp.isEmpty()) {
            return preguntasFtp;
        }

        // Fallback: cargar desde CSV local
        System.out.println("[i] Cargando preguntas desde CSV local (blocket.csv)...");
        InputStream is = ServidorQuiz.class.getClassLoader().getResourceAsStream("blocket.csv");
        if (is == null) {
            System.out.println("[ERROR] No se encontro blocket.csv en resources");
            return new ArrayList<>();
        }
        try {
            return Pregunta.cargarDesdeCSV(is);
        } catch (IOException e) {
            System.out.println("[ERROR] Error leyendo CSV: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    private static List<Pregunta> cargarDesdeFTP() {
        String ftpHost = "80.225.190.216";
        String ftpFile = "/blocket.csv"; // ruta del archivo en el FTP
        System.out.println("[i] Intentando descargar preguntas desde FTP " + ftpHost + "...");
        try {
            // Conectar al FTP usando java.net.URL (ftp://user:pass@host/fichero)
            java.net.URL url = new java.net.URL("ftp://anonymous:@" + ftpHost + ftpFile);
            java.net.URLConnection conexion = url.openConnection();
            conexion.setConnectTimeout(5000);
            conexion.setReadTimeout(5000);
            InputStream is = conexion.getInputStream();
            List<Pregunta> preguntas = Pregunta.cargarDesdeCSV(is);
            is.close();
            System.out.println("[OK] Preguntas descargadas del FTP: " + preguntas.size());
            return preguntas;
        } catch (Exception e) {
            System.out.println("[WARN] No se pudo conectar al FTP: " + e.getMessage());
            System.out.println("[i] Usando CSV local como fallback...");
            return new ArrayList<>();
        }
    }

    // Broadcast HTTP a todos los jugadores
    public static void broadcastHttp(String tipo, String body) {
        for (ManejadorClienteQuiz jugador : jugadores) {
            jugador.enviarHttp(tipo, body);
        }
    }

    public static void registrarJugador(ManejadorClienteQuiz jugador) {
        jugadores.add(jugador);
        System.out.println("[i] Jugadores conectados: " + jugadores.size());
    }

    public static void removerJugador(ManejadorClienteQuiz jugador) {
        jugadores.remove(jugador);
    }
}
