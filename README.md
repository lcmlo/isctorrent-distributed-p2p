# IscTorrent - Distributed P2P File Sharing System

## Project Overview
IscTorrent is a peer-to-peer (P2P) file-sharing application developed for the **Concurrent and Distributed Programming** (PCD) course at Iscte (3rd Year, BSc in Computer Engineering). The system enables decentralized file distribution by splitting binary data into 10KB blocks, allowing simultaneous downloads from multiple peers to optimize network performance and resilience.


![iscTorrentv2](https://github.com/user-attachments/assets/e0fed250-823f-4810-ad11-ce88087a018e)


## Technical Features
* **Multi-threaded Architecture**: Each node acts as both client and server, managing concurrent requests using a `ThreadPoolExecutor` and dedicated `NodeHandler` threads.
* **Block-based Transfer**: Files are divided into 10KB segments (defined in `FileBlockRequestMessage`), allowing for parallel block requests and assembly.
* **Advanced Synchronization**: Utilizes `CountDownLatch` and `Semaphores` to orchestrate parallel downloads and ensure thread safety in shared buffers during data reconstruction.
* **TCP Networking**: Custom messaging protocol over TCP sockets for reliable serialized object exchange (requests, answers, and search results).
* **Swing GUI**: A real-time interface to monitor active nodes, search for files across the network, and track download progress.

## Project Structure
* `main/`: Entry point and application initialization (`IscTorrent.java`).
* `node/`: Core P2P logic, socket handling, and peer-to-peer communication.
* `mensagens/`: Custom serialized protocol for block requests and connection handling.
* `mecanismos/`: Implementation of synchronization primitives (`CountDownLatch`, `Semaphore`, `SynchronizedBuffer`).
* `gui/`: User interface for network interaction.

## How to Run & Simulate
The system is designed to run in a distributed manner. The repository already includes pre-configured folders (`dl1`, `dl2`, etc.) to represent different nodes in the network.

1. **Compilation**  
   Navigate to the `src` directory and run:
   ```bash
   javac main/IscTorrent.java
   ```

2. **Launch Node 1 (Seeder/Peer)**

   ```bash
   java main.IscTorrent 8081 dl1
   ```

   *(Note: `dl1` is the name of the folder. You can use any name, but folders like `dl1` and `dl2` are included in the root to save setup time.)*

3. **Launch Node 2 (Leecher/Peer)**

   ```bash
   java main.IscTorrent 8082 dl2
   ```

   *(Connect to Node 1 via the GUI to search for files and start the distributed download process.)*

## Implementation Details

* **Extensibility**: The system easily supports new file types (e.g., `.mp4`, `.pdf`). To increase capacity or adjust granularity, constants such as block size can be modified in the core engine.
* **Reliability**: Downloads are managed by the `DownloadTasksManagerCountDownLatch`, ensuring that files are only saved after all blocks are successfully received and synchronized via `CountDownLatch`.

## Team

* Luís Lourenço (110942)
* Afonso Valente (110899)


