package com.example.zonasegura;
import android.content.Intent;
import android.net.Uri;
import android.os.*;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.button.MaterialButton;
import org.json.*;
import java.util.*;

/** Recursos, coordinación municipal y bandeja de la unidad operativa asignada. */
public final class OperationsActivity extends AppCompatActivity {
    private LinearLayout panel;private TextView status;private String page;private boolean busy;
    private JSONArray units=new JSONArray();private final Handler timer=new Handler(Looper.getMainLooper());
    private final Runnable refresh=new Runnable(){@Override public void run(){if(!busy)load();timer.postDelayed(this,30000);}};
    @Override protected void onCreate(Bundle state){InterfaceAppearance.apply(this);super.onCreate(state);
        page=getIntent().getStringExtra("page");if(page==null)page="Bandeja operativa";
        if(!InterfaceNavigation.exigir(this,page.equals("Bandeja operativa")?InterfazUsuario.OPERATIVA:InterfazUsuario.ALCALDIA))return;
        if(SessionStore.load(this)==null){finish();return;}
        ScrollView scroll=new ScrollView(this);panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(24,24,24,24);scroll.addView(panel);setContentView(scroll);scroll.setBackgroundColor(InterfaceAppearance.color(this,R.attr.civicColorBackground));
        ViewCompat.setOnApplyWindowInsetsListener(scroll,(v,i)->{var b=i.getInsets(WindowInsetsCompat.Type.systemBars()|WindowInsetsCompat.Type.ime());v.setPadding(b.left,b.top,b.right,b.bottom);return i;});load();
    }
    @Override protected void onResume(){super.onResume();timer.postDelayed(refresh,30000);}
    @Override protected void onPause(){timer.removeCallbacks(refresh);super.onPause();}
    private void shell(){panel.removeAllViews();TextView title=label(page);title.setTextSize(25);title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        status=label("");button("Actualizar",this::load);button("Volver",this::finish);
        panel.addView(GuiaUsoActivity.boton(this,SessionStore.selectedRole(this)));
        if(page.equals("Bandeja operativa"))button("Cambiar interfaz",()->InterfaceNavigation.selector(this));
        if(page.equals("Bandeja operativa"))button("Cerrar sesión",()->{SessionStore.clear(this);startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK|Intent.FLAG_ACTIVITY_NEW_TASK));finish();});
    }
    private void load(){if(busy)return;shell();busy=true;status.setText("Consultando el servidor…");
        new Thread(()->{try{
            String path=page.equals("Recursos")?"/api/recursos":page.equals("Unidades")?"/api/unidades":page.equals("Intervenciones")?"/api/intervenciones":page.equals("Órdenes municipales")?"/api/intervenciones/ordenes":"/api/operativa/ordenes";
            JSONArray rows=new JSONArray(ApiClient.call(this,"GET",path,null));
            JSONArray availableUnits=page.equals("Recursos")?new JSONArray(ApiClient.call(this,"GET","/api/unidades",null)):new JSONArray();
            deliver(()->{busy=false;units=availableUnits;status.setText("");display(rows);});
        }catch(Exception e){deliver(()->{busy=false;status.setText("No se pudo cargar. "+e.getMessage());});}}).start();
    }
    private void display(JSONArray rows){
        if(page.equals("Recursos"))button("Registrar recurso",()->resource(null));
        if(page.equals("Unidades"))button("Registrar unidad",this::newUnit);
        if(rows.length()==0)label("No hay elementos disponibles.");
        for(int i=0;i<rows.length();i++){
            JSONObject row=rows.optJSONObject(i);if(row==null)continue;
            if(page.equals("Recursos")){
                label("#"+row.optInt("id")+" · "+row.optString("nombre")+"\n"+row.optString("tipo")+" · Unidad #"+row.optInt("unidadId")+"\n"+(row.optBoolean("activo")?"Activo":"Inactivo")+" · "+(row.optBoolean("disponible")?"Disponible":"No disponible"));
                button("Editar recurso",()->resource(row));
            }else if(page.equals("Unidades")){
                label("#"+row.optInt("id")+" · "+row.optString("nombre"));
                button("Asociar cuenta operativa",()->member(row.optInt("id")));
            }else if(page.equals("Intervenciones")){
                label("Reporte #"+row.optInt("id")+" · "+row.optString("tipoIncidenteNombre")+"\n"+row.optString("descripcion")+"\n"+row.optString("estado"));
                if(row.optString("estado").equals("VALIDADO")){
                    JSONArray resources=row.optJSONArray("recursosDisponibles");
                    if(resources!=null&&resources.length()>0)button("Crear orden y asignar recurso",()->assign(row.optInt("id"),resources));else label("Sin recursos disponibles. Regístralos y asígnales una unidad.");
                }
            }else order(row);
        }
    }
    private LinearLayout form(){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(32,16,32,16);return box;}
    private EditText input(LinearLayout box,String hint,String value){EditText field=new EditText(this);field.setHint(hint);field.setText(value);field.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(2000)});box.addView(field);return field;}
    private Spinner spinner(LinearLayout box,List<String> options){Spinner s=new Spinner(this);s.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,options));box.addView(s);return s;}
    private AlertDialog dialog(String title,LinearLayout box){AlertDialog d=new AlertDialog.Builder(this).setTitle(title).setView(box).setNegativeButton("Cancelar",null).setPositiveButton("Guardar",null).create();d.setOnDismissListener(ignored->timer.postDelayed(refresh,30000));timer.removeCallbacks(refresh);d.show();return d;}
    private void newUnit(){LinearLayout f=form();EditText name=input(f,"Nombre de la unidad","");AlertDialog d=dialog("Registrar unidad",f);
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String text=name.getText().toString().trim();if(text.isEmpty()||text.length()>150){name.setError("Usa de 1 a 150 caracteres.");return;}write("POST","/api/unidades",json("nombre",text),d);});
    }
    private void member(int id){LinearLayout f=form();EditText user=input(f,"ID de usuario con rol UNIDAD_OPERATIVA","");user.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);AlertDialog d=dialog("Asociar cuenta a la unidad",f);
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{int uid=Integer.parseInt(user.getText().toString());if(uid<=0)throw new NumberFormatException();write("POST","/api/unidades/"+id+"/miembros",json("usuarioId",uid),d);}catch(NumberFormatException e){user.setError("Indica un ID válido.");}});
    }
    private void resource(JSONObject old){
        List<String> names=new ArrayList<>();List<Integer> ids=new ArrayList<>();
        for(int i=0;i<units.length();i++){JSONObject u=units.optJSONObject(i);if(u!=null&&u.optBoolean("activo")){names.add(u.optString("nombre"));ids.add(u.optInt("id"));}}
        if(ids.isEmpty()){status.setText("Primero registra una unidad activa en Unidades.");return;}
        LinearLayout f=form();EditText name=input(f,"Nombre del recurso",old==null?"":old.optString("nombre"));Spinner type=spinner(f,Arrays.asList("PATRULLA","CUADRILLA"));Spinner unit=spinner(f,names);
        CheckBox available=new CheckBox(this);available.setText("Disponible para asignar");available.setChecked(old==null||old.optBoolean("disponible"));f.addView(available);
        CheckBox active=new CheckBox(this);active.setText("Activo");active.setChecked(old==null||old.optBoolean("activo"));f.addView(active);
        if(old!=null){type.setSelection(old.optString("tipo").equals("CUADRILLA")?1:0);unit.setSelection(Math.max(0,ids.indexOf(old.optInt("unidadId"))));}
        AlertDialog d=dialog(old==null?"Registrar recurso":"Editar recurso",f);
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String text=name.getText().toString().trim();if(text.isEmpty()||text.length()>150){name.setError("Usa de 1 a 150 caracteres.");return;}
            JSONObject body=json("nombre",text);put(body,"tipo",type.getSelectedItem().toString());put(body,"unidadId",ids.get(unit.getSelectedItemPosition()));put(body,"disponible",available.isChecked());put(body,"activo",active.isChecked());
            write(old==null?"POST":"PUT","/api/recursos"+(old==null?"":"/"+old.optInt("id")),body,d);
        });
    }
    private void assign(int reportId,JSONArray resources){
        LinearLayout f=form();List<String> names=new ArrayList<>();List<JSONObject> choices=new ArrayList<>();
        for(int i=0;i<resources.length();i++){JSONObject r=resources.optJSONObject(i);if(r!=null){names.add(r.optString("nombre")+" · "+r.optString("tipo"));choices.add(r);}}
        Spinner selected=spinner(f,names);Spinner kind=spinner(f,Arrays.asList("PATRULLAJE","MANTENIMIENTO","DERIVACION"));EditText instructions=input(f,"Instrucciones para la unidad (10–2000 caracteres)","");instructions.setMinLines(3);
        String request=UUID.randomUUID().toString();AlertDialog d=dialog("Crear orden de intervención",f);
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String text=instructions.getText().toString().trim();if(text.length()<10){instructions.setError("Describe la intervención con al menos 10 caracteres.");return;}
            JSONObject body=json("reporteId",reportId);put(body,"recursoId",choices.get(selected.getSelectedItemPosition()).optInt("id"));put(body,"tipoOrden",kind.getSelectedItem().toString());put(body,"instrucciones",text);put(body,"solicitudId",request);write("POST","/api/intervenciones",body,d);
        });
    }
    private void order(JSONObject row){
        label("Orden #"+row.optInt("id")+" · Reporte #"+row.optInt("reporteId")+"\n"+row.optString("descripcion")+"\n"+row.optString("recursoNombre")+" · "+row.optString("unidadNombre")+"\n"+row.optString("tipoOrden")+" · "+row.optString("estadoOrden")+"\nInstrucciones: "+row.optString("instrucciones")+"\nAviso: "+row.optString("estadoNotificacion"));
        if(!row.isNull("resultado"))label("Resultado: "+row.optString("resultado"));
        button("Ver ubicación del incidente",()->{String point=row.optDouble("latitud")+","+row.optDouble("longitud");try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("geo:"+point+"?q="+point)));}catch(Exception e){status.setText("No hay aplicación de mapas disponible.");}});
        if(page.equals("Bandeja operativa")){
            if(row.optString("estadoNotificacion").equals("EN_BANDEJA"))button("Marcar aviso como leído",()->write("POST","/api/operativa/avisos/"+row.optInt("avisoId")+"/leer",new JSONObject(),null));
            if(row.optString("estadoOrden").equals("ASIGNADA"))button("Registrar atención y cerrar",()->{
                LinearLayout f=form();EditText result=input(f,"Resultado de la atención (10–2000 caracteres)","");result.setMinLines(3);AlertDialog d=dialog("Cerrar orden y liberar recurso",f);
                d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String text=result.getText().toString().trim();if(text.length()<10){result.setError("Describe la atención con al menos 10 caracteres.");return;}write("POST","/api/operativa/ordenes/"+row.optInt("id")+"/atender",json("resultado",text),d);});
            });
        }
    }
    private void write(String method,String path,JSONObject body,AlertDialog dialog){if(busy)return;busy=true;if(dialog!=null)dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        new Thread(()->{try{ApiClient.call(this,method,path,body);deliver(()->{busy=false;if(dialog!=null)dialog.dismiss();Toast.makeText(this,"Operación guardada",Toast.LENGTH_SHORT).show();load();});}
            catch(Exception e){deliver(()->{busy=false;if(dialog!=null)dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);status.setText(e.getMessage());Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();});}}).start();
    }
    private void deliver(Runnable action){runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())action.run();});}
    private static JSONObject json(String key,Object value){JSONObject j=new JSONObject();put(j,key,value);return j;}
    private static void put(JSONObject j,String key,Object value){try{j.put(key,value);}catch(JSONException e){throw new IllegalArgumentException(e);}}
    private TextView label(String text){TextView v=new TextView(this);v.setText(text);v.setTextSize(16);v.setTextColor(InterfaceAppearance.color(this,R.attr.civicColorText));v.setPadding(8,16,8,12);panel.addView(v);return v;}
    private void button(String text,Runnable action){MaterialButton b=new MaterialButton(this);b.setText(text);b.setOnClickListener(v->{if(!busy)action.run();});panel.addView(b);}
}
