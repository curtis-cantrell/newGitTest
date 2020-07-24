USE [StrsProfiler]
GO
BULK
INSERT ProfilerData
FROM
'C:\ManagementGateway\11.0.0\root\applications\AdminCourse\Development\profiler.data'
WITH
(
FIELDTERMINATOR = '#',
ROWTERMINATOR = '\n'
)
GO