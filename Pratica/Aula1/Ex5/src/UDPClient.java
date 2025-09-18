import java.io.IOException;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Cliente UDP que envia um pedido "TIME" a um servidor e
 * exibe a hora recebida juntamente com a hora local.
 */
public class UDPClient {
    // Tamanho máximo da mensagem que o cliente consegue receber
    public static final int MAX_MSG_SIZE = 100;
    // Mensagem de pedido que será enviada ao servidor
    public static final String GET_TIME = "TIME";
    // Tempo limite de espera por resposta (em segundos)
    public static final int TIMEOUT = 6000; // 6 segundos

    public static void main(String[] args) {
        // Verifica se foram passados dois argumentos: endereço e porta do servidor
        if (args.length != 2) {
            System.out.println("Sintaxe: java UdpTimeClient <endereco_servidor> <porto_servidor>");
            return; // Termina se os argumentos estiverem incorretos
        }

        // Guarda os argumentos em variáveis
        String serverAddress = args[0];
        int serverPort = Integer.parseInt(args[1]);

        try (DatagramSocket socket = new DatagramSocket()) { // Cria socket UDP
            // Define timeout para não ficar bloqueado indefinidamente
            socket.setSoTimeout(TIMEOUT);

            // Obtém endereço IP do servidor (pode ser hostname ou IP)
            InetAddress serverInet = InetAddress.getByName(serverAddress);

            // Prepara dados do pedido "TIME" em bytes
            byte[] requestData = GET_TIME.getBytes();

            // Cria pacote UDP com os dados, o endereço e a porta do servidor
            DatagramPacket requestPacket = new DatagramPacket(
                    requestData,
                    requestData.length,
                    serverInet,
                    serverPort
            );

            socket.send(requestPacket); // Envia o pacote ao servidor

            // Prepara buffer e pacote para receber resposta do servidor
            byte[] buffer = new byte[MAX_MSG_SIZE];
            DatagramPacket responsePacket = new DatagramPacket(buffer, buffer.length);

            socket.receive(responsePacket); // Aguarda resposta (bloqueia até chegar ou até expirar timeout)

            // Converte bytes recebidos para String
            String serverTime = new String(responsePacket.getData(),
                    0,
                    responsePacket.getLength()
            );

            // Mostra hora recebida do servidor
            System.out.println("Hora no servidor: " + serverTime);

            // Também mostra hora local para comparação
            String localTime = new SimpleDateFormat("HH:mm:ss").format(new Date());
            System.out.println("Hora local: " + localTime);

        } catch (SocketTimeoutException e) {
            // Caso o tempo de espera seja excedido
            System.out.println("Timeout: não foi recebida resposta do servidor.");
        } catch (IOException e) {
            // Captura outros erros relacionados ao socket ou comunicação
            System.out.println("Erro no cliente UDP: " + e.getMessage());
        }
    }
}


