package node;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import mecanismos.SynchronizedQueue;
import mensagens.DownloadTasksManagerCountDownLatch;
import mensagens.FileBlockAnswerMessage;
import mensagens.FileBlockRequestMessage;
import mensagens.NewConnectionRequest;
import search.FileSearchResult;
import search.WordSearchMessage;

public class NodeHandler extends Thread {
    private Socket socket;
    private ObjectInputStream in;
    private ObjectOutputStream out;
    private final ThreadPoolExecutor threadPool;
    private Node node;
    private InetAddress remoteAddress;
    private int remotePort;
    private InetAddress localAddress;
    private int localPort;
    
    // Fila sincronizada para gerir envios de mensagem para este nó 
    private final SynchronizedQueue<Object> messageQueue = new SynchronizedQueue<>();

    public NodeHandler(Socket socket, Node node) {
        this.socket = socket;
        this.node = node;
        localAddress = node.getAddress();
        localPort = node.getPort();
        this.threadPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(5);
        startMessageSenderThread();
    }
    
    private void startMessageSenderThread() {
        new Thread(() -> {
            while (!socket.isClosed()) {
                try {
                    Object message = messageQueue.dequeue(); // Usa o método bloqueante para obter mensagens
                    synchronized (this) {
                        out.writeObject(message);
                        out.flush();
                    }
                    node.debug("Mensagem enviada para " + remoteAddress + ":" + remotePort);
                } catch (IOException | InterruptedException e) {
                    node.debug("Erro ao enviar mensagem para " + remoteAddress + ":" + remotePort + ": " + e.getMessage());
                    break;
                }
            }
        }).start();
    }
	// Inicializa os canais na ordem inversa do "cliente" (quem envia o pedido)
    public void initializeStreamsForServer() throws IOException {
        in = new ObjectInputStream(socket.getInputStream());
        out = new ObjectOutputStream(socket.getOutputStream());
        out.flush();
    }

    // Inicializa os canais na ordem inversa do "servidor" (quem recebe o pedido)
    public void initializeStreamsForClient() throws IOException {
        out = new ObjectOutputStream(socket.getOutputStream());
        out.flush();
        in = new ObjectInputStream(socket.getInputStream());
    }

    @Override
    public void run() {
        try {
            processConnection();
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Erro de I/O com o nó: " + remoteAddress+":"+remotePort + " - " + e.getMessage());
        } finally {
            closeConnection();
        }
    }

    private void processConnection() throws IOException, ClassNotFoundException {
        while (true) {
            Object message = in.readObject();

            if (message instanceof List) {
                List<FileSearchResult> results = (List<FileSearchResult>) message;
                node.notifySearchResults(results);

            } else if (message instanceof WordSearchMessage) {
                WordSearchMessage searchMessage = (WordSearchMessage) message;
                List<FileSearchResult> results = FileSearchResult.searchInDirectory(
                        searchMessage, node.getAddress(), node.getPort(), node.getOwnedFileNameToSizeAndHashes());
                out.writeObject(results);

            } else if (message instanceof NewConnectionRequest) {
                NewConnectionRequest request = (NewConnectionRequest) message;
                remoteAddress = request.getAddress();
                remotePort = request.getPort();
                node.addNodeHandlers(this);
                node.debug("Handler adicionado " + request.getAddress() + ":" + request.getPort());
                node.debug("Pedido de conexão recebido de " + request.getAddress() + ":" + request.getPort());
                node.debug("Conexão confirmada com o nó em " + request.getAddress() + ":" + request.getPort());

            } else if (message instanceof FileBlockRequestMessage) {
                FileBlockRequestMessage blockRequest = (FileBlockRequestMessage) message;
                threadPool.submit(() -> processBlockRequest(blockRequest));
            } if (message instanceof FileBlockAnswerMessage) {
                FileBlockAnswerMessage blockAnswer = (FileBlockAnswerMessage) message;
                String fileHash = blockAnswer.getFileHash();
                String fileName = blockAnswer.getFilename();
              
                if (fileName != null) {
                    DownloadTasksManagerCountDownLatch manager = node.getDownloadManagerLatch(fileName);
 //                   DownloadTasksManager manager = node.getDownloadManager(fileName); //versao original
                    if (manager != null) {
                        manager.receberBloco(blockAnswer, remotePort); // Passar endereço e porta
                    } else {
                        node.debug("Nenhum manager de downloads ativo encontrado para " + fileName);
                    }
                } else {
                    node.debug("Nenhum arquivo correspondente encontrado para o hash: " + fileHash);
                }
            }
        }
    }

    private void processBlockRequest(FileBlockRequestMessage blockRequest) {
        try {
            String hash = blockRequest.getFileHash();
            long offset = blockRequest.getOffset();
            int length = blockRequest.getLength();
            String fileName = node.getOwnedFileNameForHash(hash);
            File requestedFile = node.getFileInFolder(fileName);

            if (requestedFile == null) {
                node.debug("Arquivo solicitado não encontrado: " + hash);
                return;
            }

            byte[] blockData = new byte[length];
            try (RandomAccessFile raf = new RandomAccessFile(requestedFile, "r")) {
                raf.seek(offset);
                int bytesRead = raf.read(blockData, 0, length);
                if (bytesRead < length) {
                    node.debug("Erro ao ler o bloco: bytes lidos insuficientes.");
                }
            }

            FileBlockAnswerMessage answer = new FileBlockAnswerMessage(hash,fileName,offset, blockData);
            enqueueMessage(answer);
        } catch (IOException e) {
            node.debug("Erro ao processar request do bloco: " + e.getMessage());
        }
    }

    private void enqueueMessage(Object message) {
        messageQueue.enqueue(message); // Adiciona mensagem à fila
        node.debug("Mensagem na fila para " + remoteAddress + ":" + remotePort);
    }
    
    //metodo para outras classes poderem enviar mensagens para o canal
    public void sendMessage(Object message) {
        try {
            out.writeObject(message);
            out.flush();
        } catch (IOException e) {
            System.err.println("Erro ao enviar mensagem para " + remoteAddress+":"+remotePort + ": " + e.getMessage());
        }
    }

    public void sendConnectionRequest(InetAddress clientAddress, int clientPort,InetAddress addressToConnect, int portToConnect) {
        try {
            if (out == null) {
                throw new IllegalStateException("Fluxo de saída não está configurado!");
            }
            NewConnectionRequest connectionRequest = new NewConnectionRequest(clientAddress, clientPort);
            out.writeObject(connectionRequest);
            remoteAddress = addressToConnect;
            remotePort = portToConnect;
            node.debug("Adicionei o seguinte handler " +remoteAddress+":"+remotePort);
            node.addNodeHandlers(this);
            node.debug("Pedido de conexão enviado para " + addressToConnect+":"+portToConnect);
        } catch (IOException e) {
        	System.err.println("Erro ao enviar pedido de conexão: " + e.getMessage());
        }
    }

    public void closeConnection() {
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();
            threadPool.shutdownNow();
            System.out.println("Conexão fechada com o nó: " + remoteAddress + ":" + remotePort);
        } catch (IOException e) {
            System.err.println("Erro ao fechar a conexão: " + e.getMessage());
        }
    }
    
    public InetAddress getRemoteAddress() {
        return remoteAddress;
    }

    public int getRemotePort() {
        return remotePort;
    }

	public int getLocalPort() {
		return localPort;
	}

	public InetAddress getLocalAddress() {
		return localAddress;
	}
}
