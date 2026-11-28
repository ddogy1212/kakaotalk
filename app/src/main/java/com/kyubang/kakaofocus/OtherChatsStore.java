package com.kyubang.kakaofocus;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Stats for Kakao notifications outside the explicitly configured 4 rooms.
 * Only hashed conversation identifiers and the notification display title are kept.
 * No message text. Never treats a sender-only heading as confirmed chat room identity.
 */
final class OtherChatsStore {
    private static final String PREF = "kakao_all_chat_insights";
    private static final int MAX_CHATS = 35;
    static final class Chat {
        final String token, label;
        final int[] days = new int[7];
        final long lastAt;
        Chat(String id, String title, long at) {
            token=id; label=title; lastAt=at;
        }
        int total() {int s=0;for(int n:days)s+=n;return s;}
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREF,Context.MODE_PRIVATE);
    }

    private static String key(int daysAgo) {
        Calendar c=Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR,-daysAgo);
        return new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).format(c.getTime());
    }
    static synchronized void record(Context c,String token,String label,String fingerprint) {
        if(token==null||token.isEmpty()||fingerprint==null||fingerprint.isEmpty())return;
        SharedPreferences p=prefs(c);
        JSONArray array;
        try {array=new JSONArray(p.getString("chats","[]"));}
        catch(JSONException ignored){array=new JSONArray();}
        JSONObject current=null;
        for(int i=0;i<array.length();i++){
            JSONObject o=array.optJSONObject(i);
            if(o!=null&&token.equals(o.optString("id"))){current=o;break;}
        }
        if(current==null)current=new JSONObject();
        long now=System.currentTimeMillis();
        String safe=label==null?"":label.replace("\n"," ").replace("\r"," ").trim();
        if(safe.length()>65)safe=safe.substring(0,65)+"…";
        if(safe.isEmpty())safe="대화 이름 미제공";
        try {
            JSONObject stats=current.optJSONObject("daily");
            if(stats==null)stats=new JSONObject();
            String today=key(0);
            // Same notification may be reposted: only count materially changed posts.
            boolean repeat=fingerprint.equals(current.optString("lastEvent",""))
                    &&now-current.optLong("lastEventAt",0)<120000L;
            if(!repeat)stats.put(today,Math.min(100000,stats.optInt(today,0)+1));
            JSONObject trimmed=new JSONObject();
            for(int d=0;d<14;d++){
                String day=key(d);
                if(stats.has(day))trimmed.put(day,stats.optInt(day,0));
            }
            current.put("id",token);
            current.put("name",safe);
            current.put("at",now);
            current.put("lastEvent",fingerprint);
            current.put("lastEventAt",now);
            current.put("daily",trimmed);
            JSONArray recent=new JSONArray();
            recent.put(current);
            for(int i=0;i<array.length()&&recent.length()<MAX_CHATS;i++){
                JSONObject previous=array.optJSONObject(i);
                if(previous!=null&&!token.equals(previous.optString("id")))recent.put(previous);
            }
            p.edit().putString("chats",recent.toString()).apply();
        }catch(JSONException ignored){}
    }

    static List<Chat> list(Context c){
        List<Chat> output=new ArrayList<>();
        try {
            JSONArray data=new JSONArray(prefs(c).getString("chats","[]"));
            for(int i=0;i<Math.min(data.length(),MAX_CHATS);i++){
                JSONObject o=data.optJSONObject(i);if(o==null)continue;
                String token=o.optString("id","");
                if(token.length()<8)continue;
                Chat chat=new Chat(token,o.optString("name","대화 이름 미제공"),o.optLong("at",0));
                JSONObject daily=o.optJSONObject("daily");
                if(daily!=null)for(int d=0;d<7;d++)chat.days[d]=Math.max(0,daily.optInt(key(d),0));
                if(chat.total()>0)output.add(chat);
            }
        }catch(JSONException ignored){}
        Collections.sort(output,(a,b)->Integer.compare(b.total(),a.total()));
        return output;
    }

    static int total(Context c,int daysAgo){
        if(daysAgo<0||daysAgo>=7)return 0;
        int total=0;
        for(Chat chat:list(c))total+=chat.days[daysAgo];
        return total;
    }

    static void clearAll(Context c){prefs(c).edit().clear().apply();}
}
