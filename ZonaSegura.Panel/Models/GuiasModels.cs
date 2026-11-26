using ZonaSegura.Panel.Services;
namespace ZonaSegura.Panel.Models;

public sealed record BotonGuia(string Boton,string Funcion,string Requisito,string Resultado);
public sealed record SeccionGuia(string Titulo,List<BotonGuia> Botones);
public sealed record ContenidoGuia(List<SeccionGuia> Web,List<SeccionGuia> Android);
public sealed record GuiaPorRol(string Rol,List<SeccionGuia> Web,List<SeccionGuia> Android);
public sealed record CatalogoGuias(int Version,ContenidoGuia Comunes,List<GuiaPorRol> Roles);
public sealed record GuiaUsoModel(InterfazUsuario Interfaz,IReadOnlyList<SeccionGuia> Secciones,string? Buscar);
