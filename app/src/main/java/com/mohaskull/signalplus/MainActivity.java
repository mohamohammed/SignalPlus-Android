
package com.mohaskull.signalplus;

import android.app.Activity;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.InputType;
import android.util.Base64;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilderFactory;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    final int BG=Color.rgb(2,12,18);
    final int PANEL=Color.rgb(5,24,33);
    final int CARD=Color.rgb(8,34,46);
    final int CARD2=Color.rgb(11,45,59);
    final int CYAN=Color.rgb(25,228,255);
    final int GREEN=Color.rgb(45,220,115);
    final int YELLOW=Color.rgb(255,196,66);
    final int RED=Color.rgb(255,80,80);
    final int TEXT=Color.rgb(238,250,252);
    final int MUTED=Color.rgb(155,188,198);

    LinearLayout root, content;
    TextView status, boxState, boxModel, boxSession;
    SharedPreferences prefs;
    Router router;
    ExecutorService exec=Executors.newSingleThreadExecutor();
    Handler ui=new Handler(Looper.getMainLooper());

    EditText ip,user,pass;
    boolean sessionOk=false, loginBusy=false, live=false;
    long cooldownUntil=0, lastRequestAt=0;
    int refreshSeconds=5;
    String lastReport="";
    String authMethod="hmanager_auto";
    Map<String,String> lastSignal=new HashMap<>();
    Map<String,String> lastInfo=new HashMap<>();
    Map<String,String> lastStatus=new HashMap<>();
    Map<String,String> lastNet=new HashMap<>();
    Map<String,String> lastTraffic=new HashMap<>();
    Map<String,String> lastPlmn=new HashMap<>();

    public void onCreate(Bundle b){
        super.onCreate(b);
        prefs=getSharedPreferences("sp20",0);
        cooldownUntil=prefs.getLong("cooldownUntil",0);
        refreshSeconds=prefs.getInt("refreshSeconds",5);
        authMethod=prefs.getString("authMethod","hmanager_auto");
        buildUi();
        if(prefs.getBoolean("autoDashboard",false)) showDashboard(); else showLogin();
    }

    public void onDestroy(){ live=false; super.onDestroy(); }

    void buildUi(){
        ScrollView sv=new ScrollView(this);
        root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14),dp(14),dp(14),dp(30));
        root.setBackgroundColor(BG);
        sv.addView(root);

        LinearLayout hero=new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setGravity(Gravity.CENTER);
        hero.setPadding(dp(12),dp(18),dp(12),dp(18));
        hero.setBackground(bg(PANEL,1,Color.rgb(0,98,115),30));
        root.addView(hero,new LinearLayout.LayoutParams(-1,-2));

        ImageView logo=new ImageView(this);
        logo.setImageResource(R.drawable.logo_mohaskull);
        LinearLayout.LayoutParams lpLogo=new LinearLayout.LayoutParams(dp(120),dp(120));
        lpLogo.bottomMargin=dp(8);
        hero.addView(logo,lpLogo);

        TextView title=text("إشارة بلس",32,TEXT,true);
        title.setGravity(Gravity.CENTER);
        hero.addView(title);

        TextView sub=text("Signal Plus Android v2.0 Professional Full",14,CYAN,true);
        sub.setGravity(Gravity.CENTER);
        hero.addView(sub);

        TextView badge=text("Huawei Manager Logic • Moha Skull Edition",12,MUTED,false);
        badge.setGravity(Gravity.CENTER);
        hero.addView(badge);

        LinearLayout mini=new LinearLayout(this);
        mini.setOrientation(LinearLayout.HORIZONTAL);
        mini.setPadding(0,dp(10),0,dp(6));
        root.addView(mini,new LinearLayout.LayoutParams(-1,-2));

        boxState=miniBox("الحالة","غير متصل",CYAN);
        boxModel=miniBox("الموديل","--",CYAN);
        boxSession=miniBox("الجلسة","غير مفعلة",RED);
        mini.addView(boxState); mini.addView(boxModel); mini.addView(boxSession);

        GridLayout nav=new GridLayout(this);
        nav.setColumnCount(3);
        nav.setPadding(0,dp(8),0,dp(8));
        root.addView(nav);

        addNav(nav,"دخول",()->showLogin());
        addNav(nav,"لوحة التحكم",()->showDashboard());
        addNav(nav,"Web Login",()->showWebLogin());
        addNav(nav,"الترددات",()->showBands());
        addNav(nav,"الأجهزة",()->showDevices());
        addNav(nav,"الأدوات",()->showTools());
        addNav(nav,"الإعدادات",()->showSettings());
        addNav(nav,"Auth Lab",()->showAuthLab());
        addNav(nav,"تقرير",()->showReport());

        content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content);

        status=text("",13,MUTED,false);
        status.setPadding(dp(6),dp(12),dp(6),dp(6));
        root.addView(status);

        setContentView(sv);
    }

    interface Action{ void run(); }

    void addNav(GridLayout nav,String label,final Action action){
        TextView b=text(label,16,TEXT,true);
        b.setGravity(Gravity.CENTER);
        b.setBackground(bg(CARD,1,CYAN,20));
        b.setOnClickListener(v->{ live=false; action.run(); });
        GridLayout.LayoutParams lp=new GridLayout.LayoutParams();
        lp.width=0; lp.height=dp(60);
        lp.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);
        lp.setMargins(dp(4),dp(4),dp(4),dp(4));
        nav.addView(b,lp);
    }

    TextView miniBox(String h,String v,int color){
        TextView t=text(h+"\n"+v,12,TEXT,true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(bg(CARD,1,Color.rgb(0,90,108),18));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(66),1);
        lp.setMargins(dp(3),0,dp(3),0);
        t.setLayoutParams(lp);
        return t;
    }

    void updateTop(String state,String model,String session){
        boxState.setText("الحالة\n"+state);
        boxModel.setText("الموديل\n"+model);
        boxSession.setText("الجلسة\n"+session);
    }

    TextView text(String s,int size,int color,boolean bold){
        TextView t=new TextView(this);
        t.setText(s); t.setTextSize(size); t.setTextColor(color);
        t.setPadding(dp(6),dp(6),dp(6),dp(6));
        if(bold)t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    GradientDrawable bg(int color,int stroke,int strokeColor,int radius){
        GradientDrawable d=new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        if(stroke>0)d.setStroke(dp(stroke),strokeColor);
        return d;
    }

    int dp(int v){ return (int)(v*getResources().getDisplayMetrics().density+.5f); }
    void clear(){ content.removeAllViews(); }
    void setStatus(String s){ status.setText(s); }
    void toast(String s){ Toast.makeText(this,s,Toast.LENGTH_LONG).show(); }
    void run(Runnable r){ exec.execute(r); }

    TextView label(String s){
        TextView v=text(s,15,TEXT,true);
        v.setGravity(Gravity.RIGHT);
        v.setPadding(0,dp(10),0,dp(5));
        return v;
    }

    EditText input(String hint,String value){
        EditText e=new EditText(this);
        e.setHint(hint); e.setText(value); e.setSingleLine(true); e.setTextSize(18);
        e.setTextColor(TEXT); e.setHintTextColor(MUTED);
        e.setBackground(bg(CARD,1,Color.rgb(0,94,112),16));
        e.setPadding(dp(14),0,dp(14),0);
        content.addView(e,new LinearLayout.LayoutParams(-1,dp(58)));
        return e;
    }

    Button button(String s,boolean primary){
        Button b=new Button(this);
        b.setText(s); b.setAllCaps(false); b.setTextSize(16); b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(primary?Color.BLACK:TEXT);
        b.setBackground(bg(primary?CYAN:CARD2,1,primary?CYAN:Color.rgb(0,100,120),20));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(58));
        lp.setMargins(0,dp(8),0,dp(6));
        content.addView(b,lp);
        return b;
    }

    TextView card(String h,String body){
        TextView v=text(h+"\n\n"+body,15,TEXT,false);
        v.setBackground(bg(CARD,1,Color.rgb(0,90,110),22));
        v.setPadding(dp(16),dp(14),dp(16),dp(14));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(0,dp(8),0,dp(8));
        content.addView(v,lp);
        return v;
    }

    void titleCard(String title,String sub){
        TextView v=text(title+"\n"+sub,18,TEXT,true);
        v.setBackground(bg(PANEL,1,CYAN,22));
        v.setPadding(dp(16),dp(14),dp(16),dp(14));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(0,dp(8),0,dp(10));
        content.addView(v,lp);
    }

    void metricGrid(String[][] items){
        GridLayout g=new GridLayout(this);
        g.setColumnCount(2);
        g.setPadding(0,dp(4),0,dp(4));
        content.addView(g,new LinearLayout.LayoutParams(-1,-2));
        for(String[] it:items){
            TextView v=text(it[0]+"\n"+it[1],15,TEXT,true);
            v.setGravity(Gravity.CENTER);
            int c=it.length>2?parseColorName(it[2]):Color.rgb(0,90,110);
            v.setBackground(bg(CARD,1,c,20));
            GridLayout.LayoutParams lp=new GridLayout.LayoutParams();
            lp.width=0; lp.height=dp(82);
            lp.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);
            lp.setMargins(dp(4),dp(4),dp(4),dp(4));
            g.addView(v,lp);
        }
    }

    int parseColorName(String n){
        if("green".equals(n))return GREEN;
        if("yellow".equals(n))return YELLOW;
        if("red".equals(n))return RED;
        return CYAN;
    }

    boolean rateLimited(){
        long now=System.currentTimeMillis();
        if(now-lastRequestAt<1200){ toast("انتظر لحظة قبل الطلب التالي"); return true; }
        lastRequestAt=now; return false;
    }

    long remainingCooldown(){ return Math.max(0,(cooldownUntil-System.currentTimeMillis()+999)/1000); }
    void startCooldown(int sec){ cooldownUntil=System.currentTimeMillis()+sec*1000L; prefs.edit().putLong("cooldownUntil",cooldownUntil).apply(); }

    void saveRouterFields(){
        String url=(ip==null)?prefs.getString("url","http://192.168.1.1"):clean(ip.getText().toString());
        String u=(user==null)?prefs.getString("user","admin"):user.getText().toString();
        String p=(pass==null)?prefs.getString("pass",""):pass.getText().toString();
        prefs.edit().putString("url",url).putString("user",u).putString("pass",p).apply();
        router=new Router(url);
    }

    void ensureRouter(){
        if(router==null) router=new Router(clean(prefs.getString("url","http://192.168.1.1")));
    }

    String clean(String s){
        if(s==null || s.trim().isEmpty())s="http://192.168.1.1";
        s=s.trim();
        if(!s.startsWith("http"))s="http://"+s;
        while(s.endsWith("/"))s=s.substring(0,s.length()-1);
        return s;
    }

    void showLogin(){
        clear();
        titleCard("تسجيل الدخول","استخدم hmanager_auto كطريقة افتراضية لأنها نجحت في قراءات الراوتر.");
        content.addView(label("IP / URL الراوتر"));
        ip=input("http://192.168.1.1",prefs.getString("url","http://192.168.1.1"));
        content.addView(label("اسم المستخدم"));
        user=input("admin",prefs.getString("user","admin"));
        content.addView(label("كلمة المرور"));
        pass=input("كلمة مرور الراوتر",prefs.getString("pass",""));
        pass.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);

        long rem=remainingCooldown();
        Button login=button(rem>0?"انتظر "+rem+" ثانية":"تسجيل الدخول وإنشاء Session",true);
        login.setEnabled(rem<=0);
        login.setOnClickListener(v->loginWithMethod("hmanager_auto",true));

        button("اختبار اتصال فقط",false).setOnClickListener(v->testConnection());
        button("Web Login داخل التطبيق",false).setOnClickListener(v->showWebLogin());
        button("فتح صفحة الراوتر",false).setOnClickListener(v->openRouter());
        button("مسح البيانات",false).setOnClickListener(v->{ prefs.edit().clear().apply(); sessionOk=false; router=null; authMethod="hmanager_auto"; showLogin(); });

        card("ملاحظة","إذا نجح الدخول ستنتقل مباشرة إلى لوحة التحكم الاحترافية. إذا ظهرت 108007 انتظر المؤقت ولا تكرر المحاولة.");
    }

    void testConnection(){
        if(rateLimited())return;
        saveRouterFields();
        setStatus("جاري اختبار الاتصال...");
        run(()->{
            try{
                String res=router.get("/api/webserver/SesTokInfo");
                lastReport="SesTokInfo:\n"+Router.plain(res);
                ui.post(()->{ updateTop("متصل","--",sessionOk?"مفعلة":"غير مفعلة"); setStatus("الاتصال ناجح."); });
            }catch(Exception e){ fail("فشل الاتصال: "+e.getMessage()); }
        });
    }

    void loginWithMethod(String method,boolean openDashboard){
        if(loginBusy){ toast("محاولة دخول قيد التنفيذ"); return; }
        if(rateLimited())return;
        long rem=remainingCooldown();
        if(rem>0){ toast("انتظر "+rem+" ثانية"); return; }
        saveRouterFields();
        hideKeyboard();
        loginBusy=true;
        setStatus("جاري إنشاء Session باستخدام "+method+" ...");
        run(()->{
            try{
                String u=prefs.getString("user","admin");
                String p=prefs.getString("pass","");
                Router.LoginResult lr=router.login(u,p,method);
                lastReport=lr.report;
                ui.post(()->{
                    loginBusy=false;
                    if(lr.code108007){
                        startCooldown(180);
                        sessionOk=false;
                        updateTop("مقفل","--","108007");
                        setStatus("تم تفعيل حماية 108007 لمدة 180 ثانية");
                        showLogin();
                        return;
                    }
                    if(lr.ok){
                        sessionOk=true;
                        authMethod=method;
                        prefs.edit().putString("authMethod",method).putBoolean("autoDashboard",true).apply();
                        updateTop("متصل","--","مفعلة");
                        setStatus("تم إنشاء Session بنجاح.");
                        if(openDashboard)showDashboard();
                    }else{
                        sessionOk=false;
                        updateTop("متصل","--","فشل");
                        clear();
                        card("فشل إنشاء Session",lr.report);
                        button("Auth Lab",true).setOnClickListener(v->showAuthLab());
                        button("نسخ التقرير",false).setOnClickListener(v->copyReport());
                        button("فتح صفحة الراوتر",false).setOnClickListener(v->openRouter());
                    }
                });
            }catch(Exception e){ ui.post(()->{loginBusy=false; fail("خطأ: "+e.getMessage());}); }
        });
    }

    void showAuthLab(){
        clear();
        titleCard("Auth Lab","للتشخيص فقط. الطريقة الموصى بها: HManager Auto.");
        card("الطريقة الحالية","Auth Method: "+prefs.getString("authMethod","hmanager_auto")+"\nSession: "+(sessionOk?"مفعلة":"غير مفعلة"));
        button("HManager Auto - الموصى به",true).setOnClickListener(v->loginWithMethod("hmanager_auto",true));
        button("HManager Type4 HEX",false).setOnClickListener(v->loginWithMethod("hmanager_type4_hex",true));
        button("HManager Base64 Plain",false).setOnClickListener(v->loginWithMethod("hmanager_base64_plain",true));
        button("Legacy Type4",false).setOnClickListener(v->loginWithMethod("legacy_type4",true));
        button("Hash بدون اسم المستخدم",false).setOnClickListener(v->loginWithMethod("hash_no_user",true));
        button("Plain password_type=0",false).setOnClickListener(v->loginWithMethod("plain_type0",true));
        button("Plain password_type=4",false).setOnClickListener(v->loginWithMethod("plain_type4",true));
        button("نسخ التقرير",false).setOnClickListener(v->copyReport());
    }

    void showDashboard(){
        clear();
        titleCard("لوحة التحكم الاحترافية","قراءات لحظية من الراوتر مع تقييم جودة الإشارة.");
        button("تحديث لوحة التحكم",true).setOnClickListener(v->refreshDashboard(false));
        button("تشغيل لحظي كل "+refreshSeconds+" ثواني",false).setOnClickListener(v->startLive());
        button("إيقاف اللحظي",false).setOnClickListener(v->{ live=false; setStatus("تم إيقاف التحديث اللحظي."); });
        button("نسخ التقرير",false).setOnClickListener(v->copyReport());
        refreshDashboard(false);
    }

    void startLive(){
        live=true;
        setStatus("تشغيل التحديث اللحظي كل "+refreshSeconds+" ثواني.");
        ui.post(new Runnable(){ public void run(){ if(!live)return; refreshDashboard(true); ui.postDelayed(this,refreshSeconds*1000L); }});
    }

    void refreshDashboard(boolean quiet){
        if(!quiet && rateLimited())return;
        ensureRouter();
        if(!quiet)setStatus("جاري تحديث لوحة التحكم...");
        run(()->{
            try{
                readAllBasic();
                String model=first(lastInfo,"DeviceName","devicename","Model","model");
                String provider=first(lastPlmn,"FullName","ShortName","Numeric");
                String bandRaw=first(lastSignal,"band","lteband","Band");
                String band=normalizeBand(bandRaw);
                String rsrp=first(lastSignal,"rsrp","RSRP");
                String rsrq=first(lastSignal,"rsrq","RSRQ");
                String sinr=first(lastSignal,"sinr","SINR");
                String rssi=first(lastSignal,"rssi","RSSI");
                String cell=first(lastSignal,"cell_id","cellid","CellID");
                String pci=first(lastSignal,"pci","PCI");
                String tac=first(lastSignal,"tac","TAC");
                String dlbw=first(lastSignal,"dlbandwidth","dl_bandwidth","DLBandwidth");
                String ulbw=first(lastSignal,"ulbandwidth","ul_bandwidth","ULBandwidth");
                String earfcn=first(lastSignal,"earfcn","earfcn_dl","earfcnul","EARFCN");
                String setBand=first(lastNet,"LTEBand","lteband");
                String netBand=first(lastNet,"NetworkBand","networkband");
                String statusVal=first(lastStatus,"ConnectionStatus","connectionstatus");
                String down=first(lastTraffic,"CurrentDownloadRate","currentdownloadrate","TotalDownload","totaldownload");
                String up=first(lastTraffic,"CurrentUploadRate","currentuploadrate","TotalUpload","totalupload");
                String uptime=first(lastStatus,"CurrentConnectTime","currentconnecttime","TotalConnectTime","totalconnecttime");

                String report=
                    "معلومات الاتصال\n"+
                    "الحالة: "+statusVal+"\n"+
                    "موديل الجهاز: "+model+"\n"+
                    "مزود الخدمة: "+provider+"\n"+
                    "Band: "+band+"\n"+
                    "SetBand: "+setBand+"\n"+
                    "NetBand: "+netBand+"\n\n"+
                    "معلومات الخلية\n"+
                    "Cell ID: "+cell+"\n"+
                    "PCI / رقم البرج: "+pci+"\n"+
                    "TAC: "+tac+"\n"+
                    "EARFCN: "+earfcn+"\n\n"+
                    "قوة الإشارة\n"+
                    "RSSI: "+rssi+"\n"+
                    "RSRP: "+rsrp+"\n"+
                    "RSRQ: "+rsrq+"\n"+
                    "SINR: "+sinr+"\n"+
                    "DL BW: "+dlbw+"\n"+
                    "UL BW: "+ulbw+"\n\n"+
                    "البيانات\n"+
                    "Download: "+down+"\n"+
                    "Upload: "+up+"\n"+
                    "Uptime: "+uptime+"\n\n"+
                    "Session: "+(sessionOk?"مفعلة":"غير مفعلة")+"\n"+
                    "Auth Method: "+prefs.getString("authMethod",authMethod);
                lastReport=report;

                ui.post(()->{
                    clear();
                    titleCard("لوحة التحكم الاحترافية","آخر تحديث مباشر من الراوتر.");
                    metricGrid(new String[][]{
                        {"Band",band,"cyan"},
                        {"SINR",sinr,qualitySinr(sinr)},
                        {"RSRP",rsrp,qualityRsrp(rsrp)},
                        {"RSRQ",rsrq,qualityRsrq(rsrq)},
                        {"RSSI",rssi,"cyan"},
                        {"PCI",pci,"cyan"},
                        {"Cell ID",cell,"cyan"},
                        {"DL / UL BW",dlbw+" / "+ulbw,"cyan"}
                    });
                    card("معلومات الشبكة","الموديل: "+model+"\nمزود الخدمة: "+provider+"\nالحالة: "+statusVal+"\nSetBand: "+setBand+"\nNetBand: "+netBand+"\nEARFCN: "+earfcn+"\nDownload: "+down+"\nUpload: "+up);
                    button("تحديث لوحة التحكم",true).setOnClickListener(v->refreshDashboard(false));
                    button("تشغيل لحظي كل "+refreshSeconds+" ثواني",false).setOnClickListener(v->startLive());
                    button("إيقاف اللحظي",false).setOnClickListener(v->{ live=false; setStatus("تم إيقاف التحديث اللحظي."); });
                    button("نسخ التقرير",false).setOnClickListener(v->copyReport());
                    updateTop("متصل",model,sessionOk?"مفعلة":"غير مفعلة");
                    setStatus("تم تحديث لوحة التحكم.");
                });
            }catch(Exception e){ fail("فشل قراءة لوحة التحكم: "+e.getMessage()); }
        });
    }

    void readAllBasic()throws Exception{
        lastSignal=Router.map(router.get("/api/device/signal"));
        lastInfo=safeMap("/api/device/information");
        lastStatus=safeMap("/api/monitoring/status");
        lastNet=safeMap("/api/net/net-mode");
        lastTraffic=safeMap("/api/monitoring/traffic-statistics");
        lastPlmn=safeMap("/api/net/current-plmn");
    }

    Map<String,String> safeMap(String path){
        try{return Router.map(router.get(path));}catch(Exception e){return new HashMap<>();}
    }

    String normalizeBand(String b){
        if(b==null||b.equals("--"))return "--";
        b=b.replace("LTE", "").replace("Band", "").replace("B", "").trim();
        if(b.matches("\\d+"))return "B"+b;
        return b;
    }

    String qualitySinr(String s){
        double v=num(s);
        if(v>=20)return "green";
        if(v>=10)return "yellow";
        if(v>-100)return "red";
        return "cyan";
    }

    String qualityRsrp(String s){
        double v=num(s);
        if(v>=-85)return "green";
        if(v>=-105)return "yellow";
        if(v>-200)return "red";
        return "cyan";
    }

    String qualityRsrq(String s){
        double v=num(s);
        if(v>=-10)return "green";
        if(v>=-15)return "yellow";
        if(v>-100)return "red";
        return "cyan";
    }

    double num(String s){
        if(s==null)return -999;
        try{
            String cleaned=s.replaceAll("[^0-9\\-\\.]", "");
            if(cleaned.length()==0)return -999;
            return Double.parseDouble(cleaned);
        }catch(Exception e){return -999;}
    }

    void showBands(){
        clear();
        titleCard("الترددات","قراءة وتطبيق أوامر التردد حسب دعم الراوتر والـ Firmware.");
        button("قراءة وضع الشبكة الحالي",true).setOnClickListener(v->readNetMode());

        String[] arr={"B1","B3","B7","B8","B20","B28","B38","B40"};
        ArrayList<CheckBox> checks=new ArrayList<>();
        GridLayout g=new GridLayout(this);
        g.setColumnCount(4);
        content.addView(g,new LinearLayout.LayoutParams(-1,-2));
        for(String b:arr){
            CheckBox c=new CheckBox(this);
            c.setText(b); c.setTextColor(TEXT); c.setTextSize(15); c.setGravity(Gravity.CENTER);
            c.setButtonTintList(android.content.res.ColorStateList.valueOf(CYAN));
            GridLayout.LayoutParams lp=new GridLayout.LayoutParams();
            lp.width=0; lp.height=dp(56);
            lp.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);
            lp.setMargins(dp(4),dp(4),dp(4),dp(4));
            g.addView(c,lp);
            checks.add(c);
        }

        button("تطبيق الترددات المحددة",true).setOnClickListener(v->applyBands(checks));
        button("رجوع تلقائي Auto / All Bands",false).setOnClickListener(v->setAutoBands());
        button("نسخ تقرير الشبكة",false).setOnClickListener(v->copyReport());

        card("تنبيه","إذا لم يدعم الراوتر أمر قفل التردد سيظهر رد خطأ أو لا يتغير شيء. لا تكرر الأوامر بسرعة.");
    }

    void readNetMode(){
        if(rateLimited())return;
        ensureRouter();
        setStatus("جاري قراءة وضع الشبكة...");
        run(()->{
            try{
                String raw=router.get("/api/net/net-mode");
                lastReport=Router.plain(raw);
                Map<String,String> m=Router.map(raw);
                ui.post(()->card("وضع الشبكة الحالي","NetworkMode: "+first(m,"NetworkMode","networkmode")+"\nNetworkBand: "+first(m,"NetworkBand","networkband")+"\nLTEBand: "+first(m,"LTEBand","lteband")+"\n\nRaw: "+Router.plain(raw)));
            }catch(Exception e){ fail("فشل قراءة وضع الشبكة: "+e.getMessage()); }
        });
    }

    void applyBands(ArrayList<CheckBox> checks){
        if(rateLimited())return;
        if(!sessionOk){ toast("الجلسة غير مفعلة. سجّل الدخول أولًا."); return; }
        ArrayList<String> selected=new ArrayList<>();
        for(CheckBox c:checks)if(c.isChecked())selected.add(c.getText().toString());
        if(selected.isEmpty()){ toast("اختر ترددًا واحدًا على الأقل."); return; }
        ensureRouter();
        String mask=bandMask(selected);
        String body="<request><NetworkMode>03</NetworkMode><NetworkBand>3FFFFFFF</NetworkBand><LTEBand>"+mask+"</LTEBand></request>";
        setStatus("جاري تطبيق LTEBand: "+mask);
        run(()->{
            try{
                String res=router.post("/api/net/net-mode",body);
                lastReport="Apply bands: "+selected+"\nLTEBand="+mask+"\nResponse: "+Router.plain(res);
                ui.post(()->{ setStatus("تم إرسال أمر التردد."); card("رد الراوتر",lastReport); });
            }catch(Exception e){ fail("فشل تطبيق الترددات: "+e.getMessage()); }
        });
    }

    void setAutoBands(){
        if(rateLimited())return;
        if(!sessionOk){ toast("الجلسة غير مفعلة. سجّل الدخول أولًا."); return; }
        ensureRouter();
        String body="<request><NetworkMode>00</NetworkMode><NetworkBand>3FFFFFFF</NetworkBand><LTEBand>7FFFFFFFFFFFFFFF</LTEBand></request>";
        run(()->{
            try{
                String res=router.post("/api/net/net-mode",body);
                lastReport="Auto bands response: "+Router.plain(res);
                ui.post(()->{ setStatus("تم إرسال أمر الرجوع التلقائي."); card("رد الراوتر",lastReport); });
            }catch(Exception e){ fail("فشل الرجوع التلقائي: "+e.getMessage()); }
        });
    }

    String bandMask(ArrayList<String> s){
        Map<String,Long> m=new HashMap<>();
        m.put("B1",1L);m.put("B3",4L);m.put("B7",64L);m.put("B8",128L);
        m.put("B20",524288L);m.put("B28",134217728L);
        m.put("B38",137438953472L);m.put("B40",549755813888L);
        long x=0;
        for(String b:s){ Long v=m.get(b); if(v!=null)x|=v; }
        return Long.toHexString(x).toUpperCase(Locale.US);
    }

    void showDevices(){
        clear();
        titleCard("الأجهزة المتصلة","عرض الأجهزة من مسارات Huawei الشائعة.");
        button("تحديث الأجهزة",true).setOnClickListener(v->readDevices());
        button("نسخ تقرير الأجهزة",false).setOnClickListener(v->copyReport());
        readDevices();
    }

    void readDevices(){
        if(rateLimited())return;
        ensureRouter();
        setStatus("جاري قراءة الأجهزة...");
        run(()->{
            String[] paths={"/api/monitoring/host-list","/api/wlan/host-list","/api/wlan/station-information","/api/lan/HostInfo"};
            StringBuilder out=new StringBuilder();
            ArrayList<String> cards=new ArrayList<>();
            for(String p:paths){
                try{
                    String xml=router.get(p);
                    String plain=Router.plain(xml);
                    out.append("### ").append(p).append("\n").append(plain).append("\n\n");
                    ArrayList<Map<String,String>> hosts=Router.hosts(xml);
                    for(Map<String,String> h:hosts){
                        String name=first(h,"HostName","hostname","Name","name");
                        String ip=first(h,"IPAddress","IpAddress","ipaddress","IP","ip");
                        String mac=first(h,"MacAddress","MACAddress","macaddress","MAC","mac");
                        if(name.equals("--"))name="جهاز متصل";
                        cards.add("الاسم: "+name+"\nIP: "+ip+"\nMAC: "+mac);
                    }
                }catch(Exception ignored){}
            }
            lastReport=out.toString();
            ui.post(()->{
                clear();
                titleCard("الأجهزة المتصلة","عدد الأجهزة المقروءة: "+cards.size());
                button("تحديث الأجهزة",true).setOnClickListener(v->readDevices());
                button("نسخ تقرير الأجهزة",false).setOnClickListener(v->copyReport());
                if(cards.size()==0)card("لا توجد قائمة واضحة","لم يرسل الراوتر قائمة أجهزة مفهومة من المسارات المجربة. استخدم نسخ التقرير للمراجعة.");
                for(String c:cards)card("جهاز",c);
                setStatus("تم تحديث الأجهزة.");
            });
        });
    }

    void showTools(){
        clear();
        titleCard("الأدوات","أوامر سريعة وتشخيص.");
        button("فتح صفحة الراوتر",true).setOnClickListener(v->openRouter());
        button("إعادة إنشاء Session",false).setOnClickListener(v->loginWithMethod("hmanager_auto",false));
        button("تشخيص API خام",false).setOnClickListener(v->diagnose());
        button("نسخ آخر تقرير",false).setOnClickListener(v->copyReport());
        button("إعادة تشغيل الراوتر",false).setOnClickListener(v->reboot());
        button("تسجيل خروج / مسح الجلسة",false).setOnClickListener(v->{ sessionOk=false; router=null; updateTop("غير متصل","--","غير مفعلة"); setStatus("تم مسح الجلسة الداخلية."); });
    }

    void reboot(){
        if(rateLimited())return;
        if(!sessionOk){ toast("الجلسة غير مفعلة."); return; }
        ensureRouter();
        run(()->{
            try{
                String res=router.post("/api/device/control","<request><Control>1</Control></request>");
                lastReport="Reboot response: "+Router.plain(res);
                ui.post(()->{ setStatus("تم إرسال أمر إعادة التشغيل."); card("رد الراوتر",lastReport); });
            }catch(Exception e){ fail("فشل إعادة التشغيل: "+e.getMessage()); }
        });
    }

    void diagnose(){
        if(rateLimited())return;
        ensureRouter();
        setStatus("جاري التشخيص...");
        run(()->{
            String[] paths={"/api/webserver/SesTokInfo","/api/user/state-login","/api/device/information","/api/monitoring/status","/api/device/signal","/api/net/net-mode","/api/monitoring/traffic-statistics","/api/monitoring/host-list","/api/net/current-plmn"};
            StringBuilder out=new StringBuilder();
            out.append("Signal Plus v2.0 Diagnostics\n");
            out.append("SessionOk=").append(sessionOk).append("\n");
            out.append("AuthMethod=").append(prefs.getString("authMethod",authMethod)).append("\n\n");
            for(String p:paths){
                out.append("### ").append(p).append("\n");
                try{ out.append(Router.plain(router.get(p))).append("\n\n"); }
                catch(Exception e){ out.append("ERROR: ").append(e.getMessage()).append("\n\n"); }
            }
            lastReport=out.toString();
            ui.post(()->{ clear(); titleCard("تشخيص API","تم تجهيز التقرير."); button("نسخ تقرير التشخيص",true).setOnClickListener(v->copyReport()); card("التقرير",lastReport); setStatus("انتهى التشخيص."); });
        });
    }

    void showSettings(){
        clear();
        titleCard("الإعدادات","تخصيص التحديث وحفظ البيانات.");
        card("الإعدادات الحالية","IP: "+prefs.getString("url","http://192.168.1.1")+"\nUser: "+prefs.getString("user","admin")+"\nAuth: "+prefs.getString("authMethod","hmanager_auto")+"\nRefresh: "+refreshSeconds+"s");
        button("تحديث كل 2 ثواني",false).setOnClickListener(v->setRefresh(2));
        button("تحديث كل 5 ثواني",true).setOnClickListener(v->setRefresh(5));
        button("تحديث كل 10 ثواني",false).setOnClickListener(v->setRefresh(10));
        button("فتح لوحة التحكم تلقائيًا عند التشغيل",false).setOnClickListener(v->{ prefs.edit().putBoolean("autoDashboard",true).apply(); toast("تم التفعيل"); });
        button("إلغاء الفتح التلقائي",false).setOnClickListener(v->{ prefs.edit().putBoolean("autoDashboard",false).apply(); toast("تم الإلغاء"); });
        button("مسح كل الإعدادات",false).setOnClickListener(v->{ prefs.edit().clear().apply(); sessionOk=false; router=null; authMethod="hmanager_auto"; refreshSeconds=5; showLogin(); });
    }

    void setRefresh(int s){
        refreshSeconds=s;
        prefs.edit().putInt("refreshSeconds",s).apply();
        toast("تم ضبط التحديث: "+s+" ثواني");
        showSettings();
    }

    void showReport(){
        clear();
        titleCard("آخر تقرير","يمكن نسخه وإرساله للتشخيص.");
        button("نسخ التقرير",true).setOnClickListener(v->copyReport());
        card("التقرير",lastReport.length()>0?lastReport:"لا يوجد تقرير بعد.");
    }

    void showWebLogin(){
        clear();
        saveRouterFields();
        titleCard("Web Login","سجّل الدخول داخل صفحة الراوتر ثم استخدم الجلسة.");
        WebView web=new WebView(this);
        WebSettings ws=web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        web.setWebViewClient(new WebViewClient());
        web.loadUrl(clean(prefs.getString("url","http://192.168.1.1")));
        LinearLayout.LayoutParams wlp=new LinearLayout.LayoutParams(-1,dp(420));
        wlp.setMargins(0,dp(8),0,dp(8));
        content.addView(web,wlp);
        button("استخدام جلسة WebView وفتح لوحة التحكم",true).setOnClickListener(v->captureWebSession(web));
        button("فتح المتصفح الخارجي",false).setOnClickListener(v->openRouter());
    }

    void captureWebSession(WebView web){
        if(rateLimited())return;
        saveRouterFields();
        String url=clean(prefs.getString("url","http://192.168.1.1"));
        String ck=android.webkit.CookieManager.getInstance().getCookie(url);
        if(ck==null || ck.trim().length()==0){
            toast("لم يتم العثور على Cookie. سجّل الدخول داخل الصفحة أولًا.");
            lastReport="WEB COOKIE EMPTY for "+url;
            return;
        }
        ensureRouter();
        router.setCookieFromWeb(ck);
        sessionOk=true;
        authMethod="webview_cookie";
        prefs.edit().putString("authMethod","webview_cookie").apply();
        lastReport="WEB COOKIE CAPTURED:\n"+ck;
        updateTop("متصل","--","Web");
        setStatus("تم استخدام جلسة WebView.");
        showDashboard();
    }

    void openRouter(){ startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(prefs.getString("url","http://192.168.1.1")))); }
    void copyReport(){ ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Signal Plus",lastReport)); toast("تم النسخ"); }

    void hideKeyboard(){
        try{
            View v=getCurrentFocus();
            if(v!=null)((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(v.getWindowToken(),0);
        }catch(Exception ignored){}
    }

    void fail(String msg){
        ui.post(()->{ setStatus(msg); toast(msg); });
    }

    String g(Map<String,String> m,String key){
        if(m.containsKey(key))return m.get(key);
        for(String k:m.keySet())if(k.equalsIgnoreCase(key))return m.get(k);
        return "--";
    }

    String first(Map<String,String> m,String... keys){
        for(String k:keys){
            String v=g(m,k);
            if(v!=null && !v.equals("--") && v.trim().length()>0)return v;
        }
        return "--";
    }

    public static class Router{
        String base,cookie,token;
        Router(String b){base=b;}
        void setCookieFromWeb(String ck){ if(ck!=null && ck.trim().length()>0) cookie=ck; }

        static class LoginResult{
            boolean ok,code108007; String message,report;
            LoginResult(boolean ok,boolean c,String m,String r){this.ok=ok;this.code108007=c;this.message=m;this.report=r;}
        }

        String get(String p)throws Exception{ return read(open(p,"GET"),p); }

        String post(String p,String body)throws Exception{
            if(token==null)try{ get("/api/webserver/SesTokInfo"); }catch(Exception ignored){}
            HttpURLConnection c=open(p,"POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type","application/xml");
            if(token!=null)c.setRequestProperty("__RequestVerificationToken",token);
            OutputStream os=c.getOutputStream();
            os.write(body.getBytes(StandardCharsets.UTF_8));
            os.close();
            return read(c,p);
        }

        LoginResult login(String username,String password,String method)throws Exception{
            String ses=get("/api/webserver/SesTokInfo");
            String state="";
            try{ state=get("/api/user/state-login"); }catch(Exception e){ state="state-login error: "+e.getMessage(); }
            Map<String,String> sm=map(ses);
            String tok=sm.get("TokInfo"); if(tok==null)tok=token; if(tok==null)tok="";
            String report="METHOD: "+method+"\nSesTokInfo: "+plain(ses)+"\nstate-login: "+plain(state)+"\n";
            String encoded; String type="4";

            if("hmanager_auto".equals(method) || "hmanager_type4_hex".equals(method)){
                String inner=b64Text(sha256Hex(password));
                encoded=b64Text(sha256Hex(username+inner+tok));
                type="4";
            }else if("hmanager_base64_plain".equals(method)){
                encoded=b64Text(sha256Hex(password));
                type="4";
            }else if("legacy_type4".equals(method)){
                encoded=b64(sha256(username+b64(sha256(password))+tok));
                type="4";
            }else if("hash_no_user".equals(method)){
                encoded=b64(sha256(b64(sha256(password))+tok));
                type="4";
            }else if("plain_type0".equals(method)){
                encoded=password; type="0";
            }else if("plain_type4".equals(method)){
                encoded=password; type="4";
            }else{
                String inner=b64Text(sha256Hex(password));
                encoded=b64Text(sha256Hex(username+inner+tok));
                type="4";
            }

            String body="<request><Username>"+esc(username)+"</Username><Password>"+esc(encoded)+"</Password><password_type>"+type+"</password_type></request>";
            try{
                String res=post("/api/user/login",body);
                String pl=plain(res);
                report+="login response: "+pl+"\n";
                if(pl.contains("108007"))return new LoginResult(false,true,"108007",report);
                if(pl.contains("108006"))return new LoginResult(false,false,"108006",report+"DIAGNOSIS: 108006 رفض طريقة الدخول أو البيانات.\n");
                if(pl.toLowerCase(Locale.US).contains("ok")||pl.contains("OK"))return new LoginResult(true,false,"OK",report);
                return new LoginResult(false,false,"Rejected",report);
            }catch(Exception e){
                String msg=e.getMessage()==null?"":e.getMessage();
                report+="login exception: "+msg+"\n";
                return new LoginResult(false,msg.contains("108007"),msg,report);
            }
        }

        HttpURLConnection open(String p,String method)throws Exception{
            HttpURLConnection c=(HttpURLConnection)new URL(base+p).openConnection();
            c.setRequestMethod(method);
            c.setConnectTimeout(8000);
            c.setReadTimeout(12000);
            c.setRequestProperty("User-Agent","SignalPlusAndroid/2.0");
            c.setRequestProperty("Accept","application/xml, text/xml, */*");
            if(cookie!=null)c.setRequestProperty("Cookie",cookie);
            if(token!=null)c.setRequestProperty("__RequestVerificationToken",token);
            return c;
        }

        String read(HttpURLConnection c,String path)throws Exception{
            int code=c.getResponseCode();
            capture(c);
            InputStream is=code>=200&&code<400?c.getInputStream():c.getErrorStream();
            String text=all(is);
            if(path.contains("SesTokInfo")){
                Map<String,String> m=map(text);
                if(m.get("SesInfo")!=null)cookie=m.get("SesInfo");
                if(m.get("TokInfo")!=null)token=m.get("TokInfo");
            }
            if(code<200||code>=400)throw new IOException("HTTP "+code+": "+plain(text));
            return text;
        }

        void capture(HttpURLConnection c){
            Map<String,List<String>> h=c.getHeaderFields();
            if(h==null)return;
            List<String> ck=h.get("Set-Cookie");
            if(ck!=null&&!ck.isEmpty())cookie=ck.get(0).split(";",2)[0];
            List<String> tk=h.get("__RequestVerificationToken");
            if(tk!=null&&!tk.isEmpty())token=tk.get(0);
        }

        static String all(InputStream is)throws Exception{
            if(is==null)return "";
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            byte[] buf=new byte[4096]; int n;
            while((n=is.read(buf))>=0)out.write(buf,0,n);
            return out.toString("UTF-8");
        }

        static Map<String,String> map(String xml){
            Map<String,String> m=new LinkedHashMap<>();
            try{
                DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();
                Document d=f.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
                NodeList all=d.getElementsByTagName("*");
                for(int i=0;i<all.getLength();i++){
                    Node n=all.item(i);
                    if(n.getNodeType()==Node.ELEMENT_NODE && n.getChildNodes().getLength()==1){
                        String val=n.getTextContent();
                        if(val!=null && val.trim().length()>0)m.put(n.getNodeName(),val.trim());
                    }
                }
            }catch(Exception ignored){}
            return m;
        }

        static ArrayList<Map<String,String>> hosts(String xml){
            ArrayList<Map<String,String>> list=new ArrayList<>();
            try{
                Document d=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
                NodeList hosts=d.getElementsByTagName("Host");
                for(int i=0;i<hosts.getLength();i++){
                    Node h=hosts.item(i);
                    NodeList ch=h.getChildNodes();
                    Map<String,String> m=new LinkedHashMap<>();
                    for(int j=0;j<ch.getLength();j++){
                        Node n=ch.item(j);
                        if(n.getNodeType()==Node.ELEMENT_NODE)m.put(n.getNodeName(),n.getTextContent().trim());
                    }
                    if(m.size()>0)list.add(m);
                }
            }catch(Exception ignored){}
            return list;
        }

        static byte[] sha256(String s)throws Exception{return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));}
        static String sha256Hex(String s)throws Exception{
            byte[] d=sha256(s);
            StringBuilder sb=new StringBuilder();
            for(byte x:d)sb.append(String.format("%02x",x&0xff));
            return sb.toString();
        }
        static String b64(byte[] b){return Base64.encodeToString(b,Base64.NO_WRAP);}
        static String b64Text(String s){return Base64.encodeToString(s.getBytes(StandardCharsets.UTF_8),Base64.NO_WRAP);}
        static String esc(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");}
        static String plain(String x){return x==null?"":x.replaceAll("<[^>]+>"," ").replaceAll("\\s+"," ").trim();}
    }
}
