using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using ZonaSegura.Panel.Models;
using ZonaSegura.Panel.Services;
namespace ZonaSegura.Panel.Controllers;

[Authorize(Roles="UNIDAD_OPERATIVA")]
public sealed class OperativaController(ZonaApi api):SesionController
{
    private static bool ErrorServicio(Exception e)=>e is ApiException or HttpRequestException or TaskCanceledException;
    private static string Mensaje(Exception e)=>e is ApiException?e.Message:"No se pudo consultar la API. Revisa tu conexión y vuelve a intentar.";
    [HttpGet]public async Task<IActionResult> Index(CancellationToken ct)
    {
        var model=new BandejaOperativaModel();
        try{model.Ordenes=await api.Get<List<Orden>>("api/operativa/ordenes",ct);}
        catch(Exception e)when(ErrorServicio(e)){model.Error=Mensaje(e);}
        return View(model);
    }
    [HttpPost]public async Task<IActionResult> LeerAviso(int id,CancellationToken ct)
    {
        if(id<=0)return BadRequest();
        try{await api.Send<string>(HttpMethod.Post,$"api/operativa/avisos/{id}/leer",null,true,ct);TempData["Exito"]="Aviso marcado como leído.";}
        catch(Exception e)when(ErrorServicio(e)){TempData["Error"]=Mensaje(e);}
        return RedirectToAction(nameof(Index));
    }
    private async Task<Orden?> Buscar(int id,CancellationToken ct)=>(await api.Get<List<Orden>>("api/operativa/ordenes",ct)).SingleOrDefault(item=>item.Id==id);
    [HttpGet]public async Task<IActionResult> Atender(int id,CancellationToken ct)
    {
        if(id<=0)return BadRequest();
        try{var orden=await Buscar(id,ct);if(orden==null)return NotFound();return View(new AtenderOrdenModel{Id=id,Orden=orden});}
        catch(Exception e)when(ErrorServicio(e)){TempData["Error"]=Mensaje(e);return RedirectToAction(nameof(Index));}
    }
    [HttpPost]public async Task<IActionResult> Atender([Bind("Id,Resultado")]AtenderOrdenModel model,CancellationToken ct)
    {
        if(model.Id<=0)return BadRequest();
        try{
            model.Orden=await Buscar(model.Id,ct);if(model.Orden==null)return NotFound();
            if(ModelState.IsValid){
                await api.Send<Orden>(HttpMethod.Post,$"api/operativa/ordenes/{model.Id}/atender",new{Resultado=model.Resultado.Trim()},true,ct);
                TempData["Exito"]="Atención registrada. La orden quedó cerrada y el recurso disponible.";return RedirectToAction(nameof(Index));
            }
        }catch(Exception e)when(ErrorServicio(e)){ModelState.AddModelError("",Mensaje(e));}
        return View(model);
    }
}
