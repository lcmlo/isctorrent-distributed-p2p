package mecanismos;

import java.util.LinkedList;
import java.util.Queue;

import mensagens.FileBlockAnswerMessage;

public class SynchronizedBuffer {
    private final Queue<FileBlockAnswerMessage> buffer;
    private final int capacity;

    public SynchronizedBuffer(int capacity) {
        this.capacity = capacity;
        this.buffer = new LinkedList<>();
    }

    public synchronized void addMsg(FileBlockAnswerMessage mensagem) throws InterruptedException {
        while (buffer.size() == capacity) {
            wait(); // Espera até haver espaço disponível
        }
        buffer.add(mensagem);
        notifyAll(); // Notifica as threads que estao a espera por novas mensagens
    }

    public synchronized FileBlockAnswerMessage getMsg() throws InterruptedException {
        while (buffer.isEmpty()) {
            wait(); // Espera até haver mensagens disponíveis
        }
        FileBlockAnswerMessage mensagem = buffer.poll();
        notifyAll(); // Notifica as threads que estao a espera por espaço no buffer
        return mensagem;
    }

    public synchronized boolean isEmpty() {
        return buffer.isEmpty();
    }
}
