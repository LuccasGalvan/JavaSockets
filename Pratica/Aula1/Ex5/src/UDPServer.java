import java.io.IOException;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Servidor UDP simples que responde com a hora atual sempre que
 * recebe o pedido "TIME" de um cliente.
 */
public class UDPServer {
    // Tamanho máximo das mensagens que o servidor aceita
    public static final int MAX_MSG_SIZE = 100;
    // String que o cliente deve enviar para pedir a hora
    public static final String GET_TIME = "TIME";

    public static void main(String[] args) {
        // Verifica se foi passado o argumento da porta
        if (args.length != 1) {
            System.out.println("Sintaxe: java UdpTimeServer <porto>");
            return; // Termina se não foi passada porta
        }

        int port = Integer.parseInt(args[0]); //Lê e converte o argumento para inteiro (porta onde o servidor vai escutar)

        try (DatagramSocket socket = new DatagramSocket(port)/* */) { // Cria um socket UDP e associa à porta especificada
            System.out.println("Servidor UDP iniciado na porta " + port);

            // Buffer para armazenar os dados recebidos
            byte[] buffer = new byte[MAX_MSG_SIZE];

            DatagramPacket packet = new DatagramPacket(buffer, buffer.length); // Cria um pacote genérico para receber pedidos dos clientes

            // Loop infinito para atender pedidos continuamente
            while (true) {
                System.out.println("À espera de pedidos...");

                socket.receive(packet); // Aguarda bloqueado até receber um pacote de um cliente

                // Converte os bytes recebidos para String
                String request = new String(packet.getData(), 0, packet.getLength());

                // Verifica se o pedido é exatamente "TIME"
                if (!GET_TIME.equals(request.trim())) {
                    System.out.println("Pedido inesperado: \"" + request + "\" ignorado.");
                    continue; // Volta a aguardar outro pedido
                }

                // Obtém hora atual do sistema no formato hh:mm:ss
                String currentTime = new SimpleDateFormat("HH:mm:ss").format(new Date());
                byte[] responseData = currentTime.getBytes();

                // Prepara pacote de resposta com a hora e o endereço/porta do cliente
                DatagramPacket responsePacket = new DatagramPacket(
                        responseData,
                        responseData.length,
                        packet.getAddress(), // Endereço de origem do pedido
                        packet.getPort()     // Porta de origem do pedido
                );

                socket.send(responsePacket); //Envia a resposta ao cliente

                // Mostra no ecrã o que foi enviado e para quem
                System.out.printf("\"%s\" enviado para %s:%d%n",
                        currentTime,
                        packet.getAddress().getHostAddress(),
                        packet.getPort());
            }
        } catch (IOException e) {
            // Caso ocorra erro no socket ou envio/receção
            System.err.println("Erro no servidor UDP: " + e.getMessage());
        }
    }
}
