package node;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import mecanismos.Semaphore;
import mensagens.DownloadTasksManagerCountDownLatch;
import mensagens.FileBlockRequestMessage;
import search.FileSearchResult;
import search.WordSearchMessage;

public class Node {
	private InetAddress address;
	private int port;
	private File folder;
	private File[] filesInFolder;
	private List<NodeHandler> connectedNodeHandlers; // Lista de NodeHandler para cada conexão
	private ServerSocket serverSocket;
	private SearchResultListener searchResultListener;
	private Map<String, List<Integer>> fileNameToPort = new HashMap<>(); // mapeia nome arquivo para portos dos nós que o contem
	private Map<String, Map<Long, String>> ownedFileNameToSizeAndHashes = new HashMap<>(); // Mapeia nome arquivo para o tamanho e hashes
	private Map<String, FileSearchResult> fileSearchResults = new HashMap<>();
	private final Map<String, DownloadTasksManagerCountDownLatch> activeDownloadsCountDownLatch = new HashMap<>();
	private final Semaphore semaphoreActiveDownloadsLatch = new Semaphore(1); // Exclusão mútua para activeDownloadsCountDownLatch
	private final Semaphore semaphoreFileNameToPort = new Semaphore(1); // Exclusão mútua para fileNameToPort

//	private final Map<String, DownloadTasksManager> activeDownloads = new HashMap<>(); // versao original do downloadTaskManager
//	private final Semaphore semaphoreActiveDownloads = new Semaphore(1); // versao original para exclusão mútua para activeDownloads do downloadTaskManager

	public Node(InetAddress inetAddress, int port, File folder) {
		this.address = inetAddress;
		this.port = port;
		this.folder = folder;
		this.connectedNodeHandlers = new ArrayList<>();

		if (!folder.exists()) {
			folder.mkdirs();
		}
		loadFolderContents();
	}

	public void loadFolderContents() {
		semaphoreFileNameToPort.acquire();
		if (folder.isDirectory()) {
			filesInFolder = folder.listFiles();
			if (filesInFolder != null) {
				for (File file : filesInFolder) {
					if (file.isFile() && !ownedFileNameToSizeAndHashes.containsKey(file.getName())) {
						String hash = calculateFileHash(file);
						long fileSize = file.length(); // Obtém o tamanho correto do arquivo
						System.out.println("Arquivo encontrado: " + file.getName() + " (Tamanho: " + fileSize
								+ " bytes, Hash: " + hash + ")");
						Map<Long, String> sizeAndHash = new HashMap<>();
						sizeAndHash.put(fileSize, hash); // Armazena o tamanho e a hash do arquivo
						ownedFileNameToSizeAndHashes.put(file.getName(), sizeAndHash); // Mapeia o nome do arquivo para
																						// o tamanho e hash
					}
				}
			} else {
				System.out.println("Nenhum arquivo encontrado na pasta.");
			}
		}
		semaphoreFileNameToPort.release();
	}

	// Método para calcular a hash:
	private String calculateFileHash(File file) {
		try (FileInputStream fis = new FileInputStream(file)) {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] bytes = new byte[1024];
			int bytesRead;
			while ((bytesRead = fis.read(bytes)) != -1) {
				digest.update(bytes, 0, bytesRead);
			}
			byte[] hashBytes = digest.digest();
			StringBuilder hashString = new StringBuilder();
			for (byte b : hashBytes) {
				hashString.append(String.format("%02x", b));
			}
			return hashString.toString();
		} catch (IOException | NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		return null;
	}

	// serve para avisar a gui dos resultados recebidos
	public void notifySearchResults(List<FileSearchResult> results) {
		semaphoreFileNameToPort.acquire(); // Espera pelo recurso

		try {
			for (FileSearchResult result : results) {
				String fileName = result.getFileName();
				int ownerPort = result.getOwnerNodePort();
				System.out.println("Recebi o resultado " + fileName + " com tamanho " + result.getFileSize()
						+ " na porta " + ownerPort);

				// Inicializa a lista de portas para o arquivo, se necessário
				fileNameToPort.putIfAbsent(fileName, new ArrayList<>());

				// Adiciona a porta somente se ainda não estiver associada ao arquivo
				List<Integer> ports = fileNameToPort.get(fileName);
				if (!ports.contains(ownerPort)) {
					ports.add(ownerPort);
				}

				addFileSearchResult(fileName, result);
			}
		} finally {
			semaphoreFileNameToPort.release(); // Libera o recurso
		}

		// Notifica a GUI
		if (searchResultListener != null) {
			searchResultListener.onSearchResultsReceived(results);
		}
	}

