using Microsoft.AspNetCore.Authentication;
using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.Mvc.Filters;
namespace ZonaSegura.Panel.Controllers;

public abstract class SesionController:Controller
{
    private bool SinSesion=>User.Identity?.IsAuthenticated==true&&string.IsNullOrEmpty(HttpContext.Session.GetString("token"));
    private async Task<IActionResult> SesionVencida()
    {
        await HttpContext.SignOutAsync();
        const string mensaje="Tu sesión venció. Ingresa nuevamente.";
        if(Request.Headers["X-Requested-With"]=="XMLHttpRequest")return Unauthorized(new{mensaje});
        TempData["Error"]=mensaje;
        return RedirectToAction("Ingresar","Cuenta");
    }
    public override async Task OnActionExecutionAsync(ActionExecutingContext context,ActionExecutionDelegate next)
    {
        if(SinSesion){context.Result=await SesionVencida();return;}
        var executed=await next();
        if(SinSesion)executed.Result=await SesionVencida();
    }
}
