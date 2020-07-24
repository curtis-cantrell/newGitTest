GO
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
SET ANSI_PADDING ON
GO
CREATE TABLE [StrsProfiler].[dbo].[ProfilerData](
[EventID] [varchar](max) NULL,
[EventNamespaceID] [varchar](max) NULL,
[Context] [varchar](max) NULL,
[ThreadID] [int] NULL,
[Timestamp] [datetime] NULL,
[ElapsedTime] [decimal](18, 3) NULL
) ON [PRIMARY]
GO
SET ANSI_PADDING OFF
GO