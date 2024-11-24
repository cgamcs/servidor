package libro.cap7;

import oshi.SystemInfo;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.CentralProcessor;
import oshi.hardware.HWDiskStore;
import oshi.hardware.NetworkIF;
import oshi.software.os.OperatingSystem;

import java.io.Serializable;
import java.util.List;

public class SystemInfoDetails implements Serializable, Comparable<SystemInfoDetails> {
    private static final long serialVersionUID = 1L;

    private String processorModel;
    private double processorSpeed;
    private int cores;
    private long diskCapacity;
    private String osVersion;
    private double cpuUsage;
    private long memoryUsage;
    private int rank;
    private double freeBandwidth;
    private long freeDiskSpace;
    private double freeMemoryPercentage;
    private String connectionStatus;

    private transient SystemInfo si;
    private transient HardwareAbstractionLayer hardware;
    private transient OperatingSystem os;

    public SystemInfoDetails() {
        this.si = new SystemInfo();
        this.hardware = si.getHardware();
        this.os = si.getOperatingSystem();
        updateStaticInfo();
    }

    public void updateStaticInfo() {
        CentralProcessor processor = hardware.getProcessor();
        this.processorModel = processor.getProcessorIdentifier().getName();
        long vendorFreq = processor.getProcessorIdentifier().getVendorFreq();
        this.processorSpeed = (vendorFreq > 0) ? vendorFreq / 1E9 : -1;
        this.cores = processor.getLogicalProcessorCount();
        if (!hardware.getDiskStores().isEmpty()) {
            this.diskCapacity = hardware.getDiskStores().get(0).getSize() / (1024 * 1024 * 1024);
        } else {
            this.diskCapacity = -1;
        }
        this.osVersion = os.toString();
    }

    public double obtenerAnchoBandaLibre() {
        List<NetworkIF> networkIFs = hardware.getNetworkIFs();
        double totalBandwidth = 0.0;
        double usedBandwidth = 0.0;
        for (NetworkIF networkIF : networkIFs) {
            long speed = networkIF.getSpeed();
            if (speed > 0) totalBandwidth += speed;
            usedBandwidth += networkIF.getBytesRecv() + networkIF.getBytesSent();
        }
        double usedBandwidthBits = (usedBandwidth * 8);
        double freeBandwidth = ((totalBandwidth - usedBandwidthBits) / totalBandwidth) * 100;
        return freeBandwidth < 0 ? 0 : freeBandwidth;
    }

    public void updateDynamicInfo() {
        this.cpuUsage = hardware.getProcessor().getSystemCpuLoad(1000) * 100;
        this.memoryUsage = hardware.getMemory().getAvailable() / (1024 * 1024);
        long totalMemory = hardware.getMemory().getTotal() / (1024 * 1024);
        this.freeMemoryPercentage = (this.memoryUsage * 100.0) / totalMemory;
        this.freeBandwidth = obtenerAnchoBandaLibre();
        if (!hardware.getDiskStores().isEmpty()) {
            HWDiskStore diskStore = hardware.getDiskStores().get(0);
            long totalDiskSpace = diskStore.getSize();
            long usableSpace = diskStore.getWriteBytes();
            if (usableSpace > 0) {
                this.freeDiskSpace = usableSpace / (1024 * 1024 * 1024);
            } else {
                this.freeDiskSpace = (totalDiskSpace - diskStore.getWriteBytes()) / (1024 * 1024 * 1024);
            }
        } else {
            this.freeDiskSpace = -1;
        }
        this.connectionStatus = "Conectado";
    }

    public int calculateRank() {
        double score = 0;
        score += cores * 20;
        score += processorSpeed > 0 ? processorSpeed * 25 : 0;
        score += diskCapacity > 0 ? diskCapacity / 50 : 0;
        score -= (cpuUsage / 5);
        score += (freeMemoryPercentage * 1.5);
        score += (freeBandwidth * 2);
        this.rank = (int) Math.max(score, 0);
        return this.rank;
    }

    @Override
    public int compareTo(SystemInfoDetails other) {
        return Integer.compare(other.rank, this.rank);
    }

    // Getters
    public String getProcessorModel() { return processorModel; }
    public double getProcessorSpeed() { return processorSpeed; }
    public int getCores() { return cores; }
    public long getDiskCapacity() { return diskCapacity; }
    public String getOsVersion() { return osVersion; }
    public double getCpuUsage() { return cpuUsage; }
    public long getMemoryUsage() { return memoryUsage; }
    public int getRank() { return rank; }
    public double getFreeBandwidth() { return freeBandwidth; }
    public long getFreeDiskSpace() { return freeDiskSpace; }
    public double getFreeMemoryPercentage() { return freeMemoryPercentage; }
    public String getConnectionStatus() { return connectionStatus; }
}
