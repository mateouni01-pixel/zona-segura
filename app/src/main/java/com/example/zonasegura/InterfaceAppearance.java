package com.example.zonasegura;

import android.app.Activity;
import android.content.Context;
import android.util.TypedValue;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.core.content.ContextCompat;

/** La selección controla el tema visual; los permisos siguen dependiendo de la API. */
final class InterfaceAppearance {
    static int theme(InterfazUsuario role){
        if(role==InterfazUsuario.ALCALDIA)return R.style.Theme_ZONASEGURA_Alcaldia;
        if(role==InterfazUsuario.ADMINISTRADOR)return R.style.Theme_ZONASEGURA_Administrador;
        return R.style.Theme_ZONASEGURA;
    }
    static void apply(Activity activity){activity.setTheme(theme(InterfazUsuario.buscar(SessionStore.selectedRole(activity))));}
    static Context context(Context host,InterfazUsuario role){return new ContextThemeWrapper(host,theme(role));}
    static int color(Context context,int attribute){
        TypedValue value=new TypedValue();
        if(!context.getTheme().resolveAttribute(attribute,value,true))throw new IllegalArgumentException("Atributo de color no disponible");
        return value.resourceId!=0?ContextCompat.getColor(context,value.resourceId):value.data;
    }
    private InterfaceAppearance(){}
}
