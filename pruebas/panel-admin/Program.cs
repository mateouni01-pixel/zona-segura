using System.Net;
using System.Net.Http.Json;
using System.Security.Claims;
using System.Text.Encodings.Web;
using System.Text.Json;
using System.Text.RegularExpressions;
using Microsoft.AspNetCore.Authentication;
using Microsoft.AspNetCore.Authentication.Cookies;
using Microsoft.AspNetCore.DataProtection;
using Microsoft.AspNetCore.Mvc;
using Microsoft.Extensions.Options;
using ZonaSegura.Panel.Controllers;
using ZonaSegura.Panel.Models;
using ZonaSegura.Panel.Services;

// Servidor de prueba aislado: identidad y API simuladas, sin acceso a SQL Server.
var builder=WebApplication.CreateBuilder(args);
builder.WebHost.UseUrls("http://127.0.0.1:0");
builder.Logging.ClearProviders();
builder.Services.AddDataProtection().UseEphemeralDataProtectionProvider();
builder.Services.AddControllersWithViews(options=>options.Filters.Add(new AutoValidateAntiforgeryTokenAttribute()))
    .AddApplicationPart(typeof(AdminController).Assembly);
builder.Services.AddDistributedMemoryCache();
builder.Services.AddSession();
builder.Services.AddHttpContextAccessor();
builder.Services.AddAuthentication(options=>{
    options.DefaultScheme="Test";
    options.DefaultSignInScheme=CookieAuthenticationDefaults.AuthenticationScheme;
    options.DefaultSignOutScheme=CookieAuthenticationDefaults.AuthenticationScheme;
}).AddPolicyScheme("Test","Identidad de prueba o sesión real",options=>
    options.ForwardDefaultSelector=context=>context.Request.Headers.ContainsKey("X-Test-Role")?"Header":CookieAuthenticationDefaults.AuthenticationScheme)
    .AddScheme<AuthenticationSchemeOptions,TestIdentity>("Header",_=>{})
    .AddCookie(options=>{options.LoginPath="/Cuenta/Ingresar";options.AccessDeniedPath="/Cuenta/AccesoDenegado";});
