package com.example.zonasegura;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.*;
import java.util.*;

/** Cola duradera por servidor y cuenta. Nunca almacena credenciales. */
final class PendingReports extends SQLiteOpenHelper {
    private static volatile PendingReports instance;
    static PendingReports get(Context context){
        if(instance==null)synchronized(PendingReports.class){if(instance==null)instance=new PendingReports(context.getApplicationContext());}
        return instance;
    }
    private PendingReports(Context context){super(context,"pending_reports.db",null,1);setWriteAheadLoggingEnabled(true);}
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE pending(id TEXT PRIMARY KEY, backend TEXT NOT NULL, owner INTEGER NOT NULL, payload TEXT NOT NULL, created INTEGER NOT NULL, state TEXT NOT NULL, message TEXT NOT NULL DEFAULT '', attempts INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX pending_owner ON pending(backend,owner,state)");
        db.execSQL("CREATE TABLE cache(backend TEXT NOT NULL,owner INTEGER NOT NULL,kind TEXT NOT NULL,data TEXT NOT NULL,PRIMARY KEY(backend,owner,kind))");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int version){throw new IllegalStateException("Falta migración de la cola local.");}
    String enqueue(String backend,int owner,JSONObject payload){
        String id=UUID.randomUUID().toString();ContentValues v=new ContentValues();
        v.put("id",id);v.put("backend",backend);v.put("owner",owner);v.put("payload",payload.toString());v.put("created",System.currentTimeMillis());v.put("state","PENDIENTE");
        getWritableDatabase().insertOrThrow("pending",null,v);return id;
    }
    List<Item> list(String backend,int owner){
        List<Item> list=new ArrayList<>();
        try(Cursor c=getReadableDatabase().query("pending",null,"backend=? AND owner=? AND state<>'SINCRONIZADO'",new String[]{backend,String.valueOf(owner)},null,null,"created ASC")){
            while(c.moveToNext())list.add(item(c));
        }return list;
    }
    Item find(String id){try(Cursor c=getReadableDatabase().query("pending",null,"id=?",new String[]{id},null,null,null)){return c.moveToFirst()?item(c):null;}}
    private Item item(Cursor c){return new Item(c.getString(c.getColumnIndexOrThrow("id")),c.getString(c.getColumnIndexOrThrow("backend")),c.getInt(c.getColumnIndexOrThrow("owner")),c.getString(c.getColumnIndexOrThrow("payload")),c.getString(c.getColumnIndexOrThrow("state")),c.getString(c.getColumnIndexOrThrow("message")),c.getLong(c.getColumnIndexOrThrow("created")));}
    void state(String id,String state,String message){ContentValues v=new ContentValues();v.put("state",state);v.put("message",message);getWritableDatabase().update("pending",v,"id=?",new String[]{id});}
    void attempt(String id){getWritableDatabase().execSQL("UPDATE pending SET attempts=attempts+1 WHERE id=?",new Object[]{id});}
    void cache(String backend,int owner,String kind,String json){ContentValues v=new ContentValues();v.put("backend",backend);v.put("owner",owner);v.put("kind",kind);v.put("data",json);getWritableDatabase().insertWithOnConflict("cache",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
    String cached(String backend,int owner,String kind){try(Cursor c=getReadableDatabase().query("cache",new String[]{"data"},"backend=? AND owner=? AND kind=?",new String[]{backend,String.valueOf(owner),kind},null,null,null)){return c.moveToFirst()?c.getString(0):"[]";}}
    void complete(Item item,JSONObject report)throws JSONException{
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{
            JSONArray history=new JSONArray(cached(item.backend,item.owner,"history"));JSONArray next=new JSONArray();
            for(int i=0;i<history.length();i++)if(history.getJSONObject(i).optInt("id")!=report.getInt("id"))next.put(history.getJSONObject(i));
            next.put(report);cache(item.backend,item.owner,"history",next.toString());state(item.id,"SINCRONIZADO","");db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    static final class Item{
        final String id,backend,payload,state,message;final int owner;final long created;
        Item(String id,String backend,int owner,String payload,String state,String message,long created){this.id=id;this.backend=backend;this.owner=owner;this.payload=payload;this.state=state;this.message=message;this.created=created;}
    }
}
