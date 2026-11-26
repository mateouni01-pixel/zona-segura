package com.example.zonasegura;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

/** Consulta los roles vigentes antes de mostrarlos y nuevamente antes de elegir. */
public final class InterfacesActivity extends AppCompatActivity {
    private LinearLayout content;
    private TextView status;
    private boolean busy;
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        ScrollView scroll=new ScrollView(this);content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(24),dp(24),dp(24),dp(24));
        scroll.addView(content);scroll.setFillViewport(true);scroll.setBackgroundResource(R.color.login_background);setContentView(scroll);
        ViewCompat.setOnApplyWindowInsetsListener(scroll,(view,insets)->{var bars=insets.getInsets(WindowInsetsCompat.Type.systemBars());view.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});
        comprobar(null);
    }
    private void comprobar(InterfazUsuario elegida){
        if(busy)return;
        String token=SessionStore.load(this);
        if(token==null){login();return;}
        busy=true;
        if(status==null){content.removeAllViews();texto(content,"Zona Segura",18);texto(content,"Elige tu interfaz",28);status=texto(content,"Comprobando tus permisos…",16);}else status.setText("Comprobando tus permisos…");
        new Thread(()->{
            AuthApi.Profile profile;
            try{profile=AuthApi.profile(getString(R.string.api_base_url),token);}catch(java.io.IOException error){profile=new AuthApi.Profile(0,null,null);}
            boolean offline=profile.status==0||profile.status>=500;
            if(offline)profile=SessionStore.offlineProfile(this);
            AuthApi.Profile result=profile;
            runOnUiThread(()->{
                busy=false;if(isFinishing()||isDestroyed())return;
                if(result.status==401||result.status==403){SessionStore.clear(this);login();return;}
                if(result.status!=200){status.setText("No se pudieron comprobar tus roles. Revisa la conexión.");content.removeViews(3,Math.max(0,content.getChildCount()-3));boton(content,"Reintentar",()->comprobar(null));boton(content,"Cerrar sesión",()->{SessionStore.clear(this);login();});return;}
                if(!offline)SessionStore.remember(this,result);
                if(elegida!=null&&result.hasRole(elegida.rol)){InterfaceNavigation.elegir(this,elegida);return;}
                mostrar(result,offline,elegida!=null?"Tus permisos cambiaron. Elige una interfaz disponible.":null);
            });
        }).start();
    }
    private void mostrar(AuthApi.Profile profile,boolean offline,String error){
        content.removeAllViews();texto(content,"Zona Segura",18);texto(content,"Elige tu interfaz",28);
        status=texto(content,error!=null?error:offline?"Sin conexión: solo está disponible el espacio ciudadano guardado para trabajar sin internet.":"Hola, "+profile.name+". Entra con uno de tus roles disponibles.",16);
        for(var interfaz:InterfazUsuario.disponibles(profile.roles)){
            android.content.Context themed=InterfaceAppearance.context(this,interfaz);
            MaterialCardView card=new MaterialCardView(themed);card.setRadius(dp(16));card.setStrokeWidth(dp(1));card.setStrokeColor(InterfaceAppearance.color(themed,R.attr.civicColorCardBorder));
            LinearLayout box=new LinearLayout(themed);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(18),dp(16),dp(18),dp(16));card.addView(box);
            texto(box,interfaz.nombre,22).setTextColor(InterfaceAppearance.color(themed,androidx.appcompat.R.attr.colorPrimary));texto(box,interfaz.descripcion,16);
            boton(box,"Entrar como "+interfaz.nombre,()->comprobar(interfaz));
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.topMargin=dp(16);content.addView(card,params);
        }
        if(InterfazUsuario.disponibles(profile.roles).isEmpty())texto(content,"Tu cuenta no tiene interfaces disponibles. Solicita una revisión de permisos.",16);
        boton(content,"Actualizar mis roles",()->comprobar(null));boton(content,"Cerrar sesión",()->{SessionStore.clear(this);login();});
    }
    private TextView texto(LinearLayout box,String value,int size){TextView view=new TextView(box.getContext());view.setText(value);view.setTextSize(size);view.setTextColor(InterfaceAppearance.color(box.getContext(),R.attr.civicColorText));view.setPadding(0,dp(8),0,dp(8));box.addView(view);return view;}
    private void boton(LinearLayout box,String title,Runnable action){MaterialButton button=new MaterialButton(box.getContext());button.setText(title);button.setOnClickListener(view->{if(!busy)action.run();});box.addView(button,new LinearLayout.LayoutParams(-1,-2));}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private void login(){startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));finish();}
}
