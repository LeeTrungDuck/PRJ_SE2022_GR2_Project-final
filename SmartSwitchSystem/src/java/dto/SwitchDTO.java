/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package dto;

import enums.SwitchStatus;

/**
 *
 * @author ADMIN
 */
public class SwitchDTO {
    private String switchId;
    private String deviceId;
    private String switchName;
    private int gpioPin;
    private SwitchStatus status;
    private boolean isActive;

    public SwitchDTO() {
    }

    public SwitchDTO(String switchId, String deviceId, String switchName, int gpioPin, SwitchStatus status, boolean isActive) {
        this.switchId = switchId;
        this.deviceId = deviceId;
        this.switchName = switchName;
        this.gpioPin = gpioPin;
        this.status = status;
        this.isActive = isActive;
    }

    public String getSwitchId() { return switchId; }
    public void setSwitchId(String switchId) { this.switchId = switchId; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getSwitchName() { return switchName; }
    public void setSwitchName(String switchName) { this.switchName = switchName; }
    public int getGpioPin() { return gpioPin; }
    public void setGpioPin(int gpioPin) { this.gpioPin = gpioPin; }
    public SwitchStatus getStatus() { return status; }
    public void setStatus(SwitchStatus status) { this.status = status; }
    public boolean isActive() { return isActive; }
    public void setActive(boolean isActive) { this.isActive = isActive; }
}
