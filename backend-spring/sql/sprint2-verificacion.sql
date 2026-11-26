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
