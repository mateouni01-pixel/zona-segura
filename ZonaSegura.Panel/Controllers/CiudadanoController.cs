using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using ZonaSegura.Panel.Models;
using ZonaSegura.Panel.Services;
namespace ZonaSegura.Panel.Controllers;

[Authorize(Roles="CIUDADANO")]
public sealed class CiudadanoController(ZonaApi api):SesionController
{
    private static bool ErrorServicio(Exception e)=>e is ApiException or HttpRequestException or TaskCanceledException;
    private static string Mensaje(Exception e)=>e is ApiException?e.Message:"No se pudo consultar la API. Revisa tu conexión y vuelve a intentar.";
    [HttpGet]public async Task<IActionResult> Index(CancellationToken ct)
    {
        var model=new MisReportesModel();
        try{model.Reportes=await api.Get<List<ReporteCiudadano>>("api/reportes",ct);}
        catch(Exception e)when(ErrorServicio(e)){model.Error=Mensaje(e);}
        return View(model);
    }
    private async Task Tipos(CrearReporteCiudadanoModel model,CancellationToken ct)=>model.Tipos=(await api.Get<List<Tipo>>("api/tipos-incidente",ct)).Where(item=>item.Activo).ToList();
    [HttpGet]public async Task<IActionResult> CrearReporte(CancellationToken ct)
    {
        var model=new CrearReporteCiudadanoModel();
        try{await Tipos(model,ct);}catch(Exception e)when(ErrorServicio(e)){ModelState.AddModelError("",Mensaje(e));}
        return View(model);
    }
    [HttpPost]public async Task<IActionResult> CrearReporte([Bind("Descripcion,TipoIncidenteId,Latitud,Longitud,SolicitudId")]CrearReporteCiudadanoModel model,CancellationToken ct)
    {
        if(!Guid.TryParse(model.SolicitudId,out _))ModelState.AddModelError("","La solicitud no es válida. Abre nuevamente Crear reporte.");
        try{
            await Tipos(model,ct);
            if(ModelState.IsValid){
                var reporte=await api.Send<ReporteCiudadano>(HttpMethod.Post,"api/reportes",new{Descripcion=model.Descripcion.Trim(),model.TipoIncidenteId,model.Latitud,model.Longitud},true,ct,model.SolicitudId);
                TempData["Exito"]=$"Reporte #{reporte.Id} registrado. Puedes seguir su estado en Mis reportes.";
                return RedirectToAction(nameof(Index));
            }
        }catch(Exception e)when(ErrorServicio(e)){ModelState.AddModelError("",Mensaje(e));}
        return View(model);
    }
    [HttpGet]public async Task<IActionResult> Mapa(int dias=7,CancellationToken ct=default)
    {
        if(dias is not (7 or 30 or 90))return BadRequest();
        var model=new MapaCiudadanoModel();
        try{model.Datos=await api.Get<MapaCiudadano>("api/mapa/resumen?dias="+dias,ct);}
        catch(Exception e)when(ErrorServicio(e)){model.Error=Mensaje(e);}
        return View(model);
    }
}
