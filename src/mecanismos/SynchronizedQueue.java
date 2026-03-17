package mecanismos;

import java.util.LinkedList;
import java.util.Queue;

public class SynchronizedQueue<T> {
    private final Queue<T> queue = new LinkedList<>();
   
    // Adiciona uma mensagem à fila
    public synchronized void enqueue(T message) {
        queue.add(message);
        notify(); // Notifica uma das threads que esta a espera de mensagens
    }

     //Remove e retorna a próxima mensagem na fila
     //Se a fila estiver vazia, a thread será bloqueada até que uma mensagem esteja disponível
    public synchronized T dequeue() throws InterruptedException {
        while (queue.isEmpty()) {
            wait(); // Aguarda até que uma mensagem seja adicionada
        }
        return queue.poll();
    }
    
    public synchronized boolean isEmpty() {
        return queue.isEmpty();
    }
}
