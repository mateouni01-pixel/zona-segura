package com.example.zonasegura;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Ayuda local por rol: no realiza llamadas a la API ni modifica la interfaz seleccionada. */
public final class GuiaUsoActivity extends AppCompatActivity {
    private LinearLayout sections;
    private final List<JSONObject> guide=new ArrayList<>();
    static void abrir(Activity host,String role){
        if(!SessionStore.hasRole(host,role)){InterfaceNavigation.selector(host);return;}
        host.startActivity(new Intent(host,GuiaUsoActivity.class).putExtra("rol",role));
    }
    static MaterialButton boton(Activity host,String role){
        MaterialButton button=new MaterialButton(host,null,com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setTag("guias-uso");button.setText("Guías de uso");button.setOnClickListener(view->abrir(host,role));return button;
    }
    @Override protected void onCreate(Bundle state){
        String role=getIntent().getStringExtra("rol");
        InterfazUsuario interfaz=InterfazUsuario.buscar(role);
        if(interfaz!=null)setTheme(InterfaceAppearance.theme(interfaz));
        super.onCreate(state);
        if(interfaz==null||!SessionStore.hasRole(this,role)){InterfaceNavigation.selector(this);return;}
        ScrollView scroll=new ScrollView(this);LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(24),dp(24),dp(24),dp(24));scroll.addView(content);
        scroll.setBackgroundColor(InterfaceAppearance.color(this,R.attr.civicColorBackground));setContentView(scroll);
        ViewCompat.setOnApplyWindowInsetsListener(scroll,(view,insets)->{var bars=insets.getInsets(WindowInsetsCompat.Type.systemBars()|WindowInsetsCompat.Type.ime());view.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});
        MaterialButton back=new MaterialButton(this,null,com.google.android.material.R.attr.materialButtonOutlinedStyle);back.setText("Volver");back.setOnClickListener(view->finish());content.addView(back);
        TextView title=texto(content,"Guías de uso",28);ViewCompat.setAccessibilityHeading(title,true);
        texto(content,interfaz.nombre,20);texto(content,"Conoce qué hace cada botón y qué necesitas antes de utilizarlo. Esta ayuda también está disponible sin conexión.",16);
        EditText search=new EditText(this);search.setHint("Buscar en la guía");search.setSingleLine(true);search.setTextSize(16);search.setTextColor(InterfaceAppearance.color(this,R.attr.civicColorText));content.addView(search);
        MaterialButton clear=new MaterialButton(this,null,com.google.android.material.R.attr.materialButtonOutlinedStyle);clear.setText("Limpiar búsqueda");clear.setOnClickListener(view->search.setText(""));content.addView(clear);
        sections=new LinearLayout(this);sections.setOrientation(LinearLayout.VERTICAL);content.addView(sections);
        try(var input=getAssets().open("guias-uso.json");var output=new ByteArrayOutputStream()){
            byte[] buffer=new byte[4096];int read;while((read=input.read(buffer))!=-1)output.write(buffer,0,read);
            JSONObject document=new JSONObject(output.toString("UTF-8"));JSONArray roles=document.getJSONArray("roles");
            for(int i=0;i<roles.length();i++)if(role.equals(roles.getJSONObject(i).getString("rol")))append(roles.getJSONObject(i).getJSONArray("android"));
            append(document.getJSONObject("comunes").getJSONArray("android"));
            render("");
        }catch(Exception error){texto(sections,"No se pudo abrir la guía. Reinstala la versión actualizada de la aplicación.",16);}
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence text,int start,int count,int after){}public void onTextChanged(CharSequence text,int start,int before,int count){}public void afterTextChanged(Editable text){render(text.toString());}});
    }
    private void append(JSONArray entries)throws org.json.JSONException{for(int i=0;i<entries.length();i++)guide.add(entries.getJSONObject(i));}
    private static String fold(String value){return Normalizer.normalize(value,Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT);}
    private void render(String search){
        sections.removeAllViews();String query=fold(search.trim());int matches=0;
        for(JSONObject section:guide){
            JSONArray buttons=section.optJSONArray("botones");if(buttons==null)continue;
            List<JSONObject> selected=new ArrayList<>();
            for(int i=0;i<buttons.length();i++){
                JSONObject item=buttons.optJSONObject(i);if(item==null)continue;
                String full=section.optString("titulo")+" "+item.optString("boton")+" "+item.optString("funcion")+" "+item.optString("requisito")+" "+item.optString("resultado");
                if(query.isEmpty()||fold(full).contains(query))selected.add(item);
            }
            if(selected.isEmpty())continue;
            MaterialCardView card=new MaterialCardView(this);card.setRadius(dp(14));card.setStrokeWidth(dp(1));card.setStrokeColor(InterfaceAppearance.color(this,R.attr.civicColorCardBorder));
            LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(14),dp(12),dp(14),dp(12));card.addView(box);
            MaterialButton heading=new MaterialButton(this,null,com.google.android.material.R.attr.materialButtonOutlinedStyle);heading.setText(section.optString("titulo")+" ("+selected.size()+")");ViewCompat.setAccessibilityHeading(heading,true);box.addView(heading);
            LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);box.addView(body);body.setVisibility(!query.isEmpty()||matches==0?View.VISIBLE:View.GONE);
            heading.setOnClickListener(view->{body.setVisibility(body.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE);ViewCompat.setStateDescription(heading,body.getVisibility()==View.VISIBLE?"Sección abierta":"Sección cerrada");});
            for(JSONObject item:selected){
                TextView name=texto(body,item.optString("boton"),18);name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);ViewCompat.setAccessibilityHeading(name,true);
                texto(body,item.optString("funcion"),16);
                if(!item.optString("requisito").isEmpty())texto(body,"Antes de usarlo: "+item.optString("requisito"),16);
                if(!item.optString("resultado").isEmpty())texto(body,"Qué ocurre: "+item.optString("resultado"),16);
            }
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.topMargin=dp(14);sections.addView(card,params);matches+=selected.size();
        }
        if(matches==0)texto(sections,"No hay explicaciones que coincidan. Prueba otra palabra o limpia la búsqueda.",16);
    }
    private TextView texto(LinearLayout parent,String value,int size){TextView text=new TextView(this);text.setText(value);text.setTextSize(size);text.setTextColor(InterfaceAppearance.color(this,R.attr.civicColorText));text.setPadding(0,dp(8),0,dp(8));parent.addView(text);return text;}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
