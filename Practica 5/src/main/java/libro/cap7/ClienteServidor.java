package libro.cap7;

import oshi.SystemInfo;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class ClienteServidor extends JFrame {
    private DefaultTableModel tableModel;
    private JTable table;
    private Map<Socket, SystemInfoDetails> clientesConectados;
    private SystemInfoDetails localSystem;
    private ScheduledExecutorService executorService;
    private static final int SERVER_PORT = 6789;
    private Socket serverSocket;

    public ClienteServidor() {
        super("Monitor de Sistemas");
        this.clientesConectados = new ConcurrentHashMap<>();
        this.localSystem = new SystemInfoDetails();
        this.serverSocket = null;


        initializeUI();
        startServer();
        startMonitoring();
    }

    private void initializeUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1100, 600); // Ajustar el tamaño de la ventana

        String[] columns = {"Host", "Procesador", "Velocidad (GHz)", "Núcleos",
                "Disco (GB)", "SO", "CPU (%)", "Memoria (MB)", "Rank",
                "Ancho de Banda Libre (%)", "Espacio Libre Disco (GB)",
                "Memoria Libre (%)", "Estado de Conexión", "Top Rank"};

        tableModel = new DefaultTableModel(columns, 0);
        table = new JTable(tableModel);
        add(new JScrollPane(table));

        updateTableRow("Local", localSystem, false);
    }

    private void startServer() {
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(SERVER_PORT)) {
                System.out.println("Servidor iniciado en IP: " + InetAddress.getLocalHost().getHostAddress() + " y puerto: " + SERVER_PORT);
                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    handleNewClient(clientSocket);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void handleNewClient(Socket clientSocket) {
        new Thread(() -> {
            try (ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream())) {
                if (!clientesConectados.containsKey(clientSocket)) {
                    clientesConectados.put(clientSocket, new SystemInfoDetails());
                }

                while (true) {
                    SystemInfoDetails clientInfo = (SystemInfoDetails) in.readObject();
                    clientesConectados.put(clientSocket, clientInfo);
                    updateTableRow(clientSocket.getInetAddress().getHostName(), clientInfo, false);
                }
            } catch (Exception e) {
                clientesConectados.remove(clientSocket);
                updateTable();
            }
        }).start();
    }

    private void startMonitoring() {
        executorService = Executors.newSingleThreadScheduledExecutor();
        executorService.scheduleAtFixedRate(() -> {
            localSystem.updateDynamicInfo();
            localSystem.calculateRank();
            updateTable();
        }, 0, 1, TimeUnit.SECONDS);
    }


    private void updateTable() {
        SwingUtilities.invokeLater(() -> {
            tableModel.setRowCount(0); // Limpiar la tabla antes de agregar las filas nuevas

            // Agregar el dispositivo local (servidor) en la tabla
            updateTableRow("Local", localSystem, true);

            // Evitar duplicados: crear un conjunto para almacenar direcciones MAC únicas
            Set<String> seenDevices = new HashSet<>();

            // Recorremos todos los clientes conectados
            for (Map.Entry<Socket, SystemInfoDetails> entry : clientesConectados.entrySet()) {
                String hostName = entry.getKey().getInetAddress().getHostName();
                String deviceMac = getDeviceMac(entry.getKey()); // Usar la dirección MAC como identificador único

                // Comprobamos si el dispositivo ya ha sido agregado (excluyendo duplicados por MAC)
                if (!deviceMac.equals("") && !seenDevices.contains(deviceMac)) {
                    seenDevices.add(deviceMac); // Agregar la dirección MAC a la lista de ya vistos
                    SystemInfoDetails clientInfo = entry.getValue();

                    // Verificamos si los valores de los campos son válidos antes de agregar la fila
                    if (clientInfo != null && isValidClientInfo(clientInfo)) {
                        updateTableRow(hostName, clientInfo, false);  // Agregar cliente a la tabla
                    }
                }
            }
        });
    }

    // Método para verificar que los valores del cliente sean válidos antes de agregarlo a la tabla
    private boolean isValidClientInfo(SystemInfoDetails clientInfo) {
        return clientInfo.getProcessorSpeed() > 0 &&
                clientInfo.getCpuUsage() >= 0 &&
                clientInfo.getMemoryUsage() > 0;
    }

    // Método para obtener la dirección MAC del cliente
    private String getDeviceMac(Socket socket) {
        String deviceMac = "";
        try {
            // Crear una instancia de SystemInfo para obtener hardware y obtener las interfaces de red
            SystemInfo systemInfo = new SystemInfo();
            HardwareAbstractionLayer hardware = systemInfo.getHardware(); // Obtener el hardware

            // Obtener la primera interfaz de red
            List<NetworkIF> networkIFs = hardware.getNetworkIFs();
            if (networkIFs != null && !networkIFs.isEmpty()) {
                NetworkIF networkIF = networkIFs.get(0); // Suponiendo que estamos tomando la primera interfaz de red
                deviceMac = networkIF.getMacaddr();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return deviceMac;
    }

    // Método para agregar o actualizar una fila en la tabla
    private void updateTableRow(String host, SystemInfoDetails info, boolean isTopRank) {
        // Verificamos si el cliente ya está en la tabla
        boolean isExisting = false;
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            String existingHost = (String) tableModel.getValueAt(i, 0); // Suponiendo que el nombre del host está en la primera columna
            if (existingHost.equals(host)) {
                isExisting = true;
                break;
            }
        }

        // Si el cliente no existe, lo agregamos a la tabla
        if (!isExisting) {
            tableModel.addRow(new Object[]{
                    host,
                    info.getProcessorModel(),
                    String.format("%.2f", info.getProcessorSpeed()),
                    info.getCores(),
                    info.getDiskCapacity(),
                    info.getOsVersion(),
                    String.format("%.1f", info.getCpuUsage()),
                    info.getMemoryUsage(),
                    info.getRank(),
                    String.format("%.1f", info.getFreeBandwidth()),
                    info.getFreeDiskSpace(),
                    String.format("%.1f", info.getFreeMemoryPercentage()),
                    info.getConnectionStatus(),
                    isTopRank ? "Sí" : "No" // Mostrar "Sí" si es el dispositivo con el mejor rank
            });
        } else {
            // Si el cliente ya está, solo actualizamos su fila en la tabla
            for (int i = 0; i < tableModel.getRowCount(); i++) {
                String existingHost = (String) tableModel.getValueAt(i, 0); // Suponiendo que el nombre del host está en la primera columna
                if (existingHost.equals(host)) {
                    tableModel.setValueAt(info.getProcessorModel(), i, 1);
                    tableModel.setValueAt(String.format("%.2f", info.getProcessorSpeed()), i, 2);
                    tableModel.setValueAt(info.getCores(), i, 3);
                    tableModel.setValueAt(info.getDiskCapacity(), i, 4);
                    tableModel.setValueAt(info.getOsVersion(), i, 5);
                    tableModel.setValueAt(String.format("%.1f", info.getCpuUsage()), i, 6);
                    tableModel.setValueAt(info.getMemoryUsage(), i, 7);
                    tableModel.setValueAt(info.getRank(), i, 8);
                    tableModel.setValueAt(String.format("%.1f", info.getFreeBandwidth()), i, 9);
                    tableModel.setValueAt(info.getFreeDiskSpace(), i, 10);
                    tableModel.setValueAt(String.format("%.1f", info.getFreeMemoryPercentage()), i, 11);
                    tableModel.setValueAt(info.getConnectionStatus(), i, 12);
                    tableModel.setValueAt(isTopRank ? "Sí" : "No", i, 13);
                    break;
                }
            }
        }
    }






    public void startClient(String serverIp) {
        new Thread(() -> {
            try {
                serverSocket = new Socket(serverIp, SERVER_PORT);
                ObjectOutputStream out = new ObjectOutputStream(serverSocket.getOutputStream());

                while (true) {
                    out.writeObject(localSystem);
                    Thread.sleep(1000);
                }
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    public static void main(String[] args) {
        String serverIp = "192.168.1.88"; // Cambiar por la IP del servidor
        SwingUtilities.invokeLater(() -> {
            ClienteServidor clientServer = new ClienteServidor();
            clientServer.setVisible(true);
            clientServer.startClient(serverIp); // Iniciar el cliente
        });
    }
}
