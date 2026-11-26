using System.ComponentModel.DataAnnotations;
using Microsoft.AspNetCore.Mvc;
using ZonaSegura.Panel.Services;
namespace ZonaSegura.Panel.Models;

public sealed record PerfilAcceso(int IdUsuario,string Nombre,List<string> Roles,int CiudadanoId);
public sealed class SeleccionInterfazModel
{
    public IReadOnlyList<InterfazUsuario> Interfaces{get;set;}=[];
    public string? RolActual{get;set;}
    public string? Error{get;set;}
}
public sealed record ReporteCiudadano(int Id,string Descripcion,DateTimeOffset FechaRegistroUtc,string Estado,int CiudadanoId,int TipoIncidenteId,string TipoIncidenteNombre,double Latitud,double Longitud);
public sealed class MisReportesModel
{
    public List<ReporteCiudadano> Reportes{get;set;}=[];
    public string? Error{get;set;}
}
public sealed class CrearReporteCiudadanoModel
{
    [Required,StringLength(2000,MinimumLength=10)]public string Descripcion{get;set;}="";
    [Required,Range(1,int.MaxValue)]public int? TipoIncidenteId{get;set;}
    [Required,Range(-90d,90d),ModelBinder(BinderType=typeof(CoordenadaGpsBinder))]public double? Latitud{get;set;}
    [Required,Range(-180d,180d),ModelBinder(BinderType=typeof(CoordenadaGpsBinder))]public double? Longitud{get;set;}
    public string SolicitudId{get;set;}=Guid.NewGuid().ToString();
    public List<Tipo> Tipos{get;set;}=[];
}
public sealed record MapaCiudadano(List<Zona> Zonas,int TotalReportes,int Dias,DateTimeOffset DesdeUtc,DateTimeOffset ActualizadoUtc);
public sealed class MapaCiudadanoModel
{
    public MapaCiudadano? Datos{get;set;}
    public string? Error{get;set;}
}
public sealed class BandejaOperativaModel
{
    public List<Orden> Ordenes{get;set;}=[];
    public string? Error{get;set;}
}
public sealed class AtenderOrdenModel
{
    [Range(1,int.MaxValue)]public int Id{get;set;}
    [Required,StringLength(2000,MinimumLength=10)]public string Resultado{get;set;}="";
    public Orden? Orden{get;set;}
}
