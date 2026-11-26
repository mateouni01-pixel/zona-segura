using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using ZonaSegura.Panel.Models;
using ZonaSegura.Panel.Services;
namespace ZonaSegura.Panel.Controllers;

[Authorize(Roles="ADMINISTRADOR")]
public sealed class AdminController(ZonaApi api):SesionController
{
    [HttpGet]public IActionResult Inicio()=>View();

    private static bool EsErrorServicio(Exception e)=>e is ApiException or HttpRequestException or TaskCanceledException;
    private static string Mensaje(Exception e)=>e is ApiException?e.Message:"No se pudo comunicar con la API. Actualiza la información antes de reintentar.";

    [HttpGet]public async Task<IActionResult> Index(string? buscar,CancellationToken ct)
    {
        var model=new UsuariosModel{Buscar=buscar?.Trim()};
        if(model.Buscar?.Length>100){model.Error="La búsqueda debe tener como máximo 100 caracteres.";return View(model);}
        try{model.Usuarios=await api.Get<List<UsuarioAdministrado>>("api/admin/usuarios"+(string.IsNullOrEmpty(model.Buscar)?"":"?buscar="+Uri.EscapeDataString(model.Buscar)),ct);}
        catch(Exception e)when(EsErrorServicio(e)){model.Error=Mensaje(e);}
        return View(model);
    }
    [HttpGet]public IActionResult CrearUsuario()=>View(new CrearUsuarioModel{Roles=["CIUDADANO"]});

