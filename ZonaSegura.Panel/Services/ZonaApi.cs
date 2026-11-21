using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Text.Json;
namespace ZonaSegura.Panel.Services;
public sealed class ApiException(int status,string message):Exception(message){public int Status{get;}=status;}
public sealed class ZonaApi(HttpClient http,IHttpContextAccessor accessor)
{
    public async Task<T> Get<T>(string path,CancellationToken ct)=>await Send<T>(HttpMethod.Get,path,null,true,ct);
    public async Task<T> Send<T>(HttpMethod method,string path,object? body,bool authenticated,CancellationToken ct)
    {
        using var request=new HttpRequestMessage(method,path);
        if(authenticated){var token=accessor.HttpContext!.Session.GetString("token");if(string.IsNullOrEmpty(token))throw new ApiException(401,"Tu sesión venció. Ingresa nuevamente.");request.Headers.Authorization=new AuthenticationHeaderValue("Bearer",token);}
        if(body!=null)request.Content=JsonContent.Create(body);
        using var response=await http.SendAsync(request,ct);
        if(!response.IsSuccessStatusCode){var message=$"La API rechazó la operación ({(int)response.StatusCode}).";
            try{var data=await response.Content.ReadFromJsonAsync<JsonElement>(ct);if(data.TryGetProperty("detalle",out var d)||data.TryGetProperty("detail",out d))message=d.GetString()??message;}catch(JsonException){}
            if((int)response.StatusCode==404&&(path=="api/alcaldia/panel"||path.StartsWith("api/alcaldia/panel?",StringComparison.Ordinal)))message="El servicio del mapa no está disponible en la versión del backend en ejecución. Actualiza y reinicia el backend.";
            if((int)response.StatusCode==401){message="Tu sesión venció. Ingresa nuevamente.";if(authenticated)accessor.HttpContext!.Session.Clear();}
            throw new ApiException((int)response.StatusCode,message);
        }
        if(typeof(T)==typeof(string))return (T)(object)await response.Content.ReadAsStringAsync(ct);
        return await response.Content.ReadFromJsonAsync<T>(ct)??throw new ApiException(502,"La API devolvió una respuesta vacía.");
    }
}