	public void start() {
		try {
			serverSocket = new ServerSocket(port);
			System.out.println("Nó iniciado em " + address + ":" + port);
			new Thread(this::listenForConnections).start();
		} catch (IOException e) {
			System.out.println("Erro ao iniciar o servidor no nó: " + e.getMessage());
		}
	}

	private void listenForConnections() {
		while (!serverSocket.isClosed()) {
			try {
				Socket clientSocket = serverSocket.accept();

				NodeHandler handler = new NodeHandler(clientSocket, this);
				handler.initializeStreamsForServer();
				handler.start();
			} catch (IOException e) {
				System.err.println("Erro ao aceitar conexão: " + e.getMessage());
			}
		}
	}

	public void connectToNode(InetAddress addressToConnect, int portToConnect) {
		try {
			if (addressToConnect.equals(address) && portToConnect == port) {
				System.out.println("Não é possível conectar ao próprio nó");
				return;
			}

			Socket socket = new Socket(addressToConnect, portToConnect);
			System.out.println("Conectado ao nó em " + addressToConnect + ":" + portToConnect);

			NodeHandler handler = new NodeHandler(socket, this);
			handler.initializeStreamsForClient();
			handler.sendConnectionRequest(address, port, addressToConnect, portToConnect);
			handler.start();
		} catch (IOException e) {
			System.err.println(
					"Falha na conexão com o nó em " + addressToConnect + ":" + portToConnect + " - " + e.getMessage());
		}
	}

	// Envia broadcast da um pedido de wordSearchRequest para todos os nos conectados a este no
	public void broadcastSearchRequest(String keyword) {
	    WordSearchMessage searchMessage = new WordSearchMessage(keyword, address, port);
	    semaphoreFileNameToPort.acquire(); 
	    try {
	        fileNameToPort.clear(); // Limpa o mapa antes de uma nova pesquisa, 
	        //para caso algum no ja nao tenha o ficheiro e para nao ficar com os resultados da pesquisa anterior
	    } finally {
	    	semaphoreFileNameToPort.release();
	    }
	    for (NodeHandler handler : connectedNodeHandlers) {
	        handler.sendMessage(searchMessage);
	    }
	}

	// Encerra o servidor e todas as conexões
	public void shutdown() {
		try {
			for (NodeHandler handler : connectedNodeHandlers) {
				handler.closeConnection();
			}
			connectedNodeHandlers.clear();

			if (serverSocket != null && !serverSocket.isClosed()) {
				serverSocket.close();
				System.out.println("Servidor no nó encerrado.");
			}
		} catch (IOException e) {
			System.out.println("Erro ao encerrar o servidor: " + e.getMessage());
		}
	}

	// Método para buscar o nome do arquivo em posse a partir do hash
	public String getOwnedFileNameForHash(String hash) {
		for (Map.Entry<String, Map<Long, String>> entry : ownedFileNameToSizeAndHashes.entrySet()) {
			for (String fileHash : entry.getValue().values()) {
				if (fileHash.equals(hash)) { // Verifica se o valor (hash) corresponde
					return entry.getKey(); // Retorna o nome do arquivo
				}
			}
		}
		return null; // Retorna null se não encontrar o hash
	}
	
	// Método para ir buscar a hash pelo nome de um arquivo que nao tens mas que recebeste uma filesearchresult
	public String getHashForFileNameInFileSearchResults(String fileName) {
		for (FileSearchResult result : fileSearchResults.values()) {
			if (result.getFileName().equals(fileName)) {
				return result.getFileHash();
			}
		}
		return null;
	}
	
	// Método para ir buscar o nome pela hash de um arquivo que nao tens mas que recebeste uma filesearchresult
	public String getFileNameForHashInFileSearchResults(String hash) {
		for (FileSearchResult result : fileSearchResults.values()) {
			if (result.getFileHash().equals(hash)) {
				return result.getFileName();
			}
		}
		return null;
	}
	// Método para ir buscar o tamanho pela hash de um arquivo que nao tens mas que recebeste uma filesearchresult
	public long getFileSizeForHashInFileSearchResults(String hash) {
		for (FileSearchResult result : fileSearchResults.values()) {
			if (result.getFileHash().equals(hash)) {
				return result.getFileSize();
			}
		}
		return 0;
	}

