import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.*;

/**
 * Cliente UDP que solicita um ficheiro a um servidor e o grava localmente.
 *
 * Fluxo:
 * 1. Envia o nome do ficheiro ao servidor via UDP.
 * 2. Recebe blocos de dados do servidor.
 * 3. Envia ACK (confirmação) após cada bloco.
 * 4. Continua até receber um bloco vazio (EOF).
 *
 */
public class UDPClient {

    public static final int MAX_SIZE = 4000; // tamanho máximo de cada bloco de dados
    public static final int TIMEOUT = 1000;    // tempo limite para esperar dados (em segundos)
    public static final String ACK = "ack";  // mensagem de confirmação enviada ao servidor

    public static void main(String[] args) {
        File localDirectory;           // diretório local onde o ficheiro será guardado
        String fileName, localFilePath = null;
        InetAddress serverAddr;        // endereço do servidor
        int serverPort;                // porta UDP do servidor
        DatagramPacket packet;         // pacote usado para enviar/receber dados
        int nChunks = 0;               // número de blocos recebidos
        int receivedBytes = 0;         // total de bytes recebidos

        // Validação de argumentos
        if(args.length != 4){
            System.out.println("Sintaxe: java UDPClient serverAddress serverUdpPort fileToGet localDirectory");
            return;
        }

        // Nome do ficheiro solicitado
        fileName = args[2].trim();
        //Diretório onde será guardado o ficheiro
        localDirectory = new File(args[3].trim());

        // Verificações do diretório
        if(!localDirectory.exists()){
            System.out.println("A directoria " + localDirectory + " nao existe!");
            return;
        }

        if(!localDirectory.isDirectory()){
            System.out.println("O caminho " + localDirectory + " nao se refere a uma directoria!");
            return;
        }

        if(!localDirectory.canWrite()){
            System.out.println("Sem permissoes de escrita na directoria " + localDirectory);
            return;
        }

        // Obtém caminho canónico do ficheiro a ser criado localmente
        try{
            localFilePath = localDirectory.getCanonicalPath()+File.separator+fileName;
        }catch(IOException e){
            System.out.println("Ocorreu a excepcao {" + e +"} ao obter o caminho canonico para o ficheiro local!");
            return;
        }

        // Usa try-with-resources para garantir fecho do ficheiro e socket
        try(FileOutputStream localFileOutputStream = new FileOutputStream(localFilePath);
            DatagramSocket socket = new DatagramSocket()) {

            System.out.println("Ficheiro " + localFilePath + " criado.");

            // Resolve o endereço do servidor e lê a porta
            serverAddr = InetAddress.getByName(args[0]);
            serverPort = Integer.parseInt(args[1]);

            socket.setSoTimeout(TIMEOUT); // Define o timeout do socket

            // Prepara pacote de ACK que será enviado após cada bloco recebido
            DatagramPacket ackPacket = new DatagramPacket(ACK.getBytes(), ACK.length(), serverAddr, serverPort);

            // Envia nome do ficheiro ao servidor
            packet = new DatagramPacket(fileName.getBytes(), fileName.length(), serverAddr, serverPort);

            socket.send(packet); // Envia pacote

            System.out.println("Socket receive buffer size: " + socket.getReceiveBufferSize() + " bytes");

            boolean moreChunks = true; // indica se ainda há dados a receber

            do{
                // Prepara pacote para receber próximo bloco de dados
                packet = new DatagramPacket(new byte[MAX_SIZE], MAX_SIZE);
                socket.receive(packet); // Bloqueia até receber dados ou dar timeout

                // Se o pacote recebido tiver tamanho > 0, ainda há dados para processar
                moreChunks = packet.getLength() > 0;

                // Confirma que o pacote vem do servidor esperado
                if(packet.getPort() == serverPort && packet.getAddress().equals(serverAddr)){
                    receivedBytes += packet.getLength();
                    ++nChunks;

                    socket.send(ackPacket); // Envia ACK de volta ao servidor

                    // Escreve os dados recebidos no ficheiro local
                    localFileOutputStream.write(packet.getData(), 0, packet.getLength());
                }

            }while(moreChunks); // termina quando o servidor envia um pacote vazio (EOF)

            System.out.println("Transferencia concluida.");

        }catch(UnknownHostException e){
            System.out.println("Destino desconhecido:\n\t"+e);
        }catch(NumberFormatException e){
            System.out.println("O porto do servidor deve ser um inteiro positivo:\n\t"+e);
        }catch(SocketTimeoutException e){
            System.out.println("Timeout de recepcao");
        }catch(SocketException e){
            System.out.println("Ocorreu um erro ao nivel do socket UDP:\n\t"+e);
        }catch(IOException e){
            System.out.println("Ocorreu um erro no acesso ao socket ou ao ficheiro local " + localFilePath +":\n\t"+e);
        }

        // Mostra estatísticas no final
        System.out.format("Foram recebidos %d blocos, incluindo o final vazio, " +
                "num total de %d bytes\r\n", nChunks, receivedBytes);
    }
}