-- El responsable de una revisión manual es un administrador/moderador (Usuarios).
-- Conserva la columna histórica id_personal, las decisiones y todos sus responsables.
-- Reejecutable; no concede roles ni modifica reportes o votos.
SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
BEGIN TRANSACTION;

IF OBJECT_ID(N'dbo.VerificacionesInstitucionales', N'U') IS NULL
    THROW 51000, 'Primero instala sprint2-verificacion.sql.', 1;

IF EXISTS(SELECT 1 FROM dbo.VerificacionesInstitucionales v LEFT JOIN dbo.Usuarios u ON u.id_usuario=v.id_personal WHERE u.id_usuario IS NULL)
    THROW 51000, 'Hay responsables históricos sin una cuenta de usuario. No se modificó la relación.', 1;

DECLARE @dropSql NVARCHAR(MAX)=N'';
SELECT @dropSql+=N'ALTER TABLE dbo.VerificacionesInstitucionales DROP CONSTRAINT '+QUOTENAME(fk.name)+N';'
FROM sys.foreign_keys fk
JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id=fk.object_id
WHERE fk.parent_object_id=OBJECT_ID(N'dbo.VerificacionesInstitucionales')
  AND fk.referenced_object_id=OBJECT_ID(N'dbo.PersonalAlcaldia')
  AND COL_NAME(fkc.parent_object_id,fkc.parent_column_id)=N'id_personal';
IF LEN(@dropSql)>0 EXEC sys.sp_executesql @dropSql;

IF NOT EXISTS(
    SELECT 1 FROM sys.foreign_keys fk
    JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id=fk.object_id
    WHERE fk.parent_object_id=OBJECT_ID(N'dbo.VerificacionesInstitucionales')
      AND fk.referenced_object_id=OBJECT_ID(N'dbo.Usuarios')
      AND COL_NAME(fkc.parent_object_id,fkc.parent_column_id)=N'id_personal'
      AND COL_NAME(fkc.referenced_object_id,fkc.referenced_column_id)=N'id_usuario'
)
    ALTER TABLE dbo.VerificacionesInstitucionales WITH CHECK
        ADD CONSTRAINT FK_VerificacionInstitucional_Responsable FOREIGN KEY(id_personal) REFERENCES dbo.Usuarios(id_usuario);

COMMIT TRANSACTION;
GO