	public void requestBlockFromNode(InetAddress nodeAddress, int port, FileBlockRequestMessage request)
			throws IOException {
		for (NodeHandler handler : connectedNodeHandlers) {
			if (handler.getRemoteAddress().equals(nodeAddress) && handler.getRemotePort() == port) {
				handler.sendMessage(request);
				return;
			}
		}
		System.out.println("Erro: Nó " + nodeAddress + ":" + port + " não está conectado.");
		throw new IOException("Nó " + nodeAddress + ":" + port + " não está conectado.");
	}

	// Método para armazenar os resultados da pesquisa
	public void addFileSearchResult(String filename, FileSearchResult result) {
		if (!fileSearchResults.containsKey(filename))
			fileSearchResults.put(result.getFileName(), result);
	}

	// So serve para as mensagens aparecerem na consola correta
	public void debug(String messsage) {
		System.out.println(messsage);
	}

	public List<NodeHandler> getConnectedNodeHandlers() {
		return connectedNodeHandlers;
	}

	// Método para obter o mapa de tamanho e hash de cada arquivo em posse
	public Map<String, Map<Long, String>> getOwnedFileNameToSizeAndHashes() {
		return ownedFileNameToSizeAndHashes;
	}

	public Map<String, List<Integer>> getFileNameToPort() {
		return fileNameToPort;
	}

	public InetAddress getAddress() {
		return address;
	}

	public int getPort() {
		return port;
	}

	public File getFolder() {
		return folder;
	}

	public Map<String, FileSearchResult> getFileSearchResults() {
		return fileSearchResults;
	}

	public File getFileInFolder(String fileName) {
		for (int i = 0; i < filesInFolder.length; i++) {
			if (filesInFolder[i].getName().equals(fileName)) {
				return filesInFolder[i];
			}
		}
		return null;
	}

	public Semaphore getSemaphoreFileNameToPort() {
		return semaphoreFileNameToPort;
	}

	public void addNodeHandlers(NodeHandler handler) {
		connectedNodeHandlers.add(handler);
	}

	public void setSearchResultListener(SearchResultListener listener) {
		this.searchResultListener = listener;
	}
	
	public void registerDownloadManagerLatch(List<String> fileNames, DownloadTasksManagerCountDownLatch manager) {
	    semaphoreActiveDownloadsLatch.acquire();
	    try {
	        for (String fileName : fileNames) {
	            activeDownloadsCountDownLatch.put(fileName, manager);
	        }
	    } finally {
	        semaphoreActiveDownloadsLatch.release();
	    }
	}

	public DownloadTasksManagerCountDownLatch getDownloadManagerLatch(String fileName) {
	    semaphoreActiveDownloadsLatch.acquire();
	    try {
	        return activeDownloadsCountDownLatch.get(fileName);
	    } finally {
	        semaphoreActiveDownloadsLatch.release();
	    }
	}

	public void removeDownloadManagerLatch(String fileName) {
	    semaphoreActiveDownloadsLatch.acquire();
	    try {
	       activeDownloadsCountDownLatch.remove(fileName);
	    } finally {
	        semaphoreActiveDownloadsLatch.release();
	    }
	}

//	//versao original 
//	public void registerDownloadManager(List<String> fileNames, DownloadTasksManager manager) {
//	    semaphoreActiveDownloads.acquire();
//	    try {
//	        for (String fileName : fileNames) {
//	            activeDownloads.put(fileName, manager);
//	        }
//	    } finally {
//	        semaphoreActiveDownloads.release();
//	    }
//	}
//
//
//	public DownloadTasksManager getDownloadManager(String fileName) {
//	    semaphoreActiveDownloads.acquire();
//	    try {
//	        return activeDownloads.get(fileName);
//	    } finally {
//	        semaphoreActiveDownloads.release();
//	    }
//	}
//
//
//	public void removeDownloadManager(String fileName) {
//	    semaphoreActiveDownloads.acquire();
//	    try {
//	       activeDownloads.remove(fileName);
//	    } finally {
//	        semaphoreActiveDownloads.release();
//	    }
//	}
	
}
