package io.github.nannxi.epaper;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.nfc.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MainActivity extends Activity implements NfcAdapter.ReaderCallback {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final AtomicBoolean running=new AtomicBoolean(false),cancelled=new AtomicBoolean(false);
    private NfcAdapter nfc;private CanvasView canvas;private Bitmap source;
    private TextView status,brightnessLabel,contrastLabel;private SeekBar brightness,contrast,scale;
    private Spinner dither,colorMode;private Button upload;private byte[] pending;
    private AlertDialog dialog;private TextView dialogText;private ProgressBar progress;
    private boolean waiting;private float zoom=1,panX,panY;private int[] pixels;
    private int dp(float value){return (int)(getResources().getDisplayMetrics().density*value+.5f);}
    private TextView text(String value,int size,int ink){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(ink);return v;}
    private LinearLayout vertical(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);nfc=NfcAdapter.getDefaultAdapter(this);
        getWindow().setStatusBarColor(Color.rgb(17,24,39));getWindow().setNavigationBarColor(Color.rgb(246,247,249));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        LinearLayout root=vertical();root.setPadding(dp(22),dp(22),dp(22),dp(30));root.setBackgroundColor(Color.rgb(246,247,249));scroll.addView(root);
        root.addView(text("EPAPER / NFC · 0.1.2",13,Color.rgb(97,107,125)));
        TextView title=text("把图片带到墨水屏",27,Color.rgb(17,24,39));title.setPadding(0,dp(9),0,dp(9));root.addView(title);
        root.addView(text("212 × 104 像素 · 黑 / 白 / 红",14,Color.rgb(97,107,125)));
        canvas=new CanvasView();LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(205));cp.topMargin=dp(22);root.addView(canvas,cp);
        TextView hint=text("拖动画面调整裁切 · 预览按真实像素比例显示",12,Color.rgb(97,107,125));hint.setPadding(0,dp(8),0,dp(12));root.addView(hint);
        LinearLayout buttons=new LinearLayout(this);
        Button choose=new Button(this);choose.setText("选择图片");choose.setAllCaps(false);choose.setOnClickListener(v->choose());buttons.addView(choose,new LinearLayout.LayoutParams(0,dp(50),1));
        Button rotate=new Button(this);rotate.setText("旋转 90°");rotate.setOnClickListener(v->rotate());buttons.addView(rotate,new LinearLayout.LayoutParams(0,dp(50),1));root.addView(buttons);
        root.addView(text("裁切缩放",14,Color.rgb(17,24,39)));
        scale=new SeekBar(this);scale.setMax(200);root.addView(scale);scale.setOnSeekBarChangeListener(new SimpleSeek(){public void onProgressChanged(SeekBar b,int value,boolean user){zoom=1+value/100f;update();}});
        brightnessLabel=text("亮度  0",14,Color.rgb(17,24,39));root.addView(brightnessLabel);brightness=new SeekBar(this);brightness.setMax(160);brightness.setProgress(80);root.addView(brightness);
        contrastLabel=text("对比度  0",14,Color.rgb(17,24,39));root.addView(contrastLabel);contrast=new SeekBar(this);contrast.setMax(160);contrast.setProgress(60);root.addView(contrast);
        SimpleSeek adjustment=new SimpleSeek(){public void onProgressChanged(SeekBar b,int value,boolean user){brightnessLabel.setText("亮度  "+(brightness.getProgress()-80));contrastLabel.setText("对比度  "+(contrast.getProgress()-60));update();}};
        brightness.setOnSeekBarChangeListener(adjustment);contrast.setOnSeekBarChangeListener(adjustment);
        root.addView(text("抖动方式",14,Color.rgb(17,24,39)));dither=spinner(new String[]{"无抖动 · 文字与图标","有序抖动 · 规则颗粒","误差扩散 · 照片层次"});root.addView(dither);
        root.addView(text("颜色",14,Color.rgb(17,24,39)));colorMode=spinner(new String[]{"黑白红三色","仅黑白"});root.addView(colorMode);
        AdapterView.OnItemSelectedListener selection=new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> a,View v,int i,long id){update();}public void onNothingSelected(AdapterView<?> a){}};
        dither.setOnItemSelectedListener(selection);colorMode.setOnItemSelectedListener(selection);
        status=text("选图并调整预览，满意后点击上传。",13,Color.rgb(97,107,125));status.setPadding(0,dp(15),0,dp(12));root.addView(status);
        upload=new Button(this);upload.setText("上传到墨水屏");upload.setTextColor(Color.WHITE);upload.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(209,30,50)));root.addView(upload,new LinearLayout.LayoutParams(-1,dp(54)));upload.setOnClickListener(v->startUpload());
        TextView note=text("主板需要供电。三色屏刷新约 15 秒，实物颜色与手机预览会有差异。",12,Color.rgb(97,107,125));note.setPadding(0,dp(13),0,0);root.addView(note);
        root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(dp(22),dp(22)+insets.getSystemWindowInsetTop(),dp(22),dp(30)+insets.getSystemWindowInsetBottom());return insets;});setContentView(scroll);makeSample();update();
        if(nfc==null){status.setText("这台手机没有 NFC，仍可编辑和预览图片。");upload.setEnabled(false);}
    }
    private Spinner spinner(String[] items){Spinner s=new Spinner(this);ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,items);s.setAdapter(a);s.setMinimumHeight(dp(48));return s;}
    private void makeSample(){source=Bitmap.createBitmap(424,208,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(source);c.drawColor(Color.WHITE);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(Color.rgb(220,28,45));c.drawRect(0,104,424,208,p);p.setColor(Color.BLACK);p.setTextSize(40);p.setTypeface(Typeface.create(Typeface.MONOSPACE,Typeface.BOLD));c.drawText("Hello World",65,65,p);p.setColor(Color.WHITE);p.setTextSize(30);c.drawText("2.13 inch e-Paper",65,165,p);}
    private void choose(){if(running.get()||waiting)return;Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,10);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=10||result!=RESULT_OK||data==null)return;Uri uri=data.getData();worker.execute(()->{try{
        BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;try(InputStream in=getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,o);}if(o.outWidth<=0||o.outHeight<=0)throw new IOException("无法识别图片");o.inSampleSize=1;while(o.outWidth/o.inSampleSize>2400||o.outHeight/o.inSampleSize>2400)o.inSampleSize*=2;o.inJustDecodeBounds=false;Bitmap b;try(InputStream in=getContentResolver().openInputStream(uri)){b=BitmapFactory.decodeStream(in,null,o);}if(b==null)throw new IOException("无法打开图片");runOnUiThread(()->{source=b;panX=panY=0;zoom=1;scale.setProgress(0);status.setText("拖动和缩放调整裁切，预览即上传内容。");update();});
    }catch(Exception e){runOnUiThread(()->status.setText("选图失败："+e.getMessage()));}});}
    private void rotate(){if(running.get()||waiting||source==null)return;Matrix m=new Matrix();m.postRotate(90);source=Bitmap.createBitmap(source,0,0,source.getWidth(),source.getHeight(),m,true);panX=panY=0;update();}
    private void update(){if(source==null||dither==null||colorMode==null)return;Bitmap fitted=Bitmap.createBitmap(212,104,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(fitted);c.drawColor(Color.WHITE);float factor=Math.max(212f/source.getWidth(),104f/source.getHeight())*zoom;float w=source.getWidth()*factor,h=source.getHeight()*factor;panX=Math.max(-(w-212)/2,Math.min((w-212)/2,panX));panY=Math.max(-(h-104)/2,Math.min((h-104)/2,panY));float left=(212-w)/2+panX,top=(104-h)/2+panY;c.drawBitmap(source,null,new RectF(left,top,left+w,top+h),new Paint(Paint.FILTER_BITMAP_FLAG));int[] input=new int[212*104];fitted.getPixels(input,0,212,0,0,212,104);pixels=ImageProcessor.convert(input,brightness.getProgress()-80,contrast.getProgress()-60,dither.getSelectedItemPosition(),colorMode.getSelectedItemPosition()==0);fitted.setPixels(pixels,0,212,0,0,212,104);canvas.preview=fitted;canvas.invalidate();}
    private void startUpload(){if(running.get()||waiting)return;if(nfc==null)return;if(!nfc.isEnabled()){new AlertDialog.Builder(this).setTitle("请先开启 NFC").setMessage("开启后回到这里，再点击上传。").setPositiveButton("打开设置",(d,w)->startActivity(new Intent(Settings.ACTION_NFC_SETTINGS))).setNegativeButton("取消",null).show();return;}
        pending=ImageProcessor.pack(pixels);cancelled.set(false);waiting=true;upload.setEnabled(false);
        LinearLayout body=vertical();body.setPadding(dp(24),dp(15),dp(24),dp(8));dialogText=text("请将手机 NFC 区域靠近价签背面的 NFC 线圈。",16,Color.rgb(17,24,39));body.addView(dialogText);progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);body.addView(progress,new LinearLayout.LayoutParams(-1,dp(22)));dialog=new AlertDialog.Builder(this).setTitle("靠近 NFC 价签").setView(body).setNegativeButton("取消",(d,w)->cancel()).create();dialog.setCanceledOnTouchOutside(false);dialog.setOnCancelListener(d->cancel());dialog.show();enableReader();}
    private void enableReader(){if(nfc!=null&&waiting&&!cancelled.get()){Bundle options=new Bundle();options.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY,1000);nfc.enableReaderMode(this,this,NfcAdapter.FLAG_READER_NFC_A|NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK|NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,options);}}
    private void cancel(){cancelled.set(true);waiting=false;if(nfc!=null)nfc.disableReaderMode(this);if(!running.get())upload.setEnabled(nfc!=null);status.setText("已取消。未提交的图像不会刷新；若屏幕已开始刷新，请等待完成。");}
    @Override public void onTagDiscovered(Tag tag){if(!waiting||cancelled.get()||!running.compareAndSet(false,true))return;runOnUiThread(()->dialogText.setText("正在识别价签身份…"));worker.execute(()->{NfcTransfer transfer=new NfcTransfer(cancelled);try{
        transfer.upload(tag,pending,new NfcTransfer.Listener(){public void identified(String uid){runOnUiThread(()->{dialog.setTitle("请勿移动手机");dialogText.setText("已识别价签 "+uid+"\n请保持手机位置，正在传输图片。");});}public void progress(int value){runOnUiThread(()->{progress.setProgress(value);dialogText.setText("请勿移动手机\n图片传输 "+value+"%");});}public void refreshing(){runOnUiThread(()->{progress.setProgress(100);dialogText.setText("图片已发送，等待校验和屏幕刷新。\n约需 15 秒，请继续保持位置。");});}public void complete(){runOnUiThread(()->status.setText("上传成功，屏幕已完成刷新，可以移开手机。"));}});
        runOnUiThread(()->finishDialog());
    }catch(Exception e){runOnUiThread(()->{String message=cancelled.get()?"传输已取消":e instanceof android.nfc.TagLostException?"NFC 链路中断，可能是通信冲突或手机离场。请查看诊断后重试。":e.getMessage();status.setText(message);finishDialog();new AlertDialog.Builder(this).setTitle("本次上传未完成").setMessage(message+"\n若图像已开始刷新，请等屏幕停止后再重试。\n\n"+transfer.diagnostics()).setNeutralButton("复制诊断",(d,w)->{android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);clipboard.setPrimaryClip(ClipData.newPlainText("NFC 诊断",message+"\n"+transfer.diagnostics()));Toast.makeText(this,"诊断已复制",Toast.LENGTH_SHORT).show();}).setPositiveButton("知道了",null).show();});}finally{running.set(false);runOnUiThread(()->upload.setEnabled(nfc!=null));}});}
    private void finishDialog(){waiting=false;if(nfc!=null)nfc.disableReaderMode(this);if(dialog!=null)dialog.dismiss();}
    @Override protected void onResume(){super.onResume();enableReader();}
    @Override protected void onPause(){super.onPause();if(waiting){cancel();if(dialog!=null)dialog.dismiss();}}
    @Override protected void onDestroy(){cancelled.set(true);worker.shutdownNow();super.onDestroy();}
    private static class SimpleSeek implements SeekBar.OnSeekBarChangeListener {public void onProgressChanged(SeekBar b,int value,boolean user){}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}}
    private final class CanvasView extends View {
        Bitmap preview;Paint paint=new Paint();RectF area=new RectF();float previousX,previousY;
        CanvasView(){super(MainActivity.this);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float margin=dp(10),w=getWidth()-2*margin,h=w*104/212f;if(h>getHeight()-2*margin){h=getHeight()-2*margin;w=h*212/104f;}area.set((getWidth()-w)/2,(getHeight()-h)/2,(getWidth()+w)/2,(getHeight()+h)/2);paint.setColor(Color.WHITE);paint.setStyle(Paint.Style.FILL);c.drawRect(area,paint);if(preview!=null){paint.setFilterBitmap(false);c.drawBitmap(preview,null,area,paint);}paint.setColor(Color.rgb(161,169,183));paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1));c.drawRect(area,paint);paint.setStyle(Paint.Style.FILL);}
        @Override public boolean onTouchEvent(android.view.MotionEvent e){if(waiting||running.get())return true;if(e.getAction()==MotionEvent.ACTION_DOWN){previousX=e.getX();previousY=e.getY();getParent().requestDisallowInterceptTouchEvent(true);return true;}if(e.getAction()==MotionEvent.ACTION_MOVE){panX+=(e.getX()-previousX)*212/Math.max(1,area.width());panY+=(e.getY()-previousY)*104/Math.max(1,area.height());previousX=e.getX();previousY=e.getY();update();return true;}if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL){getParent().requestDisallowInterceptTouchEvent(false);performClick();return true;}return true;}
        @Override public boolean performClick(){super.performClick();return true;}
    }
}