builder.Services.AddAuthorization();builder.Services.AddInterfacesUsuario();
var api=new FakeApi();
builder.Services.AddTransient(provider=>new ZonaApi(new HttpClient(api,false){BaseAddress=new Uri("https://api.test/")},provider.GetRequiredService<IHttpContextAccessor>()));
await using var app=builder.Build();
app.UseRouting();app.UseSession();app.UseAuthentication();
app.Use(async(context,next)=>{if(context.Request.Headers.ContainsKey("X-Test-Role")&&context.User.Identity?.IsAuthenticated==true)context.Session.SetString("token","test-token");await next();});
app.UseAuthorization();
app.MapControllerRoute("default","{controller=Cuenta}/{action=Index}/{id?}");
await app.StartAsync();
try{
    using var client=new HttpClient(new HttpClientHandler{AllowAutoRedirect=false}){BaseAddress=new Uri(app.Urls.Single())};
    var checks=0;
    void Check(bool ok,string message){if(!ok)throw new InvalidOperationException(message);checks++;Console.WriteLine("PASS: "+message);}
    using(var anonymous=await client.GetAsync("/Admin/CrearUsuario"))Check(anonymous.StatusCode==HttpStatusCode.Redirect&&anonymous.Headers.Location!.ToString().Contains("/Cuenta/Ingresar"),"El formulario exige iniciar sesión");
    client.DefaultRequestHeaders.Add("X-Test-Role","PERSONAL_ALCALDIA");
    using(var citizen=await client.GetAsync("/Admin/CrearUsuario"))Check(citizen.StatusCode==HttpStatusCode.Forbidden,"Personal municipal no puede abrir creación de cuentas");
    client.DefaultRequestHeaders.Remove("X-Test-Role");client.DefaultRequestHeaders.Add("X-Test-Role","ADMINISTRADOR");
    var html=await client.GetStringAsync("/Admin/CrearUsuario");
    Check(html.Contains("Crear usuario")&&html.Contains("value=\"ADMINISTRADOR\""),"Administrador puede abrir el formulario y seleccionar otros administradores");
    Check(html.Contains("portal-admin"),"La sesión administrativa muestra su propio panel");
    var token=WebUtility.HtmlDecode(Regex.Match(html,"name=\"__RequestVerificationToken\"[^>]*value=\"([^\"]+)\"").Groups[1].Value);
    Check(token.Length>0,"Formulario contiene protección antifalsificación");
    List<KeyValuePair<string,string>> Fields(bool includeRoles=true)=>[
        new("__RequestVerificationToken",token),new("Nombre","Cuenta de prueba"),new("Correo","prueba@example.com"),
        new("Password","ClaveSegura123"),new("ConfirmarPassword","ClaveSegura123"),new("Motivo","Alta autorizada para pruebas"),
        ..(includeRoles?new[]{new KeyValuePair<string,string>("Roles","ADMINISTRADOR")}:[])
    ];
    using(var forbiddenRequest=new HttpRequestMessage(HttpMethod.Post,"/Admin/CrearUsuario") {Content=new FormUrlEncodedContent(Fields())}){
        forbiddenRequest.Headers.Add("X-Test-Role","PERSONAL_ALCALDIA");
        using var forbidden=await client.SendAsync(forbiddenRequest);
        Check(forbidden.StatusCode==HttpStatusCode.Forbidden&&api.Requests==0,"Se bloquea también el envío directo de personal municipal");
    }
    using(var withoutCsrf=await client.PostAsync("/Admin/CrearUsuario",new FormUrlEncodedContent(Fields().Where(pair=>pair.Key!="__RequestVerificationToken"))))
        Check(withoutCsrf.StatusCode==HttpStatusCode.BadRequest&&api.Requests==0,"Se bloquea la creación sin token antifalsificación");
    using(var withoutRole=await client.PostAsync("/Admin/CrearUsuario",new FormUrlEncodedContent(Fields(false)))){
        var body=WebUtility.HtmlDecode(await withoutRole.Content.ReadAsStringAsync());
        Check(body.Contains("Selecciona al menos un rol")&&api.Requests==0,"Ningún rol seleccionado muestra error y no crea cuentas");
    }
    var mismatch=Fields();mismatch.RemoveAll(pair=>pair.Key=="ConfirmarPassword");mismatch.Add(new("ConfirmarPassword","OtraClave123"));
    using(var invalid=await client.PostAsync("/Admin/CrearUsuario",new FormUrlEncodedContent(mismatch))){
        var body=WebUtility.HtmlDecode(await invalid.Content.ReadAsStringAsync());
        Check(body.Contains("Las contraseñas no coinciden")&&api.Requests==0,"Contraseñas distintas se rechazan antes de llamar a la API");
        Check(!body.Contains("value=\"ClaveSegura123\"")&&!body.Contains("value=\"OtraClave123\""),"Las contraseñas no se vuelven a incluir en el HTML");
    }
    var oversized=Fields();oversized.RemoveAll(pair=>pair.Key is "Password" or "ConfirmarPassword");
    var longPassword=string.Concat(Enumerable.Repeat("😀",19));oversized.Add(new("Password",longPassword));oversized.Add(new("ConfirmarPassword",longPassword));
    using(var invalid=await client.PostAsync("/Admin/CrearUsuario",new FormUrlEncodedContent(oversized))){
        Check(WebUtility.HtmlDecode(await invalid.Content.ReadAsStringAsync()).Contains("72 bytes")&&api.Requests==0,"Se valida el límite de contraseña en bytes UTF-8");
    }
    api.Duplicate=true;
    using(var duplicate=await client.PostAsync("/Admin/CrearUsuario",new FormUrlEncodedContent(Fields()))){
        var body=WebUtility.HtmlDecode(await duplicate.Content.ReadAsStringAsync());
        Check(body.Contains("El correo ya está registrado")&&!body.Contains("value=\"ClaveSegura123\""),"Correo duplicado muestra el error conservando las contraseñas vacías");
    }
    api.Duplicate=false;
    using(var created=await client.PostAsync("/Admin/CrearUsuario",new FormUrlEncodedContent(Fields()))){
        Check(created.StatusCode==HttpStatusCode.Redirect&&created.Headers.Location?.ToString()=="/Admin","Creación correcta redirige al directorio de usuarios");
        Check(api.LastBody.GetProperty("roles")[0].GetString()=="ADMINISTRADOR"&&!api.LastBody.TryGetProperty("confirmarPassword",out _),"Se envían los roles al endpoint administrativo sin confirmación de contraseña");
    }
    var directory=await client.GetStringAsync("/Admin");
    Check(directory.Contains("/Admin/CrearUsuario")&&WebUtility.HtmlDecode(directory).Contains("Ya puede iniciar sesión"),"Directorio ofrece crear usuarios y muestra confirmación");
    client.DefaultRequestHeaders.Remove("X-Test-Role");client.DefaultRequestHeaders.Add("X-Test-Role","PERSONAL_ALCALDIA");
    using(var forbidden=await client.GetAsync("/Admin/Verificar"))Check(forbidden.StatusCode==HttpStatusCode.Forbidden,"Alcaldía no puede abrir la revisión administrativa");
    using(var legacy=await client.GetAsync("/Panel/Verificar"))Check(legacy.StatusCode is HttpStatusCode.Forbidden or HttpStatusCode.NotFound,"La antigua pantalla municipal no permite validar reportes");
    client.DefaultRequestHeaders.Remove("X-Test-Role");client.DefaultRequestHeaders.Add("X-Test-Role","ADMINISTRADOR");
    var review=WebUtility.HtmlDecode(await client.GetStringAsync("/Admin/Verificar"));
    Check(review.Contains("Validar reportes")&&review.Contains("/Admin/Verificar")&&!review.Contains("/Panel/Verificar"),"La validación manual aparece solo en el menú administrativo");
    var callsBeforeDecision=api.Requests;
    var decision=new List<KeyValuePair<string,string>>{new("__RequestVerificationToken",token),new("Id","10"),new("Motivo","Revisión fundamentada de la evidencia")};
    using(var missing=await client.PostAsync("/Admin/Verificar",new FormUrlEncodedContent(decision)))
        Check(missing.StatusCode==HttpStatusCode.Redirect&&api.Requests==callsBeforeDecision,"No se registra un rechazo si falta una decisión explícita");
    decision.Add(new("Confirma","true"));
    using(var verified=await client.PostAsync("/Admin/Verificar",new FormUrlEncodedContent(decision)))
        Check(verified.StatusCode==HttpStatusCode.Redirect&&api.LastPath=="/api/admin/reportes/10/verificacion"&&api.LastBody.GetProperty("confirma").GetBoolean(),"Administrador valida mediante el endpoint administrativo");
    // Desde aquí se prueba el acceso real del controlador con cookies y sesión MVC.
    HttpClient NewClient()=>new(new HttpClientHandler{AllowAutoRedirect=false}){BaseAddress=client.BaseAddress};
    string Csrf(string body)=>WebUtility.HtmlDecode(Regex.Match(body,"name=\"__RequestVerificationToken\"[^>]*value=\"([^\"]+)\"").Groups[1].Value);
    async Task<HttpResponseMessage> Login(HttpClient browser,params string[] roles){
        api.LoginRoles=roles;
        var login=await browser.GetStringAsync("/Cuenta/Ingresar");
        return await browser.PostAsync("/Cuenta/Ingresar",new FormUrlEncodedContent(new Dictionary<string,string>{
            ["__RequestVerificationToken"]=Csrf(login),["Correo"]="prueba@example.com",["Password"]="ClaveSegura123"
        }));
    }
    async Task<HttpResponseMessage> Choose(HttpClient browser,string role){
        var selection=await browser.GetStringAsync("/Cuenta/Interfaces");
        return await browser.PostAsync("/Cuenta/ElegirInterfaz",new FormUrlEncodedContent(new Dictionary<string,string>{["__RequestVerificationToken"]=Csrf(selection),["rol"]=role}));
    }
    using var publicBrowser=NewClient();
    using(var root=await publicBrowser.GetAsync("/"))Check(root.Headers.Location?.ToString()=="/Cuenta/Ingresar","La raíz anónima dirige al acceso");
    var access=WebUtility.HtmlDecode(await publicBrowser.GetStringAsync("/Cuenta/Ingresar"));
    Check(access.Contains("portal-acceso")&&access.Contains("roles de tu cuenta")&&!access.Contains("<select"),"El login explica la elección posterior sin conceder roles");
    var registry=app.Services.GetRequiredService<InterfacesUsuario>();
    string[] allRoles=["CIUDADANO","PERSONAL_ALCALDIA","ADMINISTRADOR","UNIDAD_OPERATIVA"];
    string[] destinations=["/Ciudadano","/Panel","/Admin/Inicio","/Operativa"];
    for(int mask=1;mask<16;mask++){
        var roles=allRoles.Where((role,index)=>(mask&(1<<index))!=0).ToArray();
        using var browser=NewClient();
        using(var login=await Login(browser,roles))Check(login.StatusCode==HttpStatusCode.Redirect&&login.Headers.Location?.ToString()=="/Cuenta/Interfaces",$"Combinación {mask}: login abre el selector");
        var selection=await browser.GetStringAsync("/Cuenta/Interfaces");
        Check(allRoles.All(role=>selection.Contains("data-rol=\""+role+"\"")==roles.Contains(role)),$"Combinación {mask}: solo aparecen los roles asignados");
        foreach(var role in roles){
            using var selected=await Choose(browser,role);
            Check(selected.StatusCode==HttpStatusCode.Redirect&&selected.Headers.Location?.ToString()==destinations[Array.IndexOf(allRoles,role)],$"Combinación {mask}: abre {role}");
            using var root=await browser.GetAsync("/");
            Check(root.Headers.Location?.ToString()==destinations[Array.IndexOf(allRoles,role)],$"Combinación {mask}: conserva la interfaz elegida");
        }
    }
    using var adminBrowser=NewClient();
    using(var login=await Login(adminBrowser,"ADMINISTRADOR"))Check(login.Headers.Location?.ToString()=="/Cuenta/Interfaces","Administrador elige su interfaz");
    using(var chosen=await Choose(adminBrowser,"ADMINISTRADOR"))Check(chosen.Headers.Location?.ToString()=="/Admin/Inicio","La estrategia administrativa abre su inicio");
    var adminHome=WebUtility.HtmlDecode(await adminBrowser.GetStringAsync("/Admin/Inicio"));
    Check(adminHome.Contains("portal-admin")&&adminHome.Contains("Administra Zona Segura")&&!adminHome.Contains("Menú de Personal de Alcaldía"),"El menú administrativo no mezcla otros roles");
    using(var forbidden=await adminBrowser.GetAsync("/Panel"))Check(forbidden.StatusCode==HttpStatusCode.Redirect&&forbidden.Headers.Location!.ToString().Contains("/Cuenta/AccesoDenegado"),"Administrador sin rol municipal no entra al mapa por URL directa");
    using(var denied=await adminBrowser.GetAsync("/Cuenta/AccesoDenegado"))Check(denied.StatusCode==HttpStatusCode.Forbidden,"La falta de permisos responde 403");
    using var municipalBrowser=NewClient();
    using(var login=await Login(municipalBrowser,"PERSONAL_ALCALDIA")){}
    using(var chosen=await Choose(municipalBrowser,"PERSONAL_ALCALDIA")){}
    var municipalHome=WebUtility.HtmlDecode(await municipalBrowser.GetStringAsync("/Panel"));
    Check(municipalHome.Contains("portal-alcaldia")&&municipalHome.Contains("Mapa y reportes")&&!municipalHome.Contains("Menú de Administrador"),"Alcaldía conserva su propia interfaz");
    Check(!municipalHome.Contains("/Panel/Verificar")&&!municipalHome.Contains("data-verificar-url")&&municipalHome.Contains("En espera de validación"),"El mapa municipal conserva la separación de validación");
    using var combinedBrowser=NewClient();
    using(var login=await Login(combinedBrowser,"CIUDADANO","PERSONAL_ALCALDIA")){}
    using(var chosen=await Choose(combinedBrowser,"CIUDADANO")){}
    var citizenHome=WebUtility.HtmlDecode(await combinedBrowser.GetStringAsync("/Ciudadano"));
    Check(citizenHome.Contains("portal-ciudadano")&&citizenHome.Contains("Mis reportes")&&citizenHome.Contains("Cambiar interfaz")&&!citizenHome.Contains("Menú de Personal de Alcaldía"),"Ciudadano y Alcaldía: el usuario puede trabajar como ciudadano");
    using(var chosen=await Choose(combinedBrowser,"PERSONAL_ALCALDIA")){}
    Check(WebUtility.HtmlDecode(await combinedBrowser.GetStringAsync("/Panel/Recursos")).Contains("portal-alcaldia"),"Ciudadano y Alcaldía: cambiar adapta también las pantallas interiores");
    using(var spoofed=await Choose(combinedBrowser,"ADMINISTRADOR"))Check(spoofed.StatusCode==HttpStatusCode.Redirect&&spoofed.Headers.Location!.ToString().Contains("/Cuenta/AccesoDenegado"),"Elegir un rol no asignado no concede permisos");
    using var revokedRole=NewClient();
    using(var login=await Login(revokedRole,"CIUDADANO","ADMINISTRADOR")){}
    var beforeRevoke=await revokedRole.GetStringAsync("/Cuenta/Interfaces");
    api.LoginRoles=["CIUDADANO"];
    using(var revokedChoice=await revokedRole.PostAsync("/Cuenta/ElegirInterfaz",new FormUrlEncodedContent(new Dictionary<string,string>{["__RequestVerificationToken"]=Csrf(beforeRevoke),["rol"]="ADMINISTRADOR"})))
        Check(revokedChoice.StatusCode==HttpStatusCode.Redirect&&revokedChoice.Headers.Location!.ToString().Contains("/Cuenta/AccesoDenegado"),"Se comprueban los roles actuales al elegir, incluso si se revocaron con el selector abierto");
    using(var csrf=await revokedRole.PostAsync("/Cuenta/ElegirInterfaz",new FormUrlEncodedContent(new Dictionary<string,string>{["rol"]="CIUDADANO"})))Check(csrf.StatusCode==HttpStatusCode.BadRequest,"Cambiar interfaz exige antifalsificación");
    using(var noRole=await Login(NewClient(),"ROL_DESCONOCIDO"))Check(noRole.StatusCode==HttpStatusCode.OK,"Un rol desconocido no inicia una sesión con interfaz");
    using var citizenBrowser=NewClient();
    using(var login=await Login(citizenBrowser,"CIUDADANO")){}
    using(var chosen=await Choose(citizenBrowser,"CIUDADANO")){}
    var createReport=await citizenBrowser.GetStringAsync("/Ciudadano/CrearReporte");
    Check(createReport.Contains("obtener-ubicacion")&&createReport.Contains("SolicitudId"),"Ciudadano tiene creación de reportes con ubicación y solicitud única");
    var reportRequest=Guid.NewGuid().ToString();
    using(var created=await citizenBrowser.PostAsync("/Ciudadano/CrearReporte",new FormUrlEncodedContent(new Dictionary<string,string>{
        ["__RequestVerificationToken"]=Csrf(createReport),["Descripcion"]="Incidente de prueba del ciudadano",["TipoIncidenteId"]="1",
        ["Latitud"]="-17.7833",["Longitud"]="-63.1821",["SolicitudId"]=reportRequest,["CiudadanoId"]="999"
    }))){
        if(created.StatusCode!=HttpStatusCode.Redirect){var body=await created.Content.ReadAsStringAsync();Console.WriteLine("Validación de reporte: "+string.Join("; ",Regex.Matches(body,"<li>(.*?)</li>").Select(match=>WebUtility.HtmlDecode(match.Groups[1].Value))));}
        Check(created.StatusCode==HttpStatusCode.Redirect&&created.Headers.Location?.ToString()=="/Ciudadano"&&api.LastIdempotencyKey==reportRequest&&!api.LastBody.TryGetProperty("ciudadanoId",out _),$"El reporte usa idempotencia y deja que la API determine al autor de la sesión (HTTP {(int)created.StatusCode}, ruta {created.Headers.Location}, API {api.LastPath})");
    }
    Check((await citizenBrowser.GetStringAsync("/Ciudadano/Mapa")).Contains("mapa-ciudadano"),"El mapa ciudadano consulta el resumen propio de la API");
    using(var denied=await citizenBrowser.GetAsync("/Operativa"))Check(denied.StatusCode==HttpStatusCode.Redirect&&denied.Headers.Location!.ToString().Contains("AccesoDenegado"),"Ciudadano no puede abrir la bandeja operativa sin ese rol");
    using var operationalBrowser=NewClient();
    using(var login=await Login(operationalBrowser,"UNIDAD_OPERATIVA")){}
    using(var chosen=await Choose(operationalBrowser,"UNIDAD_OPERATIVA")){}
    var inbox=WebUtility.HtmlDecode(await operationalBrowser.GetStringAsync("/Operativa"));
    Check(inbox.Contains("portal-operativa")&&inbox.Contains("Orden #31")&&inbox.Contains("Registrar atención"),"Unidad operativa tiene su bandeja de órdenes asignadas");
    var attendance=await operationalBrowser.GetStringAsync("/Operativa/Atender/31");
    using(var closed=await operationalBrowser.PostAsync("/Operativa/Atender",new FormUrlEncodedContent(new Dictionary<string,string>{
        ["__RequestVerificationToken"]=Csrf(attendance),["Id"]="31",["Resultado"]="Atención completada de forma satisfactoria"
    })))Check(closed.StatusCode==HttpStatusCode.Redirect&&api.LastPath=="/api/operativa/ordenes/31/atender","Registrar atención usa el endpoint limitado a la unidad de la sesión");
    using(var foreign=await operationalBrowser.GetAsync("/Operativa/Atender/999"))Check(foreign.StatusCode==HttpStatusCode.NotFound,"No se puede abrir una orden ausente de la bandeja de la unidad");
    api.RejectProfile=true;
    using var revoked=NewClient();
    using(var login=await Login(revoked,"ADMINISTRADOR"))Check(login.StatusCode==HttpStatusCode.OK,"El login verifica el perfil vigente antes de iniciar la sesión");
    using(var home=await revoked.GetAsync("/Admin/Inicio"))Check(home.StatusCode==HttpStatusCode.Redirect&&home.Headers.Location!.ToString().Contains("/Cuenta/Ingresar"),"Un perfil rechazado por la API no deja una sesión autorizada");
    api.RejectProfile=false;
    api.ExpireSession=true;
    using(var expired=await combinedBrowser.GetAsync("/Panel/Recursos"))Check(expired.Headers.Location?.ToString()=="/Cuenta/Ingresar","Una API que devuelve 401 cierra la sesión y solicita ingresar nuevamente");
    api.ExpireSession=false;
    using(var home=await combinedBrowser.GetAsync("/Admin/Inicio"))Check(home.StatusCode==HttpStatusCode.Redirect&&home.Headers.Location!.ToString().Contains("/Cuenta/Ingresar"),"La sesión vencida tampoco permite volver al otro panel");
    var logoutToken=Csrf(adminHome);
    using(var logout=await adminBrowser.PostAsync("/Cuenta/Salir",new FormUrlEncodedContent(new Dictionary<string,string>{["__RequestVerificationToken"]=logoutToken})))Check(logout.StatusCode==HttpStatusCode.Redirect,"Cerrar sesión termina el acceso institucional");
    using(var home=await adminBrowser.GetAsync("/Admin/Inicio"))Check(home.StatusCode==HttpStatusCode.Redirect&&home.Headers.Location!.ToString().Contains("/Cuenta/Ingresar"),"Después de salir no se puede recuperar el panel por URL directa");
    using(var anonymous=await publicBrowser.GetAsync("/Admin/EliminarUsuario/42"))Check(anonymous.StatusCode==HttpStatusCode.Redirect,"Eliminar exige iniciar sesión");
    using var municipalDelete=new HttpRequestMessage(HttpMethod.Get,"/Admin/EliminarUsuario/42");
    municipalDelete.Headers.Add("X-Test-Role","PERSONAL_ALCALDIA");
    using(var forbidden=await client.SendAsync(municipalDelete))Check(forbidden.StatusCode==HttpStatusCode.Forbidden,"Alcaldía no puede abrir la eliminación de cuentas");
    var deletion=WebUtility.HtmlDecode(await client.GetStringAsync("/Admin/EliminarUsuario/42"));
    Check(deletion.Contains("Eliminar cuenta #42")&&deletion.Contains("ConfirmacionId")&&deletion.Contains("Esta acción es definitiva")&&api.DeleteRequests==0,"Abrir la confirmación muestra la cuenta y no elimina nada");
    var deletionToken=Csrf(deletion);
    List<KeyValuePair<string,string>> DeleteFields(string confirmation="42")=>[
        new("__RequestVerificationToken",deletionToken),new("Id","42"),new("Motivo","Cuenta duplicada de prueba"),new("ConfirmacionId",confirmation)
    ];
    using(var invalid=await client.PostAsync("/Admin/EliminarUsuario",new FormUrlEncodedContent(DeleteFields("43"))))
        Check(WebUtility.HtmlDecode(await invalid.Content.ReadAsStringAsync()).Contains("no coincide")&&api.DeleteRequests==0,"La confirmación debe coincidir con el ID de la cuenta");
    using(var invalid=await client.PostAsync("/Admin/EliminarUsuario",new FormUrlEncodedContent(DeleteFields().Where(pair=>pair.Key!="__RequestVerificationToken"))))
        Check(invalid.StatusCode==HttpStatusCode.BadRequest&&api.DeleteRequests==0,"Eliminar exige protección antifalsificación");
    using(var own=await client.GetAsync("/Admin/EliminarUsuario/1"))Check(own.StatusCode==HttpStatusCode.Redirect&&api.DeleteRequests==0,"La cuenta administrativa propia no se puede eliminar");
    api.LinkedDeletion=true;
    using(var linked=await client.PostAsync("/Admin/EliminarUsuario",new FormUrlEncodedContent(DeleteFields())))
        Check(linked.StatusCode==HttpStatusCode.OK&&WebUtility.HtmlDecode(await linked.Content.ReadAsStringAsync()).Contains("actividad vinculada"),"La web muestra la restricción de historial que devuelve la API");
    api.LinkedDeletion=false;
    using(var removed=await client.PostAsync("/Admin/EliminarUsuario",new FormUrlEncodedContent(DeleteFields())))
        Check(removed.StatusCode==HttpStatusCode.Redirect&&removed.Headers.Location?.ToString()=="/Admin"&&api.LastPath=="/api/admin/usuarios/42"&&api.LastBody.GetProperty("confirmacionId").GetInt32()==42,"Eliminar usa DELETE con motivo y confirmación y vuelve al directorio");
    Check(WebUtility.HtmlDecode(await client.GetStringAsync("/Admin")).Contains("se eliminó definitivamente"),"El directorio confirma la eliminación definitiva");
    Console.WriteLine($"{checks} comprobaciones MVC correctas; API simulada, sin modificar cuentas reales.");
}finally{await app.StopAsync();}

