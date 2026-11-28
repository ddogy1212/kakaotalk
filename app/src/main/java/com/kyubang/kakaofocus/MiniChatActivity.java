package com.kyubang.kakaofocus;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

public final class MiniChatActivity extends Activity {
    private final int pink=0xffffb6d9, ink=0xfff5f1f4, muted=0xffb9aeb7;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private LinearLayout messages;
    private ScrollView scroll;
    private TextView status;
    private EditText draft;
    private Button send;
    private String token,label,rendered="";
    private final Runnable ticker=new Runnable(){public void run(){
        if(locked()){finish();return;}
        if(!token.equals(MiniChatStore.token(MiniChatActivity.this))||
           !label.equals(MiniChatStore.label(MiniChatActivity.this))){finish();return;}
        render();handler.postDelayed(this,650);
    }};
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private boolean locked(){
        KeyguardManager k=(KeyguardManager)getSystemService(Context.KEYGUARD_SERVICE);
        return k==null||k.isDeviceLocked()||k.isKeyguardLocked();
    }
    private GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private TextView text(String s,int sp,int color){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(color);return v;}
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE|WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        getWindow().setDimAmount(.18f);
        if(locked()){finish();return;}
        token=MiniChatStore.token(this);label=MiniChatStore.label(this);
        // Widget taps never open the settings or the Kakao app.
        // An unconfigured widget displays the same small dialog in a disabled state.
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(1);panel.setPadding(dp(13),dp(10),dp(13),dp(10));
        int a=Math.round(MiniChatStore.opacity(this)*2.55f);
        panel.setBackground(bg(Color.argb(a,30,29,34),22));
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=text(token.isEmpty()?"미니 채팅":label,17,ink);title.setTypeface(null,Typeface.BOLD);
        title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        Button close=new Button(this);close.setText("×");close.setTextSize(24);close.setContentDescription("닫기");
        close.setTextColor(pink);close.setBackgroundColor(Color.TRANSPARENT);close.setOnClickListener(v->finish());
        header.addView(close,new LinearLayout.LayoutParams(dp(44),dp(44)));panel.addView(header);
        status=text("",11,muted);panel.addView(status);
        scroll=new ScrollView(this);scroll.setFillViewport(false);
        messages=new LinearLayout(this);messages.setOrientation(1);scroll.addView(messages);
        int h=Math.min(dp(260),getResources().getDisplayMetrics().heightPixels/3);
        panel.addView(scroll,new LinearLayout.LayoutParams(-1,h));
        LinearLayout composer=new LinearLayout(this);composer.setGravity(Gravity.CENTER_VERTICAL);
        draft=new EditText(this);draft.setTextColor(ink);draft.setHintTextColor(muted);draft.setTextSize(MiniChatStore.font(this));
        draft.setHint("답장 입력 · 줄바꿈 가능");draft.setMaxLines(5);draft.setSaveEnabled(false);
        draft.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        draft.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_ENTER_ACTION);
        draft.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(2000)});
        draft.setPadding(dp(12),dp(9),dp(12),dp(9));draft.setBackground(bg(0xff37343c,17));
        composer.addView(draft,new LinearLayout.LayoutParams(0,-2,1));
        send=new Button(this);send.setText("전송");send.setTextColor(0xff35232e);send.setBackground(bg(pink,14));
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(dp(58),dp(44));sp.leftMargin=dp(6);composer.addView(send,sp);panel.addView(composer);
        send.setOnClickListener(v->{
            String body=draft.getText().toString().trim();
            if(body.isEmpty())return;
            if(!NotificationWatcher.miniSend(this,token,label,body)){
                Toast.makeText(this,"사용 가능한 답장 알림이 없어. 작성한 내용은 그대로 유지했어.",Toast.LENGTH_LONG).show();render();return;
            }
            draft.setText("");
            Toast.makeText(this,"카톡에 답장 요청을 전달했어. 수신 확인은 불가해.",Toast.LENGTH_SHORT).show();
            if(MiniChatStore.autoClose(this))finish();else render();
        });
        TextView note=text("여러 문장은 줄바꿈으로 한 번에 보낼 수 있어.\n전송 요청은 실제 전달 확인과 다름",10,muted);note.setGravity(Gravity.CENTER);note.setPadding(0,dp(7),0,0);panel.addView(note);
        setContentView(panel);getWindow().setLayout((int)(getResources().getDisplayMetrics().widthPixels*MiniChatStore.width(this)/100f),-2);
        getWindow().setGravity(Gravity.CENTER);
        setFinishOnTouchOutside(true);
        NotificationWatcher.refreshMini();render();
    }
    private void render(){
        if(isFinishing()||messages==null)return;
        boolean configured=!token.isEmpty();
        boolean connected=NotificationWatcher.isConnected();
        int available=configured&&connected?NotificationWatcher.miniReplyCount():0;
        boolean can=available>0&&!locked();
        status.setText(!configured?"앱의 톡방 관리에서 미니 채팅을 연결해 줘.":!connected?"알림 접근 연결 필요":can?"답장 가능 알림 "+available+"개":"새 답장 가능 알림을 기다리는 중");
        draft.setEnabled(configured);
        send.setEnabled(can);send.setAlpha(can?1f:.45f);
        List<MiniChatStore.Line> lines=configured?MiniChatStore.lines():Collections.emptyList();
        StringBuilder fingerprint=new StringBuilder();for(MiniChatStore.Line l:lines)fingerprint.append(l.id).append('|');
        String fresh=fingerprint.toString();
        if(!fresh.equals(rendered)||messages.getChildCount()==0){
            rendered=fresh;messages.removeAllViews();
            if(lines.isEmpty()){TextView empty=text("아직 표시할 알림이 없어.",13,muted);empty.setGravity(Gravity.CENTER);messages.addView(empty,new LinearLayout.LayoutParams(-1,dp(100)));}
            SimpleDateFormat time=new SimpleDateFormat("HH:mm",Locale.KOREA);
            for(MiniChatStore.Line l:lines){
                LinearLayout line=new LinearLayout(this);line.setOrientation(1);line.setGravity(l.outgoing?Gravity.END:Gravity.START);
                TextView bubble=text(l.text,MiniChatStore.font(this),l.outgoing?0xff32212c:ink);
                bubble.setMaxWidth((int)(getResources().getDisplayMetrics().widthPixels*MiniChatStore.width(this)/100f*.78));
                bubble.setBackground(bg(l.outgoing?pink:0xff3a3740,15));bubble.setPadding(dp(10),dp(7),dp(10),dp(7));
                line.addView(bubble,new LinearLayout.LayoutParams(-2,-2));
                TextView when=text(time.format(new Date(l.time))+(l.outgoing?" · 전송 요청":""),10,muted);line.addView(when);
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(7);messages.addView(line,lp);
            }
            scroll.post(()->scroll.fullScroll(View.FOCUS_DOWN));
        }
        if(MiniChatStore.count()>0){MiniChatStore.acknowledge();MiniChatWidgetProvider.updateAll(this);}
    }
    @Override protected void onResume(){super.onResume();if(!isFinishing()&&token!=null){handler.removeCallbacks(ticker);handler.post(ticker);}}
    @Override protected void onPause(){handler.removeCallbacks(ticker);super.onPause();if(!isChangingConfigurations())finish();}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);super.onDestroy();}
}
