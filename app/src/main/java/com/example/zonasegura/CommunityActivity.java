package com.example.zonasegura;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.*;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.location.LocationManagerCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.button.MaterialButton;
import org.json.*;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

public final class CommunityActivity extends AppCompatActivity {
    private LinearLayout panel;
    private TextView status;
    private String page;
    private boolean busy;
    private double lat, lng;
    private boolean located;
    private final android.os.CancellationSignal gps = new android.os.CancellationSignal();
    private final List<String[]> exportRows = new ArrayList<>();
    private final androidx.activity.result.ActivityResultLauncher<String[]> permissions =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), ignored -> locate());
    private final androidx.activity.result.ActivityResultLauncher<String> pdf =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("application/pdf"), uri -> export(uri,true));
    private final androidx.activity.result.ActivityResultLauncher<String> excel =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"), uri -> export(uri,false));

    static void addActions(AppCompatActivity host, int anchorId, String role) {
        View anchor = host.findViewById(anchorId);
        LinearLayout parent = (LinearLayout) anchor.getParent();
        if (parent.findViewWithTag("community-menu") != null) return;
        LinearLayout menu = new LinearLayout(host);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setTag("community-menu");
        LinearLayout.LayoutParams menuParams = new LinearLayout.LayoutParams(-1, -2);
        menuParams.topMargin = Math.round(8 * host.getResources().getDisplayMetrics().density);
        parent.addView(menu, parent.indexOfChild(anchor) + 1, menuParams);
        String[] pages;
        switch (role) {
            case "account": pages = new String[]{"Crear cuenta","Recuperar contraseña"}; break;
            case "municipal": pages = new String[]{"Mapa de riesgo","Verificar reportes","Unidades","Recursos","Intervenciones","Órdenes municipales"}; break;
            case "admin": pages = new String[]{"Moderación"}; break;
            default: pages = new String[]{"Mapa de riesgo","Validar reportes"};
        }
        for (String name : pages) {
            int style = role.equals("citizen")
                    ? com.google.android.material.R.attr.materialButtonOutlinedStyle
                    : com.google.android.material.R.attr.materialButtonStyle;
            MaterialButton button = new MaterialButton(host, null, style);
            button.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
            button.setPaddingRelative(
                    Math.round(16 * host.getResources().getDisplayMetrics().density),
                    button.getPaddingTop(), button.getPaddingEnd(), button.getPaddingBottom());
            button.setText(name);
            if (role.equals("citizen")) {
                button.setIconResource(name.equals("Mapa de riesgo") ? R.drawable.nav_mapa : R.drawable.nav_validar);
            }
            button.setOnClickListener(v -> host.startActivity(Arrays.asList("Recursos","Unidades","Intervenciones","Órdenes municipales").contains(name)
                    ? new Intent(host,OperationsActivity.class).putExtra("page",name) : name.equals("Mapa de riesgo")
                    ? new Intent(host, RiskMapActivity.class)
                    : new Intent(host, CommunityActivity.class).putExtra("page",name)));
            menu.addView(button, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        page = getIntent().getStringExtra("page");
        if (page == null) { finish(); return; }
        if (page.equals("Mapa de riesgo")) { startActivity(new Intent(this,RiskMapActivity.class)); finish(); return; }
        ScrollView scroll = new ScrollView(this);
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(22);
        panel.setPadding(pad,pad,pad,pad);
        scroll.addView(panel);
        scroll.setBackgroundResource(R.color.login_background);
        setContentView(scroll);
        if ("Validar reportes".equals(page)) CitizenNavigation.attach(this, R.id.nav_validar);
        ViewCompat.setOnApplyWindowInsetsListener(scroll,(v,i) -> {
            var bars = i.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left,bars.top,bars.right,bars.bottom); return i;
        });
        render();
    }

    private void render() {
        panel.removeAllViews();
        TextView title = label(page);
        title.setTextSize(28);
        title.setTypeface(android.graphics.Typeface.create("sans-serif-medium", 0));
        ViewCompat.setAccessibilityHeading(title, true);
        title.setTextColor(-1);
        title.setBackgroundResource(R.drawable.civic_header);
        title.setPadding(dp(18),dp(20),dp(18),dp(20));
        status = label("");
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        if (!page.equals("Crear cuenta") && !page.equals("Recuperar contraseña")) secondaryButton("Volver", this::finish);
        switch (page) {
            case "Crear cuenta": account(true); backToLogin(); return;
            case "Recuperar contraseña": account(false); backToLogin(); return;
            case "Validar reportes":
                label("Radio de validación · 500 m\nSe necesitan tres confirmaciones de ciudadanos distintos del autor.");
                if (!located) {
                    button("Buscar a 500 metros", () -> {
                        if (hasGps()) locate();
                        else permissions.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
                    });
                    return;
                }
                break;
        }
        secondaryButton("Actualizar", this::render);
        String path;
        switch(page) {
            case "Mapa de riesgo": path="/api/mapa/zonas"; break;
            case "Intervenciones": path="/api/intervenciones"; break;
            case "Verificar reportes": path="/api/alcaldia/reportes-verificacion"; break;
            case "Moderación": path="/api/moderacion/reportes"; break;
            case "Validar reportes": path="/api/reportes/cercanos?lat="+lat+"&lng="+lng+"&radio=500"; break;
            default: finish(); return;
        }
        run(() -> {
            JSONArray rows = new JSONArray(CommunityApi.call(this,false,path,null,null));
            return () -> display(rows);
        });
    }

    private boolean hasGps() { return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED; }
    @android.annotation.SuppressLint("MissingPermission")
    private void locate() {
        if (!hasGps()) { status.setText("Se necesita ubicación precisa para buscar reportes cercanos."); return; }
        LocationManager manager=getSystemService(LocationManager.class);
        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) { status.setText("Activa el GPS."); return; }
        status.setText("Buscando ubicación…");
        LocationManagerCompat.getCurrentLocation(manager,LocationManager.GPS_PROVIDER,gps,
                androidx.core.content.ContextCompat.getMainExecutor(this), result -> {
                    if (isDestroyed() || isFinishing()) return;
                    if(result==null){status.setText("No se obtuvo la ubicación. Intenta nuevamente.");return;}
                    lat=result.getLatitude(); lng=result.getLongitude(); located=true; render();
                });
    }
    @Override protected void onDestroy(){gps.cancel();super.onDestroy();}

    private void account(boolean register) {
        EditText name=register ? field("Nombre",1) : null;
        EditText email=field("Correo electrónico",33);
        EditText password=register ? field("Contraseña",129) : null;
        button(register?"Registrar cuenta":"Solicitar recuperación",()->{
            String address=email.getText().toString().trim();
            if(!android.util.Patterns.EMAIL_ADDRESS.matcher(address).matches()){email.setError("Revisa el correo.");return;}
            JSONObject data=new JSONObject();
            put(data,"correo",address);
            if(register){
                String person=name.getText().toString().trim();
                if(person.isEmpty()){name.setError("Ingresa tu nombre.");return;}
                if(password.length()<8 || password.getText().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72){password.setError("Usa al menos 8 caracteres y como máximo 72 bytes.");return;}
                put(data,"nombre",person);put(data,"password",password.getText().toString());
            }
            run(()->{
                CommunityApi.call(this,true,register?"/api/auth/register":"/api/auth/recuperacion",data,null);
                return ()->new AlertDialog.Builder(this).setMessage(register?"Cuenta creada. Ya puedes iniciar sesión.":"Si existe la cuenta, recibirás las instrucciones por correo.")
                        .setPositiveButton("Aceptar",(d,w)->finish()).show();
            });
        });
    }

    private void display(JSONArray rows) {
        if(page.equals("Mapa de riesgo")) { map(rows); return; }
        exportRows.clear(); exportRows.add(new String[]{"Reporte","Tipo","Estado"});
        if(rows.length()==0) label("No hay elementos disponibles.");
        for(int i=0;i<rows.length();i++) {
            JSONObject row=rows.optJSONObject(i);
            if(row==null || row.optInt("id")<=0) continue;
            int id=row.optInt("id");
            String type=row.optString("tipoIncidenteNombre"), state=row.optString("estado");
            TextView card=label("#"+id+" · "+type+"\n"+row.optString("descripcion")+"\n"+state.replace('_',' '));
            card.setBackgroundResource(R.drawable.civic_card);
            card.setPadding(dp(14),dp(12),dp(14),dp(12));
            String base="/api/reportes/"+id;
            if(page.equals("Validar reportes")){
                label("Confirmaciones: "+row.optInt("confirmaciones")+" de 3 · Votos negativos: "+row.optInt("votosNegativos")
                        +" · A "+row.optLong("distanciaMetros")+" m");
                if(row.optBoolean("yaVoto"))label("Ya registraste tu voto. El reporte sigue pendiente hasta reunir tres confirmaciones o la verificación de Alcaldía.");
                else {
                    JSONObject vote=new JSONObject();put(vote,"latitud",lat);put(vote,"longitud",lng);
                    button("Sí, confirmo este incidente",()->{put(vote,"confirma",true);post(base+"/votos",vote);});
                    secondaryButton("Marcar como falso o spam",()->{put(vote,"confirma",false);post(base+"/votos",vote);});
                }
            } else if(page.equals("Verificar reportes")){
                label("Confirmaciones ciudadanas: "+row.optInt("confirmaciones")+" de 3 · Votos negativos: "+row.optInt("votosNegativos"));
                label("Registra una inspección o comprobación municipal. La decisión queda asociada a tu cuenta y fecha.");
                if(!row.isNull("latitud") && !row.isNull("longitud")){
                    String point=row.optDouble("latitud")+","+row.optDouble("longitud");
                    label("Ubicación del incidente: "+point);
                    secondaryButton("Abrir ubicación",()->{
                        try {startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("geo:"+point+"?q="+point)));}
                        catch(android.content.ActivityNotFoundException error){status.setText("No hay una aplicación de mapas disponible.");}
                    });
                }
                button("Confirmar tras verificar",()->verify(id,true));
                secondaryButton("Rechazar tras verificar",()->verify(id,false));
            } else if(page.equals("Moderación")){
                label("Votos negativos: "+row.optInt("votosNegativos")+" · Índice: "+String.format(java.util.Locale.getDefault(),"%.0f%%",row.optDouble("indiceVeracidad")*100));
                if(row.optBoolean("verificadoInstitucionalmente"))label("Existe una comprobación institucional. Su decisión se conserva.");
                if((state.equals("PENDIENTE_VALIDACION")||state.equals("VALIDADO"))&&!row.optBoolean("verificadoInstitucionalmente"))
                    button("Rechazar tras revisión",()->moderate(id,false));
                if(row.optBoolean("sancionado"))label("La cuenta del reportante tiene el acceso suspendido. El levantamiento se gestiona desde el panel administrativo.");
                else if(row.optBoolean("sancionPrevia"))label("Este reporte ya fue utilizado para una sanción. No se vuelve a sancionar el mismo hecho.");
                else if(state.equals("RECHAZADO"))button("Suspender acceso del reportante",()->moderate(id,true));
            } else {
                exportRows.add(new String[]{String.valueOf(id),type,state});
                if(state.equals("VALIDADO")){
                    JSONArray resources=row.optJSONArray("recursosDisponibles");
                    if(resources!=null && resources.length()>0) button("Asignar recurso",()->choose(id,resources));
                    else label("Sin recursos disponibles");
                }
                if(state.equals("EN_ATENCION")) confirm("Marcar como atendido","/api/intervenciones/"+id+"/atender");
                if(row.has("estadoNotificacion")) label("Aviso a Fuerzas del Orden · "+row.optString("estadoNotificacion"));
            }
        }
        if(page.equals("Intervenciones")){
            secondaryButton("Exportar PDF",()->pdf.launch("intervenciones.pdf"));
            secondaryButton("Exportar Excel",()->excel.launch("intervenciones.xlsx"));
        }
    }

    private void moderate(int id,boolean sanction){
        EditText reason=new EditText(this);
        reason.setHint(sanction?"Describe la conducta y el motivo de la suspensión":"Describe la revisión y el motivo del rechazo");
        reason.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        reason.setMinLines(3);
        reason.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(2000)});
        AlertDialog dialog=new AlertDialog.Builder(this)
                .setTitle(sanction?"Suspender acceso":"Rechazar reporte")
                .setMessage(sanction?"Se suspenderá el acceso de la cuenta. La acción queda auditada y requiere un motivo."
                        :"Revisa el hecho antes de decidir. Los votos negativos no provocan un rechazo automático. La decisión conserva motivo y responsable.")
                .setView(reason).setNegativeButton("Cancelar",null).setPositiveButton("Registrar decisión",null).create();
        dialog.setOnShowListener(ignored->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            if(busy)return;
            String text=reason.getText().toString().trim();
            if(text.length()<10||text.length()>2000){reason.setError("Escribe un motivo de 10 a 2000 caracteres.");return;}
            JSONObject body=new JSONObject();put(body,"motivo",text);
            dialog.dismiss();post("/api/reportes/"+id+(sanction?"/sancion":"/rechazo"),body);
        }));
        dialog.show();
    }

    private void verify(int id,boolean confirms){
        EditText reason=new EditText(this);
        reason.setHint("Describe qué comprobaste y cómo lo verificaste");
        reason.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        reason.setMinLines(3);
        reason.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(2000)});
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(confirms?"Confirmar incidente":"Rechazar incidente")
                .setMessage("La decisión cambiará el estado del reporte. Registra la comprobación realizada.")
                .setView(reason).setNegativeButton("Cancelar",null).setPositiveButton("Guardar verificación",null).create();
        dialog.setOnShowListener(ignored->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String text=reason.getText().toString().trim();
            if(text.length()<10){reason.setError("Describe la comprobación con al menos 10 caracteres.");return;}
            JSONObject body=new JSONObject();put(body,"confirma",confirms);put(body,"motivo",text);
            dialog.dismiss();post("/api/alcaldia/reportes/"+id+"/verificacion",body);
        }));
        dialog.show();
    }

    private void choose(int id, JSONArray resources){
        List<String> names=new ArrayList<>();List<Integer> ids=new ArrayList<>();
        for(int i=0;i<resources.length();i++){
            JSONObject r=resources.optJSONObject(i);
            if(r!=null && r.optInt("id")>0){ids.add(r.optInt("id"));names.add(r.optString("nombre"));}
        }
        new AlertDialog.Builder(this).setTitle("Recurso para la orden").setItems(names.toArray(new String[0]),(d,index)->{
            JSONObject body=new JSONObject();put(body,"reporteId",id);put(body,"recursoId",ids.get(index));
            post("/api/intervenciones",body);
        }).show();
    }

    private void confirm(String action,String path){
        button(action,()->new AlertDialog.Builder(this).setMessage(action+"?").setNegativeButton("Cancelar",null)
                .setPositiveButton("Confirmar",(d,w)->post(path,new JSONObject())).show());
    }
    private void post(String path,JSONObject data){
        run(()->{
            String response=CommunityApi.call(this,false,path,data,null);
            String message;
            if(path.endsWith("/votos") || path.endsWith("/verificacion")){
                JSONObject result=new JSONObject(response);
                message="Registrado · "+result.optString("estado").replace('_',' ')
                        +" · "+result.optInt("confirmaciones")+" de 3 confirmaciones ciudadanas";
            }else message="Acción registrada.";
            return ()->{render();Toast.makeText(this,message,Toast.LENGTH_LONG).show();};
        });
    }

    private void export(Uri uri,boolean asPdf){
        if(uri==null)return;
        List<String[]> snapshot=new ArrayList<>(exportRows);
        run(()->{
            try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){
                if(out==null)throw new java.io.IOException("No se pudo abrir el archivo.");
                if(asPdf) InterventionExport.pdf(snapshot,out);else InterventionExport.excel(snapshot,out);
            }
            return ()->status.setText("Archivo guardado.");
        });
    }

    private void map(JSONArray zones) {
        if(zones.length()==0){label("Aún no hay zonas publicadas.");return;}
        RiskMapView map=new RiskMapView(this,zones);
        panel.addView(map,new LinearLayout.LayoutParams(-1,dp(360)));
        button("Activar o desactivar capa térmica",map::toggle);
        label("Rojo · riesgo alto    Ámbar · medio    Verde · bajo");
    }
    private interface Job { Runnable execute() throws Exception; }
    private void run(Job job){
        if(busy)return;
        busy=true;status.setText("Procesando…");
        new Thread(()->{
            Runnable next;
            try{next=job.execute();}catch(Exception e){
                String message=e instanceof java.io.IOException ? e.getMessage() : "Respuesta no válida.";
                next=()->status.setText("No se pudo completar la operación. "+message);
            }
            Runnable result=next;
            runOnUiThread(()->{if(!isDestroyed()&&!isFinishing()){busy=false;status.setText("");result.run();}});
        }).start();
    }
    private static void put(JSONObject data,String key,Object value){
        try{data.put(key,value);}catch(JSONException e){throw new IllegalArgumentException(e);}
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private TextView label(String value) {
        TextView label = new TextView(this);
        label.setText(value);
        label.setTextSize(16);
        label.setTextColor(getColor(R.color.login_text));
        label.setLineSpacing(dp(3), 1f);
        label.setPadding(0, dp(8), 0, dp(8));
        panel.addView(label, new LinearLayout.LayoutParams(-1, -2));
        return label;
    }
    
    private void secondaryButton(String title, Runnable action) {
        MaterialButton button = new MaterialButton(this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(title);
        button.setOnClickListener(view -> {
            if (!busy) action.run();
        });
        panel.addView(button, new LinearLayout.LayoutParams(-1, -2));
    }
    private void backToLogin() {
        MaterialButton back = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        back.setText("Volver al inicio de sesión");
        back.setOnClickListener(v -> finish());
        panel.addView(back, new LinearLayout.LayoutParams(-1, -2));
    }

    private EditText field(String hint, int type) {
        com.google.android.material.textfield.TextInputLayout box =
                new com.google.android.material.textfield.TextInputLayout(this);
        box.setBoxBackgroundMode(com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE);
        box.setHint(hint);
        box.setBoxCornerRadii(dp(12), dp(12), dp(12), dp(12));
        com.google.android.material.textfield.TextInputEditText input =
                new com.google.android.material.textfield.TextInputEditText(box.getContext());
        input.setSingleLine(true);
        input.setInputType(type);
        box.addView(input, new LinearLayout.LayoutParams(-1, -2));
        if ((type & InputType.TYPE_MASK_VARIATION) == InputType.TYPE_TEXT_VARIATION_PASSWORD) {
            box.setEndIconMode(com.google.android.material.textfield.TextInputLayout.END_ICON_PASSWORD_TOGGLE);
            input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(12);
        panel.addView(box, params);
        return input;
    }
    private void button(String title,Runnable action){MaterialButton b=new MaterialButton(this);b.setText(title);b.setOnClickListener(v->{if(!busy)action.run();});panel.addView(b);}
}
