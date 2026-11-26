using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;

namespace ZonaSegura.Panel.Services;

public sealed record EnlaceInterfaz(string Texto,string Accion,params string[] AccionesRelacionadas);
public sealed record InterfazUsuario(string Rol,string Nombre,string Descripcion,string Encabezado,
    string CssClase,string Controlador,string Accion,IReadOnlyList<EnlaceInterfaz> Menu);

// Strategy: cada rol define su entrada y navegación; las combinaciones filtran estrategias.
public interface IInterfazStrategy
{
    InterfazUsuario Interfaz{get;}
    RedirectToActionResult Abrir();
}
public abstract class InterfazStrategy(InterfazUsuario interfaz):IInterfazStrategy
{
    public InterfazUsuario Interfaz{get;}=interfaz;
    public RedirectToActionResult Abrir()=>new(Interfaz.Accion,Interfaz.Controlador,null);
}
public sealed class InterfazCiudadano():InterfazStrategy(new("CIUDADANO","Ciudadano",
    "Crea reportes, consulta su seguimiento y explora el mapa de incidentes.","Zona Segura · Ciudadanía",
    "portal-ciudadano","Ciudadano","Index",[new("Mis reportes","Index"),new("Crear reporte","CrearReporte"),new("Mapa de incidentes","Mapa")]));
public sealed class InterfazAlcaldia():InterfazStrategy(new("PERSONAL_ALCALDIA","Personal de Alcaldía",
    "Coordina incidentes, recursos, unidades y órdenes de intervención.","Alcaldía · Santa Cruz de la Sierra",
    "portal-alcaldia","Panel","Index",[new("Mapa y reportes","Index","Asignar"),new("Órdenes de intervención","Ordenes"),new("Recursos preventivos","Recursos","EditarRecurso"),new("Unidades operativas","Unidades")]));
public sealed class InterfazAdministrador():InterfazStrategy(new("ADMINISTRADOR","Administrador",
    "Gestiona cuentas, valida reportes y consulta moderación y auditoría.","Zona Segura · Administración",
    "portal-admin","Admin","Inicio",[new("Inicio","Inicio"),new("Usuarios y permisos","Index","CrearUsuario","EditarUsuario","EliminarUsuario"),new("Validar reportes","Verificar"),new("Moderación de reportes","Moderacion"),new("Auditoría","Auditoria")]));
public sealed class InterfazOperativa():InterfazStrategy(new("UNIDAD_OPERATIVA","Unidad Operativa",
    "Consulta las órdenes de tu unidad, confirma avisos y registra la atención.","Zona Segura · Operaciones",
    "portal-operativa","Operativa","Index",[new("Órdenes de mi unidad","Index","Atender")]));

public sealed class InterfacesUsuario(IEnumerable<IInterfazStrategy> estrategias)
{
    public const string SesionRol="interfaz-activa";
    private readonly IReadOnlyList<IInterfazStrategy> todas=estrategias.ToList();
    public IInterfazStrategy? Buscar(string? rol)=>todas.SingleOrDefault(item=>item.Interfaz.Rol==rol);
    public IReadOnlyList<IInterfazStrategy> Disponibles(ClaimsPrincipal usuario)=>
        usuario.Identity?.IsAuthenticated==true?todas.Where(item=>usuario.IsInRole(item.Interfaz.Rol)).ToList():[];
    public IInterfazStrategy? Actual(HttpContext context,string? controlador=null)
    {
        var disponibles=Disponibles(context.User);
        return disponibles.FirstOrDefault(item=>item.Interfaz.Controlador.Equals(controlador,StringComparison.OrdinalIgnoreCase))
            ??disponibles.FirstOrDefault(item=>item.Interfaz.Rol==context.Session.GetString(SesionRol));
    }
}
public static class RegistroInterfaces
{
    public static IServiceCollection AddInterfacesUsuario(this IServiceCollection services)=>services
        .AddSingleton<IInterfazStrategy,InterfazCiudadano>().AddSingleton<IInterfazStrategy,InterfazAlcaldia>()
        .AddSingleton<IInterfazStrategy,InterfazAdministrador>().AddSingleton<IInterfazStrategy,InterfazOperativa>()
        .AddSingleton<InterfacesUsuario>();
}
