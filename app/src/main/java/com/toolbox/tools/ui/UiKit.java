package com.toolbox.tools.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class UiKit {
    public static final int LATAR = Color.rgb(7,16,22);
    public static final int PERMUKAAN = Color.rgb(13,27,36);
    public static final int PERMUKAAN_2 = Color.rgb(17,40,50);
    public static final int GARIS = Color.rgb(36,70,80);
    public static final int NEON = Color.rgb(0,240,181);
    public static final int NEON_BIRU = Color.rgb(76,201,255);
    public static final int TEKS = Color.rgb(232,255,248);
    public static final int TEKS_REDUP = Color.rgb(143,184,174);
    public static final int BAHAYA = Color.rgb(255,107,122);
    public static final int PERINGATAN = Color.rgb(255,209,102);

    public static final int ICON_EDITOR = 1;
    public static final int ICON_APPS = 2;
    public static final int ICON_COMPONENT = 3;
    public static final int ICON_LAYOUT = 4;
    public static final int ICON_FLOW = 5;
    public static final int ICON_DATA = 6;
    public static final int ICON_LINK = 7;
    public static final int ICON_ASSET = 8;
    public static final int ICON_SETTINGS = 9;
    public static final int ICON_SAVE = 10;
    public static final int ICON_HISTORY = 11;
    public static final int ICON_BUILD = 12;
    public static final int ICON_SHIELD = 13;
    public static final int ICON_HEALTH = 14;
    public static final int ICON_CODE = 15;
    public static final int ICON_SLIDERS = 16;
    public static final int ICON_PLAY = 17;
    public static final int ICON_FOLDER = 18;
    public static final int ICON_HOME = 19;
    public static final int ICON_PLUS = 20;
    public static final int ICON_TARGET = 21;

    private UiKit() {}

    public static int dp(Context c, int value) { return Math.round(value * c.getResources().getDisplayMetrics().density); }
    public static int dp(Context c, float value) { return Math.round(value * c.getResources().getDisplayMetrics().density); }

    public static TextView teks(Context c, String text, float sp, int color) {
        TextView v = new TextView(c);
        v.setText(text); v.setTextSize(sp); v.setTextColor(color);
        v.setGravity(Gravity.CENTER_VERTICAL); v.setFontFeatureSettings("kern");
        return v;
    }

    public static TextView judul(Context c, String text, float sp) {
        TextView v = teks(c, text, sp, TEKS);
        v.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return v;
    }

    public static TextView tombol(Context c, String text, boolean aktif) {
        TextView v = judul(c, text, 12.5f);
        v.setGravity(Gravity.CENTER); v.setMinHeight(dp(c, 40));
        v.setPadding(dp(c,12),dp(c,8),dp(c,12),dp(c,8));
        v.setBackground(kartu(aktif ? Color.rgb(9,57,52) : PERMUKAAN_2, aktif ? NEON : GARIS,14,1));
        v.setTextColor(aktif ? NEON : TEKS); v.setClickable(true); v.setFocusable(true);
        return v;
    }

    public static TextView chip(Context c, String text, boolean aktif) {
        TextView v = teks(c,text,11.5f,aktif ? LATAR : TEKS_REDUP);
        v.setGravity(Gravity.CENTER); v.setPadding(dp(c,10),dp(c,6),dp(c,10),dp(c,6));
        v.setBackground(kartu(aktif ? NEON : PERMUKAAN, aktif ? NEON : GARIS,999,1));
        v.setClickable(true); return v;
    }

    public static Drawable menuIcon(Context c, int type, int color) {
        return new MenuIconDrawable(type,color,dp(c,22));
    }

    public static LinearLayout visualTile(Context c, int iconType, String label, boolean active) {
        LinearLayout tile=kolom(c); tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(c,9),dp(c,10),dp(c,9),dp(c,8));
        tile.setBackground(kartuPx(c,active?Color.rgb(9,57,52):PERMUKAAN_2,active?NEON:GARIS,18,1));
        tile.setClickable(true); tile.setFocusable(true); tile.setContentDescription("Menu visual • "+label);
        ImageView icon=new ImageView(c); icon.setImageDrawable(menuIcon(c,iconType,active?NEON:NEON_BIRU));
        tile.addView(icon,new LinearLayout.LayoutParams(dp(c,34),dp(c,34)));
        TextView caption=judul(c,label,10.5f); caption.setGravity(Gravity.CENTER); caption.setMaxLines(2);
        caption.setTextColor(active?NEON:TEKS);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.topMargin=dp(c,7); tile.addView(caption,cp); return tile;
    }

    public static LinearLayout baris(Context c){LinearLayout v=new LinearLayout(c);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    public static LinearLayout kolom(Context c){LinearLayout v=new LinearLayout(c);v.setOrientation(LinearLayout.VERTICAL);return v;}

    public static GradientDrawable kartu(int fill,int stroke,int radiusDp,int strokeDp){
        GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(radiusDp);if(strokeDp>0)d.setStroke(strokeDp,stroke);return d;
    }
    public static GradientDrawable kartuPx(Context c,int fill,int stroke,int radiusDp,int strokeDp){
        GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(c,radiusDp));if(strokeDp>0)d.setStroke(dp(c,strokeDp),stroke);return d;
    }
    public static LinearLayout.LayoutParams lp(int w,int h,float weight,int marginDp,Context c){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w,h,weight);int m=dp(c,marginDp);p.setMargins(m,m,m,m);return p;
    }
    public static FrameLayout.LayoutParams flp(int w,int h,int gravity,int marginDp,Context c){
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(w,h,gravity);int m=dp(c,marginDp);p.setMargins(m,m,m,m);return p;
    }
    public static void ruang(LinearLayout parent,Context c,int dp){
        View v=new View(c);parent.addView(v,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,UiKit.dp(c,dp)));
    }
    public static TextView labelBagian(Context c,String text){
        TextView v=judul(c,text,12f);v.setTextColor(NEON_BIRU);v.setPadding(0,dp(c,6),0,dp(c,6));return v;
    }

    private static final class MenuIconDrawable extends Drawable {
        private final int type; private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG); private final int size;
        MenuIconDrawable(int type,int color,int size){
            this.type=type;this.size=size;paint.setColor(color);paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(2f,size/10f));paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);
        }
        @Override public int getIntrinsicWidth(){return size;}
        @Override public int getIntrinsicHeight(){return size;}
        @Override public void draw(Canvas canvas){
            RectF b=new RectF(getBounds());float w=b.width(),h=b.height(),cx=b.centerX(),cy=b.centerY(),p=Math.max(2f,w*.16f);
            RectF in=new RectF(b.left+p,b.top+p,b.right-p,b.bottom-p);
            switch(type){
                case ICON_APPS:{
                    float s=w*.22f,g=w*.10f;
                    canvas.drawRoundRect(new RectF(cx-g-s,cy-g-s,cx-g,cy-g),3,3,paint);
                    canvas.drawRoundRect(new RectF(cx+g,cy-g-s,cx+g+s,cy-g),3,3,paint);
                    canvas.drawRoundRect(new RectF(cx-g-s,cy+g,cx-g,cy+g+s),3,3,paint);
                    canvas.drawRoundRect(new RectF(cx+g,cy+g,cx+g+s,cy+g+s),3,3,paint);break;}
                case ICON_COMPONENT:
                    canvas.drawRoundRect(in,5,5,paint);canvas.drawLine(cx,cy-h*.18f,cx,cy+h*.18f,paint);canvas.drawLine(cx-w*.18f,cy,cx+w*.18f,cy,paint);break;
                case ICON_LAYOUT:
                    canvas.drawRoundRect(in,4,4,paint);canvas.drawLine(in.left,in.top+h*.24f,in.right,in.top+h*.24f,paint);canvas.drawLine(in.left+w*.28f,in.top+h*.24f,in.left+w*.28f,in.bottom,paint);break;
                case ICON_FLOW:
                    canvas.drawCircle(in.left+w*.12f,cy,w*.08f,paint);canvas.drawCircle(cx,in.top+h*.08f,w*.08f,paint);canvas.drawCircle(in.right-w*.12f,cy,w*.08f,paint);
                    canvas.drawLine(in.left+w*.20f,cy,cx-w*.07f,in.top+h*.10f,paint);canvas.drawLine(cx+w*.07f,in.top+h*.10f,in.right-w*.20f,cy,paint);break;
                case ICON_DATA:
                    canvas.drawOval(new RectF(in.left,in.top,in.right,in.top+h*.26f),paint);canvas.drawLine(in.left,in.top+h*.13f,in.left,in.bottom-h*.13f,paint);
                    canvas.drawLine(in.right,in.top+h*.13f,in.right,in.bottom-h*.13f,paint);canvas.drawOval(new RectF(in.left,in.bottom-h*.26f,in.right,in.bottom),paint);break;
                case ICON_LINK:
                    canvas.drawCircle(cx-w*.18f,cy,w*.15f,paint);canvas.drawCircle(cx+w*.18f,cy,w*.15f,paint);canvas.drawLine(cx-w*.03f,cy,cx+w*.03f,cy,paint);break;
                case ICON_ASSET:
                    canvas.drawRoundRect(in,4,4,paint);canvas.drawCircle(in.right-w*.18f,in.top+h*.18f,w*.06f,paint);canvas.drawLine(in.left+w*.08f,in.bottom-h*.10f,cx,cy,paint);canvas.drawLine(cx,cy,in.right-w*.05f,in.bottom-h*.10f,paint);break;
                case ICON_SETTINGS:
                    canvas.drawCircle(cx,cy,w*.16f,paint);for(int i=0;i<8;i++){double a=i*Math.PI/4;canvas.drawLine(cx+(float)Math.cos(a)*w*.24f,cy+(float)Math.sin(a)*h*.24f,cx+(float)Math.cos(a)*w*.34f,cy+(float)Math.sin(a)*h*.34f,paint);}break;
                case ICON_SAVE:
                    canvas.drawRoundRect(in,3,3,paint);canvas.drawRect(in.left+w*.12f,in.top,in.right-w*.12f,in.top+h*.24f,paint);canvas.drawRect(in.left+w*.15f,cy,in.right-w*.15f,in.bottom,paint);break;
                case ICON_HISTORY:
                    canvas.drawArc(in,45,285,false,paint);canvas.drawLine(in.left,cy,in.left+w*.14f,cy-h*.10f,paint);canvas.drawLine(in.left,cy,in.left+w*.14f,cy+h*.10f,paint);break;
                case ICON_BUILD:
                    canvas.drawRoundRect(in,4,4,paint);canvas.drawLine(cx,in.bottom-h*.08f,cx,in.top+h*.08f,paint);canvas.drawLine(cx,in.top+h*.08f,cx-w*.12f,in.top+h*.20f,paint);canvas.drawLine(cx,in.top+h*.08f,cx+w*.12f,in.top+h*.20f,paint);break;
                case ICON_SHIELD:{
                    Path q=new Path();q.moveTo(cx,in.top);q.lineTo(in.right,in.top+h*.16f);q.lineTo(in.right-w*.08f,in.bottom-h*.18f);q.lineTo(cx,in.bottom);q.lineTo(in.left+w*.08f,in.bottom-h*.18f);q.lineTo(in.left,in.top+h*.16f);q.close();canvas.drawPath(q,paint);break;}
                case ICON_HEALTH:
                    canvas.drawLine(in.left,cy,in.left+w*.22f,cy,paint);canvas.drawLine(in.left+w*.22f,cy,cx-w*.10f,in.top+h*.08f,paint);canvas.drawLine(cx-w*.10f,in.top+h*.08f,cx+w*.08f,in.bottom-h*.08f,paint);canvas.drawLine(cx+w*.08f,in.bottom-h*.08f,in.right-w*.22f,cy,paint);canvas.drawLine(in.right-w*.22f,cy,in.right,cy,paint);break;
                case ICON_CODE:
                    canvas.drawLine(cx-w*.08f,in.top,cx-w*.30f,cy,paint);canvas.drawLine(cx-w*.30f,cy,cx-w*.08f,in.bottom,paint);canvas.drawLine(cx+w*.08f,in.top,cx+w*.30f,cy,paint);canvas.drawLine(cx+w*.30f,cy,cx+w*.08f,in.bottom,paint);break;
                case ICON_SLIDERS:
                    for(int i=0;i<3;i++){float yy=in.top+h*(.12f+i*.25f);canvas.drawLine(in.left,yy,in.right,yy,paint);float xx=i==1?cx+w*.18f:cx-w*.12f;canvas.drawCircle(xx,yy,w*.055f,paint);}break;
                case ICON_PLAY:{
                    Path q=new Path();q.moveTo(cx-w*.15f,cy-h*.22f);q.lineTo(cx+w*.24f,cy);q.lineTo(cx-w*.15f,cy+h*.22f);q.close();canvas.drawPath(q,paint);break;}
                case ICON_FOLDER:
                    canvas.drawRoundRect(new RectF(in.left,in.top+h*.16f,in.right,in.bottom),4,4,paint);canvas.drawLine(in.left,in.top+h*.16f,in.left+w*.28f,in.top+h*.16f,paint);canvas.drawLine(in.left+w*.28f,in.top+h*.16f,in.left+w*.38f,in.top,paint);canvas.drawLine(in.left+w*.38f,in.top,in.right-w*.20f,in.top,paint);break;
                case ICON_HOME:{
                    Path q=new Path();q.moveTo(in.left,cy);q.lineTo(cx,in.top);q.lineTo(in.right,cy);q.lineTo(in.right-w*.10f,in.bottom);q.lineTo(in.left+w*.10f,in.bottom);q.close();canvas.drawPath(q,paint);break;}
                case ICON_PLUS:
                    canvas.drawCircle(cx,cy,w*.31f,paint);canvas.drawLine(cx,cy-h*.16f,cx,cy+h*.16f,paint);canvas.drawLine(cx-w*.16f,cy,cx+w*.16f,cy,paint);break;
                case ICON_TARGET:
                    canvas.drawCircle(cx,cy,w*.31f,paint);canvas.drawCircle(cx,cy,w*.15f,paint);canvas.drawCircle(cx,cy,w*.03f,paint);break;
                case ICON_EDITOR:
                default:
                    canvas.drawRoundRect(in,4,4,paint);canvas.drawLine(in.left+w*.18f,in.bottom-h*.15f,in.right-w*.10f,in.top+h*.10f,paint);canvas.drawLine(in.right-w*.16f,in.top+h*.04f,in.right-w*.04f,in.top+h*.16f,paint);break;
            }
        }
        @Override public void setAlpha(int alpha){paint.setAlpha(alpha);invalidateSelf();}
        @Override public void setColorFilter(ColorFilter f){paint.setColorFilter(f);invalidateSelf();}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
}
