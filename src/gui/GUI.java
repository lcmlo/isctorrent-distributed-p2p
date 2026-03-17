package gui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;

import mensagens.DownloadTasksManagerCountDownLatch;
import mensagens.FileBlockRequestMessage;
import node.Node;
import node.SearchResultListener;
import search.FileSearchResult;

public class GUI extends JFrame implements SearchResultListener {
	private JTextField searchField;
	private JButton searchButton;
	private JLabel searchLabel;
	private JList<String> resultsList;
	private DefaultListModel<String> listModel; 
	private JButton downloadButton;
	private JButton connectButton;
	private Node node;

	public Node getNode() {
		return node;
	}

	public GUI(Node node) {
		this.node = node;
		this.node.setSearchResultListener(this); // Regista a GUI como ouvinte

		setTitle("IscTorrent Node - IP: " + node.getAddress() + ", Port: " + node.getPort());
		setSize(500, 400);
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setLayout(new BorderLayout());

		JPanel topPanel = new JPanel();
		searchLabel = new JLabel("Texto a procurar:");
		searchField = new JTextField(20);
		searchButton = new JButton("Procurar");
		topPanel.add(searchLabel);
		topPanel.add(searchField);
		topPanel.add(searchButton);
		add(topPanel, BorderLayout.NORTH);

		listModel = new DefaultListModel<>();
		resultsList = new JList<>(listModel);
		JScrollPane scrollPane = new JScrollPane(resultsList);
		add(scrollPane, BorderLayout.CENTER);

		JPanel actionPanel = new JPanel();
		actionPanel.setLayout(new GridLayout(2, 1));
		downloadButton = new JButton("Descarregar Ficheiro");
		connectButton = new JButton("Ligar a Nó");
		actionPanel.add(downloadButton);
		actionPanel.add(connectButton);
		add(actionPanel, BorderLayout.EAST);

		searchButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				performSearch();
			}
		});

		connectButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				showConnectionDialog();
			}
		});

		downloadButton.addActionListener(new ActionListener() {
		    @Override
		    public void actionPerformed(ActionEvent e) {
		        List<String> selectedFiles = resultsList.getSelectedValuesList(); // Obtém todos os itens selecionados

		        if (selectedFiles.isEmpty()) {
		            node.debug("Selecione pelo menos um arquivo para baixar.");
		            JOptionPane.showMessageDialog(GUI.this, "Selecione pelo menos um arquivo para baixar.");
		            return;
		        }

		        // Listas para os dados de download
		        List<String> fileNames = new ArrayList<>();
		        List<Integer> totalBlocksList = new ArrayList<>();
		        Map<String, List<Integer>> filePortMapping = new HashMap<>();

		        for (String selectedFile : selectedFiles) {
		            String fileName = selectedFile.split("<")[0].trim(); // Extrai o nome do arquivo
		            File localFile = new File(node.getFolder(), fileName);

		            // Verifica se o arquivo já existe localmente
		            if (localFile.exists()) {
		                JOptionPane.showMessageDialog(GUI.this, "O arquivo \"" + fileName + "\" já existe na pasta \n apenas farei o download dos restantes.");
		                fileNames.remove(fileName);
		                continue;
		            }

		            // Obtém os portos associados ao arquivo
		            List<Integer> ports = node.getFileNameToPort().get(fileName);
		            node.debug("Lista de portos com o ficheiro "+ fileName +": " + ports);

		            if (ports == null || ports.isEmpty()) {
		                JOptionPane.showMessageDialog(GUI.this, "Nenhum nó disponível para o arquivo: " + fileName);
		                continue;
		            }

		            // Calcula o tamanho total do arquivo e determina o número de blocos
		            FileSearchResult searchResult = node.getFileSearchResults().get(fileName);
		            if (searchResult == null) {
		                JOptionPane.showMessageDialog(GUI.this, "Informações do arquivo não disponíveis para: " + fileName);
		                continue;
		            }

		            long fileSize = searchResult.getFileSize();
		            int blockSize = FileBlockRequestMessage.getBlockSize();
		            int totalBlocks = (int) Math.ceil((double) fileSize / blockSize);

		            // Armazena as informações do arquivo
		            fileNames.add(fileName);
		            totalBlocksList.add(totalBlocks);
		            filePortMapping.put(fileName, ports);
		        }

		        // Verifica se há arquivos válidos
		        if (fileNames.isEmpty()) {
		            node.debug("Nenhum arquivo válido selecionado para download.");
		            return;
		        }

		        // Cria um único manager para todos os arquivos
		        DownloadTasksManagerCountDownLatch downloadManager = new DownloadTasksManagerCountDownLatch(
		        		fileNames,
		            node.getFolder(),
		            GUI.this,
		            totalBlocksList,
		            filePortMapping
		        );

		        // Regista o manager no nó
		        node.registerDownloadManagerLatch(fileNames, downloadManager);

		        // Inicia o processo de download
		        downloadManager.iniciarDownload();
		        
//		      // versao original   Cria um único manager para todos os arquivos
//		        DownloadTasksManager downloadTaskManager = new DownloadTasksManager(
//		        		fileNames,
//		        		node.getFolder(),
//		        		GUI.this,
//		        		totalBlocksList,
//		        		filePortMapping
//		        		);
//		        
//		        // Regista o manager no nó
//		        node.registerDownloadManager(fileNames, downloadTaskManager);
//		        
//		        // Inicia o processo de download
//		        downloadTaskManager.iniciarDownload();
		    }
		});

	}

	public void showDownloadCompleteMessage(String fileName, Map<Integer, Map<String, Integer>> portoDeFornecedoresParaBlocosPorFicheiro, long tempoDecorrido) {
	    StringBuilder mensagem = new StringBuilder("Descarga completa.\n");

	    mensagem.append("Arquivo: ").append(fileName).append("\n");

	    // Itera sobre os fornecedores (portos)
	    for (Map.Entry<Integer, Map<String, Integer>> portEntry : portoDeFornecedoresParaBlocosPorFicheiro.entrySet()) {
	        Integer porto = portEntry.getKey();
	        Map<String, Integer> fileToBlocks = portEntry.getValue();

	        // Adiciona as informações do porto e dos blocos fornecidos para o arquivo específico
	        if (fileToBlocks.containsKey(fileName)) {
	            Integer blocosFornecidos = fileToBlocks.get(fileName);

	            mensagem.append("Fornecedor [endereco=").append(node.getAddress())
	                    .append(", porto=").append(porto)
	                    .append("]: ").append(blocosFornecidos).append(" blocos fornecidos\n");
	        }
	    }

	    mensagem.append("Tempo decorrido: ").append(tempoDecorrido / 1000).append("s");

	    // Mostra a mensagem na GUI
	    JOptionPane.showMessageDialog(this, mensagem.toString(), "Descarga completa", JOptionPane.INFORMATION_MESSAGE);
	}


	private void performSearch() {
		String keyword = searchField.getText().trim();
		if (keyword.isEmpty()) {
			return;
		}
		node.broadcastSearchRequest(keyword);
	}

	@Override
	public void onSearchResultsReceived(List<FileSearchResult> results) {
	    node.getSemaphoreFileNameToPort().acquire(); // Espera pelo recurso
	    try {
	        listModel.clear();
	        Map<String, List<Integer>> fileNameToPort = node.getFileNameToPort();
	        if (results.isEmpty()) {
	            listModel.addElement("Nenhum arquivo encontrado");
	        } else {
	            for (Map.Entry<String, List<Integer>> entry : fileNameToPort.entrySet()) {
	                String file = entry.getKey();
	                List<Integer> ports = entry.getValue();

	                // Conta o número de portos associados ao arquivo
	                int nodeCount = ports.size();

	                // Adiciona o resultado formatado na lista
	                String resultText = file + "<" + nodeCount + ">";
	                listModel.addElement(resultText);
	            }
	        }
	    } finally {
	    	node.getSemaphoreFileNameToPort().release(); // Libera o recurso
	    }
	}

	private void showConnectionDialog() {
		JDialog connectionDialog = new JDialog(this, "Conectar a Nó Remoto", true);
		connectionDialog.setLayout(new BorderLayout());

		JPanel inputPanel = new JPanel(new FlowLayout());
		JLabel addressLabel = new JLabel("Endereço:");
		JTextField addressField = new JTextField("localhost", 15); // Default
		JLabel portLabel = new JLabel("Porta:");
		JTextField portField = new JTextField("808x", 5); // Default
		JButton okButton = new JButton("OK");
		JButton cancelButton = new JButton("Cancelar");

		inputPanel.add(addressLabel);
		inputPanel.add(addressField);
		inputPanel.add(portLabel);
		inputPanel.add(portField);
		inputPanel.add(cancelButton);
		inputPanel.add(okButton);

		okButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				String addressStr = addressField.getText();
				try {
					InetAddress address = InetAddress.getByName(addressStr);
					int port = Integer.parseInt(portField.getText());
					node.connectToNode(address, port);
				} catch (UnknownHostException ex) {
					ex.printStackTrace();
				}

				connectionDialog.dispose();
			}
		});

		cancelButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				connectionDialog.dispose();
			}
		});

		connectionDialog.add(inputPanel, BorderLayout.CENTER);
		connectionDialog.setLocationRelativeTo(this);
		connectionDialog.pack();
		connectionDialog.setVisible(true);
	}
}
