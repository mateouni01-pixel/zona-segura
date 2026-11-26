using System.Globalization;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using System.Text.Json;
using ZonaSegura.Panel.Models;
using ZonaSegura.Panel.Services;
namespace ZonaSegura.Panel.Controllers;
[Authorize(Roles="PERSONAL_ALCALDIA")]
public sealed class PanelController(ZonaApi api):SesionController
{
    private void ValidarFiltros(Filtros filtros)
    {
        if(filtros.Desde>filtros.Hasta||filtros.Hasta.DayNumber-filtros.Desde.DayNumber>365)ModelState.AddModelError("","El rango debe estar ordenado y abarcar como máximo 366 días.");
        if(!string.IsNullOrWhiteSpace(filtros.Estado)&&!new[]{"PENDIENTE_VALIDACION","VALIDADO","RECHAZADO","EN_ATENCION","ATENDIDO"}.Contains(filtros.Estado))ModelState.AddModelError("","Estado inválido.");
        var bounds=new[]{filtros.Sur,filtros.Oeste,filtros.Norte,filtros.Este};
        int count=bounds.Count(value=>value.HasValue);
        if(count!=0&&(count!=4||bounds.Any(value=>!value.HasValue||!double.IsFinite(value.Value))||filtros.Sur>=filtros.Norte||filtros.Oeste>=filtros.Este))ModelState.AddModelError("","El área debe tener cuatro límites geográficos válidos y ordenados.");
    }
    private static string Query(Filtros f)=>"api/alcaldia/panel?desde="+f.Desde.ToString("yyyy-MM-dd")+"&hasta="+f.Hasta.ToString("yyyy-MM-dd")
        +(f.TipoIncidenteId.HasValue?"&tipoIncidenteId="+f.TipoIncidenteId:"")+(string.IsNullOrWhiteSpace(f.Estado)?"":"&estado="+Uri.EscapeDataString(f.Estado))
        +(f.VeracidadMinima.HasValue?"&veracidadMinima="+f.VeracidadMinima.Value.ToString(CultureInfo.InvariantCulture):"")
        +(f.Sur.HasValue?"&sur="+f.Sur.Value.ToString(CultureInfo.InvariantCulture)+"&oeste="+f.Oeste!.Value.ToString(CultureInfo.InvariantCulture)+"&norte="+f.Norte!.Value.ToString(CultureInfo.InvariantCulture)+"&este="+f.Este!.Value.ToString(CultureInfo.InvariantCulture):"");
    [HttpGet]public async Task<IActionResult> Index(Filtros filtros,CancellationToken ct)
    {
        var model=new PanelModel{Filtros=filtros};
        ValidarFiltros(filtros);
        try{model.Tipos=await api.Get<List<Tipo>>("api/tipos-incidente",ct);if(ModelState.IsValid)model.Datos=await api.Get<Resumen>(Query(filtros),ct);}
        catch(ApiException e){model.Error=e.Message;}catch(HttpRequestException){model.Error="No se pudo conectar con la API.";}catch(TaskCanceledException){model.Error="La API tardó demasiado en responder.";}
        return View(model);
    }
    [HttpGet]public async Task<IActionResult> Datos(Filtros filtros,CancellationToken ct)
    {
        ValidarFiltros(filtros);
        if(!ModelState.IsValid)return BadRequest(new{mensaje=string.Join(" ",ModelState.Values.SelectMany(value=>value.Errors).Select(error=>string.IsNullOrEmpty(error.ErrorMessage)?"Revisa los filtros de la consulta.":error.ErrorMessage).Distinct())});
        try{return Json(await api.Get<Resumen>(Query(filtros),ct));}
        catch(ApiException e){return StatusCode(e.Status,new{mensaje=e.Message});}
        catch(HttpRequestException){return StatusCode(502,new{mensaje="No se pudo conectar con la API. El mapa y los indicadores no se actualizaron."});}
        catch(TaskCanceledException)when(!ct.IsCancellationRequested){return StatusCode(504,new{mensaje="La API tardó demasiado. Vuelve a consultar el área."});}
    }
    [HttpGet]public async Task<IActionResult> Cobertura(CancellationToken ct)
    {
        try{return Json(await api.Get<JsonElement>("api/territorio/cobertura",ct));}
        catch(ApiException e){return StatusCode(e.Status,new{mensaje=e.Message});}
        catch(HttpRequestException){return StatusCode(502,new{mensaje="No se pudieron cargar los límites de cobertura."});}
        catch(TaskCanceledException)when(!ct.IsCancellationRequested){return StatusCode(504,new{mensaje="La cobertura tardó demasiado en responder."});}
    }
    [HttpGet]public async Task<IActionResult> Recursos(CancellationToken ct)=>await Listado<Recurso>("api/recursos",ct);
    [HttpGet]public async Task<IActionResult> Unidades(CancellationToken ct)=>await Listado<Unidad>("api/unidades",ct);
    [HttpGet]public async Task<IActionResult> Ordenes(CancellationToken ct)=>await Listado<Orden>("api/intervenciones/ordenes",ct);
    private async Task<IActionResult> Listado<T>(string path,CancellationToken ct){try{return View(await api.Get<List<T>>(path,ct));}catch(Exception e)when(e is ApiException or HttpRequestException or TaskCanceledException){TempData["Error"]=e is ApiException?e.Message:"No se pudo cargar la información.";return RedirectToAction(nameof(Index));}}
    [HttpGet]public async Task<IActionResult> EditarRecurso(int? id,CancellationToken ct)
    {try{var model=new RecursoModel{Unidades=await api.Get<List<Unidad>>("api/unidades",ct)};if(id.HasValue){var r=(await api.Get<List<Recurso>>("api/recursos",ct)).SingleOrDefault(r=>r.Id==id);if(r==null)return NotFound();model.Id=r.Id;model.Nombre=r.Nombre;model.Tipo=r.Tipo;model.UnidadId=r.UnidadId;model.Disponible=r.Disponible;model.Activo=r.Activo;}return View(model);}catch(Exception e)when(e is ApiException or HttpRequestException or TaskCanceledException){TempData["Error"]="No se pudo cargar el recurso.";return RedirectToAction(nameof(Recursos));}}
    [HttpPost]public async Task<IActionResult> EditarRecurso(RecursoModel model,CancellationToken ct)
    {try{model.Unidades=await api.Get<List<Unidad>>("api/unidades",ct);if(ModelState.IsValid){await api.Send<string>(model.Id.HasValue?HttpMethod.Put:HttpMethod.Post,"api/recursos"+(model.Id.HasValue?"/"+model.Id:""),new{model.Nombre,model.Tipo,model.UnidadId,model.Disponible,model.Activo},true,ct);TempData["Exito"]="Recurso guardado.";return RedirectToAction(nameof(Recursos));}}
        catch(Exception e)when(e is ApiException or HttpRequestException or TaskCanceledException){ModelState.AddModelError("",e is ApiException?e.Message:"No se pudo guardar el recurso.");}return View(model);}
    [HttpPost]public async Task<IActionResult> CrearUnidad(string nombre,CancellationToken ct)
    {if(string.IsNullOrWhiteSpace(nombre)||nombre.Trim().Length>150){TempData["Error"]="Indica un nombre de hasta 150 caracteres.";return RedirectToAction(nameof(Unidades));}try{await api.Send<string>(HttpMethod.Post,"api/unidades",new{nombre},true,ct);TempData["Exito"]="Unidad registrada.";}catch(Exception e)when(e is ApiException or HttpRequestException or TaskCanceledException){TempData["Error"]=e is ApiException?e.Message:"No se pudo crear la unidad.";}return RedirectToAction(nameof(Unidades));}
    [HttpPost]public async Task<IActionResult> AsociarMiembro(int id,int usuarioId,CancellationToken ct)
    {if(id<=0||usuarioId<=0){TempData["Error"]="Indica unidad y usuario válidos.";return RedirectToAction(nameof(Unidades));}try{await api.Send<string>(HttpMethod.Post,$"api/unidades/{id}/miembros",new{usuarioId},true,ct);TempData["Exito"]="Cuenta operativa asociada.";}catch(Exception e)when(e is ApiException or HttpRequestException or TaskCanceledException){TempData["Error"]=e is ApiException?e.Message:"No se pudo asociar la cuenta.";}return RedirectToAction(nameof(Unidades));}
    [HttpGet]public async Task<IActionResult> Asignar(int id,CancellationToken ct)
    {try{var item=(await api.Get<List<Caso>>("api/intervenciones",ct)).SingleOrDefault(c=>c.Id==id&&c.Estado=="VALIDADO");if(item==null)return NotFound();return View(new AsignarModel{ReporteId=id,Recursos=item.RecursosDisponibles});}catch(Exception e)when(e is ApiException or HttpRequestException or TaskCanceledException){TempData["Error"]="No se pudo cargar la asignación.";return RedirectToAction(nameof(Index));}}
    [HttpPost]public async Task<IActionResult> Asignar(AsignarModel model,CancellationToken ct)
    {if(!Guid.TryParse(model.SolicitudId,out _))ModelState.AddModelError("","Solicitud inválida.");try{var item=(await api.Get<List<Caso>>("api/intervenciones",ct)).SingleOrDefault(c=>c.Id==model.ReporteId);model.Recursos=item?.RecursosDisponibles??[];if(ModelState.IsValid){await api.Send<string>(HttpMethod.Post,"api/intervenciones",new{model.ReporteId,model.RecursoId,model.TipoOrden,model.Instrucciones,model.SolicitudId},true,ct);TempData["Exito"]="Orden creada y recurso reservado.";return RedirectToAction(nameof(Ordenes));}}
        catch(Exception e)when(e is ApiException or HttpRequestException or TaskCanceledException){ModelState.AddModelError("",e is ApiException?e.Message:"No se pudo confirmar la orden. Conserva esta solicitud al reintentar.");}return View(model);}
    [AllowAnonymous]public IActionResult Error()=>View();
}
