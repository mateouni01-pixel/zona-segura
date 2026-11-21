using System.ComponentModel.DataAnnotations;
namespace ZonaSegura.Panel.Models;

public sealed record UsuarioAdministrado(int Id,string Nombre,string Correo,bool Activo,List<string> Roles,bool Sancionado);
public sealed record ReporteModeracion(int Id,string TipoIncidenteNombre,string Descripcion,string Estado,double IndiceVeracidad,int VotosNegativos,int AutorId,bool Sancionado,bool VerificadoInstitucionalmente=false,bool SancionPrevia=false);
public sealed record AccionAuditada(long Id,string Accion,int ObjetivoId,string Motivo,int ResponsableId,DateTimeOffset FechaUtc,string? Detalle=null);
public sealed class UsuariosModel
{
    public string? Buscar{get;set;}
    public List<UsuarioAdministrado> Usuarios{get;set;}=[];
    public string? Error{get;set;}
}
public sealed class EditarUsuarioModel
{
    [Range(1,int.MaxValue)]public int Id{get;set;}
    public string Nombre{get;set;}="";
    public string Correo{get;set;}="";
    public bool Activo{get;set;}
    public bool Sancionado{get;set;}
    [MinLength(1,ErrorMessage="Selecciona al menos un rol.")]public List<string> Roles{get;set;}=[];
    [Required,StringLength(2000,MinimumLength=10)]public string Motivo{get;set;}="";
    public static readonly string[] RolesDisponibles=["CIUDADANO","PERSONAL_ALCALDIA","ADMINISTRADOR","UNIDAD_OPERATIVA"];
}
public sealed class MotivoAdminModel
{
    [Range(1,int.MaxValue)]public int Id{get;set;}
    [Required,StringLength(2000,MinimumLength=10)]public string Motivo{get;set;}="";
}
public sealed class ModeracionModel
{
    public List<ReporteModeracion> Reportes{get;set;}=[];
    public string? Error{get;set;}
}
public sealed class AuditoriaModel
{
    [Range(1,int.MaxValue)]public int? UsuarioId{get;set;}
    public List<AccionAuditada> Acciones{get;set;}=[];
    public string? Error{get;set;}
}
