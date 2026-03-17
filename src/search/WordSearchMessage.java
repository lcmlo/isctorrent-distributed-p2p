package search;

import java.io.Serializable;
import java.net.InetAddress;

public class WordSearchMessage implements Serializable {
    private static final long serialVersionUID = 2L;
    private String key; // Palavra-chave para pesquisa
    private InetAddress address; // Endereço do nó que originou a mensagem
    private int port; // Porta do nó que originou a mensagem

    public WordSearchMessage(String key, InetAddress address, int port) {
        this.key = key;
        this.address = address;
        this.port = port;
    }

    public String getKey() {
        return key;
    }

    public InetAddress getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }
}
