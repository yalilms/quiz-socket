package org.hlanz.quiz.ssl;

import org.hlanz.quiz.protocolo.HttpUtil;

import javax.net.ssl.*;
import java.io.*;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.Scanner;

/**
 * Cliente Quiz con SSL/TLS
 * Usa SSLSocket en vez de Socket para comunicacion cifrada
 * Puerto: 8443
 */
public class ClienteQuizSSL {
    private static String HOST = "localhost";
    private static int PUERTO = 8443;

    private SSLSocket socket;
    private PrintWriter salida;
    private BufferedReader entrada;
    private Scanner scanner;
    private volatile boolean conectado = true;

    public ClienteQuizSSL() {
        scanner = new Scanner(System.in);
    }

    public void iniciar() {
        try {
            // Crear TrustManager que acepta cualquier certificado (para pruebas)
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() { return null; }
                        public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                        public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                    }
            };

            // Configurar contexto SSL
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

            // Crear SSLSocket
            SSLSocketFactory factory = sslContext.getSocketFactory();
            socket = (SSLSocket) factory.createSocket(HOST, PUERTO);

            entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            salida = new PrintWriter(socket.getOutputStream(), false);

            System.out.println("=== QUIZ GAME (SSL/TLS) ===");
            System.out.println("Protocolo: HTTP/1.1 sobre TCP con SSL/TLS");
            System.out.println("Conectado al servidor en puerto " + PUERTO + "\n");

            // Leer respuesta HTTP de bienvenida
            Map<String, String> bienvenida = HttpUtil.parse(entrada);
            if (bienvenida != null) {
                System.out.println(bienvenida.get("body"));
            }

            // Enviar nombre con peticion HTTP: POST /join
            System.out.print("Introduce tu nombre: ");
            String nombre = scanner.nextLine().trim();
            enviarPeticion("POST", "/join", nombre);

            // Hilo listener para recibir respuestas HTTP del servidor
            Thread listener = new Thread(new ListenerServidor());
            listener.setDaemon(true);
            listener.start();

            // Hilo principal: leer input del usuario
            while (conectado) {
                String input = scanner.nextLine().trim().toUpperCase();
                if (input.isEmpty()) continue;

                if (input.length() == 1 && "ABCD".contains(input)) {
                    enviarPeticion("POST", "/answer", input);
                } else {
                    System.out.println("[!] Respuesta invalida. Solo A, B, C o D.");
                }
            }

        } catch (Exception e) {
            System.err.println("[ERROR] No se pudo conectar al servidor SSL: " + e.getMessage());
        } finally {
            cerrarConexion();
        }
    }

    private void enviarPeticion(String method, String path, String body) {
        if (salida != null) {
            salida.print(HttpUtil.buildRequest(method, path, body));
            salida.flush();
        }
    }

    private void cerrarConexion() {
        try {
            conectado = false;
            if (scanner != null) scanner.close();
            if (salida != null) salida.close();
            if (entrada != null) entrada.close();
            if (socket != null) socket.close();
            System.out.println("\nDesconectado del servidor.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private class ListenerServidor implements Runnable {
        @Override
        public void run() {
            try {
                while (conectado) {
                    Map<String, String> respuesta = HttpUtil.parse(entrada);
                    if (respuesta == null) break;
                    procesarRespuesta(respuesta);
                }
            } catch (IOException e) {
                if (conectado) {
                    System.err.println("[!] Conexion perdida con el servidor");
                    conectado = false;
                }
            }
        }

        private void procesarRespuesta(Map<String, String> respuesta) {
            String tipo = respuesta.get("X-Type");
            String body = respuesta.get("body");
            if (tipo == null || body == null) return;

            switch (tipo) {
                case "QUESTION":
                    String[] lineas = body.split("\n");
                    System.out.println("\n========================================");
                    System.out.println("  " + lineas[0]);
                    System.out.println("========================================");
                    for (int i = 1; i < lineas.length; i++) {
                        System.out.println("  " + lineas[i].charAt(0) + ") " + lineas[i].substring(2));
                    }
                    System.out.println("----------------------------------------");
                    System.out.print("Tu respuesta (A/B/C/D): ");
                    break;
                case "RESULT":
                    System.out.println("\n>> Respuesta correcta: " + body);
                    break;
                case "RANKING":
                    System.out.println("\n--- RANKING ---");
                    for (String pos : body.split(",")) {
                        System.out.println("  " + pos);
                    }
                    System.out.println("---------------");
                    break;
                case "NEXT":
                    System.out.println("\nSiguiente pregunta en 3 segundos...");
                    break;
                case "END":
                    System.out.println("\n=== FIN DEL QUIZ ===");
                    System.out.println("--- RANKING FINAL ---");
                    for (String pos : body.split(",")) {
                        System.out.println("  " + pos);
                    }
                    System.out.println("---------------------");
                    System.out.println("Gracias por jugar!");
                    conectado = false;
                    break;
                case "WAIT":
                    System.out.println("[i] " + body);
                    break;
                case "WELCOME":
                    System.out.println("[i] " + body);
                    break;
                default:
                    System.out.println(body);
                    break;
            }
        }
    }

    public static void main(String[] args) {
        if (args.length > 0) HOST = args[0];
        if (args.length > 1) PUERTO = Integer.parseInt(args[1]);
        ClienteQuizSSL cliente = new ClienteQuizSSL();
        cliente.iniciar();
    }
}
