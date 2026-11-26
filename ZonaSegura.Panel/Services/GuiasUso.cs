using System.Globalization;
using System.Text.Json;
using ZonaSegura.Panel.Models;
namespace ZonaSegura.Panel.Services;

// La misma fuente JSON se empaqueta como contenido MVC y como asset de Android.
public sealed class GuiasUso
{
    private readonly CatalogoGuias catalogo=JsonSerializer.Deserialize<CatalogoGuias>(
        File.ReadAllText(Path.Combine(AppContext.BaseDirectory,"Content","guias-uso.json")),new JsonSerializerOptions(JsonSerializerDefaults.Web))
        ??throw new InvalidOperationException("No se pudo leer el contenido de las guías de uso.");
    public IReadOnlyList<SeccionGuia> Para(string rol,string? buscar=null)
    {
        var guia=catalogo.Roles.SingleOrDefault(item=>item.Rol==rol);
        if(guia==null)return [];
        var secciones=guia.Web.Concat(catalogo.Comunes.Web);
        if(string.IsNullOrWhiteSpace(buscar))return secciones.ToList();
        bool Contiene(string valor)=>CultureInfo.GetCultureInfo("es").CompareInfo.IndexOf(valor,buscar,CompareOptions.IgnoreCase|CompareOptions.IgnoreNonSpace)>=0;
        return secciones.Select(section=>new SeccionGuia(section.Titulo,section.Botones.Where(b=>Contiene(section.Titulo)||Contiene(b.Boton)||Contiene(b.Funcion)||Contiene(b.Requisito)||Contiene(b.Resultado)).ToList()))
            .Where(section=>section.Botones.Count>0).ToList();
    }
}
