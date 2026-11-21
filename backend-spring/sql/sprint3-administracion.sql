-- Administración, moderación y sanciones. Aplicar después de sprint2-operaciones.sql.
-- Aditivo y reejecutable: no crea cuentas, concede permisos ni altera datos históricos.
SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET ARITHABORT ON;
SET NUMERIC_ROUNDABORT OFF;
BEGIN TRANSACTION;

IF OBJECT_ID(N'dbo.DecisionesModeracion', N'U') IS NULL
CREATE TABLE dbo.DecisionesModeracion (
    id_decision INT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    id_reporte INT NOT NULL REFERENCES dbo.Reportes(id_reporte),
    id_responsable INT NOT NULL REFERENCES dbo.Usuarios(id_usuario),
    motivo NVARCHAR(2000) NOT NULL,
    estado_anterior NVARCHAR(30) NOT NULL,
    fecha_utc DATETIME2(0) NOT NULL CONSTRAINT DF_Moderacion_fecha DEFAULT SYSUTCDATETIME(),
    CONSTRAINT UQ_Moderacion_reporte UNIQUE(id_reporte),
    CONSTRAINT CK_Moderacion_motivo CHECK(LEN(LTRIM(RTRIM(motivo))) BETWEEN 10 AND 2000)
);

IF OBJECT_ID(N'dbo.SancionesCuenta', N'U') IS NULL
CREATE TABLE dbo.SancionesCuenta (
    id_sancion INT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    id_usuario INT NOT NULL REFERENCES dbo.Usuarios(id_usuario),
    id_reporte INT NOT NULL REFERENCES dbo.Reportes(id_reporte),
    id_responsable INT NOT NULL REFERENCES dbo.Usuarios(id_usuario),
    motivo NVARCHAR(2000) NOT NULL,
    activo_anterior BIT NOT NULL,
    fecha_utc DATETIME2(0) NOT NULL CONSTRAINT DF_Sanciones_fecha DEFAULT SYSUTCDATETIME(),
    levantada_utc DATETIME2(0) NULL,
    id_responsable_levantamiento INT NULL REFERENCES dbo.Usuarios(id_usuario),
    motivo_levantamiento NVARCHAR(2000) NULL,
    CONSTRAINT UQ_Sanciones_reporte UNIQUE(id_reporte),
    CONSTRAINT CK_Sanciones_motivo CHECK(LEN(LTRIM(RTRIM(motivo))) BETWEEN 10 AND 2000),
    CONSTRAINT CK_Sanciones_levantamiento CHECK(
        (levantada_utc IS NULL AND id_responsable_levantamiento IS NULL AND motivo_levantamiento IS NULL)
        OR (levantada_utc IS NOT NULL AND id_responsable_levantamiento IS NOT NULL
            AND motivo_levantamiento IS NOT NULL AND LEN(LTRIM(RTRIM(motivo_levantamiento))) BETWEEN 10 AND 2000))
);

IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE name=N'UQ_Sancion_Activa_Usuario' AND object_id=OBJECT_ID(N'dbo.SancionesCuenta'))
    CREATE UNIQUE INDEX UQ_Sancion_Activa_Usuario ON dbo.SancionesCuenta(id_usuario) WHERE levantada_utc IS NULL;

IF OBJECT_ID(N'dbo.AuditoriaAdministrativa', N'U') IS NULL
CREATE TABLE dbo.AuditoriaAdministrativa (
    id_auditoria INT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    accion NVARCHAR(50) NOT NULL,
    objetivo_id INT NOT NULL,
    id_usuario_objetivo INT NOT NULL REFERENCES dbo.Usuarios(id_usuario),
    id_responsable INT NOT NULL REFERENCES dbo.Usuarios(id_usuario),
    motivo NVARCHAR(2000) NOT NULL,
    detalle NVARCHAR(1500) NOT NULL,
    fecha_utc DATETIME2(0) NOT NULL CONSTRAINT DF_Auditoria_fecha DEFAULT SYSUTCDATETIME(),
    CONSTRAINT CK_Auditoria_motivo CHECK(LEN(LTRIM(RTRIM(motivo))) BETWEEN 10 AND 2000)
);

IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE name=N'IX_Auditoria_Usuario' AND object_id=OBJECT_ID(N'dbo.AuditoriaAdministrativa'))
    CREATE INDEX IX_Auditoria_Usuario ON dbo.AuditoriaAdministrativa(id_usuario_objetivo,id_auditoria DESC);

COMMIT TRANSACTION;
GO
