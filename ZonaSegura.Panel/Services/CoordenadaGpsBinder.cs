using System.Globalization;
using Microsoft.AspNetCore.Mvc.ModelBinding;
namespace ZonaSegura.Panel.Services;

// Geolocation entrega decimales con punto, independientemente del idioma de Windows.
public sealed class CoordenadaGpsBinder:IModelBinder
{
    public Task BindModelAsync(ModelBindingContext context)
    {
        var value=context.ValueProvider.GetValue(context.ModelName);
        if(value==ValueProviderResult.None)return Task.CompletedTask;
        context.ModelState.SetModelValue(context.ModelName,value);
        if(string.IsNullOrWhiteSpace(value.FirstValue))context.Result=ModelBindingResult.Success(null);
        else if(double.TryParse(value.FirstValue,NumberStyles.AllowLeadingSign|NumberStyles.AllowDecimalPoint,CultureInfo.InvariantCulture,out var coordinate)&&double.IsFinite(coordinate))
            context.Result=ModelBindingResult.Success(coordinate);
        else context.ModelState.TryAddModelError(context.ModelName,"La coordenada GPS no es válida. Obtén nuevamente tu ubicación.");
        return Task.CompletedTask;
    }
}
