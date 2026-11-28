package com.kyubang.kakaofocus;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.*;
import java.util.*;

public final class MiniChatSettingsActivity extends Activity {
    private final int ink=0xff34232f,pink=0xffb93478;
    private LinearLayout page;
    private TextView binding;
    private ImageView preview;
    private CheckBox autoClose,count;
    private SeekBar opacity,font,width;
    private int dp(float d){return Math.round(d*getResources().getDisplayMetrics().density);}
    private TextView text(String s,int sp){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(ink);return t;}
    private void add(android.view.View v,int top){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(top);page.addView(v,lp);}
    private Button button(String s,Runnable r){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(pink);b.setOnClickListener(v->r.run());add(b,6);return b;}
    @Override protected void onCreate(Bundle b){
        super.onCreate(b);getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        ScrollView sc=new ScrollView(this);page=new LinearLayout(this);page.setOrientation(1);page.setPadding(dp(18),dp(24),dp(18),dp(28));page.setBackgroundColor(0xfffff5fa);sc.addView(page);setContentView(sc);
        TextView title=text("미니 채팅",26);title.setTextColor(pink);title.setTypeface(null,android.graphics.Typeface.BOLD);add(title,0);
        add(text("카톡을 열지 않고 선택한 톡방의 알림을 확인하고 답장해. 사진 위젯은 1×1 크기 그대로.",14),8);
        binding=text("",16);add(binding,18);
        button("최근 활성 카톡 알림에서 톡방 연결",()->chooseRoom());
        button("연결 해제 · 대화 기록 지우기",()->{MiniChatStore.bind(this,"","");refresh();});
        button("알림 접근 권한 설정",()->startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        add(text("연결할 톡방에서 새 메시지가 도착했을 때 선택해. 이름만으로 연결하지 않으며 식별자가 없는 알림은 지원하지 않아.",12),5);
        add(text("위젯 사진",18),16);
        preview=new ImageView(this);preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable d=new GradientDrawable();d.setColor(0xff302831);d.setCornerRadius(dp(16));preview.setBackground(d);preview.setClipToOutline(true);
        LinearLayout.LayoutParams pv=new LinearLayout.LayoutParams(dp(86),dp(86));pv.gravity=Gravity.CENTER;pv.topMargin=dp(8);page.addView(preview,pv);
        button("사진 선택 (정사각형 중앙 맞춤)",()->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,72);});
        button("사진 초기화",()->{MiniChatStore.removePhoto(this);refresh();});
        autoClose=new CheckBox(this);autoClose.setText("답장 후 자동 닫기");autoClose.setChecked(MiniChatStore.autoClose(this));add(autoClose,10);
        count=new CheckBox(this);count.setText("새 알림 숫자 표시");count.setChecked(MiniChatStore.showCount(this));add(count,2);
        opacity=slider("팝업 불투명도",50,100,MiniChatStore.opacity(this),"%");
        font=slider("글씨 크기",10,22,MiniChatStore.font(this),"sp");
        width=slider("팝업 너비",60,95,MiniChatStore.width(this),"%");
        autoClose.setOnCheckedChangeListener((v,x)->save());count.setOnCheckedChangeListener((v,x)->save());
        button("미니 채팅 열기",()->startActivity(new Intent(this,MiniChatActivity.class)));
        button("현재 대화 기록 지우기",()->{MiniChatStore.clear();MiniChatWidgetProvider.updateAll(this);Toast.makeText(this,"기기 메모리 기록 삭제됨. 활성 알림은 다시 표시될 수 있어.",Toast.LENGTH_SHORT).show();});
        add(text("기록은 앱 프로세스의 메모리에만 보관되며 권한 해제나 재시작 시 사라질 수 있어. 전체 카톡 대화, 사진 전송, 읽음 여부 및 실제 전송 성공은 확인할 수 없어. 잠금 상태에서는 내용을 표시하지 않아.",12),12);
        refresh();
    }
    private SeekBar slider(String title,int min,int max,int value,String unit){
        TextView caption=text(title+" · "+value+unit,14);add(caption,10);
        SeekBar bar=new SeekBar(this);bar.setMax(max-min);bar.setProgress(value-min);add(bar,0);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar s,int p,boolean from){caption.setText(title+" · "+(p+min)+unit);if(from)save();}
            public void onStartTrackingTouch(SeekBar s){}
            public void onStopTrackingTouch(SeekBar s){save();}
        });return bar;
    }
    private void save(){
        if(opacity==null||font==null||width==null)return;
        MiniChatStore.save(this,autoClose.isChecked(),count.isChecked(),opacity.getProgress()+50,font.getProgress()+10,width.getProgress()+60);
        MiniChatWidgetProvider.updateAll(this);
    }
    private void chooseRoom(){
        List<NotificationWatcher.MiniTarget> choices=NotificationWatcher.miniChoices();
        if(choices.isEmpty()){new AlertDialog.Builder(this).setTitle("연결 가능한 알림 없음").setMessage("알림 접근 권한을 확인하고 해당 톡방의 새 카톡 알림을 받은 다음 다시 시도해. 카톡이 대화 식별자를 제공하지 않으면 연결할 수 없어.").setPositiveButton("확인",null).show();return;}
        String[] labels=new String[choices.size()];
        for(int i=0;i<labels.length;i++){
            NotificationWatcher.MiniTarget t=choices.get(i);
            labels[i]=t.label+" · "+new java.text.SimpleDateFormat("HH:mm",Locale.KOREA).format(new Date(t.sbn.getPostTime()))+" · "+t.token.substring(t.token.length()-5);
        }
        new AlertDialog.Builder(this).setTitle("허용할 톡방 선택").setItems(labels,(dialog,index)->{
            NotificationWatcher.MiniTarget t=choices.get(index);
            new AlertDialog.Builder(this).setTitle(t.label+" 연결").setMessage("선택한 알림이 원하는 톡방인지 확인해. 이 톡방의 알림 문구와 답장 기능만 미니 채팅에 사용돼.")
                .setNegativeButton("취소",null).setPositiveButton("연결",(d,w)->{MiniChatStore.bind(this,t.token,t.label);NotificationWatcher.refreshMini();refresh();}).show();
        }).show();
    }
    private void refresh(){
        binding.setText(MiniChatStore.token(this).isEmpty()?"연결된 톡방 없음":"연결됨 · "+MiniChatStore.label(this));
        Bitmap b=MiniChatStore.bitmap(this);if(b==null)preview.setImageResource(android.R.drawable.ic_dialog_email);else preview.setImageBitmap(b);
        MiniChatWidgetProvider.updateAll(this);
    }
    @Override protected void onActivityResult(int req,int result,Intent data){
        super.onActivityResult(req,result,data);
        if(req==72&&result==RESULT_OK&&data!=null){if(!MiniChatStore.savePhoto(this,data.getData()))Toast.makeText(this,"사진을 저장하지 못했어.",Toast.LENGTH_SHORT).show();refresh();}
    }
}
