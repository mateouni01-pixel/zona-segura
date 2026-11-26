package com.example.zonasegura;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.LinearLayout;
import com.google.android.material.button.MaterialButton;
import java.util.EnumMap;
import java.util.Map;

/** Strategy: cada interfaz resuelve su pantalla inicial sin enumerar combinaciones. */
final class InterfaceNavigation {
    private interface Estrategia { Intent abrir(Activity host); }
    private static final Map<InterfazUsuario,Estrategia> ESTRATEGIAS=new EnumMap<>(InterfazUsuario.class);
    static {
        ESTRATEGIAS.put(InterfazUsuario.CIUDADANO,host->new Intent(host,HomeActivity.class));
        ESTRATEGIAS.put(InterfazUsuario.ALCALDIA,host->new Intent(host,MunicipalActivity.class));
        ESTRATEGIAS.put(InterfazUsuario.ADMINISTRADOR,host->new Intent(host,AdminActivity.class));
        ESTRATEGIAS.put(InterfazUsuario.OPERATIVA,host->new Intent(host,OperationsActivity.class).putExtra("page","Bandeja operativa"));
    }
    static void elegir(Activity host,InterfazUsuario interfaz){
        if(!SessionStore.selectRole(host,interfaz.rol)){selector(host);return;}
        host.startActivity(ESTRATEGIAS.get(interfaz).abrir(host).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        host.finish();
    }
    static void selector(Activity host){
        host.startActivity(new Intent(host,InterfacesActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));host.finish();
    }
    static boolean exigir(Activity host,InterfazUsuario interfaz){
        if(SessionStore.load(host)==null||!interfaz.rol.equals(SessionStore.selectedRole(host))){selector(host);return false;}
        return true;
    }
    static void agregarSelector(Activity host,int anchorId){
        View anchor=host.findViewById(anchorId);
        LinearLayout parent=(LinearLayout)anchor.getParent();
        if(parent.findViewWithTag("interface-switch")!=null)return;
        MaterialButton button=new MaterialButton(host,null,com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setTag("interface-switch");button.setText("Cambiar interfaz");button.setOnClickListener(view->selector(host));
        parent.addView(button,parent.indexOfChild(anchor)+1,new LinearLayout.LayoutParams(-1,-2));
        parent.addView(GuiaUsoActivity.boton(host,SessionStore.selectedRole(host)),parent.indexOfChild(button)+1,new LinearLayout.LayoutParams(-1,-2));
    }
    private InterfaceNavigation(){}
}
