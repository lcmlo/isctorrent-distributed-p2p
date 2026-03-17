package mensagens;
//
//import java.io.File;
//import java.io.FileOutputStream;
//import java.io.IOException;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//
//import gui.GUI;
//import mecanismos.Semaphore;
//import mecanismos.SynchronizedBuffer;
//import mecanismos.SynchronizedQueue;
//import node.Node;
//
//public class DownloadTasksManager {
//	private final SynchronizedBuffer buffer;
//	private final List<String> fileNames;
//	private final File folder;
//	private final GUI parentGUI;
//	private final Node currentNode;
//	private final long startTime;
//	private final Map<String, byte[][]> fileDataMap = new HashMap<>();
//	private final List<Integer> totalBlocksList;
//	private final Map<Integer, Map<String, Integer>> portoDeFornecedoresParaBlocosPorFicheiro = new HashMap<>();
//	private final Semaphore semaphore = new Semaphore(1);
//
//	public DownloadTasksManager(List<String> fileNames, File folder, GUI parentGUI,
//   		 List<Integer> totalBlocksList, Map<String, List<Integer>> portsWithFile) {
//		this.buffer = new SynchronizedBuffer(100000); // cabe 1 GB = 10240 * 100000
//		this.fileNames = fileNames;
//		this.folder = folder;
//		this.parentGUI = parentGUI;
//		this.currentNode = parentGUI.getNode();
//		this.totalBlocksList = totalBlocksList;
//		this.startTime = System.currentTimeMillis();
//
//		   // Inicializar mapa 
//        for (int i = 0; i < totalBlocksList.size();i++) {
//            int totalBlocks = totalBlocksList.get(i);
//            fileDataMap.put(fileNames.get(i), new byte[totalBlocks][]);
//        }
//        criarBlockRequests(portsWithFile);
//	}
//
//	public void iniciarDownload() {
//		for (int i = 0; i < fileNames.size();i++) {
//			String fileName = fileNames.get(i);
//			int totalBlocks = totalBlocksList.get(i);
//			//Cria uma Thread principal para cada arquivo que o espera para salvar
//		new Thread(() -> {
//			try {
//				currentNode.debug("Iniciando o download do arquivo: " + fileName);
//	            byte[][] fileData = fileDataMap.get(fileName); // Obter o array para armazenar os blocos deste arquivo
//	            int blocksReceived = 0;
//				while (blocksReceived < totalBlocks) {
//                    FileBlockAnswerMessage mensagem = buffer.getMsg();
//
//                    // Verifica se a mensagem corresponde ao arquivo atual
//                    if (mensagem.getFilename().equals(fileName)) {
//                        int blockIndex = (int) (mensagem.getOffset() / FileBlockRequestMessage.getBlockSize());
//                        fileData[blockIndex] = mensagem.getData();
//                        blocksReceived++;
//
//                        currentNode.debug("Bloco recebido para " + fileName + ": " + blockIndex +
//                                " (" + blocksReceived + "/" + totalBlocks + ")");
//                    } else {
//                        // Mensagem não corresponde, reinsere no buffer para outra thread processar
//                        buffer.addMsg(mensagem);
//                    }
//                }
//
//				  // Após todos os blocos deste arquivo serem recebidos
//                currentNode.debug("Todos os blocos recebidos para " + fileName);
//                salvarArquivo(fileName, fileDataMap.get(fileName));
//                parentGUI.showDownloadCompleteMessage(fileName,
//                		portoDeFornecedoresParaBlocosPorFicheiro,
//                        System.currentTimeMillis() - startTime);
//
//            } catch (Exception e) {
//                currentNode.debug("Erro no download do arquivo " + fileName +" : " + e.getMessage());
//            }
//		}).start();
//		}
//	}
//	
//	private void criarBlockRequests(Map<String, List<Integer>> portsWithFile) {
//	    currentNode.debug("Criando blocos para os arquivos: " + fileNames);
//
//	    int blockSize = FileBlockRequestMessage.getBlockSize();
//
//	    // Inicializar filas de blocos por porta
//	    Map<Integer, SynchronizedQueue<FileBlockRequestMessage>> portQueues = new HashMap<>();
//
//	    try {
//	        // Para cada arquivo na lista
//	        for (int i = 0; i < fileNames.size(); i++) {
//	            String fileName = fileNames.get(i);
//	            int totalBlocks = totalBlocksList.get(i);
//	            List<Integer> availablePorts = portsWithFile.get(fileName);
//
//	            if (availablePorts == null || availablePorts.isEmpty()) {
//	                currentNode.debug("Erro: Nenhum porto disponível para o arquivo " + fileName);
//	                throw new IllegalStateException("Nenhum porto disponível para o arquivo " + fileName);
//	            }
//
//	            currentNode.debug("Arquivo " + fileName + " tem " + totalBlocks + " blocos. Portos disponíveis: " + availablePorts);
//
//	            // Inicializar filas para as portas associadas a este arquivo, se ainda não foram criadas
//	            for (int port : availablePorts) {
//	                portQueues.putIfAbsent(port, new SynchronizedQueue<>());
//	            }
//
//	            // Dividir blocos entre as portas
//	            String fileHash = currentNode.getHashForFileNameInFileSearchResults(fileName);
//	            long fileSize = currentNode.getFileSizeForHashInFileSearchResults(fileHash);
//	            for (int blockIndex = 1; blockIndex <= totalBlocks; blockIndex++) {
//	                long offset = (long) (blockIndex-1) * blockSize;
//	                int length = blockSize; 
//	                if(blockIndex == totalBlocks) {
//	                	length = (int) (fileSize - offset); 
//	                }
//
//	                if (fileHash == null) {
//	                    currentNode.debug("Erro: Hash do arquivo não encontrado para " + fileName);
//	                    throw new IllegalStateException("Hash do arquivo não encontrado para " + fileName);
//	                }
//	                int port = availablePorts.get(blockIndex % availablePorts.size());
//	                currentNode.debug("Atribui o bloco com indice "+blockIndex+" e tamanho "+length+" ao porto "+port +" sendo que existem "+totalBlocks + " no total" + " e o ficheiro tem tamanho " + fileSize);
//	                FileBlockRequestMessage request = new FileBlockRequestMessage(offset, length, fileHash);
//	                // Adicionar o pedido na fila do porto
//	                portQueues.get(port).enqueue(request);
//	            }
//	        }
//
//	        // Criar threads para cado porto (apenas uma thread por porta)
//	        for (Map.Entry<Integer, SynchronizedQueue<FileBlockRequestMessage>> entry : portQueues.entrySet()) {
//	            int port = entry.getKey();
//	            SynchronizedQueue<FileBlockRequestMessage> queue = entry.getValue();
//	            new Thread(() -> {
//	                currentNode.debug("Thread iniciada para o porto " + port);
//	                while (true) {
//	                    try {
//	                        FileBlockRequestMessage request = queue.dequeue();
//	                        currentNode.requestBlockFromNode(currentNode.getAddress(), port, request);
//	                        currentNode.debug("Request do bloco com offset " + request.getOffset() + " do arquivo "
//	                                + currentNode.getFileNameForHashInFileSearchResults(request.getFileHash()) + " enviado para o porto " + port);
//	                    } catch (InterruptedException e) {
//	                        Thread.currentThread().interrupt();
//	                        currentNode.debug("Thread para o porto " + port + " interrompida.");
//	                        break;
//	                    } catch (IOException e) {
//	                        currentNode.debug("Erro ao enviar request para o porto " + port + ": " + e.getMessage());
//	                    }
//	                }
//	            }).start();
//	        }
//	    } catch (Exception e) {
//	        currentNode.debug("Erro durante a criação dos requests: " + e.getMessage());
//	        e.printStackTrace();
//	    }
//	}
//
//	private void salvarArquivo(String fileName, byte[][] fileData) throws IOException {
//        File outputFile = new File(folder, fileName);
//        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
//            for (byte[] block : fileData) {
//                fos.write(block);
//            }
//        }
//        currentNode.debug("Arquivo salvo com sucesso: " + outputFile.getAbsolutePath());
//        currentNode.loadFolderContents(); // Atualizar lista de arquivos do nó
//        currentNode.removeDownloadManager(fileName); // Remover o gerenciador
//    }
//
//    public void receberBloco(FileBlockAnswerMessage mensagem, int port) {
//        try {
//            buffer.addMsg(mensagem); // Adiciona a mensagem ao buffer
//
//            semaphore.acquire();
//            portoDeFornecedoresParaBlocosPorFicheiro
//                .computeIfAbsent(port, k -> new HashMap<>()) // Inicializa o mapa interno se necessário
//                .merge(mensagem.getFilename(), 1, Integer::sum); // Incrementa a contagem para o ficheiro específico
//            currentNode.debug("Bloco adicionado ao buffer: " + mensagem.getOffset() +
//                              ", fornecedor: " + currentNode.getAddress() + ":" + port +
//                              ", ficheiro: " + mensagem.getFilename());
//        } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
//            currentNode.debug("Erro ao adicionar bloco ao buffer: " + e.getMessage());
//        } finally {
//            semaphore.release();
//        }
//    }
//
//}
