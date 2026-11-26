using Microsoft.AspNetCore.Authentication.Cookies;
using ZonaSegura.Panel.Services;
var builder=WebApplication.CreateBuilder(args);
builder.Services.AddControllersWithViews(options=>options.Filters.Add(new Microsoft.AspNetCore.Mvc.AutoValidateAntiforgeryTokenAttribute()));
builder.Services.AddDistributedMemoryCache();
builder.Services.AddSession(options=>{options.Cookie.HttpOnly=true;options.Cookie.IsEssential=true;options.Cookie.SameSite=SameSiteMode.Lax;options.Cookie.SecurePolicy=builder.Environment.IsDevelopment()?CookieSecurePolicy.SameAsRequest:CookieSecurePolicy.Always;options.IdleTimeout=TimeSpan.FromMinutes(55);});
builder.Services.AddAuthentication(CookieAuthenticationDefaults.AuthenticationScheme).AddCookie(options=>{
    options.LoginPath="/Cuenta/Ingresar";options.AccessDeniedPath="/Cuenta/AccesoDenegado";options.ExpireTimeSpan=TimeSpan.FromMinutes(55);options.SlidingExpiration=false;
    options.Cookie.HttpOnly=true;options.Cookie.SameSite=SameSiteMode.Lax;
    options.Cookie.SecurePolicy=builder.Environment.IsDevelopment()?CookieSecurePolicy.SameAsRequest:CookieSecurePolicy.Always;
});
builder.Services.AddAuthorization();builder.Services.AddHttpContextAccessor();builder.Services.AddInterfacesUsuario();
builder.Services.AddSingleton<GuiasUso>();
var apiAddress=new Uri(builder.Configuration["Api:BaseUrl"]??"http://127.0.0.1:8080/");
if(apiAddress.Scheme!=Uri.UriSchemeHttps&&!(builder.Environment.IsDevelopment()&&apiAddress.Scheme==Uri.UriSchemeHttp&&apiAddress.IsLoopback))
    throw new InvalidOperationException("La API debe utilizar HTTPS. HTTP solo se admite en desarrollo sobre localhost.");
builder.Services.AddHttpClient<ZonaApi>(client=>{
    client.BaseAddress=apiAddress;client.Timeout=TimeSpan.FromSeconds(20);
}).ConfigurePrimaryHttpMessageHandler(()=>ApiTransport.Create(builder.Environment,builder.Configuration));
var app=builder.Build();
if(!app.Environment.IsDevelopment()){app.UseExceptionHandler("/Panel/Error");app.UseHsts();app.UseHttpsRedirection();}
app.UseStaticFiles();app.UseRouting();app.UseSession();app.UseAuthentication();app.UseAuthorization();
app.Use(async(context,next)=>{context.Response.Headers.CacheControl="no-store";context.Response.Headers["X-Content-Type-Options"]="nosniff";context.Response.Headers["X-Frame-Options"]="DENY";await next();});
app.MapControllerRoute("default","{controller=Cuenta}/{action=Index}/{id?}");app.Run();
