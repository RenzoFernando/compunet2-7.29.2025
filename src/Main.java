// Archivo: src/Main.java

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Esta es la clase principal de tu servidor web.
 * Un servidor web es un programa que escucha peticiones de clientes (como navegadores web)
 * y les envía recursos (como páginas HTML, imágenes, etc.).
 */
public class Main {

    /**
     * El método init() arranca el servidor y lo pone en un estado de "escucha" infinita.
     * @throws IOException si hay un error al crear el ServerSocket.
     */
    public void init() throws IOException {
        // ServerSocket es como la "oreja" de tu servidor. Se queda escuchando en un puerto específico
        // en el computador. En este caso, el puerto 8080.
        // Los puertos son como "puertas" numeradas en una dirección IP para que diferentes
        // programas puedan comunicarse por la red al mismo tiempo.
        ServerSocket server = new ServerSocket(8080);

        // Este bucle infinito (while(true)) mantiene al servidor corriendo y escuchando
        // peticiones constantemente. Si no estuviera en un bucle, atendería una sola
        // petición y se apagaría.
        var isAlive = true;
        while (isAlive) {
            System.out.println("Esperando cliente...");

            // El método server.accept() es bloqueante. Esto significa que el programa se detiene aquí
            // y espera hasta que un cliente (un navegador) se conecte.
            // Cuando un cliente se conecta, accept() devuelve un objeto Socket.
            var socket = server.accept();

            // El Socket representa la conexión individual y directa con ESE cliente.
            // A través de este socket, puedes enviar y recibir datos.
            System.out.println("¡Cliente conectado!");

            // En lugar de manejar la petición aquí mismo, se la pasamos a un "trabajador" (worker).
            // Esto es clave para que el servidor pueda atender a MÚLTIPLES clientes a la vez.
            dispatchWorker(socket);
        }
    }

    /**
     * Este método crea y lanza un nuevo hilo (Thread) para manejar la petición de un cliente.
     * Esto se llama "servidor multi-hilos". Permite que el bucle principal (en init)
     * vuelva inmediatamente a esperar por más clientes, mientras el nuevo hilo atiende
     * al cliente actual en paralelo.
     * @param socket La conexión con el cliente que se va a manejar.
     */
    public void dispatchWorker(Socket socket) {
        // Creamos un nuevo hilo. Un hilo es como un sub-proceso que puede ejecutar código
        // de forma concurrente al programa principal.
        new Thread(
                // Usamos una expresión lambda () -> { ... } para definir la tarea que el hilo ejecutará.
                // Es una forma corta y moderna de implementar la interfaz Runnable.
                () -> {
                    try {
                        // Dentro del hilo, llamamos al método que realmente procesará la petición.
                        handlerRequest(socket);
                    } catch (IOException e) {
                        // Si algo sale mal al manejar la petición, imprimimos el error.
                        throw new RuntimeException(e);
                    }
                }
        ).start(); // El método start() inicia la ejecución del hilo.
    }

    /**
     * Este método lee la petición HTTP del cliente y determina qué recurso se está pidiendo.
     * @param socket La conexión con el cliente.
     * @throws IOException Si hay un error de entrada/salida.
     */
    public void handlerRequest(Socket socket) throws IOException {

        // Para leer los datos que envía el cliente, obtenemos el "InputStream" del socket.
        // Lo envolvemos en un BufferedReader para poder leer línea por línea de forma eficiente.
        var reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        // Leemos la petición del cliente línea por línea.
        // La primera línea de una petición GET es la más importante.
        // Tiene el formato: "GET /recurso HTTP/1.1"
        // Ejemplo: "GET /index.html HTTP/1.1"
        String requestLine = reader.readLine();

        // Verificamos que la línea no sea nula y que sea una petición GET
        if (requestLine != null && requestLine.startsWith("GET")) {
            // Hacemos un split por los espacios para obtener las partes.
            // "GET", "/recurso", "HTTP/1.1"
            // La parte [1] es el recurso que se pide.
            // Usamos un ternario para manejar el caso de la raíz "/" -> "index.html"
            String resource = requestLine.split(" ")[1];
            String resourceName = resource.equals("/") ? "index.html" : resource.substring(1);

            System.out.println("El cliente esta pidiendo: " + resourceName);

            // Una vez que sabemos qué archivo quiere el cliente, llamamos al método
            // que se encargará de buscarlo y enviárselo.
            sendResponse(socket, resourceName);
        }

        // Cerramos el socket al finalizar, ya que la conexión es por petición.
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }

