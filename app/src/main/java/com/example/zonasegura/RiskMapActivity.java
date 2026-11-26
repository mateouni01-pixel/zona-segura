package com.example.zonasegura;

import android.annotation.SuppressLint;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.checkbox.MaterialCheckBox;
import org.json.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class RiskMapActivity extends AppCompatActivity {
    private static final String ASSET_HOST="appassets.androidplatform.net";
    private static final Set<String> ASSETS=new HashSet<>(Arrays.asList("mapa.html","mapa.js","mapa.css","leaflet.js","leaflet.css"));
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final AtomicInteger generation=new AtomicInteger();
    private final List<Integer> typeIds=new ArrayList<>();
    private WebView web;
    private TextView status, tilesNotice, locationStatus;
    private LocationManager locationManager;
    private boolean mapReady, resumed, trackingLocation, centerOnLocation=true;
    private final Runnable locationTimeout=()->{
        if(trackingLocation)locationStatus.setText(R.string.map_location_waiting);
    };
    private final Runnable locationExpired=()->{
        clearLocation();locationStatus.setText(R.string.map_location_waiting);
    };
    private final ActivityResultLauncher<String[]> locationPermission=registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), result->{
                if(hasLocationPermission())startLocation();
                else locationStatus.setText(R.string.map_location_denied);
            });
    private final LocationListener locationListener=new LocationListener(){
        @Override public void onLocationChanged(Location location){showLocation(location);}
        @Override public void onProviderEnabled(String provider){
            stopLocation();startLocation();
        }
        @Override public void onProviderDisabled(String provider){
            stopLocation();clearLocation();startLocation();
        }
        @Override public void onStatusChanged(String provider,int state,Bundle extras){}
    };
    private Spinner types;
    private int days=7, selectedType, restoredType;
    private boolean bindingTypes=true, catalogLoaded;
    private JSONObject bounds;
    private double centerLat=-17.7833, centerLng=-63.1821;
    private int zoom=13;
    private final Runnable autoRefresh=new Runnable(){
        @Override public void run(){
            if(isDestroyed()||isFinishing())return;
            reload();ui.postDelayed(this,30000);
        }
    };

    @SuppressLint({"SetJavaScriptEnabled", "ClickableViewAccessibility"})
    @Override protected void onCreate(Bundle state){
        InterfaceAppearance.apply(this);
        super.onCreate(state);
        if(SessionStore.load(this)==null){startActivity(new Intent(this,MainActivity.class));finish();return;}
        if(state!=null){days=state.getInt("days",7);selectedType=state.getInt("type");centerLat=state.getDouble("lat",centerLat);centerLng=state.getDouble("lng",centerLng);zoom=state.getInt("zoom",13);}
        restoredType=selectedType;
        setContentView(R.layout.activity_risk_map);
        if ("CIUDADANO".equals(SessionStore.selectedRole(this))) CitizenNavigation.attach(this, R.id.nav_mapa);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.riskMapRoot),(v,insets)->{
            var bars=insets.getInsets(WindowInsetsCompat.Type.systemBars());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;
        });
        status=findViewById(R.id.riskMapStatus);tilesNotice=findViewById(R.id.riskMapTilesNotice);
        locationStatus=findViewById(R.id.riskMapLocationStatus);
        locationManager=getSystemService(LocationManager.class);
        findViewById(R.id.riskMapLocation).setOnClickListener(v->{
            centerOnLocation=true;
            if(hasLocationPermission()){
                stopLocation();clearLocation();startLocation();
            }else locationPermission.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION});
        });
        findViewById(R.id.riskMapBack).setOnClickListener(v->finish());
        findViewById(R.id.riskMapCity).setOnClickListener(v->web.evaluateJavascript("window.centerSantaCruz && window.centerSantaCruz()",null));
        findViewById(R.id.riskMapRefresh).setOnClickListener(v->{if(!catalogLoaded)loadTypes();web.evaluateJavascript("window.refreshRisk && window.refreshRisk()",null);});
        Spinner periods=findViewById(R.id.riskMapPeriod);
        ArrayAdapter<String> periodAdapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,new String[]{"Últimos 7 días","Últimos 30 días","Últimos 90 días"});
        periodAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);periods.setAdapter(periodAdapter);
        periods.setSelection(days==30 ? 1 : days==90 ? 2 : 0);
        periods.setOnItemSelectedListener(new Selection(){@Override public void onItemSelected(AdapterView<?> p,View v,int pos,long id){days=new int[]{7,30,90}[pos];reload();}});
        types=findViewById(R.id.riskMapType);typeIds.add(0);
        ArrayAdapter<String> initial=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,new String[]{getString(R.string.map_all_types)});types.setAdapter(initial);
        types.setOnItemSelectedListener(new Selection(){@Override public void onItemSelected(AdapterView<?> p,View v,int pos,long id){if(!bindingTypes && pos<typeIds.size()){selectedType=typeIds.get(pos);reload();}}});
        web=findViewById(R.id.riskMapWeb);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);
        web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT);
        web.getSettings().setUserAgentString(web.getSettings().getUserAgentString()+" ZonaSegura/1.0 (+https://github.com/mateouni01-pixel/zona-segura)");
        web.setWebViewClient(new LocalMapClient());web.addJavascriptInterface(new MapBridge(),"ZonaMap");
        web.setOnTouchListener((v,event)->{v.getParent().requestDisallowInterceptTouchEvent(event.getActionMasked()!=MotionEvent.ACTION_UP && event.getActionMasked()!=MotionEvent.ACTION_CANCEL);return false;});
        MaterialCheckBox heat=findViewById(R.id.riskMapHeat);
        heat.setOnCheckedChangeListener((button,checked)->web.evaluateJavascript("window.showRisk && window.showRisk("+checked+")",null));
        web.loadUrl("https://"+ASSET_HOST+"/assets/mapa/mapa.html#"+centerLat+","+centerLng+","+zoom);
        loadTypes();
    }

    private boolean hasLocationPermission(){
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED;
    }

    @SuppressLint("MissingPermission")
    private void startLocation(){
        if(!mapReady || !resumed || trackingLocation)return;
        if(!hasLocationPermission()){
            clearLocation();locationStatus.setText(R.string.map_location_hint);return;
        }
        locationStatus.setText(R.string.map_location_searching);
        boolean precise=checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;
        try{
            for(String provider:new String[]{LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER}){
                if(provider.equals(LocationManager.GPS_PROVIDER) && !precise)continue;
                if(locationManager.getAllProviders().contains(provider) && locationManager.isProviderEnabled(provider)){
                    locationManager.requestLocationUpdates(provider,5000,0,locationListener,Looper.getMainLooper());
                    trackingLocation=true;
                }
            }
            if(trackingLocation)ui.postDelayed(locationTimeout,20000);
            else locationStatus.setText(precise?R.string.map_location_disabled:R.string.map_location_approximate_unavailable);
        }catch(SecurityException exception){
            stopLocation();clearLocation();locationStatus.setText(R.string.map_location_denied);
        }
    }

    private void showLocation(Location location){
        if(!resumed || !mapReady || !trackingLocation || !hasLocationPermission())return;
        long age=SystemClock.elapsedRealtimeNanos()-location.getElapsedRealtimeNanos();
        if(age<0 || age>TimeUnit.MINUTES.toNanos(2) || !location.hasAccuracy())return;
        double lat=location.getLatitude(),lng=location.getLongitude(),accuracy=location.getAccuracy();
        if(!Double.isFinite(lat) || !Double.isFinite(lng) || !Double.isFinite(accuracy) || accuracy<0)return;
        ui.removeCallbacks(locationTimeout);
        ui.removeCallbacks(locationExpired);
        ui.postDelayed(locationExpired,TimeUnit.MINUTES.toMillis(2)-TimeUnit.NANOSECONDS.toMillis(age));
        web.evaluateJavascript("window.showCitizenLocation && window.showCitizenLocation("+lat+","+lng+","+accuracy+","+centerOnLocation+")",null);
        centerOnLocation=false;
        locationStatus.setText(getString(R.string.map_location_found,Math.max(1,Math.round(accuracy))));
    }

    private void clearLocation(){
        if(web!=null && mapReady)web.evaluateJavascript("window.clearCitizenLocation && window.clearCitizenLocation()",null);
    }

    private void stopLocation(){
        ui.removeCallbacks(locationTimeout);
        ui.removeCallbacks(locationExpired);
        if(locationManager!=null)locationManager.removeUpdates(locationListener);
        trackingLocation=false;
    }

    private void loadTypes(){
        worker.execute(()->{
            try{
                JSONArray data=new JSONArray(CommunityApi.call(this,false,"/api/tipos-incidente",null,null));
                ui.post(()->{
                    if(isDestroyed()||isFinishing())return;
                    bindingTypes=true;typeIds.clear();typeIds.add(0);
                    List<String> names=new ArrayList<>();names.add(getString(R.string.map_all_types));
                    for(int i=0;i<data.length();i++){JSONObject type=data.optJSONObject(i);if(type!=null && type.optInt("id")>0){typeIds.add(type.optInt("id"));names.add(type.optString("nombre"));}}
                    ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,names);adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);types.setAdapter(adapter);
                    selectedType=typeIds.contains(restoredType)?restoredType:0;types.setSelection(typeIds.indexOf(selectedType));
                    bindingTypes=false;catalogLoaded=true;reload();
                });
            }catch(Exception error){ui.post(()->{if(!isDestroyed())Toast.makeText(this,R.string.map_types_error,Toast.LENGTH_LONG).show();});}
        });
    }

    private void reload(){
        if(bounds==null || isDestroyed())return;
        int requestId=generation.incrementAndGet();
        String path="/api/mapa/resumen?sur="+bounds.optDouble("sur")+"&oeste="+bounds.optDouble("oeste")+"&norte="+bounds.optDouble("norte")+"&este="+bounds.optDouble("este")+"&dias="+days+(selectedType>0?"&tipoIncidenteId="+selectedType:"");
        status.setText(R.string.map_loading);
        worker.execute(()->{
            if(requestId!=generation.get())return;
            try{
                JSONObject summary=new JSONObject(CommunityApi.call(this,false,path,null,null));
                JSONArray zones=summary.getJSONArray("zonas");int total=summary.getInt("totalReportes"),period=summary.getInt("dias");
                String json=JSONObject.quote(summary.toString());
                ui.post(()->{
                    if(isDestroyed()||isFinishing()||requestId!=generation.get())return;
                    web.evaluateJavascript("window.renderRisk && window.renderRisk(JSON.parse("+json+"))",null);
                    status.setText(total==0?getString(R.string.map_empty):getString(R.string.map_summary,total,zones.length(),period));
                });
            }catch(Exception error){ui.post(()->{if(!isDestroyed() && requestId==generation.get()){
                web.evaluateJavascript("window.renderRisk && window.renderRisk({zonas:[]})",null);status.setText(R.string.map_error);
            }});}
        });
    }

    private final class MapBridge {
        @JavascriptInterface public void viewport(String json){
            if(json==null || json.length()>2048)return;
            try{
                JSONObject area=new JSONObject(json);
                for(String key:new String[]{"sur","oeste","norte","este","lat","lng"})if(!Double.isFinite(area.getDouble(key)))return;
                ui.post(()->{if(isDestroyed()||isFinishing())return;bounds=area;centerLat=area.optDouble("lat");centerLng=area.optDouble("lng");zoom=area.optInt("zoom",13);reload();});
            }catch(JSONException ignored){}
        }
        @JavascriptInterface public void tilesUnavailable(){ui.post(()->{if(!isDestroyed())tilesNotice.setVisibility(View.VISIBLE);});}
    }

    private final class LocalMapClient extends WebViewClient {
        @Override public void onPageFinished(WebView view,String url){
            if(ASSET_HOST.equals(Uri.parse(url).getHost())){
                mapReady=true;
                MaterialCheckBox heat=findViewById(R.id.riskMapHeat);
                view.evaluateJavascript("window.showRisk && window.showRisk("+heat.isChecked()+")",null);
                worker.execute(()->{
                    try(java.util.Scanner reader=new java.util.Scanner(getAssets().open("distritos-urbanos.json"),"UTF-8")){
                        String data=reader.useDelimiter("\\A").next();
                        String json=JSONObject.quote(data);
                        ui.post(()->{if(!isDestroyed()&&!isFinishing())view.evaluateJavascript("window.showDistricts && window.showDistricts(JSON.parse("+json+"))",null);});
                    }catch(IOException ignored){}
                });
                startLocation();
            }
        }
        @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
            Uri uri=request.getUrl();String path=uri.getPath();
            if("https".equals(uri.getScheme()) && ASSET_HOST.equals(uri.getHost()) && path!=null && path.startsWith("/assets/mapa/")){
                String file=path.substring("/assets/mapa/".length());
                if(ASSETS.contains(file))try{
                    String mime=file.endsWith(".html")?"text/html":file.endsWith(".css")?"text/css":"application/javascript";
                    return new WebResourceResponse(mime,"UTF-8",getAssets().open("mapa/"+file));
                }catch(IOException ignored){}
            }
            if("https".equals(uri.getScheme()) && "tile.openstreetmap.org".equals(uri.getHost()) && !request.isForMainFrame())return null;
            return new WebResourceResponse("text/plain","UTF-8",403,"Forbidden",Collections.emptyMap(),new ByteArrayInputStream(new byte[0]));
        }
        @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
            if(ASSET_HOST.equals(request.getUrl().getHost()))return false;
            if(request.hasGesture() && "https".equals(request.getUrl().getScheme())){
                try{startActivity(new Intent(Intent.ACTION_VIEW,request.getUrl()));}catch(android.content.ActivityNotFoundException ignored){}
            }
            return true;
        }
    }
    private abstract static class Selection implements AdapterView.OnItemSelectedListener {
        @Override public void onNothingSelected(AdapterView<?> parent){}
    }
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putInt("days",days);state.putInt("type",selectedType);state.putDouble("lat",centerLat);state.putDouble("lng",centerLng);state.putInt("zoom",zoom);}
    @Override protected void onResume(){super.onResume();resumed=true;ui.removeCallbacks(autoRefresh);ui.postDelayed(autoRefresh,30000);startLocation();}
    @Override protected void onPause(){resumed=false;stopLocation();clearLocation();ui.removeCallbacks(autoRefresh);super.onPause();}
    @Override protected void onDestroy(){stopLocation();generation.incrementAndGet();worker.shutdownNow();ui.removeCallbacksAndMessages(null);if(web!=null){web.removeJavascriptInterface("ZonaMap");web.destroy();}super.onDestroy();}
}
