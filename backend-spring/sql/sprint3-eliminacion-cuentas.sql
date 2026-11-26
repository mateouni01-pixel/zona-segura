-- Auditoria conserva el ID historico del objetivo aunque una cuenta sin actividad se elimine.
-- Se mantiene la FK del responsable y las FK de reportes/operaciones. No borra filas.
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF OBJECT_ID(N'dbo.AuditoriaAdministrativa', N'U') IS NULL
    THROW 50000, 'Aplica primero sprint3-administracion.sql.', 1;
DECLARE @constraint sysname;
DECLARE @sql nvarchar(max);
WHILE EXISTS (
    SELECT 1 FROM sys.foreign_key_columns f
    WHERE f.parent_object_id=OBJECT_ID(N'dbo.AuditoriaAdministrativa')
      AND COL_NAME(f.parent_object_id,f.parent_column_id)=N'id_usuario_objetivo'
      AND f.referenced_object_id=OBJECT_ID(N'dbo.Usuarios'))
BEGIN
    SELECT TOP (1) @constraint=OBJECT_NAME(f.constraint_object_id)
    FROM sys.foreign_key_columns f
    WHERE f.parent_object_id=OBJECT_ID(N'dbo.AuditoriaAdministrativa')
      AND COL_NAME(f.parent_object_id,f.parent_column_id)=N'id_usuario_objetivo'
      AND f.referenced_object_id=OBJECT_ID(N'dbo.Usuarios');
    SET @sql=N'ALTER TABLE dbo.AuditoriaAdministrativa DROP CONSTRAINT '+QUOTENAME(@constraint);
    EXEC(@sql);
END;
COMMIT TRANSACTION;
GO
