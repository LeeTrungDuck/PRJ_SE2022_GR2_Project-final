/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package dto;

/**
 *
 * @author ADMIN
 */
public class DevicePermissionDTO {
    private int permissionId;
    private String userId;
    private String switchId;
    private boolean canView;
    private boolean canControl;
    private String grantedBy;
    private boolean isActive;

    public DevicePermissionDTO() {
    }

    public DevicePermissionDTO(int permissionId, String userId, String switchId, boolean canView, boolean canControl, String grantedBy, boolean isActive) {
        this.permissionId = permissionId;
        this.userId = userId;
        this.switchId = switchId;
        this.canView = canView;
        this.canControl = canControl;
        this.grantedBy = grantedBy;
        this.isActive = isActive;
    }

    public int getPermissionId() { return permissionId; }
    public void setPermissionId(int permissionId) { this.permissionId = permissionId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getSwitchId() { return switchId; }
    public void setSwitchId(String switchId) { this.switchId = switchId; }
    public boolean isCanView() { return canView; }
    public void setCanView(boolean canView) { this.canView = canView; }
    public boolean isCanControl() { return canControl; }
    public void setCanControl(boolean canControl) { this.canControl = canControl; }
    public String getGrantedBy() { return grantedBy; }
    public void setGrantedBy(String grantedBy) { this.grantedBy = grantedBy; }
    public boolean isActive() { return isActive; }
    public void setActive(boolean isActive) { this.isActive = isActive; }
}
