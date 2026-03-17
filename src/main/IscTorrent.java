package main;

import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;

import gui.GUI;
import node.Node;

public class IscTorrent {
    public static void main(String[] args) throws UnknownHostException {
        if (args.length < 2) {
            System.out.println("java IscTorrent <port> <folder>");
            return;
        }

        InetAddress inetAddress = InetAddress.getByName(null);
        int port = Integer.parseInt(args[0]);
        String folderPath =args[1];
        File folder = new File(folderPath);

        // Inicializa o nó com o porto e pasta especificados
        Node node = new Node(inetAddress, port, folder);
        new GUI(node).setVisible(true);
        node.start();

    }
}
