using System.Net.Security;
using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;

namespace ZonaSegura.Panel.Services;

internal static class ApiTransport
{
    public static HttpClientHandler Create(IWebHostEnvironment environment,IConfiguration configuration)
    {
        var handler=new HttpClientHandler{AllowAutoRedirect=false};
        string? caPath=configuration["Api:DevelopmentCaPath"];
        var address=new Uri(configuration["Api:BaseUrl"]??"http://127.0.0.1:8080/");
        if(environment.IsDevelopment()&&address.IsLoopback&&address.Scheme==Uri.UriSchemeHttps&&!string.IsNullOrWhiteSpace(caPath))
        {
            var root=X509CertificateLoader.LoadCertificateFromFile(Path.GetFullPath(caPath,environment.ContentRootPath));
            handler.ServerCertificateCustomValidationCallback=(request,certificate,_,errors)=>
            {
                if(errors==SslPolicyErrors.None)return true;
                if(certificate==null||request.RequestUri?.IsLoopback!=true||errors!=SslPolicyErrors.RemoteCertificateChainErrors)return false;
                using var chain=new X509Chain();
                chain.ChainPolicy.TrustMode=X509ChainTrustMode.CustomRootTrust;
                chain.ChainPolicy.CustomTrustStore.Add(root);
                chain.ChainPolicy.RevocationMode=X509RevocationMode.NoCheck;
                chain.ChainPolicy.DisableCertificateDownloads=true;
                chain.ChainPolicy.ApplicationPolicy.Add(new Oid("1.3.6.1.5.5.7.3.1"));
                return chain.Build(certificate);
            };
        }
        return handler;
    }
}
