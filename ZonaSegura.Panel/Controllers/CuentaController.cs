using Microsoft.AspNetCore.Authentication;
using Microsoft.AspNetCore.Authentication.Cookies;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using System.Security.Claims;
using System.Text.Json;
using ZonaSegura.Panel.Models;
using ZonaSegura.Panel.Services;
namespace ZonaSegura.Panel.Controllers;
public sealed class CuentaController(ZonaApi api,InterfacesUsuario interfaces):Controller
{
    private IActionResult InicioSeleccionado()=>interfaces.Actual(HttpContext)?.Abrir()??RedirectToAction(nameof(Interfaces));
    private bool TieneSesion=>User.Identity?.IsAuthenticated==true&&!string.IsNullOrEmpty(HttpContext.Session.GetString("token"));
    [HttpGet]public IActionResult Index()=>TieneSesion?InicioSeleccionado():RedirectToAction(nameof(Ingresar));
    [HttpGet]public async Task<IActionResult> Ingresar()
    {
        if(TieneSesion)return InicioSeleccionado();
        HttpContext.Session.Clear();await HttpContext.SignOutAsync();return View(new LoginModel());
    }
    [HttpGet]public IActionResult AccesoDenegado()
    {
        if(User.Identity?.IsAuthenticated!=true)return RedirectToAction(nameof(Ingresar));
        Response.StatusCode=StatusCodes.Status403Forbidden;return View();
    }
    private async Task ActualizarPerfil(CancellationToken ct)
    {
        var perfil=await api.Get<PerfilAcceso>("api/auth/me",ct);
        var roles=perfil.Roles.Where(role=>interfaces.Buscar(role)!=null).Distinct().ToList();
        if(perfil.IdUsuario<=0||roles.Count==0)throw new ApiException(403,"Tu cuenta no tiene una interfaz disponible.");
        var claims=new List<Claim>{new(ClaimTypes.Name,perfil.Nombre),new(ClaimTypes.NameIdentifier,perfil.IdUsuario.ToString())};
        claims.AddRange(roles.Select(role=>new Claim(ClaimTypes.Role,role)));
        var principal=new ClaimsPrincipal(new ClaimsIdentity(claims,CookieAuthenticationDefaults.AuthenticationScheme));
        await HttpContext.SignInAsync(CookieAuthenticationDefaults.AuthenticationScheme,principal,new AuthenticationProperties{IsPersistent=false,ExpiresUtc=DateTimeOffset.UtcNow.AddMinutes(55)});
        HttpContext.User=principal;
        if(!roles.Contains(HttpContext.Session.GetString(InterfacesUsuario.SesionRol)??""))HttpContext.Session.Remove(InterfacesUsuario.SesionRol);
    }
    [HttpPost]public async Task<IActionResult> Ingresar(LoginModel model,CancellationToken ct)
    {
        if(!ModelState.IsValid)return View(model);
        HttpContext.Session.Clear();await HttpContext.SignOutAsync();
        try{
            var data=await api.Send<JsonElement>(HttpMethod.Post,"api/auth/login",new{model.Correo,model.Password},false,ct);
            HttpContext.Session.SetString("token",data.GetProperty("token").GetString()!);
            await ActualizarPerfil(ct);
            return RedirectToAction(nameof(Interfaces));
        }catch(ApiException e){ModelState.AddModelError("",e.Message);}catch(HttpRequestException){ModelState.AddModelError("","No se pudo conectar con la API.");}catch(TaskCanceledException){ModelState.AddModelError("","La API tardó demasiado en responder.");}
        HttpContext.Session.Clear();ModelState.Remove(nameof(model.Password));model.Password="";return View(model);
    }
    private SeleccionInterfazModel Seleccion(string? error=null)=>new(){Interfaces=interfaces.Disponibles(User).Select(item=>item.Interfaz).ToList(),RolActual=HttpContext.Session.GetString(InterfacesUsuario.SesionRol),Error=error};
    private async Task<IActionResult> ErrorSeleccion(Exception error)
    {
        if(error is ApiException {Status:401 or 403}||string.IsNullOrEmpty(HttpContext.Session.GetString("token"))){
            HttpContext.Session.Clear();await HttpContext.SignOutAsync();TempData["Error"]="Tu acceso cambió o tu sesión venció. Ingresa nuevamente.";return RedirectToAction(nameof(Ingresar));
        }
        return View("Interfaces",Seleccion(error is ApiException?error.Message:"No se pudieron comprobar tus roles. Revisa la conexión y vuelve a intentar."));
    }
    [Authorize,HttpGet]public async Task<IActionResult> Interfaces(CancellationToken ct)
    {
        try{await ActualizarPerfil(ct);return View(Seleccion());}
        catch(Exception e)when(e is ApiException or HttpRequestException or TaskCanceledException){return await ErrorSeleccion(e);}
    }
    [Authorize,HttpPost]public async Task<IActionResult> ElegirInterfaz(string rol,CancellationToken ct)
    {
        var estrategia=interfaces.Buscar(rol);if(estrategia==null)return BadRequest();
        try{
            await ActualizarPerfil(ct);
            if(!User.IsInRole(rol))return Forbid();
            HttpContext.Session.SetString(InterfacesUsuario.SesionRol,rol);
            return estrategia.Abrir();
        }catch(Exception e)when(e is ApiException or HttpRequestException or TaskCanceledException){return await ErrorSeleccion(e);}
    }
    [HttpPost]public async Task<IActionResult> Salir(){HttpContext.Session.Clear();await HttpContext.SignOutAsync();return RedirectToAction(nameof(Ingresar));}
}
