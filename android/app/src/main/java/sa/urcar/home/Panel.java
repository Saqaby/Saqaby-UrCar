package sa.urcar.home;

import android.content.Context;
import android.graphics.Typeface;
import android.media.session.MediaController;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The panel on the driver's side, as in the preview: clock and temperature, the Hijri and Gregorian dates and the
 * place, the adhan bar, the card (the next prayer / the music), the Quran radio, and four buttons. Built in code;
 * the screen is 1280×720 at density 1.0, so sizes are pixels of a 720-high panel (H = 1% of it, the preview's cqh).
 */
final class Panel extends LinearLayout {
    interface Actions { void youtube(); void bluetooth(); void settings(); void apps(); void radio(); void khatma(); void openMusic(); void library(); }

    private final Actions act;
    private final float H;
    private Ui.Theme th;
    private boolean day;

    private LinearLayout ramRow;
    private TextView ramL, ramN, ramU;
    private TextView clock, ap, temp, hijri, greg, place, bar, tabP, tabM, tabW, wTemp, wSky, wLine1, wLine2, nm, tm, cd, ram, mApp, mTitle, mArtist, mEmpty, rT, rF;
    private Ui.Icon wIcon, wxIcon, pinIcon, rIcon, playIcon, prevIcon, nextIcon;
    private LinearLayout card, prayerPage, musicPage, weatherPage, wHours, musicOn, radioBtn, kBtn, keysRow, tabs;
    private TextView kT, kF;
    private Ui.Icon kIcon;
    private boolean kOn, libOn;
    private LinearLayout libBtn;
    private Ui.Icon libIcon;
    private final LinearLayout[] keys = new LinearLayout[4];
    private final Ui.Icon[] keyIcons = new Ui.Icon[4];
    private final TextView[] keyLabels = new TextView[4];
    private View playBtn, prevBtn, nextBtn;
    private String page;
    MediaController media;

    Panel(Context c, Actions act, int height) {
        super(c);
        this.act = act;
        H = height / 100f;
        page = Prefs.page(c);
        setOrientation(VERTICAL);
        setLayoutDirection(LAYOUT_DIRECTION_RTL);
        setPadding(px(2.6f), px(2), px(2.6f), px(2));
        build(c);
        setDay(false);
    }
    private int px(float h) { return Math.round(h * H); }

    private void build(Context c) {
        Typeface reg = Ui.regular(c), med = Ui.medium(c), disp = Ui.display(c);
        // ---- clock and temperature
        LinearLayout r1 = row();
        clock = Ui.text(c, px(6.2f), disp, 0); clock.setTextDirection(TEXT_DIRECTION_LTR);
        ap = Ui.text(c, px(3), reg, 0); ap.setPadding(px(1.2f), 0, 0, 0);
        temp = Ui.text(c, px(4), med, 0);
        wxIcon = new Ui.Icon(c, Ui.Icon.SUN, 0);
        r1.addView(clock); r1.addView(ap); r1.addView(spacer()); r1.addView(wxIcon, new LayoutParams(px(4.4f), px(4.4f))); r1.addView(temp);
        temp.setOnClickListener(v -> showPage("w"));
        wxIcon.setOnClickListener(v -> showPage("w"));
        addView(r1);
        hijri = Ui.text(c, px(3.3f), med, 0);
        addView(hijri, lp(px(0.6f)));
        LinearLayout r3 = row();
        greg = Ui.text(c, px(2.7f), reg, 0);
        pinIcon = new Ui.Icon(c, Ui.Icon.PIN, 0);
        place = Ui.text(c, px(2.7f), reg, 0);
        r3.addView(greg); r3.addView(spacer()); r3.addView(pinIcon, new LayoutParams(px(2.9f), px(2.9f))); r3.addView(place);
        addView(r3, lp(px(0.6f)));

        // ---- the adhan bar
        bar = Ui.text(c, px(3.6f), med, 0);
        bar.setGravity(Gravity.CENTER);
        bar.setVisibility(GONE);
        addView(bar, new LayoutParams(LayoutParams.MATCH_PARENT, px(7)) {{ topMargin = px(1.6f); }});

        // ---- the card: tabs, then the prayer page or the music page
        card = new LinearLayout(c);
        card.setOrientation(VERTICAL);
        card.setPadding(px(2.4f), px(1.2f), px(2.4f), px(1.8f));
        tabs = row();
        tabP = Ui.text(c, px(2.9f), reg, 0); tabP.setPadding(px(1.8f), px(1), px(1.8f), px(1));
        tabM = Ui.text(c, px(2.9f), reg, 0); tabM.setPadding(px(1.8f), px(1), px(1.8f), px(1));
        tabW = Ui.text(c, px(2.9f), reg, 0); tabW.setPadding(px(1.8f), px(1), px(1.8f), px(1));
        tabW.setText("الطقس");
        tabP.setOnClickListener(v -> showPage("p"));
        tabM.setOnClickListener(v -> showPage("m"));
        tabW.setOnClickListener(v -> showPage("w"));
        tabs.addView(tabP); tabs.addView(tabM); tabs.addView(tabW);
        card.addView(tabs);
        FrameLayout pages = new FrameLayout(c);

        prayerPage = new LinearLayout(c);
        prayerPage.setOrientation(VERTICAL);
        LinearLayout pr = row();
        nm = Ui.text(c, px(6), med, 0);
        tm = Ui.text(c, px(4.4f), reg, 0);
        pr.addView(nm); pr.addView(spacer()); pr.addView(tm);
        prayerPage.addView(pr, lp(px(1)));
        cd = Ui.text(c, px(4.2f), med, 0);
        prayerPage.addView(cd, lp(px(0.6f)));
        ram = Ui.text(c, px(2.6f), reg, 0);
        prayerPage.addView(ram, lp(px(1.6f)));
        ramRow = row();   // «باقٍ على رمضان  ٩٥  يومًا»: the number big
        ramL = Ui.text(c, px(3), reg, 0); ramL.setText("باقٍ على رمضان");
        ramN = Ui.text(c, px(6.4f), disp, 0); ramN.setPadding(px(1.6f), 0, px(1.2f), 0);
        ramU = Ui.text(c, px(3), reg, 0);
        ramRow.addView(ramL); ramRow.addView(ramN); ramRow.addView(ramU);
        prayerPage.addView(ramRow, lp(px(1.4f)));
        pages.addView(prayerPage);

        musicPage = new LinearLayout(c);
        musicPage.setOrientation(VERTICAL);
        musicOn = new LinearLayout(c);
        musicOn.setOrientation(VERTICAL);
        mApp = Ui.text(c, px(2.5f), reg, 0);
        mTitle = Ui.text(c, px(3.6f), med, 0); mTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        mArtist = Ui.text(c, px(2.8f), reg, 0); mArtist.setEllipsize(android.text.TextUtils.TruncateAt.END);
        musicOn.addView(mApp, lp(px(1)));
        musicOn.addView(mTitle, lp(px(0.8f)));
        musicOn.addView(mArtist, lp(px(0.6f)));
        musicOn.setOnClickListener(v -> act.openMusic());
        LinearLayout ctl = row();
        ctl.setLayoutDirection(LAYOUT_DIRECTION_LTR);
        ctl.setGravity(Gravity.CENTER);
        prevIcon = new Ui.Icon(c, Ui.Icon.PREV, 0); playIcon = new Ui.Icon(c, Ui.Icon.PAUSE, 0); nextIcon = new Ui.Icon(c, Ui.Icon.NEXT, 0);
        prevBtn = round(c, prevIcon, px(7)); playBtn = round(c, playIcon, px(8.4f)); nextBtn = round(c, nextIcon, px(7));
        prevBtn.setOnClickListener(v -> { if (media != null) media.getTransportControls().skipToPrevious(); });
        nextBtn.setOnClickListener(v -> { if (media != null) media.getTransportControls().skipToNext(); });
        playBtn.setOnClickListener(v -> { if (media == null) return;
            if (NotiListener.playing(media)) media.getTransportControls().pause(); else media.getTransportControls().play(); });
        ctl.addView(prevBtn); ctl.addView(playBtn); ctl.addView(nextBtn);
        musicOn.addView(ctl, lp(px(1.6f)));
        musicPage.addView(musicOn);
        mEmpty = Ui.text(c, px(3), reg, 0);
        mEmpty.setSingleLine(false);
        mEmpty.setOnClickListener(v -> act.openMusic());
        musicPage.addView(mEmpty, lp(px(1.4f)));
        pages.addView(musicPage);

        // ---- the weather page: now, today, the next five hours (Open-Meteo)
        weatherPage = new LinearLayout(c);
        weatherPage.setOrientation(VERTICAL);
        LinearLayout wr = row();
        wIcon = new Ui.Icon(c, Ui.Icon.SUN, 0);
        wr.addView(wIcon, new LayoutParams(px(7), px(7)));
        wTemp = Ui.text(c, px(7), disp, 0);
        wTemp.setPadding(px(1.4f), 0, px(1.4f), 0);
        wr.addView(wTemp);
        wSky = Ui.text(c, px(3.4f), med, 0);
        wr.addView(wSky);
        weatherPage.addView(wr, lp(px(0.6f)));
        wLine1 = Ui.text(c, px(2.6f), reg, 0);
        wLine2 = Ui.text(c, px(2.6f), reg, 0);
        weatherPage.addView(wLine1, lp(px(0.8f)));
        weatherPage.addView(wLine2, lp(px(0.4f)));
        wHours = row();
        for (int i = 0; i < 5; i++) {
            LinearLayout hcell = new LinearLayout(c);
            hcell.setOrientation(VERTICAL);
            hcell.setGravity(Gravity.CENTER);
            TextView hh = Ui.text(c, px(2.3f), reg, 0);
            Ui.Icon hi = new Ui.Icon(c, Ui.Icon.SUN, 0);
            TextView ht = Ui.text(c, px(2.6f), med, 0);
            hcell.addView(hh); hcell.addView(hi, new LayoutParams(px(3.6f), px(3.6f))); hcell.addView(ht);
            wHours.addView(hcell, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        }
        weatherPage.addView(wHours, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT) {{ topMargin = px(1.4f); }});
        pages.addView(weatherPage);
        card.addView(pages);
        card.setOnTouchListener(new SwipeTouch());
        addView(card, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1) {{ topMargin = px(1.6f); }});

        // ---- the Quran radio and the car's khatma, side by side: a title and a line under it each
        LinearLayout quran = row();
        radioBtn = row();
        radioBtn.setPadding(px(2), 0, px(2), 0);
        rIcon = new Ui.Icon(c, Ui.Icon.RADIO, 0);
        LinearLayout rTx = new LinearLayout(c); rTx.setOrientation(VERTICAL); rTx.setPadding(px(1.2f), 0, 0, 0);
        rT = Ui.text(c, px(3), med, 0);
        rF = Ui.text(c, px(2.5f), reg, 0);
        rTx.addView(rT); rTx.addView(rF);
        radioBtn.addView(rIcon, new LayoutParams(px(4.2f), px(4.2f)));
        radioBtn.addView(rTx);
        radioBtn.setOnClickListener(v -> act.radio());
        kBtn = row();
        kBtn.setPadding(px(2), 0, px(2), 0);
        kIcon = new Ui.Icon(c, Ui.Icon.PLAY, 0);
        LinearLayout kTx = new LinearLayout(c); kTx.setOrientation(VERTICAL); kTx.setPadding(px(1.2f), 0, 0, 0);
        kT = Ui.text(c, px(3), med, 0);
        kF = Ui.text(c, px(2.5f), reg, 0);
        kF.setEllipsize(android.text.TextUtils.TruncateAt.END);
        kTx.addView(kT); kTx.addView(kF);
        kBtn.addView(kIcon, new LayoutParams(px(4.2f), px(4.2f)));
        kBtn.addView(kTx);
        kBtn.setOnClickListener(v -> act.khatma());
        quran.addView(radioBtn, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1));
        LayoutParams kl = new LayoutParams(0, LayoutParams.MATCH_PARENT, 1);
        kl.setMarginStart(px(1.4f));
        quran.addView(kBtn, kl);
        libBtn = row();   // the audio library: a square with headphones
        libBtn.setGravity(Gravity.CENTER);
        libIcon = new Ui.Icon(c, Ui.Icon.PHONES, 0);
        libBtn.addView(libIcon, new LayoutParams(px(5), px(5)));
        libBtn.setOnClickListener(v -> act.library());
        LayoutParams ll = new LayoutParams(px(10), LayoutParams.MATCH_PARENT);
        ll.setMarginStart(px(1.4f));
        quran.addView(libBtn, ll);
        addView(quran, new LayoutParams(LayoutParams.MATCH_PARENT, px(10)) {{ topMargin = px(1.6f); }});

        // ---- the four buttons
        keysRow = row();
        int[] icons = {Ui.Icon.YOUTUBE, Ui.Icon.BT, Ui.Icon.GEAR, Ui.Icon.APPS};
        String[] labels = {"يوتيوب", "بلوتوث", "الإعدادات", "التطبيقات"};
        Runnable[] runs = {act::youtube, act::bluetooth, act::settings, act::apps};
        for (int i = 0; i < 4; i++) {
            LinearLayout k = new LinearLayout(c);
            k.setOrientation(VERTICAL);
            k.setGravity(Gravity.CENTER);
            keyIcons[i] = new Ui.Icon(c, icons[i], 0);
            keyLabels[i] = Ui.text(c, px(2.7f), med, 0);
            keyLabels[i].setText(labels[i]);
            k.addView(keyIcons[i], new LayoutParams(px(6.4f), px(6.4f)));
            k.addView(keyLabels[i], lp(px(1)));
            final Runnable r = runs[i];
            k.setOnClickListener(v -> r.run());
            LayoutParams l = new LayoutParams(0, LayoutParams.MATCH_PARENT, 1);
            if (i > 0) l.setMarginStart(px(1.4f));
            keysRow.addView(k, l);
            keys[i] = k;
        }
        addView(keysRow, new LayoutParams(LayoutParams.MATCH_PARENT, px(15)) {{ topMargin = px(1.6f); }});
        showPage(page);
    }

    private LinearLayout row() { LinearLayout r = new LinearLayout(getContext()); r.setOrientation(HORIZONTAL); r.setGravity(Gravity.CENTER_VERTICAL); return r; }
    private View spacer() { View v = new View(getContext()); v.setLayoutParams(new LayoutParams(0, 1, 1)); return v; }
    private LayoutParams lp(int top) { LayoutParams l = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT); l.topMargin = top; return l; }
    private View round(Context c, Ui.Icon icon, int size) {
        FrameLayout f = new FrameLayout(c);
        f.addView(icon, new FrameLayout.LayoutParams(size / 2, size / 2, Gravity.CENTER));
        LayoutParams l = new LayoutParams(size, size);
        l.setMargins(px(1.5f), 0, px(1.5f), 0);
        f.setLayoutParams(l);
        return f;
    }

    void showPage(String p) {
        page = p;
        Prefs.put(getContext(), "page", p);
        prayerPage.setVisibility(p.equals("p") ? VISIBLE : INVISIBLE);
        musicPage.setVisibility(p.equals("m") ? VISIBLE : INVISIBLE);
        weatherPage.setVisibility(p.equals("w") ? VISIBLE : INVISIBLE);
        paintTabs();
    }

    /** A horizontal swipe on the card turns the page, as in the preview. */
    private final class SwipeTouch implements OnTouchListener {
        float x0, y0;
        @Override public boolean onTouch(View v, MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_DOWN) { x0 = e.getX(); y0 = e.getY(); return true; }
            if (e.getAction() == MotionEvent.ACTION_UP) {
                float dx = e.getX() - x0, dy = e.getY() - y0;
                if (Math.abs(dx) > px(6) && Math.abs(dx) > Math.abs(dy)) showPage(page.equals("p") ? "m" : page.equals("m") ? "w" : "p");
                return true;
            }
            return true;
        }
    }

    // ---------------------------------------------------------------- the look
    void setDay(boolean d) {
        day = d;
        th = new Ui.Theme(d);
        setBackgroundColor(th.bg);
        clock.setTextColor(th.fg); ap.setTextColor(th.muted); temp.setTextColor(th.fg); wxIcon.set(wxIcon.kind, th.sand);
        wTemp.setTextColor(th.fg); wSky.setTextColor(th.sand); wLine1.setTextColor(th.muted); wLine2.setTextColor(th.muted);
        for (int i = 0; i < wHours.getChildCount(); i++) {
            LinearLayout cell = (LinearLayout) wHours.getChildAt(i);
            ((TextView) cell.getChildAt(0)).setTextColor(th.muted); ((TextView) cell.getChildAt(2)).setTextColor(th.fg);
        }
        hijri.setTextColor(th.sand); greg.setTextColor(th.muted); place.setTextColor(th.muted); pinIcon.set(Ui.Icon.PIN, th.muted);
        bar.setBackground(Ui.box(th.sand, 0, px(1.6f))); bar.setTextColor(th.sandInk);
        card.setBackground(Ui.box(th.panel, th.line, px(2.6f)));
        nm.setTextColor(th.fg); tm.setTextColor(th.muted); cd.setTextColor(th.sand); ram.setTextColor(th.muted); ramL.setTextColor(th.muted); ramN.setTextColor(th.sand); ramU.setTextColor(th.muted);
        mApp.setTextColor(th.muted); mTitle.setTextColor(th.fg); mArtist.setTextColor(th.muted); mEmpty.setTextColor(th.muted);
        prevBtn.setBackground(Ui.box(th.bg, th.line, px(5))); nextBtn.setBackground(Ui.box(th.bg, th.line, px(5)));
        playBtn.setBackground(Ui.box(th.sand, 0, px(5)));
        prevIcon.set(Ui.Icon.PREV, th.fg); nextIcon.set(Ui.Icon.NEXT, th.fg); playIcon.set(playIcon.kind, th.sandInk);
        for (int i = 0; i < 4; i++) {
            keys[i].setBackground(Ui.box(th.panel, th.line, px(2.4f)));
            keyIcons[i].set(keyIcons[i].kind, th.sand);
            keyLabels[i].setTextColor(th.fg);
        }
        paintRadio(radioOn);
        paintTabs();
    }
    boolean isDay() { return day; }

    private void paintTabs() {
        if (th == null) return;
        tabP.setBackground(page.equals("p") ? Ui.box(th.panel2, 0, px(3)) : null);
        tabM.setBackground(page.equals("m") ? Ui.box(th.panel2, 0, px(3)) : null);
        tabP.setTextColor(page.equals("p") ? th.fg : th.muted);
        tabM.setTextColor(page.equals("m") ? th.fg : th.muted);
        tabW.setBackground(page.equals("w") ? Ui.box(th.panel2, 0, px(3)) : null);
        tabW.setTextColor(page.equals("w") ? th.fg : th.muted);
    }

    private boolean radioOn;
    void paintRadio(boolean on) {
        radioOn = on;
        radioBtn.setBackground(on ? Ui.box(th.sand, 0, px(2)) : Ui.box(th.panel, th.line, px(2)));
        rIcon.set(Ui.Icon.RADIO, on ? th.sandInk : th.sand);
        rT.setTextColor(on ? th.sandInk : th.fg);
        rF.setTextColor(on ? th.sandInk : th.muted);
        kBtn.setBackground(kOn ? Ui.box(th.sand, 0, px(2)) : Ui.box(th.panel, th.line, px(2)));
        kIcon.set(kOn ? Ui.Icon.PAUSE : Ui.Icon.PLAY, kOn ? th.sandInk : th.sand);
        kT.setTextColor(kOn ? th.sandInk : th.fg);
        kF.setTextColor(kOn ? th.sandInk : th.muted);
        libBtn.setBackground(libOn ? Ui.box(th.sand, 0, px(2)) : Ui.box(th.panel, th.line, px(2)));
        libIcon.set(Ui.Icon.PHONES, libOn ? th.sandInk : th.sand);
    }
    /** The library's button turns gold while one of its surahs plays. */
    void showLibrary(boolean on) { if (on != libOn) { libOn = on; paintRadio(radioOn); } }
    /** «ختمة السيارة»: «الختمة» or «إيقاف الختمة», and «٣٧٪، يوسف» under it. */
    void showKhatma(String label, String line, boolean on) {
        set(kT, label); set(kF, line);
        if (on != kOn) { kOn = on; paintRadio(radioOn); }
    }

    // ---------------------------------------------------------------- the content, every second
    void showTime(String clockText, String apText, String hijriText, String gregText) {
        set(clock, clockText); set(ap, apText); set(hijri, hijriText); set(greg, gregText);
    }
    void showPlace(String name) { set(place, name); pinIcon.setVisibility(name.isEmpty() ? GONE : VISIBLE); }
    /** The weather page; w null: «يُحدَّث الطقس…». */
    void showForecast(Net.Wx w, boolean night) {
        if (weatherPage == null) return;
        if (w == null) { set(wTemp, ""); set(wSky, "يُحدَّث الطقس عند الاتصال بالإنترنت"); set(wLine1, ""); set(wLine2, ""); wHours.setVisibility(GONE); return; }
        wIcon.set(Ui.wxIcon(w.code, night), th.sand);
        set(wTemp, Ui.ar(Math.round(w.temp)) + "°");
        set(wSky, Net.sky(w.code));
        set(wLine1, "الإحساس " + Ui.ar(Math.round(w.feels)) + "°" + (w.hum >= 0 ? "  ·  الرطوبة " + Ui.ar(w.hum) + "٪" : "")
                + (w.wind >= 0 ? "  ·  الرياح " + Ui.ar(Math.round(w.wind)) + " كم/س" : ""));
        set(wLine2, "العظمى " + Ui.ar(Math.round(w.max)) + "°  ·  الصغرى " + Ui.ar(Math.round(w.min)) + "°"
                + (w.rain >= 0 ? "  ·  المطر " + Ui.ar(w.rain) + "٪" : ""));
        wHours.setVisibility(VISIBLE);
        for (int i = 0; i < 5; i++) {
            LinearLayout cell = (LinearLayout) wHours.getChildAt(i);
            int h = w.hour[i], h12 = h % 12 == 0 ? 12 : h % 12;
            set((TextView) cell.getChildAt(0), Ui.ar(h12) + (h < 12 ? " ص" : " م"));
            ((Ui.Icon) cell.getChildAt(1)).set(Ui.wxIcon(w.hcode[i], h < 6 || h >= 18), th.sand);
            set((TextView) cell.getChildAt(2), Ui.ar(Math.round(w.htemp[i])) + "°");
        }
    }

    void showWeather(String t, int icon, boolean stale) {
        set(temp, t);
        wxIcon.set(icon, th.sand);
        wxIcon.setVisibility(t.isEmpty() ? GONE : VISIBLE);
        temp.setTextColor(stale ? th.muted : th.fg);
    }
    void showBar(String text) {
        bar.setVisibility(text == null ? GONE : VISIBLE);
        if (text != null) set(bar, text);
    }
    private boolean nowState;
    void showPrayer(String label, String name, String time, String count, String ramadan, int ramadanDays, boolean now) {
        set(tabP, label); set(nm, name); set(tm, time); set(cd, count);
        ramRow.setVisibility(ramadanDays > 0 ? VISIBLE : GONE);
        if (ramadanDays > 0) { set(ramN, Ui.ar(ramadanDays)); set(ramU, ramadanDays >= 3 && ramadanDays <= 10 ? "أيام" : "يومًا"); }
        set(ram, ramadanDays > 0 ? "" : ramadan);   // during Ramadan: iftar and imsak
        ram.setVisibility(ramadanDays > 0 || ramadan.isEmpty() ? GONE : VISIBLE);
        if (now != nowState) { nowState = now; card.setBackground(Ui.box(now ? th.panel2 : th.panel, now ? th.sand : th.line, px(2.6f))); }
    }
    void showMusic(MediaController mc, String appName, String title, String artist, boolean playing) {
        media = mc;
        set(tabM, mc == null ? "الموسيقى" : appName + (playing ? "  ●" : ""));
        musicOn.setVisibility(mc == null ? GONE : VISIBLE);
        mEmpty.setVisibility(mc == null ? VISIBLE : GONE);
        if (mc == null) { set(mEmpty, NotiListener.allowed(getContext()) ? "لا يعمل صوت الآن. اضغط لفتح آخر تطبيق صوت." : "لعرض الموسيقى: اضغط هنا وفعّل «سيارتك» في «الوصول إلى الإشعارات»."); return; }
        set(mApp, appName); set(mTitle, title.isEmpty() ? appName : title); set(mArtist, artist);
        playIcon.set(playing ? Ui.Icon.PAUSE : Ui.Icon.PLAY, th.sandInk);
    }
    void showRadio(String label, String freq, boolean on) {
        set(rT, label); set(rF, freq);
        if (on != radioOn) paintRadio(on);
    }

    private static void set(TextView t, String s) { if (!s.contentEquals(t.getText())) t.setText(s); }
}
