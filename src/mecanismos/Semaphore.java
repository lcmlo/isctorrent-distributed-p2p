package mecanismos;

public class Semaphore {
    private int permits;

    public Semaphore(int initialPermits) {
        this.permits = initialPermits;
    }

    // Adquire se houver permits, se nao espera ate haver
    public synchronized void acquire() {
        while (permits <= 0) {
            try {
                wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        permits--;
    }

    // Liberta o permit e avisa quem estiver a espera
    public synchronized void release() {
        permits++;
        notify();
    }
}