sealed class TestIdentity(IOptionsMonitor<AuthenticationSchemeOptions> options,ILoggerFactory logger,UrlEncoder encoder)
    :AuthenticationHandler<AuthenticationSchemeOptions>(options,logger,encoder)
{
    protected override Task<AuthenticateResult> HandleAuthenticateAsync(){
        var role=Request.Headers["X-Test-Role"].ToString();
        if(string.IsNullOrEmpty(role))return Task.FromResult(AuthenticateResult.NoResult());
        var identity=new ClaimsIdentity([new(ClaimTypes.NameIdentifier,"1"),new(ClaimTypes.Name,"Administrador de prueba"),new(ClaimTypes.Role,role)],Scheme.Name);
        return Task.FromResult(AuthenticateResult.Success(new AuthenticationTicket(new ClaimsPrincipal(identity),Scheme.Name)));
    }
}
sealed class FakeApi:HttpMessageHandler
{
    public bool Duplicate{get;set;}
    public int Requests{get;private set;}
    public JsonElement LastBody{get;private set;}
    public string? LastPath{get;private set;}
    public string[] LoginRoles{get;set;}=["ADMINISTRADOR"];
    public bool RejectProfile{get;set;}
    public bool ExpireSession{get;set;}
    public bool LinkedDeletion{get;set;}
    public int DeleteRequests{get;private set;}
    public string? LastIdempotencyKey{get;private set;}
    private static Orden TestOrder=>new(31,11,"Incidente de prueba","EN_ATENCION","Alumbrado",1,"Cuadrilla",1,"Unidad de prueba","MANTENIMIENTO","Revisar luminaria dañada","ASIGNADA",null,-17.7833,-63.1821,"2026-11-25T12:00:00Z",null,7,"EN_BANDEJA");
    protected override async Task<HttpResponseMessage> SendAsync(HttpRequestMessage request,CancellationToken ct){
        Requests++;
        LastPath=request.RequestUri?.AbsolutePath;
        LastIdempotencyKey=request.Headers.TryGetValues("Idempotency-Key",out var keys)?keys.Single():null;
        if(LastPath=="/api/auth/login")return new(HttpStatusCode.OK){Content=JsonContent.Create(new{token="test-token",idUsuario=1,nombre="Usuario de prueba",roles=LoginRoles})};
        if(request.Headers.Authorization?.Parameter!="test-token")throw new InvalidOperationException("Falta autenticación con la API");
        if(ExpireSession)return new(HttpStatusCode.Unauthorized){Content=JsonContent.Create(new{detalle="Sesión vencida."})};
        if(LastPath=="/api/auth/me")return RejectProfile?new(HttpStatusCode.Forbidden){Content=JsonContent.Create(new{detalle="Perfil no autorizado."})}
            :new(HttpStatusCode.OK){Content=JsonContent.Create(new PerfilAcceso(1,"Usuario de prueba",LoginRoles.ToList(),LoginRoles.Contains("CIUDADANO")?1:0))};
        if(LastPath is "/api/admin/perfil" or "/api/alcaldia/perfil")return new(RejectProfile?HttpStatusCode.Forbidden:HttpStatusCode.OK){Content=JsonContent.Create(new{detalle="El perfil ya no está autorizado."})};
        if(LastPath=="/api/tipos-incidente")return new(HttpStatusCode.OK){Content=JsonContent.Create(new[]{new Tipo(1,"Alumbrado",true)})};
        if(LastPath=="/api/mapa/resumen")return new(HttpStatusCode.OK){Content=JsonContent.Create(new MapaCiudadano([],0,7,DateTimeOffset.UtcNow.AddDays(-7),DateTimeOffset.UtcNow))};
        if(LastPath=="/api/operativa/ordenes")return new(HttpStatusCode.OK){Content=JsonContent.Create(new[]{TestOrder})};
        if(LastPath=="/api/alcaldia/panel")return new(HttpStatusCode.OK){Content=JsonContent.Create(new Resumen([
            new Fila(10,"Reporte pendiente de prueba","PENDIENTE_VALIDACION",DateTimeOffset.UtcNow,0,"Alumbrado",-17.7833,-63.1821),
            new Fila(11,"Reporte validado de prueba","VALIDADO",DateTimeOffset.UtcNow,1,"Alumbrado",-17.7834,-63.1822)
        ],[],"2026-11-20","2026-11-25"))};
        if(request.Method==HttpMethod.Get&&LastPath=="/api/admin/usuarios/42")return new(HttpStatusCode.OK){Content=JsonContent.Create(new UsuarioAdministrado(42,"Cuenta de prueba","prueba@example.com",true,["CIUDADANO"],false))};
        if(request.Method==HttpMethod.Get)return new(HttpStatusCode.OK){Content=JsonContent.Create(Array.Empty<object>())};
        LastBody=await request.Content!.ReadFromJsonAsync<JsonElement>(ct);
        if(LastPath=="/api/reportes")return new(HttpStatusCode.Created){Content=JsonContent.Create(new ReporteCiudadano(11,"Incidente de prueba del ciudadano",DateTimeOffset.UtcNow,"PENDIENTE_VALIDACION",1,1,"Alumbrado",-17.7833,-63.1821))};
        if(LastPath=="/api/operativa/ordenes/31/atender")return new(HttpStatusCode.OK){Content=JsonContent.Create(TestOrder with{EstadoOrden="ATENDIDA"})};
        if(request.Method==HttpMethod.Delete&&LastPath=="/api/admin/usuarios/42"){
            DeleteRequests++;
            return LinkedDeletion?new(HttpStatusCode.Conflict){Content=JsonContent.Create(new{detalle="La cuenta tiene actividad vinculada."})}:new(HttpStatusCode.NoContent);
        }
        if(LastPath=="/api/admin/reportes/10/verificacion")return new(HttpStatusCode.OK){Content=JsonContent.Create(new{estado="VALIDADO"})};
        if(LastPath!="/api/admin/usuarios")throw new InvalidOperationException("Endpoint de creación incorrecto");
        return Duplicate?new(HttpStatusCode.Conflict){Content=JsonContent.Create(new{detalle="El correo ya está registrado."})}
            :new(HttpStatusCode.Created){Content=JsonContent.Create(new{id=42,nombre="Cuenta de prueba",correo="prueba@example.com",activo=true,roles=new[]{"ADMINISTRADOR"},sancionado=false})};
    }
}
