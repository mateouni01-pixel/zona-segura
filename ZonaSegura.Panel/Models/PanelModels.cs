using System.ComponentModel.DataAnnotations;
namespace ZonaSegura.Panel.Models;
public sealed class LoginModel
{
    [Required,EmailAddress] public string Correo{get;set;}="";
    [Required,DataType(DataType.Password)] public string Password{get;set;}="";
}
public sealed class Filtros
{
    public DateOnly Desde{get;set;}=DateOnly.FromDateTime(DateTime.UtcNow).AddDays(-6);
    public DateOnly Hasta{get;set;}=DateOnly.FromDateTime(DateTime.UtcNow);
    [Range(1,int.MaxValue)]public int? TipoIncidenteId{get;set;}
    public string? Estado{get;set;}
    [Range(0,1)]public double? VeracidadMinima{get;set;}
    [Range(-90d,90d)]public double? Sur{get;set;}
    [Range(-180d,180d)]public double? Oeste{get;set;}
    [Range(-90d,90d)]public double? Norte{get;set;}
    [Range(-180d,180d)]public double? Este{get;set;}
}
public sealed record Tipo(int Id,string Nombre,bool Activo);
public sealed record Fila(int Id,string Descripcion,string Estado,DateTimeOffset FechaRegistroUtc,double IndiceVeracidad,string TipoIncidenteNombre,double Latitud,double Longitud);
public sealed record Zona(string Id,string Nivel,int Cantidad,double[][] Poligono);
public sealed record Indicadores(int Total,int Pendientes,int Validados,int EnAtencion,int Atendidos,int Rechazados,int ZonasAltas);
public sealed record Resumen(List<Fila> Reportes,List<Zona> Zonas,string Desde,string Hasta,Indicadores? Indicadores=null)
{
    public Indicadores Metricas=>Indicadores??new(Reportes.Count,Reportes.Count(r=>r.Estado=="PENDIENTE_VALIDACION"),Reportes.Count(r=>r.Estado=="VALIDADO"),Reportes.Count(r=>r.Estado=="EN_ATENCION"),Reportes.Count(r=>r.Estado=="ATENDIDO"),Reportes.Count(r=>r.Estado=="RECHAZADO"),Zonas.Count(z=>z.Nivel=="ALTO"));
}
public sealed class PanelModel
{
    public Filtros Filtros{get;set;}=new();public List<Tipo> Tipos{get;set;}=[];public Resumen Datos{get;set;}=new([],[],"","");public string? Error{get;set;}
}
public sealed record Recurso(int Id,string Nombre,string Tipo,int UnidadId,bool Disponible,bool Activo);
public sealed record Unidad(int Id,string Nombre,bool Activo);
public sealed record Caso(int Id,string Descripcion,string Estado,string TipoIncidenteNombre,List<Recurso> RecursosDisponibles);
public sealed record Revision(int Id,string Descripcion,string Estado,string TipoIncidenteNombre,int Confirmaciones,int VotosNegativos,int ConfirmacionesRequeridas,string? OrigenValidacion,bool YaVoto,long? DistanciaMetros,double? Latitud,double? Longitud);
public sealed record Orden(int Id,int ReporteId,string Descripcion,string Estado,string TipoIncidenteNombre,int RecursoId,string RecursoNombre,int UnidadId,string UnidadNombre,string TipoOrden,string Instrucciones,string EstadoOrden,string? Resultado,double Latitud,double Longitud,string FechaUtc,string? CierreUtc,int AvisoId,string EstadoNotificacion);
public sealed class VerificarModel
{
    [Range(1,int.MaxValue)]public int Id{get;set;}
    [Required]public bool? Confirma{get;set;}
    [Required,StringLength(2000,MinimumLength=10)] public string Motivo{get;set;}="";
}
public sealed class RecursoModel
{
    public int? Id{get;set;}
    [Required,StringLength(150)]public string Nombre{get;set;}="";
    [Required,RegularExpression("PATRULLA|CUADRILLA")]public string Tipo{get;set;}="PATRULLA";
    [Range(1,int.MaxValue)]public int UnidadId{get;set;}
    public bool Disponible{get;set;}=true;public bool Activo{get;set;}=true;
    public List<Unidad> Unidades{get;set;}=[];
}
public sealed class AsignarModel
{
    [Range(1,int.MaxValue)]public int ReporteId{get;set;}
    [Range(1,int.MaxValue)]public int RecursoId{get;set;}
    [Required,RegularExpression("PATRULLAJE|MANTENIMIENTO|DERIVACION")]public string TipoOrden{get;set;}="PATRULLAJE";
    [Required,StringLength(2000,MinimumLength=10)]public string Instrucciones{get;set;}="";
    public string SolicitudId{get;set;}=Guid.NewGuid().ToString();
    public List<Recurso> Recursos{get;set;}=[];
}
