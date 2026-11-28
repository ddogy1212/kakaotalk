package com.kyubang.kakaofocus;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.view.View;
import android.widget.RemoteViews;

public final class MiniChatWidgetProvider extends AppWidgetProvider {
    @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids) { updateAll(c); }
    @Override public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m,int id,android.os.Bundle options){updateAll(c);}
    static void updateAll(Context c) {
        AppWidgetManager m=AppWidgetManager.getInstance(c);
        int[] ids=m.getAppWidgetIds(new ComponentName(c,MiniChatWidgetProvider.class));
        if(ids.length==0) return;
        RemoteViews v=new RemoteViews(c.getPackageName(),R.layout.widget_mini_chat);
        Bitmap b=MiniChatStore.bitmap(c);
        v.setViewVisibility(R.id.mini_photo,b==null?View.GONE:View.VISIBLE);
        v.setViewVisibility(R.id.mini_icon,b==null?View.VISIBLE:View.GONE);
        if(b!=null) v.setImageViewBitmap(R.id.mini_photo,b);
        int count=MiniChatStore.count();
        v.setViewVisibility(R.id.mini_badge,MiniChatStore.showCount(c)&&count>0?View.VISIBLE:View.GONE);
        v.setTextViewText(R.id.mini_badge,count>99?"99+":Integer.toString(count));
        Intent i=new Intent(c,MiniChatActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        v.setOnClickPendingIntent(R.id.mini_root,PendingIntent.getActivity(c,714,i,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
        m.updateAppWidget(ids,v);
        if(b!=null)b.recycle();
    }
}
