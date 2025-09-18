import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.*;
import java.util.Date;

/**
 * Servidor UDP para envio de ficheiros.
 * Recebe o nome de um ficheiro via UDP e envia o seu conteúdo em blocos,
 * aguardando confirmações (ACK) do cliente a cada envio.
 *
 */
public class UDPServer {

    // Tamanho máximo do bloco de dados a enviar (em bytes)
    public static final int MAX_SIZE = 4000;
    // Tempo limite de espera para ACK (em segundos)
    public static final int TIMEOUT = 1000;

    // Mensagem esperada como confirmação (ACK) do cliente
    public static final String ACK = "ack";

    public static void main(String[] args){

        File localDirectory; // Diretório onde procurar os ficheiros
        String requestedFileName, requestedCanonicalFilePath = null;

        int listeningPort;
        DatagramPacket packet;

        // Buffer onde será lido cada bloco do ficheiro
        byte []fileChunk = new byte[MAX_SIZE];
        int nbytes;

        // Verifica se os argumentos são válidos
        if(args.length != 2){
            System.out.println("Sintaxe: java UDPServer listeningPort localRootDirectory");
            return;
        }

        // Diretório base onde os ficheiros estão armazenados
        localDirectory = new File(args[1]);

        // Validações de existência, tipo e permissões do diretório
        if(!localDirectory.exists()){
            System.out.println("A directoria " + localDirectory + " nao existe!");
            return;
        }

        if(!localDirectory.isDirectory()){
            System.out.println("O caminho " + localDirectory + " nao se refere a uma directoria!");
            return;
        }

        if(!localDirectory.canRead()){
            System.out.println("Sem permissoes de leitura na directoria " + localDirectory + "!");
            return;
        }

        // Porto de escuta para o servidor
        listeningPort = Integer.parseInt(args[0]);
        if(listeningPort <= 0)
            throw new NumberFormatException("Porto UDP de escuta indicado <= 0 (" + listeningPort + ")");

        // Cria o socket UDP para escutar pedidos
        try(DatagramSocket socket = new DatagramSocket(listeningPort) ) {

            System.out.println("Servidor iniciado...");

            // Loop infinito para atender pedidos continuamente
            while(true) {

                // Aguarda um pedido de cliente (nome do ficheiro)
                packet = new DatagramPacket(new byte[MAX_SIZE], MAX_SIZE);

                socket.receive(packet); // Aguarda resposta (bloqueia até chegar ou até expirar timeout)

                // Extrai o nome do ficheiro solicitado
                requestedFileName = new String(packet.getData()).trim();

                System.out.println("Recebido pedido para \"" + requestedFileName + "\" de " + packet.getAddress().getHostAddress() + ":" + packet.getPort());

                // Gera o caminho canónico do ficheiro solicitado
                requestedCanonicalFilePath = new File(requestedFileName).getCanonicalPath();

                // Verifica se o ficheiro está dentro do diretório base (evita acesso indevido a ficheiros fora da pasta)
                if (!requestedCanonicalFilePath.startsWith(localDirectory.getCanonicalPath() + File.separator)) {
                    System.out.println("Nao e' permitido aceder ao ficheiro " + requestedCanonicalFilePath + "!");
                    System.out.println("A directoria de base nao corresponde a " + localDirectory.getCanonicalPath() + "!");
                    continue;
                }

                // Tenta abrir o ficheiro para leitura
                try (FileInputStream requestedFileInputStream = new FileInputStream(requestedCanonicalFilePath)) {
                    System.out.println("Ficheiro " + requestedCanonicalFilePath + " aberto para leitura.");

                    int nChunks = 0;     // Contador de blocos enviados
                    int totalBytes = 0;  // Total de bytes enviados

                    do {
                        // Lê próximo bloco do ficheiro
                        nbytes = requestedFileInputStream.read(fileChunk);

                        if (nbytes == -1) { // EOF (end of file)
                            nbytes = 0; // Indica fim de transmissão
                        } else {
                            ++nChunks;
                            totalBytes += nbytes;
                        }

                        // Prepara o pacote com os dados lidos
                        packet.setData(fileChunk, 0, nbytes);
                        packet.setLength(nbytes);

                        socket.send(packet); // Envia o pacote para o cliente

                        // Aguarda confirmação (ACK) do cliente antes de enviar próximo bloco
                        waitAck(socket, packet.getAddress(), packet.getPort());


                    } while (nbytes > 0); // Continua até EOF

                    // Mostra estatísticas da transferência
                    System.out.format("Transferencia concluida (%d blocos num total de %d bytes)\r\n",
                            nChunks,totalBytes );

                } catch (Exception e) {
                    // Captura qualquer erro ao atender o pedido
                    System.out.println("Ocorreu uma excepcao ao atender o pedido atual:\n\t" + e);
                }
            } //while

        }catch(NumberFormatException e){
            System.out.println("O porto de escuta deve ser um inteiro positivo:\n\t"+e);
        }catch(SocketException e){
            System.out.println("Ocorreu uma excepcao ao nivel do socket UDP:\n\t"+e);
        }catch(FileNotFoundException e){
            System.out.println("Ocorreu a excepcao {" + e + "} ao tentar abrir o ficheiro " + requestedCanonicalFilePath + "!");
        }catch(IOException e){
            System.out.println("Ocorreu a excepcao de E/S: \n\t" + e);
        }

    }

    /**
     * Aguarda o recebimento de um pacote de ACK do cliente.
     * Se o tempo limite expirar, lança uma exceção.
     */
    private static void waitAck(DatagramSocket socket, InetAddress clientAddress, int clientPort) throws Exception {
        DatagramPacket ackPacket;
        boolean isAck;

        int timeout = TIMEOUT * 1000; // converte para milissegundos
        Long startTime = new Date().getTime();

        try {
            do {
                // Atualiza tempo restante para o timeout
                timeout = timeout - (int)(new Date().getTime() - startTime);
                if(timeout < 0)
                    throw new SocketTimeoutException();

                socket.setSoTimeout(TIMEOUT); // Configura timeout no socket

                // Prepara pacote para receber ACK
                ackPacket = new DatagramPacket(new byte[ACK.length()], ACK.length());

                socket.receive(ackPacket); // Recebe um pacote de um cliente

                // Verifica se o pacote contém a mensagem de ACK
                isAck = ACK.equalsIgnoreCase(new String(ackPacket.getData(), 0, ackPacket.getLength()));

            } while(ackPacket.getPort() != clientPort // Verifica se vem da mesma porta
                    || !ackPacket.getAddress().equals(clientAddress) // Verifica se vem do mesmo endereço
                    || !isAck); // Verifica se é de fato um ACK

        } catch(SocketTimeoutException e) {
            // Caso o tempo de espera esgote, lança exceção
            throw new Exception("Timeout ao aguardar rececao de um \"ack\"");
        } finally {
            // Desativa timeout (volta ao modo bloqueante)
            socket.setSoTimeout(0);
        }
    }
}