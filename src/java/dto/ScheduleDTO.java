/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package dto;

import enums.ScheduleAction;
import java.time.LocalTime;

/**
 *
 * @author ADMIN
 */
public class ScheduleDTO {
    private int scheduleId;
    private String switchId;
    private String userId;
    private ScheduleAction action;
    private LocalTime runTime;
    private boolean isEnabled;

    public ScheduleDTO() {
    }

    public ScheduleDTO(int scheduleId, String switchId, String userId, ScheduleAction action, LocalTime runTime, boolean isEnabled) {
        this.scheduleId = scheduleId;
        this.switchId = switchId;
        this.userId = userId;
        this.action = action;
        this.runTime = runTime;
        this.isEnabled = isEnabled;
    }

    public int getScheduleId() { return scheduleId; }
    public void setScheduleId(int scheduleId) { this.scheduleId = scheduleId; }
    public String getSwitchId() { return switchId; }
    public void setSwitchId(String switchId) { this.switchId = switchId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public ScheduleAction getAction() { return action; }
    public void setAction(ScheduleAction action) { this.action = action; }
    public LocalTime getRunTime() { return runTime; }
    public void setRunTime(LocalTime runTime) { this.runTime = runTime; }
    public boolean isEnabled() { return isEnabled; }
    public void setEnabled(boolean isEnabled) { this.isEnabled = isEnabled; }
}
