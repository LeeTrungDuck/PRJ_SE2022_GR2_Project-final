USE master;
GO

IF DB_ID('SmartSwitchSystem') IS NOT NULL
BEGIN
    ALTER DATABASE SmartSwitchSystem 
    SET SINGLE_USER WITH ROLLBACK IMMEDIATE;

    DROP DATABASE SmartSwitchSystem;
END
GO

CREATE DATABASE SmartSwitchSystem;
GO

USE SmartSwitchSystem;
GO

create table tblUser(
    user_id varchar(20) primary key CHECK (user_id LIKE 'U%'), 
    user_name Nvarchar(50) not null,
    password varchar(50) not null,
    full_name Nvarchar(50) not null,
    role varchar(10) default 'VIEWER' check (role IN ('ADMIN','OPERATOR','VIEWER')),
    is_active bit DEFAULT 1
)

create table tblESP32_Device(
    device_id varchar(20) primary key CHECK (device_id LIKE 'ESP%'),
    name nvarchar(50) not null,
    host_name varchar(255) not null,
    status VARCHAR(10) DEFAULT 'OFFLINE' CHECK (status IN ('ONLINE', 'OFFLINE', 'ERROR')), /* 3 trạng thái thiết bị*/
    last_seen DATETIME 

)

create table tblSwitch(
    switch_id varchar(20) primary key CHECK (switch_id LIKE 'SW%'),
    device_id varchar(20) references tblESP32_Device(device_id) not null,
    switch_name nvarchar(50) not null,
    gpio_pin TINYINT not null,
    status varchar(6) default 'OFF' check(status in('ON','OFF'))
)

create table tblDevice_Permission(
    permission_id INT IDENTITY(1,1) primary key,
    user_id varchar(20) references tblUser(user_id) not null,
    switch_id varchar(20) references tblSwitch(switch_id) not null,
    canView bit default 1,
    canControl bit default 1,
    granted_by varchar(20) references tblUser(user_id),
    UNIQUE (user_id, switch_id)
    )
/* lưu lịch sử truy cập thiết bị*/
create table tblControl_History(
    history_id INT IDENTITY(1,1) primary key,
    user_id varchar(20) references tblUser(user_id),
    switch_id varchar(20) references tblSwitch(switch_id),
    command varchar(10) not null CHECK (command IN ('ON','OFF')),/* lưu lệnh khi điều khiển*/
    result varchar(10) not null CHECK (result IN ('ON','OFF','ERROR')) /* lưu kết quả của lệnh*/,
    control_time DATETIME DEFAULT GETDATE()
)