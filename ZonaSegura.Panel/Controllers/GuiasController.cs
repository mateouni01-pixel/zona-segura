using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using ZonaSegura.Panel.Models;
using ZonaSegura.Panel.Services;
namespace ZonaSegura.Panel.Controllers;

[Authorize]
public sealed class GuiasController(InterfacesUsuario interfaces,GuiasUso guias):SesionController
{
    [HttpGet]public IActionResult Index(string? rol,string? buscar)
    {
        var estrategia=interfaces.Buscar(rol??HttpContext.Session.GetString(InterfacesUsuario.SesionRol));
        if(estrategia==null)return RedirectToAction("Interfaces","Cuenta");
        if(!User.IsInRole(estrategia.Interfaz.Rol))return Forbid();
        buscar=buscar?.Trim();if(buscar?.Length>100)return BadRequest();
        ViewData["InterfazRol"]=estrategia.Interfaz.Rol;
        return View(new GuiaUsoModel(estrategia.Interfaz,guias.Para(estrategia.Interfaz.Rol,buscar),buscar));
    }
}
