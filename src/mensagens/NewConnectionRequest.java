package mensagens;

import java.io.Serializable;
import java.net.InetAddress;

public class NewConnectionRequest implements Serializable { 
   
	private static final long serialVersionUID = 1L;
	private InetAddress address;
    private int port;

    public NewConnectionRequest(InetAddress address, int port) {
        this.address = address;
        this.port = port;
    }

    public InetAddress getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    @Override
    public String toString() {
        return "NewConnectionRequest{address='" + address + "', port=" + port + "}";
    }
}

