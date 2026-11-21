using Microsoft.AspNetCore.Authentication;
using Microsoft.AspNetCore.Authentication.Cookies;
using Microsoft.AspNetCore.Mvc;
using System.Security.Claims;
using System.Text.Json;
using ZonaSegura.Panel.Models;
using ZonaSegura.Panel.Services;
namespace ZonaSegura.Panel.Controllers;
public sealed class CuentaController(ZonaApi api):Controller
{
    [HttpGet]public IActionResult Ingresar()=>View(new LoginModel());
    [HttpPost]public async Task<IActionResult> Ingresar(LoginModel model,CancellationToken ct)
    {
        if(!ModelState.IsValid)return View(model);
        HttpContext.Session.Clear();
        await HttpContext.SignOutAsync();
        try{
            var data=await api.Send<JsonElement>(HttpMethod.Post,"api/auth/login",new{model.Correo,model.Password},false,ct);
            var roles=data.GetProperty("roles").EnumerateArray().Select(r=>r.GetString()).Where(r=>r is "PERSONAL_ALCALDIA" or "ADMINISTRADOR").Cast<string>().Distinct().ToList();
            if(roles.Count==0){ModelState.AddModelError("","Esta cuenta no tiene acceso institucional al panel.");ModelState.Remove(nameof(model.Password));model.Password="";return View(model);}
            HttpContext.Session.SetString("token",data.GetProperty("token").GetString()!);
            try{foreach(var role in roles)await api.Get<JsonElement>(role=="ADMINISTRADOR"?"api/admin/perfil":"api/alcaldia/perfil",ct);}catch{HttpContext.Session.Clear();throw;}
            string name=data.GetProperty("nombre").GetString()??"Personal institucional";
            var claims=new List<Claim>{new(ClaimTypes.Name,name),new(ClaimTypes.NameIdentifier,data.GetProperty("idUsuario").ToString())};
            claims.AddRange(roles.Select(role=>new Claim(ClaimTypes.Role,role)));
            await HttpContext.SignInAsync(CookieAuthenticationDefaults.AuthenticationScheme,new ClaimsPrincipal(new ClaimsIdentity(claims,CookieAuthenticationDefaults.AuthenticationScheme)),new AuthenticationProperties{IsPersistent=false,ExpiresUtc=DateTimeOffset.UtcNow.AddMinutes(55)});
            return RedirectToAction("Index",roles.Contains("ADMINISTRADOR")?"Admin":"Panel");
        }catch(ApiException e){ModelState.AddModelError("",e.Message);}catch(HttpRequestException){ModelState.AddModelError("","No se pudo conectar con la API.");}catch(TaskCanceledException){ModelState.AddModelError("","La API tardó demasiado en responder.");}
        ModelState.Remove(nameof(model.Password));model.Password="";return View(model);
    }
    [HttpPost]public async Task<IActionResult> Salir(){HttpContext.Session.Clear();await HttpContext.SignOutAsync();return RedirectToAction(nameof(Ingresar));}
}
