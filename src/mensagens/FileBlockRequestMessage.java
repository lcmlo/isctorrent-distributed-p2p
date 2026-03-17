package mensagens;

import java.io.Serializable;
import java.net.InetAddress;

public class FileBlockRequestMessage implements Serializable {
    private static final long serialVersionUID = 3L;
    private static final int BLOCK_SIZE = 10240; // Tamanho de cada bloco em bytes (10KB)
    private long offset; // Deslocamento do bloco dentro do arquivo
    private int length;  // Tamanho do bloco, pode ser menor que BLOCK_SIZE para o último bloco
    private String fileHash; // Hash do arquivo solicitado

    public FileBlockRequestMessage(long offset, int length, String fileHash) {
        this.offset = offset;
        this.length = length;
        this.fileHash = fileHash;
    }

    public long getOffset() {
        return offset;
    }

    public int getLength() {
        return length;
    }

    public String getFileHash() {
        return fileHash;
    }

    @Override
    public String toString() {
        return "FileBlockRequestMessage{offset=" + offset + ", length=" + length + ", fileHash=" + fileHash +"}"; 
    }

    public static int getBlockSize() {
        return BLOCK_SIZE;
    }
}
