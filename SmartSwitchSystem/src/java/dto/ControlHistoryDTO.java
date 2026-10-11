/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package dto;

import enums.Command;
import enums.ControlResult;
import java.time.LocalDateTime;

/**
 *
 * @author ADMIN
 */
public class ControlHistoryDTO {
    private int historyId;
    private String userId;
    private String switchId;
    private Command command;
    private ControlResult result;
    private LocalDateTime controlTime;

    public ControlHistoryDTO() {
    }
    public LocalDateTime getTimeNow(){
        return LocalDateTime.now();
    }
    public ControlHistoryDTO(int historyId, String userId, String switchId, Command command, ControlResult result, LocalDateTime controlTime) {
        this.historyId = historyId;
        this.userId = userId;
        this.switchId = switchId;
        this.command = command;
        this.result = result;
        this.controlTime = controlTime;
    }

    public int getHistoryId() { return historyId; }
    public void setHistoryId(int historyId) { this.historyId = historyId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getSwitchId() { return switchId; }
    public void setSwitchId(String switchId) { this.switchId = switchId; }
    public Command getCommand() { return command; }
    public void setCommand(Command command) { this.command = command; }
    public ControlResult getResult() { return result; }
    public void setResult(ControlResult result) { this.result = result; }
    public LocalDateTime getControlTime() { return controlTime; }
    public void setControlTime(LocalDateTime controlTime) { this.controlTime = controlTime; }
}
