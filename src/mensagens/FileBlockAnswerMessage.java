package mensagens;

import java.io.Serializable;

public class FileBlockAnswerMessage implements Serializable {
    private static final long serialVersionUID = 2L;
    private String fileHash; // Hash do arquivo para identificar de qual arquivo é o bloco
    private long offset; // Deslocamento do bloco dentro do arquivo
    private byte[] data; // Dados binários do bloco
    private String filename;

    public FileBlockAnswerMessage(String fileHash,String filename, long offset, byte[] data) {
        this.fileHash = fileHash;
        this.offset = offset;
        this.data = data;
        this.filename = filename;
    }

    public String getFileHash() {
        return fileHash;
    }

    public long getOffset() {
        return offset;
    }

    public byte[] getData() {
        return data;
    }
    
    public String getFilename() {
		return filename;
	}

	@Override
    public String toString() {
        return "FileBlockAnswerMessage{fileHash=" + fileHash + ", offset=" + offset + ", dataSize=" + data.length + "}";
    }
}
