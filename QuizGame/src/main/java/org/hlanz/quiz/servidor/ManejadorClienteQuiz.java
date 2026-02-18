package org.hlanz.quiz.servidor;

import org.hlanz.quiz.protocolo.HttpUtil;

import java.io.*;
import java.net.Socket;
import java.util.Map;

public class ManejadorClienteQuiz implements Runnable {
    private Socket socket;
    private PrintWriter salida;
    private BufferedReader entrada;
    private String nombreUsuario;
    private int puntuacionTotal = 0;

    // Control de respuesta por ronda
    private volatile boolean haRespondido = false;
    private char respuesta;
    private long tiempoRespuesta; // ms que tardo en responder

    public ManejadorClienteQuiz(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try {
            entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            salida = new PrintWriter(socket.getOutputStream(), false);

            // Enviar bienvenida como respuesta HTTP
            enviarHttp("WELCOME", "Conectado al Quiz! Envia POST /join con tu nombre");

            // Esperar peticion POST /join
            Map<String, String> peticion = HttpUtil.parse(entrada);
            if (peticion != null && "/join".equals(peticion.get("path"))) {
                nombreUsuario = peticion.get("body").trim();
            }

            if (nombreUsuario == null || nombreUsuario.isEmpty()) {
                nombreUsuario = "Jugador_" + socket.getPort();
            }

            System.out.println("[+] " + nombreUsuario + " se ha unido desde " + socket.getInetAddress());
            enviarHttp("WELCOME", nombreUsuario);
            enviarHttp("WAIT", "Esperando a que empiece la partida...");

            // Registrar en el servidor
            ServidorQuiz.registrarJugador(this);

            // Bucle de lectura: recibe peticiones HTTP del cliente
            Map<String, String> mensaje;
            while ((mensaje = HttpUtil.parse(entrada)) != null) {
                String path = mensaje.get("path");
                String body = mensaje.get("body");

                // POST /answer -> procesar respuesta
                if ("/answer".equals(path) && !haRespondido && body != null && !body.isEmpty()) {
                    char resp = Character.toUpperCase(body.trim().charAt(0));
                    if (resp == 'A' || resp == 'B' || resp == 'C' || resp == 'D') {
                        respuesta = resp;
                        tiempoRespuesta = System.currentTimeMillis();
                        haRespondido = true;
                        enviarHttp("WAIT", "Respuesta recibida. Esperando a los demas...");
                    }
                }
            }

        } catch (IOException e) {
            System.out.println("[-] Error con " + nombreUsuario + ": " + e.getMessage());
        } finally {
            desconectar();
        }
    }

    // Enviar respuesta HTTP al cliente
    public void enviarHttp(String tipo, String body) {
        if (salida != null) {
            salida.print(HttpUtil.buildResponse(tipo, body));
            salida.flush();
        }
    }

    // Preparar para nueva ronda
    public void nuevaRonda() {
        haRespondido = false;
        respuesta = ' ';
        tiempoRespuesta = 0;
    }

    public void sumarPuntos(int puntos) {
        puntuacionTotal += puntos;
    }

    private void desconectar() {
        try {
            ServidorQuiz.removerJugador(this);
            if (nombreUsuario != null) {
                System.out.println("[-] " + nombreUsuario + " se ha desconectado");
            }
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Getters
    public String getNombreUsuario() { return nombreUsuario; }
    public int getPuntuacionTotal() { return puntuacionTotal; }
    public boolean haRespondido() { return haRespondido; }
    public char getRespuesta() { return respuesta; }
    public long getTiempoRespuesta() { return tiempoRespuesta; }
    public boolean estaConectado() { return socket != null && !socket.isClosed(); }
}
