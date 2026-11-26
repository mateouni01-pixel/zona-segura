-- Zona Segura - esquema inicial para SQL Server.
-- Ejecutar en SQL Server Management Studio. No crea usuarios de prueba ni contraseñas.
-- El backend Spring Boot debe generar el hash de la contraseña y emitir el JWT.

CREATE DATABASE ZonaSegura;
GO
USE ZonaSegura;
GO

-- SPRINT 1: identidad, roles y autenticación.
IF OBJECT_ID(N'dbo.Usuarios', N'U') IS NULL
CREATE TABLE dbo.Usuarios (
    id_usuario INT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    nombre NVARCHAR(150) NOT NULL,
    correo NVARCHAR(254) NOT NULL,
    password_hash NVARCHAR(255) NOT NULL,
    activo BIT NOT NULL CONSTRAINT DF_Usuarios_activo DEFAULT (1),
    creado_en DATETIME2(0) NOT NULL CONSTRAINT DF_Usuarios_creado DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT UQ_Usuarios_correo UNIQUE (correo)
);
GO

IF OBJECT_ID(N'dbo.Roles', N'U') IS NULL
CREATE TABLE dbo.Roles (
    id_rol TINYINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    nombre NVARCHAR(40) NOT NULL,
    CONSTRAINT UQ_Roles_nombre UNIQUE (nombre)
);
GO

IF OBJECT_ID(N'dbo.UsuarioRoles', N'U') IS NULL
CREATE TABLE dbo.UsuarioRoles (
    id_usuario INT NOT NULL,
    id_rol TINYINT NOT NULL,
    CONSTRAINT PK_UsuarioRoles PRIMARY KEY (id_usuario, id_rol),
    CONSTRAINT FK_UsuarioRoles_Usuarios FOREIGN KEY (id_usuario) REFERENCES dbo.Usuarios(id_usuario),
    CONSTRAINT FK_UsuarioRoles_Roles FOREIGN KEY (id_rol) REFERENCES dbo.Roles(id_rol)
);
GO

IF NOT EXISTS (SELECT 1 FROM dbo.Roles WHERE nombre = N'CIUDADANO')
    INSERT dbo.Roles(nombre) VALUES (N'CIUDADANO');
IF NOT EXISTS (SELECT 1 FROM dbo.Roles WHERE nombre = N'ADMINISTRADOR')
    INSERT dbo.Roles(nombre) VALUES (N'ADMINISTRADOR');
IF NOT EXISTS (SELECT 1 FROM dbo.Roles WHERE nombre = N'PERSONAL_ALCALDIA')
    INSERT dbo.Roles(nombre) VALUES (N'PERSONAL_ALCALDIA');
GO

-- Las clases Ciudadano, Administrador y PersonalAlcaldia heredan de Usuario.
IF OBJECT_ID(N'dbo.Ciudadanos', N'U') IS NULL
CREATE TABLE dbo.Ciudadanos (
    id_usuario INT NOT NULL PRIMARY KEY,
    telefono NVARCHAR(30) NULL,
    reputacion INT NOT NULL CONSTRAINT DF_Ciudadanos_reputacion DEFAULT (0),
    CONSTRAINT FK_Ciudadanos_Usuarios FOREIGN KEY (id_usuario) REFERENCES dbo.Usuarios(id_usuario)
);
GO

IF OBJECT_ID(N'dbo.Administradores', N'U') IS NULL
CREATE TABLE dbo.Administradores (
    id_usuario INT NOT NULL PRIMARY KEY,
    nivel_acceso NVARCHAR(50) NOT NULL,
    CONSTRAINT FK_Administradores_Usuarios FOREIGN KEY (id_usuario) REFERENCES dbo.Usuarios(id_usuario)
);
GO

IF OBJECT_ID(N'dbo.PersonalAlcaldia', N'U') IS NULL
CREATE TABLE dbo.PersonalAlcaldia (
    id_usuario INT NOT NULL PRIMARY KEY,
    cargo NVARCHAR(100) NOT NULL,
    rol_acceso NVARCHAR(50) NOT NULL,
    CONSTRAINT FK_PersonalAlcaldia_Usuarios FOREIGN KEY (id_usuario) REFERENCES dbo.Usuarios(id_usuario)
);
GO

