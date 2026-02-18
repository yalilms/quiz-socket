package org.hlanz.quiz.protocolo;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Utilidad para construir y parsear peticiones/respuestas HTTP
 * sobre sockets TCP persistentes.
 *
 * Cliente -> Servidor: peticiones HTTP (POST /join, POST /answer)
 * Servidor -> Cliente: respuestas HTTP (200 OK con X-Type)
 */
public class HttpUtil {

    // ============= CONSTRUIR MENSAJES =============

    // Construir peticion HTTP (cliente -> servidor)
    // Ejemplo:
    //   POST /answer HTTP/1.1
    //   Content-Length: 1
    //
    //   A
    public static String buildRequest(String method, String path, String body) {
        StringBuilder sb = new StringBuilder();
        sb.append(method).append(" ").append(path).append(" HTTP/1.1\r\n");
        sb.append("Content-Length: ").append(body.length()).append("\r\n");
        sb.append("\r\n");
        sb.append(body);
        return sb.toString();
    }

    // Construir respuesta HTTP (servidor -> cliente)
    // Ejemplo:
    //   HTTP/1.1 200 OK
    //   X-Type: QUESTION
    //   Content-Length: 35
    //
    //   Cual es la capital de Francia?
    public static String buildResponse(String type, String body) {
        StringBuilder sb = new StringBuilder();
        sb.append("HTTP/1.1 200 OK\r\n");
        sb.append("X-Type: ").append(type).append("\r\n");
        sb.append("Content-Length: ").append(body.length()).append("\r\n");
        sb.append("\r\n");
        sb.append(body);
        return sb.toString();
    }

    // ============= PARSEAR MENSAJES =============

    // Parsear un mensaje HTTP desde un BufferedReader.
    // Devuelve un Map con las claves:
    //   "firstLine" -> "POST /answer HTTP/1.1" o "HTTP/1.1 200 OK"
    //   "method"    -> "POST" (solo en requests)
    //   "path"      -> "/answer" (solo en requests)
    //   "X-Type"    -> "QUESTION" (solo en responses)
    //   "Content-Length" -> "35"
    //   "body"      -> contenido del body
    public static Map<String, String> parse(BufferedReader reader) throws IOException {
        Map<String, String> resultado = new HashMap<>();

        // Leer primera linea (request line o status line)
        String primeraLinea = reader.readLine();
        if (primeraLinea == null) {
            return null; // Conexion cerrada
        }
        resultado.put("firstLine", primeraLinea);

        // Detectar si es request o response
        if (primeraLinea.startsWith("HTTP/")) {
            // Es una respuesta: HTTP/1.1 200 OK
            resultado.put("tipo", "response");
        } else {
            // Es una peticion: POST /answer HTTP/1.1
            resultado.put("tipo", "request");
            String[] partes = primeraLinea.split(" ");
            if (partes.length >= 2) {
                resultado.put("method", partes[0]);
                resultado.put("path", partes[1]);
            }
        }

        // Leer headers hasta linea vacia
        String linea;
        while ((linea = reader.readLine()) != null && !linea.isEmpty()) {
            int separador = linea.indexOf(": ");
            if (separador > 0) {
                String clave = linea.substring(0, separador);
                String valor = linea.substring(separador + 2);
                resultado.put(clave, valor);
            }
        }

        // Leer body segun Content-Length
        String contentLength = resultado.get("Content-Length");
        if (contentLength != null) {
            int length = Integer.parseInt(contentLength);
            if (length > 0) {
                char[] bodyChars = new char[length];
                int leidos = 0;
                while (leidos < length) {
                    int r = reader.read(bodyChars, leidos, length - leidos);
                    if (r == -1) break;
                    leidos += r;
                }
                resultado.put("body", new String(bodyChars, 0, leidos));
            } else {
                resultado.put("body", "");
            }
        } else {
            resultado.put("body", "");
        }

        return resultado;
    }
}
