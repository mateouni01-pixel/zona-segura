-- Actualización aditiva. Aplicar después de sprint2-verificacion.sql.
-- No ejecutar zona_segura.sql sobre una base existente.
SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET ARITHABORT ON;
SET NUMERIC_ROUNDABORT OFF;
BEGIN TRANSACTION;
IF COL_LENGTH('dbo.Reportes','id_cliente') IS NULL ALTER TABLE dbo.Reportes ADD id_cliente UNIQUEIDENTIFIER NULL;
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE name='UQ_Reportes_Cliente' AND object_id=OBJECT_ID('dbo.Reportes'))
    EXEC(N'CREATE UNIQUE INDEX UQ_Reportes_Cliente ON dbo.Reportes(id_ciudadano,id_cliente) WHERE id_cliente IS NOT NULL');
IF NOT EXISTS(SELECT 1 FROM dbo.Roles WHERE nombre=N'UNIDAD_OPERATIVA') INSERT dbo.Roles(nombre) VALUES(N'UNIDAD_OPERATIVA');
IF OBJECT_ID('dbo.UnidadesOperativas','U') IS NULL
CREATE TABLE dbo.UnidadesOperativas(id_unidad INT IDENTITY PRIMARY KEY,nombre NVARCHAR(150) NOT NULL,activo BIT NOT NULL DEFAULT 1);
IF OBJECT_ID('dbo.MiembrosUnidad','U') IS NULL
CREATE TABLE dbo.MiembrosUnidad(id_usuario INT PRIMARY KEY REFERENCES dbo.Usuarios(id_usuario),id_unidad INT NOT NULL REFERENCES dbo.UnidadesOperativas(id_unidad));
IF COL_LENGTH('dbo.RecursosPreventivos','nombre') IS NULL ALTER TABLE dbo.RecursosPreventivos ADD nombre NVARCHAR(150) NOT NULL CONSTRAINT DF_Recurso_nombre DEFAULT N'';
IF COL_LENGTH('dbo.RecursosPreventivos','activo') IS NULL ALTER TABLE dbo.RecursosPreventivos ADD activo BIT NOT NULL CONSTRAINT DF_Recurso_activo DEFAULT 1;
IF COL_LENGTH('dbo.RecursosPreventivos','id_unidad') IS NULL ALTER TABLE dbo.RecursosPreventivos ADD id_unidad INT NULL REFERENCES dbo.UnidadesOperativas(id_unidad);
IF COL_LENGTH('dbo.OrdenesIntervencion','id_reporte') IS NULL ALTER TABLE dbo.OrdenesIntervencion ADD id_reporte INT NULL REFERENCES dbo.Reportes(id_reporte);
IF COL_LENGTH('dbo.OrdenesIntervencion','id_unidad') IS NULL ALTER TABLE dbo.OrdenesIntervencion ADD id_unidad INT NULL REFERENCES dbo.UnidadesOperativas(id_unidad);
IF COL_LENGTH('dbo.OrdenesIntervencion','instrucciones') IS NULL ALTER TABLE dbo.OrdenesIntervencion ADD instrucciones NVARCHAR(2000) NOT NULL CONSTRAINT DF_Orden_instrucciones DEFAULT N'';
IF COL_LENGTH('dbo.OrdenesIntervencion','tipo_orden') IS NULL ALTER TABLE dbo.OrdenesIntervencion ADD tipo_orden NVARCHAR(40) NOT NULL CONSTRAINT DF_Orden_tipo DEFAULT N'PATRULLAJE';
IF COL_LENGTH('dbo.OrdenesIntervencion','resultado') IS NULL ALTER TABLE dbo.OrdenesIntervencion ADD resultado NVARCHAR(2000) NULL;
IF COL_LENGTH('dbo.OrdenesIntervencion','fecha_cierre') IS NULL ALTER TABLE dbo.OrdenesIntervencion ADD fecha_cierre DATETIME2(0) NULL;
IF COL_LENGTH('dbo.OrdenesIntervencion','id_usuario_cierre') IS NULL ALTER TABLE dbo.OrdenesIntervencion ADD id_usuario_cierre INT NULL REFERENCES dbo.Usuarios(id_usuario);
IF COL_LENGTH('dbo.OrdenesIntervencion','solicitud_id') IS NULL ALTER TABLE dbo.OrdenesIntervencion ADD solicitud_id UNIQUEIDENTIFIER NULL;
ALTER TABLE dbo.OrdenesIntervencion ALTER COLUMN id_zona INT NULL;
IF OBJECT_ID('dbo.AvisosOrden','U') IS NULL
CREATE TABLE dbo.AvisosOrden(id_aviso INT IDENTITY PRIMARY KEY,id_orden INT NOT NULL UNIQUE REFERENCES dbo.OrdenesIntervencion(id_orden),
    id_unidad INT NOT NULL REFERENCES dbo.UnidadesOperativas(id_unidad),fecha_utc DATETIME2(0) NOT NULL DEFAULT SYSUTCDATETIME(),
    leido_utc DATETIME2(0) NULL,id_lector INT NULL REFERENCES dbo.Usuarios(id_usuario));
COMMIT;
GO
-- Lote separado: SQL Server necesita resolver las columnas recién agregadas.
SET XACT_ABORT ON;
BEGIN TRANSACTION;
UPDATE dbo.RecursosPreventivos SET nombre=CONCAT(tipo,N' #',id_recurso) WHERE nombre=N'';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE name='UQ_Orden_ReporteActiva' AND object_id=OBJECT_ID('dbo.OrdenesIntervencion'))
 CREATE UNIQUE INDEX UQ_Orden_ReporteActiva ON dbo.OrdenesIntervencion(id_reporte) WHERE id_reporte IS NOT NULL AND estado_atencion=N'ASIGNADA';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE name='UQ_Orden_RecursoActivo' AND object_id=OBJECT_ID('dbo.OrdenesIntervencion'))
 CREATE UNIQUE INDEX UQ_Orden_RecursoActivo ON dbo.OrdenesIntervencion(id_recurso) WHERE estado_atencion=N'ASIGNADA';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE name='UQ_Orden_Solicitud' AND object_id=OBJECT_ID('dbo.OrdenesIntervencion'))
 CREATE UNIQUE INDEX UQ_Orden_Solicitud ON dbo.OrdenesIntervencion(id_personal,solicitud_id) WHERE solicitud_id IS NOT NULL;
COMMIT;
GO
