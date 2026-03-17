package search;

import java.io.Serializable;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FileSearchResult implements Serializable {
    private static final long serialVersionUID = 10L;
    private WordSearchMessage wsm; // Mensagem de pesquisa associada
    private String fileHash; // Hash do arquivo (
    private long fileSize; // Tamanho do arquivo
    private String fileName; // Nome do arquivo
    private InetAddress nodeAddress; // Endereço do nó que possui o arquivo
    private int nodePort; // Porto do nó que possui o arquivo

    // Construtor principal
    public FileSearchResult(WordSearchMessage wsm, String fileHash, long fileSize, String fileName,InetAddress nodeAddress,int nodePort) {
        this.wsm = wsm;
        this.fileHash = fileHash;
        this.fileSize = fileSize;
        this.fileName = fileName;
        this.nodeAddress = nodeAddress;
        this.nodePort = nodePort;
    }

    // Método estático para pesquisar arquivos na pasta
    public static List<FileSearchResult> searchInDirectory(WordSearchMessage wsm, InetAddress nodeAddress, int nodePort, Map<String, Map<Long, String>> fileNameToSizeAndHashes) {
        List<FileSearchResult> results = new ArrayList<>();
        String searchKey = wsm.getKey(); // Palavra-chave da pesquisa

        // Itera sobre o mapa de arquivos e os seus tamanhos e hashes
        for (Map.Entry<String, Map<Long, String>> entry : fileNameToSizeAndHashes.entrySet()) { 
            String fileName = entry.getKey();
            // Obtém o hash (primeiro valor do mapa interno)
            String fileHash = entry.getValue().values().iterator().next(); 

            // Verifica se o nome do arquivo contém a palavra-chave
            if (fileName.contains(searchKey)) {
                long fileSize = entry.getValue().keySet().iterator().next();  // Pega o tamanho do arquivo (primeira chave do mapa interno)

                results.add(new FileSearchResult(
                    wsm,
                    fileHash,
                    fileSize,
                    fileName,
                    nodeAddress,
                    nodePort
                ));
            }
        }

        return results;
    }

    @Override
    public String toString() {
    	return String.format("FileSearchResult[fileName=%s, fileHash=%s, fileSize=%d, nodeAddress=%s, nodePort=%d]",
    			fileName, fileHash, fileSize, nodeAddress.getHostAddress(), nodePort);
    }
    
    // Getters
    public WordSearchMessage getWsm() {
        return wsm;
    }

    public String getFileHash() {
        return fileHash;
    }

    public long getFileSize() {
        return fileSize;
    }

    public String getFileName() {
        return fileName;
    }

    public InetAddress getOwnerNodeAddress() {
        return nodeAddress;
    }

    public int getOwnerNodePort() {
        return nodePort;
    }

}
