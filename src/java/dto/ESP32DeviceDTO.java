/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package dto;

import enums.DeviceStatus;
import java.time.LocalDateTime;

/**
 *
 * @author ADMIN
 */
public class ESP32DeviceDTO {
    private String deviceId;
    private String name;
    private String hostName;
    private DeviceStatus status;
    private LocalDateTime lastSeen;

    public ESP32DeviceDTO() {
    }

    public ESP32DeviceDTO(String deviceId, String name, String hostName, DeviceStatus status, LocalDateTime lastSeen) {
        this.deviceId = deviceId;
        this.name = name;
        this.hostName = hostName;
        this.status = status;
        this.lastSeen = lastSeen;
    }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getHostName() { return hostName; }
    public void setHostName(String hostName) { this.hostName = hostName; }
    public DeviceStatus getStatus() { return status; }
    public void setStatus(DeviceStatus status) { this.status = status; }
    public LocalDateTime getLastSeen() { return lastSeen; }
    public void setLastSeen(LocalDateTime lastSeen) { this.lastSeen = lastSeen; }
}
