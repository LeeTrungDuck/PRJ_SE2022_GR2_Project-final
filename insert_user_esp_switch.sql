insert into tblESP32_Device values ('ESP1',N'Phòng ngủ','esp32-switch1','ONLINE',null,1);
insert into tblESP32_Device values ('ESP2',N'Phòng Bếp','esp32-switch2','ONLINE',null,1);
insert into tblESP32_Device values ('ESP3',N'Phòng Khách','esp32-switch3','ONLINE',null,0);
insert into tblESP32_Device values ('ESP4',N'Phòng ngủ 2','esp32-switch4','ONLINE',null,0);


insert into tblUser values('U1',N'LeeDuck','123456',N'Lê Trung Đức','ADMIN',1,'ltrungduc05@gmail.com','0123456789')
insert into tblUser values('U2',N'VoHai','123456',N'Võ Phước Hải','OPERATOR',1,'haiVo06@gmail.com','0133456789')
insert into tblUser values('U3',N'TagoreVan','123456',N'Vạn Tường Tagore','VIEWER',1,'Tagore10k@gmail.com','0223456789')


insert into tblSwitch values ('SW1','ESP1',N'Đèn 1',2,'OFF',1);
insert into tblSwitch values ('SW2','ESP1',N'Đèn 2',5,'OFF',1);
insert into tblSwitch values ('SW3','ESP1',N'Đèn 3',18,'OFF',1);
insert into tblSwitch values ('SW4','ESP1',N'Đèn 4',19,'OFF',0);
insert into tblSwitch values ('SW5','ESP2',N'Đèn 5',2,'OFF',1);
insert into tblSwitch values ('SW6','ESP3',N'Đèn 6',5,'OFF',1);
insert into tblSwitch values ('SW7','ESP2',N'Đèn 7',18,'OFF',1);
insert into tblSwitch values ('SW8','ESP3',N'Đèn 8',19,'OFF',1);



insert into tblDevice_Permission values('U3','SW2',1,'U1',1);
insert into tblDevice_Permission values('U3','SW3',0,'U1',1);
insert into tblDevice_Permission values('U3','SW4',1,'U1',1);
insert into tblDevice_Permission values('U3','SW1',1,'U1',1);
insert into tblDevice_Permission values('U2','SW6',1,'U1',1);
insert into tblDevice_Permission values('U2','SW7',1,'U1',1);
insert into tblDevice_Permission values('U2','SW8',0,'U1',1);
insert into tblDevice_Permission values('U2','SW3',1,'U1',1);


select * from tblSwitch
select * from tblESP32_Device
select * from tblUser
select * from tblDevice_Permission


