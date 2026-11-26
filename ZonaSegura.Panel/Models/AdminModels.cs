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
public sealed class CrearUsuarioModel : IValidatableObject
{
    [Required(ErrorMessage="Escribe el nombre."),StringLength(150)]public string Nombre{get;set;}="";
    [Required(ErrorMessage="Escribe el correo."),EmailAddress(ErrorMessage="Escribe un correo válido."),StringLength(254)]public string Correo{get;set;}="";
    [Required(ErrorMessage="Escribe una contraseña."),StringLength(72,MinimumLength=8,ErrorMessage="La contraseña debe tener al menos 8 caracteres y como máximo 72 bytes."),DataType(DataType.Password)]public string Password{get;set;}="";
    [Required(ErrorMessage="Confirma la contraseña."),Compare(nameof(Password),ErrorMessage="Las contraseñas no coinciden."),DataType(DataType.Password)]public string ConfirmarPassword{get;set;}="";
    [MinLength(1,ErrorMessage="Selecciona al menos un rol.")]public List<string> Roles{get;set;}=[];
    [Required(ErrorMessage="Escribe el motivo de creación."),StringLength(2000,MinimumLength=10,ErrorMessage="El motivo debe tener entre 10 y 2000 caracteres.")]public string Motivo{get;set;}="";

    public IEnumerable<ValidationResult> Validate(ValidationContext validationContext)
    {
        if(!string.IsNullOrEmpty(Password)&&System.Text.Encoding.UTF8.GetByteCount(Password)>72)
            yield return new ValidationResult("La contraseña supera los 72 bytes. Usa una contraseña más corta.",[nameof(Password)]);
    }
}
public sealed class MotivoAdminModel
{
    [Range(1,int.MaxValue)]public int Id{get;set;}
    [Required,StringLength(2000,MinimumLength=10)]public string Motivo{get;set;}="";
}
public sealed class EliminarUsuarioModel
{
    [Range(1,int.MaxValue)]public int Id{get;set;}
    public string Nombre{get;set;}="";
    public string Correo{get;set;}="";
    [Required(ErrorMessage="Escribe el motivo de eliminación."),StringLength(2000,MinimumLength=10)]public string Motivo{get;set;}="";
    [Required(ErrorMessage="Escribe el ID de la cuenta."),Compare(nameof(Id),ErrorMessage="El ID de confirmación no coincide con la cuenta.")]public int? ConfirmacionId{get;set;}
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
