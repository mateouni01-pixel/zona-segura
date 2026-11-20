-- Aplicar sobre ZonaSegura. No modifica reportes ni sus estados.
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name=N'IX_Reportes_Mapa' AND object_id=OBJECT_ID(N'dbo.Reportes'))
    CREATE INDEX IX_Reportes_Mapa ON dbo.Reportes(estado, fecha_hora, id_tipo) INCLUDE(id_ubicacion);
GO