-- SPRINT 2: reportes, ubicación, validación y recursos.
IF OBJECT_ID(N'dbo.TiposIncidente', N'U') IS NULL
CREATE TABLE dbo.TiposIncidente (
    id_tipo INT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    nombre NVARCHAR(100) NOT NULL,
    activo BIT NOT NULL CONSTRAINT DF_TiposIncidente_activo DEFAULT (1),
    CONSTRAINT UQ_TiposIncidente_nombre UNIQUE (nombre)
);
GO

IF OBJECT_ID(N'dbo.UbicacionesGPS', N'U') IS NULL
CREATE TABLE dbo.UbicacionesGPS (
    id_ubicacion INT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    latitud DECIMAL(9,6) NOT NULL,
    longitud DECIMAL(9,6) NOT NULL,
    CONSTRAINT CK_UbicacionesGPS_latitud CHECK (latitud BETWEEN -90 AND 90),
    CONSTRAINT CK_UbicacionesGPS_longitud CHECK (longitud BETWEEN -180 AND 180)
);
GO

IF OBJECT_ID(N'dbo.ZonasRiesgo', N'U') IS NULL
CREATE TABLE dbo.ZonasRiesgo (
    id_zona INT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    nivel_peligrosidad NVARCHAR(50) NOT NULL,
    poligono NVARCHAR(MAX) NULL
);
GO

IF OBJECT_ID(N'dbo.Reportes', N'U') IS NULL
CREATE TABLE dbo.Reportes (
    id_reporte INT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    id_ciudadano INT NOT NULL,
    id_tipo INT NOT NULL,
    id_ubicacion INT NOT NULL,
    id_zona INT NULL,
    indice_veracidad DECIMAL(5,4) NOT NULL CONSTRAINT DF_Reportes_veracidad DEFAULT (0),
    descripcion NVARCHAR(2000) NOT NULL,
    estado NVARCHAR(30) NOT NULL CONSTRAINT DF_Reportes_estado DEFAULT (N'PENDIENTE_VALIDACION'),
    fecha_hora DATETIME2(0) NOT NULL CONSTRAINT DF_Reportes_fecha DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT FK_Reportes_Ciudadanos FOREIGN KEY (id_ciudadano) REFERENCES dbo.Ciudadanos(id_usuario),
    CONSTRAINT FK_Reportes_TiposIncidente FOREIGN KEY (id_tipo) REFERENCES dbo.TiposIncidente(id_tipo),
    CONSTRAINT FK_Reportes_UbicacionesGPS FOREIGN KEY (id_ubicacion) REFERENCES dbo.UbicacionesGPS(id_ubicacion),
    CONSTRAINT FK_Reportes_ZonasRiesgo FOREIGN KEY (id_zona) REFERENCES dbo.ZonasRiesgo(id_zona),
    CONSTRAINT UQ_Reportes_Ubicacion UNIQUE (id_ubicacion),
    CONSTRAINT CK_Reportes_veracidad CHECK (indice_veracidad BETWEEN 0 AND 1),
    CONSTRAINT CK_Reportes_estado CHECK (estado IN (
        N'PENDIENTE_VALIDACION', N'VALIDADO', N'RECHAZADO', N'EN_ATENCION', N'ATENDIDO'))
);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name=N'IX_Reportes_Mapa' AND object_id=OBJECT_ID(N'dbo.Reportes'))
    CREATE INDEX IX_Reportes_Mapa ON dbo.Reportes(estado, fecha_hora, id_tipo) INCLUDE(id_ubicacion);
GO

IF OBJECT_ID(N'dbo.ValidacionesComunitarias', N'U') IS NULL
CREATE TABLE dbo.ValidacionesComunitarias (
    id_validacion INT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    id_reporte INT NOT NULL,
    id_ciudadano INT NOT NULL,
    tipo_voto BIT NOT NULL,
    fecha_hora DATETIME2(0) NOT NULL CONSTRAINT DF_Validaciones_fecha DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT FK_Validaciones_Reportes FOREIGN KEY (id_reporte) REFERENCES dbo.Reportes(id_reporte),
    CONSTRAINT FK_Validaciones_Ciudadanos FOREIGN KEY (id_ciudadano) REFERENCES dbo.Ciudadanos(id_usuario),
    CONSTRAINT UQ_Validaciones_reporte_ciudadano UNIQUE (id_reporte, id_ciudadano)
);
GO