    [HttpPost]public async Task<IActionResult> CrearUsuario([Bind("Nombre,Correo,Password,ConfirmarPassword,Roles,Motivo")]CrearUsuarioModel model,CancellationToken ct)
    {
        model.Roles??=[];
        if(model.Roles.Count==0)ModelState.AddModelError(nameof(model.Roles),"Selecciona al menos un rol.");
        if(model.Roles.Any(role=>!EditarUsuarioModel.RolesDisponibles.Contains(role)))ModelState.AddModelError(nameof(model.Roles),"Hay un rol no permitido.");
        if(ModelState.IsValid){
            try{
                var user=await api.Send<UsuarioAdministrado>(HttpMethod.Post,"api/admin/usuarios",new{
                    Nombre=model.Nombre.Trim(),Correo=model.Correo.Trim(),model.Password,
                    Roles=model.Roles.Distinct().ToArray(),Motivo=model.Motivo.Trim()
                },true,ct);
                TempData["Exito"]=$"Cuenta de {user.Nombre} creada. Ya puede iniciar sesión con los permisos asignados.";
                return RedirectToAction(nameof(Index));
            }catch(Exception e)when(EsErrorServicio(e)){ModelState.AddModelError("",Mensaje(e));}
        }
        ModelState.SetModelValue(nameof(model.Password),null,null);ModelState.SetModelValue(nameof(model.ConfirmarPassword),null,null);
        model.Password="";model.ConfirmarPassword="";
        return View(model);
    }
    [HttpGet]public async Task<IActionResult> EditarUsuario(int id,CancellationToken ct)
    {
        if(id<=0)return BadRequest();
        try{
            var user=await api.Get<UsuarioAdministrado>($"api/admin/usuarios/{id}",ct);
            return View(new EditarUsuarioModel{Id=user.Id,Nombre=user.Nombre,Correo=user.Correo,Activo=user.Activo,Roles=user.Roles,Sancionado=user.Sancionado});
        }catch(Exception e)when(EsErrorServicio(e)){TempData["Error"]=Mensaje(e);return RedirectToAction(nameof(Index));}
    }
    [HttpPost]public async Task<IActionResult> EditarUsuario([Bind("Id,Roles,Activo,Motivo")]EditarUsuarioModel model,CancellationToken ct)
    {
        if(model.Roles.Any(role=>!EditarUsuarioModel.RolesDisponibles.Contains(role)))ModelState.AddModelError(nameof(model.Roles),"Hay un rol no permitido.");
        try{
            var current=await api.Get<UsuarioAdministrado>($"api/admin/usuarios/{model.Id}",ct);
            model.Nombre=current.Nombre;model.Correo=current.Correo;model.Sancionado=current.Sancionado;
            if(ModelState.IsValid){
                await api.Send<string>(HttpMethod.Put,$"api/admin/usuarios/{model.Id}",new{Roles=model.Roles.Distinct().ToArray(),model.Activo,Motivo=model.Motivo.Trim()},true,ct);
                TempData["Exito"]="Permisos y acceso actualizados. El cambio quedó registrado en la auditoría.";
                return RedirectToAction(nameof(Index));
            }
        }catch(Exception e)when(EsErrorServicio(e)){ModelState.AddModelError("",Mensaje(e));}
        return View(model);
    }
    [HttpGet]public async Task<IActionResult> Moderacion(CancellationToken ct)
    {
        var model=new ModeracionModel();
        try{model.Reportes=await api.Get<List<ReporteModeracion>>("api/moderacion/reportes",ct);}
        catch(Exception e)when(EsErrorServicio(e)){model.Error=Mensaje(e);}
        return View(model);
    }
    [HttpGet]public async Task<IActionResult> EliminarUsuario(int id,CancellationToken ct)
    {
        if(id<=0)return BadRequest();
        if(User.FindFirst(System.Security.Claims.ClaimTypes.NameIdentifier)?.Value==id.ToString()){
            TempData["Error"]="No puedes eliminar tu propia cuenta administrativa.";return RedirectToAction(nameof(Index));
        }
        try{
            var user=await api.Get<UsuarioAdministrado>($"api/admin/usuarios/{id}",ct);
            return View(new EliminarUsuarioModel{Id=user.Id,Nombre=user.Nombre,Correo=user.Correo});
        }catch(Exception e)when(EsErrorServicio(e)){TempData["Error"]=Mensaje(e);return RedirectToAction(nameof(Index));}
    }
    [HttpPost]public async Task<IActionResult> EliminarUsuario([Bind("Id,Motivo,ConfirmacionId")]EliminarUsuarioModel model,CancellationToken ct)
    {
        if(model.Id<=0)return BadRequest();
        if(User.FindFirst(System.Security.Claims.ClaimTypes.NameIdentifier)?.Value==model.Id.ToString()){
            TempData["Error"]="No puedes eliminar tu propia cuenta administrativa.";return RedirectToAction(nameof(Index));
        }
        try{
            var user=await api.Get<UsuarioAdministrado>($"api/admin/usuarios/{model.Id}",ct);
            model.Nombre=user.Nombre;model.Correo=user.Correo;
            if(ModelState.IsValid){
                await api.Send<string>(HttpMethod.Delete,$"api/admin/usuarios/{model.Id}",new{Motivo=model.Motivo.Trim(),model.ConfirmacionId},true,ct);
                TempData["Exito"]=$"La cuenta #{model.Id} se eliminó definitivamente. La decisión quedó registrada en auditoría.";
                return RedirectToAction(nameof(Index));
            }
        }catch(Exception e)when(EsErrorServicio(e)){ModelState.AddModelError("",Mensaje(e));}
        return View(model);
    }
    [HttpGet]public async Task<IActionResult> Verificar(CancellationToken ct)
    {
        try{return View(await api.Get<List<Revision>>("api/admin/reportes-verificacion",ct));}
        catch(Exception e)when(EsErrorServicio(e)){TempData["Error"]=Mensaje(e);return RedirectToAction(nameof(Index));}
    }
    [HttpPost]public async Task<IActionResult> Verificar([Bind("Id,Confirma,Motivo")]VerificarModel model,CancellationToken ct)
    {
        if(!ModelState.IsValid){TempData["Error"]="Indica la decisión y un motivo de 10 a 2000 caracteres.";return RedirectToAction(nameof(Verificar));}
        try{await api.Send<string>(HttpMethod.Post,$"api/admin/reportes/{model.Id}/verificacion",new{model.Confirma,Motivo=model.Motivo.Trim()},true,ct);TempData["Exito"]="Revisión administrativa registrada.";}
        catch(Exception e)when(EsErrorServicio(e)){TempData["Error"]=Mensaje(e);}
        return RedirectToAction(nameof(Verificar));
    }
    [HttpPost]public async Task<IActionResult> Rechazar([Bind("Id,Motivo")]MotivoAdminModel model,CancellationToken ct)
    {
        if(!ModelState.IsValid){TempData["Error"]="Indica el reporte y un motivo de 10 a 2000 caracteres.";return RedirectToAction(nameof(Moderacion));}
        try{await api.Send<string>(HttpMethod.Post,$"api/reportes/{model.Id}/rechazo",new{Motivo=model.Motivo.Trim()},true,ct);TempData["Exito"]="Reporte rechazado con una decisión administrativa auditada.";}
        catch(Exception e)when(EsErrorServicio(e)){TempData["Error"]=Mensaje(e);}
        return RedirectToAction(nameof(Moderacion));
    }
    [HttpPost]public async Task<IActionResult> Sancionar([Bind("Id,Motivo")]MotivoAdminModel model,CancellationToken ct)
    {
        if(!ModelState.IsValid){TempData["Error"]="Indica el reporte y un motivo de 10 a 2000 caracteres.";return RedirectToAction(nameof(Moderacion));}
        try{await api.Send<string>(HttpMethod.Post,$"api/reportes/{model.Id}/sancion",new{Motivo=model.Motivo.Trim()},true,ct);TempData["Exito"]="Sanción registrada. El acceso de la cuenta quedó suspendido.";}
        catch(Exception e)when(EsErrorServicio(e)){TempData["Error"]=Mensaje(e);}
        return RedirectToAction(nameof(Moderacion));
    }
    [HttpPost]public async Task<IActionResult> LevantarSancion([Bind("Id,Motivo")]MotivoAdminModel model,CancellationToken ct)
    {
        if(!ModelState.IsValid){TempData["Error"]="Escribe un motivo de 10 a 2000 caracteres.";return RedirectToAction(nameof(EditarUsuario),new{id=model.Id});}
        try{await api.Send<string>(HttpMethod.Post,$"api/admin/usuarios/{model.Id}/levantar-sancion",new{Motivo=model.Motivo.Trim()},true,ct);TempData["Exito"]="Sanción levantada. Consulta el estado de acceso actualizado de la cuenta.";}
        catch(Exception e)when(EsErrorServicio(e)){TempData["Error"]=Mensaje(e);}
        return RedirectToAction(nameof(EditarUsuario),new{id=model.Id});
    }
    [HttpGet]public async Task<IActionResult> Auditoria([Bind("UsuarioId")]AuditoriaModel model,CancellationToken ct)
    {
        if(ModelState.IsValid){
            try{model.Acciones=await api.Get<List<AccionAuditada>>("api/admin/auditoria"+(model.UsuarioId.HasValue?"?usuarioId="+model.UsuarioId.Value:""),ct);}
            catch(Exception e)when(EsErrorServicio(e)){model.Error=Mensaje(e);}
        }
        return View(model);
    }
}
