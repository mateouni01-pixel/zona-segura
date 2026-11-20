-- Ejecutar sobre la base ZonaSegura del backend Spring Boot.
-- Amplía el catálogo sin borrar incidentes o cuentas existentes.
IF COL_LENGTH(N'dbo.TiposIncidente', N'activo') IS NULL
    ALTER TABLE dbo.TiposIncidente ADD activo BIT NOT NULL
        CONSTRAINT DF_TiposIncidente_activo DEFAULT (1);
GO

SET XACT_ABORT ON;
SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;
BEGIN TRANSACTION;
IF NOT EXISTS (SELECT 1 FROM dbo.TiposIncidente)
BEGIN
    INSERT dbo.TiposIncidente(nombre) VALUES
        (N'Alumbrado público'),
        (N'Infraestructura peligrosa');
END;
COMMIT TRANSACTION;
GO