IF OBJECT_ID(N'dbo.EvidenciasFoto', N'U') IS NULL
CREATE TABLE dbo.EvidenciasFoto (
    id_evidencia INT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    id_reporte INT NOT NULL,
    url NVARCHAR(1000) NOT NULL,
    CONSTRAINT FK_Evidencias_Reportes FOREIGN KEY (id_reporte) REFERENCES dbo.Reportes(id_reporte),
    CONSTRAINT UQ_Evidencias_reporte UNIQUE (id_reporte)
);
GO

IF OBJECT_ID(N'dbo.HistorialReputacion', N'U') IS NULL
CREATE TABLE dbo.HistorialReputacion (
    id_ciudadano INT NOT NULL PRIMARY KEY,
    puntaje INT NOT NULL CONSTRAINT DF_Historial_puntaje DEFAULT (0),
    CONSTRAINT FK_Historial_Ciudadanos FOREIGN KEY (id_ciudadano) REFERENCES dbo.Ciudadanos(id_usuario)
);
GO

IF OBJECT_ID(N'dbo.RecursosPreventivos', N'U') IS NULL
CREATE TABLE dbo.RecursosPreventivos (
    id_recurso INT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    tipo NVARCHAR(100) NOT NULL,
    disponible BIT NOT NULL CONSTRAINT DF_Recursos_disponible DEFAULT (1)
);
GO

IF OBJECT_ID(N'dbo.OrdenesIntervencion', N'U') IS NULL
CREATE TABLE dbo.OrdenesIntervencion (
    id_orden INT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    id_zona INT NOT NULL,
    id_personal INT NOT NULL,
    id_recurso INT NOT NULL,
    estado_atencion NVARCHAR(50) NOT NULL,
    fecha_hora DATETIME2(0) NOT NULL CONSTRAINT DF_Ordenes_fecha DEFAULT (SYSUTCDATETIME()),
    CONSTRAINT FK_Ordenes_ZonasRiesgo FOREIGN KEY (id_zona) REFERENCES dbo.ZonasRiesgo(id_zona),
    CONSTRAINT FK_Ordenes_Personal FOREIGN KEY (id_personal) REFERENCES dbo.PersonalAlcaldia(id_usuario),
    CONSTRAINT FK_Ordenes_Recursos FOREIGN KEY (id_recurso) REFERENCES dbo.RecursosPreventivos(id_recurso)
);
GO

-- MapaGeoespacial y CapaTermica representan vistas/cálculos sobre reportes y zonas;
-- no se guardan como tablas hasta definir cómo el backend genera esas capas.
-- Los reportes de cada historial se obtienen mediante Reportes.id_ciudadano.

SELECT @@SERVERNAME AS servidor, DB_NAME() AS base_actual;

-- Aplicar sobre la base de Zona Segura. Actualización aditiva e idempotente.
-- No confirma reportes antiguos ni cambia votos existentes.
IF OBJECT_ID(N'dbo.VerificacionesInstitucionales', N'U') IS NULL
CREATE TABLE dbo.VerificacionesInstitucionales (
    id_verificacion INT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    id_reporte INT NOT NULL,
    id_personal INT NOT NULL,
    confirma BIT NOT NULL,
    motivo NVARCHAR(2000) NOT NULL,
    fecha_hora DATETIME2(0) NOT NULL CONSTRAINT DF_VerificacionInstitucional_fecha DEFAULT SYSUTCDATETIME(),
    CONSTRAINT FK_VerificacionInstitucional_Reporte FOREIGN KEY(id_reporte) REFERENCES dbo.Reportes(id_reporte),
    CONSTRAINT FK_VerificacionInstitucional_Responsable FOREIGN KEY(id_personal) REFERENCES dbo.Usuarios(id_usuario),
    CONSTRAINT UQ_VerificacionInstitucional_Reporte UNIQUE(id_reporte),
    CONSTRAINT CK_VerificacionInstitucional_Motivo CHECK(LEN(LTRIM(RTRIM(motivo))) BETWEEN 10 AND 2000)
);
GO

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


-- Administración, moderación y sanciones. Aplicar después de sprint2-operaciones.sql.
-- Aditivo y reejecutable: no crea cuentas, concede permisos ni altera datos históricos.
SET XACT_ABORT ON;
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
    id_usuario_objetivo INT NOT NULL, -- ID historico: se conserva tras eliminar la cuenta.
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