    /**
     * Este método busca el archivo solicitado en el sistema de archivos y lo envía
     * al cliente como una respuesta HTTP.
     * @param socket La conexión con el cliente.
     * @param resourceName El nombre del archivo solicitado (ej: "index.html").
     * @throws IOException Si hay un error de entrada/salida.
     */
    public void sendResponse(Socket socket, String resourceName) throws IOException {
        // Creamos un objeto File para representar la ruta al archivo.
        // Se asume que los archivos están en una carpeta llamada "resourses" en la raíz del proyecto.
        var res = new File("resourses/" + resourceName);

        // Obtenemos el stream de salida para escribir la respuesta al cliente.
        OutputStream outputStream = socket.getOutputStream();

        // Verificamos si el archivo realmente existe y no es un directorio.
        if (res.exists() && !res.isDirectory()) {
            // Si existe, preparamos la respuesta HTTP 200 OK.

            // --- CONSTRUCCIÓN DE LA RESPUESTA HTTP ---
            // Una respuesta HTTP tiene 3 partes:
            // 1. Línea de estado
            // 2. Cabeceras (Headers)
            // 3. Cuerpo del mensaje (Body)

            String statusLine = "HTTP/1.1 200 OK\r\n";
            String contentType = "Content-Type: " + getContentType(resourceName) + "\r\n";
            String contentLength = "Content-Length: " + res.length() + "\r\n";
            String connection = "Connection: close\r\n";

            outputStream.write(statusLine.getBytes());
            outputStream.write(contentType.getBytes());
            outputStream.write(contentLength.getBytes());
            outputStream.write(connection.getBytes());
            outputStream.write("\r\n".getBytes()); // Línea en blanco para separar cabeceras y cuerpo

            // Enviamos el contenido del archivo
            FileInputStream fis = new FileInputStream(res);
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            fis.close();

        } else {
            // Si el archivo no existe, buscamos y enviamos una página 404 personalizada.
            System.out.println("No existe el recurso " + resourceName + ". Enviando respuesta 404.");
            File notFoundFile = new File("resourses/404.html");
            String statusLine = "HTTP/1.1 404 Not Found\r\n";
            String contentTypeHeader = "Content-Type: text/html\r\n";
            String connectionHeader = "Connection: close\r\n";

            // Verificamos si tenemos un archivo 404.html personalizado
            if (notFoundFile.exists()) {
                String contentLengthHeader = "Content-Length: " + notFoundFile.length() + "\r\n";
                outputStream.write(statusLine.getBytes());
                outputStream.write(contentTypeHeader.getBytes());
                outputStream.write(contentLengthHeader.getBytes());
                outputStream.write(connectionHeader.getBytes());
                outputStream.write("\r\n".getBytes());

                // Enviamos el contenido del archivo 404.html
                FileInputStream fis = new FileInputStream(notFoundFile);
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                fis.close();
            } else {
                // Si no hay archivo 404.html, enviamos una respuesta genérica.
                String responseBody = "<html><body><h1>404 Not Found</h1><p>El recurso solicitado no existe.</p></body></html>";
                String contentLengthHeader = "Content-Length: " + responseBody.length() + "\r\n";

                outputStream.write(statusLine.getBytes());
                outputStream.write(contentTypeHeader.getBytes());
                outputStream.write(contentLengthHeader.getBytes());
                outputStream.write(connectionHeader.getBytes());
                outputStream.write("\r\n".getBytes());
                outputStream.write(responseBody.getBytes());
            }
        }

        outputStream.flush();
        outputStream.close();
    }

    /**
     * Determina el tipo de contenido (MIME type) basado en la extensión del archivo.
     * @param fileName El nombre del archivo.
     * @return El MIME type como String.
     */
    private String getContentType(String fileName) {
        if (fileName.endsWith(".html") || fileName.endsWith(".htm")) {
            return "text/html";
        } else if (fileName.endsWith(".css")) {
            return "text/css";
        } else if (fileName.endsWith(".js")) {
            return "application/javascript";
        } else if (fileName.endsWith(".ico")) {
            return "image/x-icon";
        } else if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (fileName.endsWith(".png")) {
            return "image/png";
        } else if (fileName.endsWith(".gif")) {
            return "image/gif";
        } else {
            return "application/octet-stream"; // Tipo genérico para archivos binarios
        }
    }


    /**
     * El punto de entrada del programa.
     * @param args Argumentos de la línea de comandos (no se usan aquí).
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        Main main = new Main();
        main.init();
    }
}
