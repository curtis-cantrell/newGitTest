USE [StrsProfiler]
GO

SELECT Context AS 'Statements', COUNT(*) AS 'Requests',
AVG( ElapsedTime ) AS 'Average Response Time MS',
SUM( ElapsedTime ) AS 'Total Response Time MS'
FROM [dbo].[ProfilerData]
WHERE EventID = 'streamserve.notification.profiler.profilerevent.repositoryconnectionprovider.command'
GROUP BY Context
ORDER BY 'Average Response Time MS' DESC
--DB statement response times
SELECT Context, ElapsedTime
FROM [dbo].[ProfilerData]
WHERE EventNamespaceID = 'streamserve.notification.profiler.
profilerevent.repositoryconnectionprovider'
ORDER BY ElapsedTime DESC