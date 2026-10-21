package io.github.nannxi.epaper;

import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
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
  private static final int BG = 0xfff4f5f1,
      INK = 0xff202e29,
      MUTED = 0xff657169,
      LINE = 0xffe5e9e2,
      GREEN = 0xff285846,
      PALE = 0xffeaf1e9,
      RED = 0xffc75047;
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private final AtomicBoolean running = new AtomicBoolean(false),
      cancelled = new AtomicBoolean(false);
  private NfcAdapter nfc;
  private CanvasView canvas;
  private Bitmap source;
  private LinearLayout previewCard, adjustCard;
  private TextView status,
      scaleValue,
      brightnessValue,
      contrastValue,
      strengthValue,
      ditherHint,
      sourceName;
  private SeekBar brightness, contrast, scale, strength;
  private Button upload;
  private TextView[] ditherOptions, colorOptions;
  private int ditherMode, colorMode;
  private byte[] pending;
  private AlertDialog dialog;
  private TextView dialogTitle, dialogText, dialogPercent, dialogStep;
  private ProgressBar progress;
  private volatile boolean waiting;
  private boolean building = true;
  private float zoom = 1, panX, panY;
  private int[] pixels;

  private int dp(float v) {
    return (int) (getResources().getDisplayMetrics().density * v + .5f);
  }

  private LinearLayout column() {
    LinearLayout l = new LinearLayout(this);
    l.setOrientation(LinearLayout.VERTICAL);
    return l;
  }

  private LinearLayout row() {
    LinearLayout l = new LinearLayout(this);
    l.setGravity(Gravity.CENTER_VERTICAL);
    return l;
  }

  private TextView text(String label, int size, int color) {
    TextView t = new TextView(this);
    t.setText(label);
    t.setTextSize(size);
    t.setTextColor(color);
    t.setFontFeatureSettings("tnum");
    return t;
  }

  private void bold(TextView t) {
    t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
  }

  private GradientDrawable shape(int fill, int radius, int stroke) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(fill);
    d.setCornerRadius(dp(radius));
    if (stroke != 0) d.setStroke(dp(1), stroke);
    return d;
  }

  private Drawable touch(int fill, int radius, int stroke) {
    return new RippleDrawable(
        ColorStateList.valueOf(0x18285846),
        shape(fill, radius, stroke),
        shape(Color.WHITE, radius, 0));
  }

  private void gap(LinearLayout l, int height) {
    Space s = new Space(this);
    l.addView(s, new LinearLayout.LayoutParams(1, dp(height)));
  }

  private LinearLayout card(LinearLayout parent) {
    LinearLayout c = column();
    c.setPadding(dp(20), dp(20), dp(20), dp(20));
    c.setBackground(shape(Color.WHITE, 22, LINE));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.bottomMargin = dp(16);
    parent.addView(c, lp);
    return c;
  }

  private Button button(String label, int icon, boolean filled) {
    Button b = new Button(this);
    b.setAllCaps(false);
    b.setText(label);
    b.setTextSize(14);
    bold(b);
    b.setTextColor(filled ? Color.WHITE : GREEN);
    b.setBackground(touch(filled ? GREEN : PALE, 14, 0));
    b.setMinHeight(0);
    b.setMinimumHeight(0);
    b.setPadding(dp(14), 0, dp(14), 0);
    b.setCompoundDrawablePadding(dp(9));
    if (icon >= 0)
      b.setCompoundDrawablesRelativeWithIntrinsicBounds(
          new Glyph(icon, filled ? Color.WHITE : GREEN, 19), null, null, null);
    return b;
  }

  private TextView chip(String label) {
    TextView v = text(label, 11, GREEN);
    bold(v);
    v.setPadding(dp(10), dp(6), dp(10), dp(6));
    v.setBackground(shape(PALE, 20, 0));
    return v;
  }

  private SeekBar slider(LinearLayout parent, String label, int max, int value, int id) {
    LinearLayout heading = row();
    heading.addView(text(label, 14, INK), new LinearLayout.LayoutParams(0, -2, 1));
    TextView number = text("", 13, GREEN);
    bold(number);
    heading.addView(number);
    parent.addView(heading);
    SeekBar seek = new SeekBar(this);
    seek.setMax(max);
    seek.setProgress(value);
    seek.setPadding(dp(1), 0, dp(1), 0);
    seek.setProgressTintList(ColorStateList.valueOf(GREEN));
    seek.setThumbTintList(ColorStateList.valueOf(GREEN));
    seek.setProgressBackgroundTintList(ColorStateList.valueOf(LINE));
    seek.setSplitTrack(false);
    parent.addView(seek, new LinearLayout.LayoutParams(-1, dp(44)));
    if (id == 0) scaleValue = number;
    else if (id == 1) brightnessValue = number;
    else if (id == 2) contrastValue = number;
    else strengthValue = number;
    seek.setContentDescription(label);
    seek.setOnSeekBarChangeListener(
        new SimpleSeek() {
          public void onProgressChanged(SeekBar s, int p, boolean user) {
            if (!building) {
              zoom = 1 + scale.getProgress() / 100f;
              update();
            }
          }
        });
    return seek;
  }

  private TextView[] segments(LinearLayout parent, String[] labels, boolean colors) {
    LinearLayout bar = row();
    bar.setPadding(dp(4), dp(4), dp(4), dp(4));
    bar.setBackground(shape(BG, 13, 0));
    TextView[] options = new TextView[labels.length];
    for (int i = 0; i < labels.length; i++) {
      final int index = i;
      TextView t = text(labels[i], 13, MUTED);
      t.setGravity(Gravity.CENTER);
      bold(t);
      bar.addView(t, new LinearLayout.LayoutParams(0, dp(44), 1));
      t.setOnClickListener(
          v -> {
            if (waiting || running.get()) return;
            if (colors) colorMode = index;
            else ditherMode = index;
            update();
          });
      options[i] = t;
    }
    parent.addView(bar);
    return options;
  }

  private void select(TextView[] options, int current) {
    for (int i = 0; i < options.length; i++) {
      TextView t = options[i];
      boolean selected = i == current;
      t.setTextColor(selected ? GREEN : MUTED);
      t.setBackground(touch(selected ? Color.WHITE : BG, 10, selected ? LINE : 0));
      t.setSelected(selected);
      t.setContentDescription(t.getText() + (selected ? "，已选择" : ""));
    }
  }

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    nfc = NfcAdapter.getDefaultAdapter(this);
    getWindow().setStatusBarColor(BG);
    getWindow().setNavigationBarColor(Color.WHITE);
    getWindow()
        .getDecorView()
        .setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    LinearLayout root = column();
    root.setBackgroundColor(BG);
    LinearLayout header = column();
    header.setPadding(dp(20), dp(12), dp(20), 0);
    root.addView(header, new LinearLayout.LayoutParams(-1, -2));
    LinearLayout brand = row();
    ImageView mark = new ImageView(this);
    mark.setImageDrawable(getApplicationInfo().loadIcon(getPackageManager()));
    mark.setContentDescription("墨屏图标");
    brand.addView(mark, new LinearLayout.LayoutParams(dp(32), dp(32)));
    TextView name = text("墨屏", 19, INK);
    bold(name);
    LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(0, -2, 1);
    np.leftMargin = dp(10);
    brand.addView(name, np);
    TextView about = chip("2.13 英寸");
    about.setContentDescription("查看设备和版本信息");
    about.setOnClickListener(
        v ->
            new AlertDialog.Builder(this)
                .setTitle("墨屏")
                .setMessage(
                    "图片编辑与 NFC 上传\n\n"
                        + "2.13 英寸 · 212 × 104 · 黑白红\n"
                        + "版本 0.1.4\n\n"
                        + "上传期间保持主板供电，手机靠近背面的 NFC 线圈。")
                .setPositiveButton("知道了", null)
                .show());
    brand.addView(about);
    header.addView(brand);
    gap(header, 14);
    previewCard = card(header);
    previewCard.setPadding(dp(8), dp(8), dp(8), dp(8));
    canvas = new CanvasView();
    canvas.setContentDescription("墨水屏预览，可拖动调整图片裁切");
    previewCard.addView(canvas, new LinearLayout.LayoutParams(-1, -2));
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    scroll.setClipToPadding(false);
    scroll.setVerticalScrollBarEnabled(false);
    LinearLayout page = column();
    page.setPadding(dp(20), 0, dp(20), dp(4));
    scroll.addView(page);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    adjustCard = card(page);
    LinearLayout adjustHeading = row();
    TextView adjustment = text("图像调整", 17, INK);
    bold(adjustment);
    adjustHeading.addView(adjustment, new LinearLayout.LayoutParams(0, -2, 1));
    TextView reset = text("重置", 13, GREEN);
    bold(reset);
    reset.setGravity(Gravity.CENTER);
    reset.setBackground(touch(PALE, 12, 0));
    reset.setPadding(dp(12), 0, dp(12), 0);
    adjustHeading.addView(reset, new LinearLayout.LayoutParams(-2, dp(44)));
    reset.setOnClickListener(v -> resetAdjustments());
    adjustCard.addView(adjustHeading);
    gap(adjustCard, 16);
    LinearLayout actions = row();
    Button choose = button("选择图片", 1, false), rotate = button("旋转 90°", 2, false);
    actions.addView(choose, new LinearLayout.LayoutParams(0, dp(46), 1));
    LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, dp(46), 1);
    rp.leftMargin = dp(10);
    actions.addView(rotate, rp);
    choose.setOnClickListener(v -> choose());
    rotate.setOnClickListener(v -> rotate());
    rotate.setContentDescription("顺时针旋转图片 90 度");
    adjustCard.addView(actions);
    gap(adjustCard, 12);
    LinearLayout caption = row();
    sourceName = text("示例画面", 12, MUTED);
    sourceName.setSingleLine(true);
    sourceName.setEllipsize(android.text.TextUtils.TruncateAt.END);
    caption.addView(sourceName, new LinearLayout.LayoutParams(0, -2, 1));
    caption.addView(text("212 × 104", 11, MUTED));
    adjustCard.addView(caption);
    gap(adjustCard, 20);
    scale = slider(adjustCard, "裁切缩放", 200, 0, 0);
    adjustCard.addView(text("拖动预览调整位置", 11, MUTED));
    gap(adjustCard, 22);
    adjustCard.addView(text("输出颜色", 13, MUTED));
    gap(adjustCard, 9);
    colorOptions = segments(adjustCard, new String[] {"黑白红", "黑白"}, true);
    gap(adjustCard, 22);
    brightness = slider(adjustCard, "亮度", 160, 80, 1);
    gap(adjustCard, 6);
    contrast = slider(adjustCard, "对比度", 160, 60, 2);
    gap(adjustCard, 12);
    adjustCard.addView(text("抖动方式", 13, MUTED));
    gap(adjustCard, 9);
    ditherOptions = segments(adjustCard, new String[] {"关闭", "有序", "扩散"}, false);
    ditherHint = text("", 11, MUTED);
    ditherHint.setPadding(0, dp(9), 0, dp(18));
    adjustCard.addView(ditherHint);
    strength = slider(adjustCard, "抖动强度", 100, 100, 3);
    TextView note = text("预览已转换为屏幕像素，实物颜色会略有差异。", 11, MUTED);
    note.setGravity(Gravity.CENTER);
    note.setPadding(dp(8), 0, dp(8), dp(16));
    page.addView(note);
    LinearLayout footer = column();
    footer.setPadding(dp(20), dp(12), dp(20), dp(14));
    footer.setBackground(shape(Color.WHITE, 0, LINE));
    status = text("准备就绪 · 上传前请为墨水屏供电", 12, MUTED);
    status.setGravity(Gravity.CENTER);
    status.setPadding(0, 0, 0, dp(10));
    footer.addView(status);
    upload = button("上传到墨水屏", 3, true);
    upload.setTextSize(16);
    footer.addView(upload, new LinearLayout.LayoutParams(-1, dp(54)));
    upload.setOnClickListener(v -> startUpload());
    root.addView(footer);
    root.setOnApplyWindowInsetsListener(
        (v, insets) -> {
          root.setPadding(
              insets.getSystemWindowInsetLeft(),
              insets.getSystemWindowInsetTop(),
              insets.getSystemWindowInsetRight(),
              0);
          footer.setPadding(dp(20), dp(12), dp(20), dp(14) + insets.getSystemWindowInsetBottom());
          return insets;
        });
    setContentView(root);
    root.requestApplyInsets();
    makeSample();
    building = false;
    update();
    if (nfc == null) {
      status.setText("此设备不支持 NFC，仍可编辑图片");
      upload.setEnabled(false);
    }
  }

  private void makeSample() {
    source = Bitmap.createBitmap(424, 208, Bitmap.Config.ARGB_8888);
    Canvas c = new Canvas(source);
    c.drawColor(Color.WHITE);
    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    p.setColor(Color.rgb(220, 28, 45));
    c.drawRect(0, 104, 424, 208, p);
    p.setColor(Color.BLACK);
    p.setTextSize(40);
    p.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
    c.drawText("Hello World", 65, 65, p);
    p.setColor(Color.WHITE);
    p.setTextSize(30);
    c.drawText("2.13 inch e-Paper", 65, 165, p);
  }

  private void choose() {
    if (running.get() || waiting) return;
    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
    i.setType("image/*");
    i.addCategory(Intent.CATEGORY_OPENABLE);
    startActivityForResult(i, 10);
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (request != 10 || result != RESULT_OK || data == null) return;
    Uri uri = data.getData();
    status.setText("正在载入图片…");
    worker.execute(
        () -> {
          try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
              BitmapFactory.decodeStream(in, null, o);
            }
            if (o.outWidth <= 0 || o.outHeight <= 0) throw new IOException("无法识别图片");
            o.inSampleSize = 1;
            while (o.outWidth / o.inSampleSize > 2400 || o.outHeight / o.inSampleSize > 2400)
              o.inSampleSize *= 2;
            o.inJustDecodeBounds = false;
            Bitmap image;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
              image = BitmapFactory.decodeStream(in, null, o);
            }
            if (image == null) throw new IOException("无法打开图片");
            runOnUiThread(
                () -> {
                  if (isFinishing() || isDestroyed()) return;
                  source = image;
                  panX = panY = 0;
                  zoom = 1;
                  scale.setProgress(0);
                  sourceName.setText("已选图片 · 拖动调整裁切");
                  status.setTextColor(MUTED);
                  status.setText("图片已载入，调整后即可上传");
                  update();
                });
          } catch (Exception e) {
            runOnUiThread(() -> status.setText("选图失败：" + e.getMessage()));
          }
        });
  }

  private void rotate() {
    if (running.get() || waiting || source == null) return;
    Matrix m = new Matrix();
    m.postRotate(90);
    source = Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), m, true);
    panX = panY = 0;
    update();
  }

  private void resetAdjustments() {
    if (waiting || running.get()) return;
    building = true;
    brightness.setProgress(80);
    contrast.setProgress(60);
    strength.setProgress(100);
    ditherMode = 0;
    colorMode = 0;
    building = false;
    update();
  }

  private static String signed(int v) {
    return v > 0 ? "+" + v : String.valueOf(v);
  }

  private void update() {
    if (building || source == null) return;
    scaleValue.setText(Math.round(zoom * 100) + "%");
    brightnessValue.setText(signed(brightness.getProgress() - 80));
    contrastValue.setText(signed(contrast.getProgress() - 60));
    strengthValue.setText(ditherMode == 0 ? "未启用" : strength.getProgress() + "%");
    strength.setEnabled(ditherMode != 0 && !waiting && !running.get());
    strength.setAlpha(ditherMode == 0 ? .3f : 1f);
    strengthValue.setTextColor(ditherMode == 0 ? MUTED : GREEN);
    select(ditherOptions, ditherMode);
    select(colorOptions, colorMode);
    ditherHint.setText(
        new String[] {"适合文字与线条，保持边缘干净。", "规则颗粒，适合插画与渐变。", "保留更多明暗层次，适合照片。"}[ditherMode]);
    Bitmap fitted = Bitmap.createBitmap(212, 104, Bitmap.Config.ARGB_8888);
    Canvas c = new Canvas(fitted);
    c.drawColor(Color.WHITE);
    float factor = Math.max(212f / source.getWidth(), 104f / source.getHeight()) * zoom;
    float w = source.getWidth() * factor, h = source.getHeight() * factor;
    panX = Math.max(-(w - 212) / 2, Math.min((w - 212) / 2, panX));
    panY = Math.max(-(h - 104) / 2, Math.min((h - 104) / 2, panY));
    float left = (212 - w) / 2 + panX, top = (104 - h) / 2 + panY;
    c.drawBitmap(
        source, null, new RectF(left, top, left + w, top + h), new Paint(Paint.FILTER_BITMAP_FLAG));
    int[] input = new int[212 * 104];
    fitted.getPixels(input, 0, 212, 0, 0, 212, 104);
    pixels =
        ImageProcessor.convert(
            input,
            brightness.getProgress() - 80,
            contrast.getProgress() - 60,
            ditherMode,
            strength.getProgress(),
            colorMode == 0);
    fitted.setPixels(pixels, 0, 212, 0, 0, 212, 104);
    canvas.preview = fitted;
    canvas.invalidate();
  }

  private void enableTree(View v, boolean enabled) {
    v.setEnabled(enabled);
    if (v instanceof ViewGroup) {
      ViewGroup g = (ViewGroup) v;
      for (int i = 0; i < g.getChildCount(); i++) enableTree(g.getChildAt(i), enabled);
    }
  }

  private void editorEnabled(boolean enabled) {
    enableTree(previewCard, enabled);
    enableTree(adjustCard, enabled);
    strength.setEnabled(enabled && ditherMode != 0);
    upload.setEnabled(enabled && nfc != null);
    upload.setAlpha(enabled && nfc != null ? 1f : .5f);
  }

  private void keepScreen(boolean enabled) {
    int flag = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
    if (enabled) getWindow().addFlags(flag);
    else getWindow().clearFlags(flag);
    if (dialog != null && dialog.getWindow() != null) {
      if (enabled) dialog.getWindow().addFlags(flag);
      else dialog.getWindow().clearFlags(flag);
    }
  }

  private void startUpload() {
    if (running.get() || waiting || pixels == null || nfc == null) return;
    if (!nfc.isEnabled()) {
      new AlertDialog.Builder(this)
          .setTitle("请开启 NFC")
          .setMessage("开启后返回这里，再点击上传。")
          .setPositiveButton(
              "打开设置", (d, w) -> startActivity(new Intent(Settings.ACTION_NFC_SETTINGS)))
          .setNegativeButton("取消", null)
          .show();
      return;
    }
    pending = ImageProcessor.pack(pixels);
    cancelled.set(false);
    waiting = true;
    editorEnabled(false);
    status.setTextColor(MUTED);
    status.setText("等待连接 · 上传期间保持亮屏");
    LinearLayout body = column();
    body.setPadding(dp(26), dp(26), dp(26), dp(8));
    TextView icon = text("", 18, GREEN);
    icon.setGravity(Gravity.CENTER);
    icon.setCompoundDrawablesRelativeWithIntrinsicBounds(null, new Glyph(3, GREEN, 38), null, null);
    body.addView(icon);
    gap(body, 16);
    dialogTitle = text("靠近墨水屏", 22, INK);
    bold(dialogTitle);
    dialogTitle.setGravity(Gravity.CENTER);
    body.addView(dialogTitle);
    gap(body, 12);
    dialogText = text("将手机 NFC 区域贴近墨水屏背面的线圈。", 14, MUTED);
    dialogText.setGravity(Gravity.CENTER);
    dialogText.setLineSpacing(dp(3), 1);
    body.addView(dialogText);
    gap(body, 22);
    dialogPercent = text("等待连接", 24, GREEN);
    bold(dialogPercent);
    dialogPercent.setGravity(Gravity.CENTER);
    body.addView(dialogPercent);
    gap(body, 10);
    progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
    progress.setMax(100);
    progress.setProgressTintList(ColorStateList.valueOf(GREEN));
    progress.setProgressBackgroundTintList(ColorStateList.valueOf(LINE));
    body.addView(progress, new LinearLayout.LayoutParams(-1, dp(8)));
    gap(body, 14);
    dialogStep = text("1 连接     ·     2 传图     ·     3 刷新", 11, MUTED);
    dialogStep.setGravity(Gravity.CENTER);
    body.addView(dialogStep);
    gap(body, 15);
    TextView awake = text("屏幕保持常亮", 11, MUTED);
    awake.setGravity(Gravity.CENTER);
    body.addView(awake);
    dialog =
        new AlertDialog.Builder(this)
            .setView(body)
            .setNegativeButton("取消上传", (d, w) -> cancel())
            .create();
    dialog.setCanceledOnTouchOutside(false);
    dialog.setOnCancelListener(d -> cancel());
    dialog.show();
    if (dialog.getWindow() != null)
      dialog.getWindow().setBackgroundDrawable(shape(Color.WHITE, 24, 0));
    dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(MUTED);
    keepScreen(true);
    try {
      enableReader();
    } catch (RuntimeException e) {
      cancel();
      showFailure("无法启动 NFC，请检查系统 NFC 设置。", e.toString());
    }
  }

  private void enableReader() {
    if (nfc != null && waiting && !cancelled.get()) {
      Bundle options = new Bundle();
      options.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 1000);
      nfc.enableReaderMode(
          this,
          this,
          NfcAdapter.FLAG_READER_NFC_A
              | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
              | NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
          options);
    }
  }

  private void disableReader() {
    if (nfc != null)
      try {
        nfc.disableReaderMode(this);
      } catch (RuntimeException ignored) {
      }
  }

  private void cancel() {
    cancelled.set(true);
    finishDialog();
    if (!running.get()) editorEnabled(true);
    status.setText("已取消 · 若屏幕已开始刷新，请等待完成");
  }

  private void transferUi(Runnable action) {
    runOnUiThread(
        () -> {
          if (waiting && !cancelled.get() && !isFinishing() && !isDestroyed()) action.run();
        });
  }

  @Override
  public void onTagDiscovered(Tag tag) {
    if (!waiting || cancelled.get() || !running.compareAndSet(false, true)) return;
    transferUi(
        () -> {
          dialogTitle.setText("正在识别");
          dialogText.setText("保持手机位置，正在核对墨水屏身份。");
        });
    worker.execute(
        () -> {
          NfcTransfer transfer = new NfcTransfer(cancelled);
          try {
            transfer.upload(
                tag,
                pending,
                new NfcTransfer.Listener() {
                  public void identified(String uid) {
                    transferUi(
                        () -> {
                          dialogTitle.setText("请勿移动手机");
                          dialogText.setText("墨水屏已连接，正在传输图片。");
                          dialogPercent.setText("0%");
                          dialogStep.setText("✓ 已连接     ·     正在传图     ·     等待刷新");
                        });
                  }

                  public void progress(int value) {
                    transferUi(
                        () -> {
                          progress.setProgress(value);
                          dialogPercent.setText(value + "%");
                        });
                  }

                  public void refreshing() {
                    transferUi(
                        () -> {
                          progress.setProgress(100);
                          dialogTitle.setText("正在刷新屏幕");
                          dialogText.setText("图片已发送。刷新约需 15 秒，请继续保持位置。");
                          dialogPercent.setText("100%");
                          dialogStep.setText("✓ 已连接     ·     ✓ 已传图     ·     正在刷新");
                        });
                  }

                  public void complete() {
                    transferUi(
                        () -> {
                          status.setTextColor(GREEN);
                          status.setText("上传完成 · 可以移开手机了");
                          Toast.makeText(MainActivity.this, "墨水屏已刷新", Toast.LENGTH_SHORT).show();
                        });
                  }
                });
            runOnUiThread(() -> finishDialog());
          } catch (Exception e) {
            runOnUiThread(
                () -> {
                  boolean aborted = cancelled.get();
                  finishDialog();
                  if (!aborted && !isFinishing() && !isDestroyed()) {
                    String message =
                        e instanceof android.nfc.TagLostException
                            ? "NFC 连接中断，请保持手机靠近后重试。"
                            : e.getMessage();
                    if (message == null) message = "连接未完成，请重试。";
                    status.setTextColor(RED);
                    status.setText("上传未完成 · 可查看诊断后重试");
                    showFailure(message, transfer.diagnostics());
                  }
                });
          } finally {
            runOnUiThread(
                () -> {
                  running.set(false);
                  keepScreen(false);
                  if (!isFinishing() && !isDestroyed()) editorEnabled(true);
                });
          }
        });
  }

  private void showFailure(String message, String diagnostics) {
    String detail = message + "\n\n" + diagnostics;
    new AlertDialog.Builder(this)
        .setTitle("上传未完成")
        .setMessage(message + "\n\n若屏幕已开始刷新，请等它停止后再试。")
        .setNegativeButton("关闭", null)
        .setNeutralButton(
            "复制诊断",
            (d, w) -> {
              android.content.ClipboardManager clipboard =
                  (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
              clipboard.setPrimaryClip(ClipData.newPlainText("NFC 诊断", detail));
              Toast.makeText(this, "诊断已复制", Toast.LENGTH_SHORT).show();
            })
        .setPositiveButton(
            "查看诊断",
            (d, w) -> {
              ScrollView scroll = new ScrollView(this);
              TextView report = text(detail, 12, INK);
              report.setTypeface(Typeface.MONOSPACE);
              report.setTextIsSelectable(true);
              report.setPadding(dp(22), dp(16), dp(22), dp(16));
              scroll.addView(report);
              new AlertDialog.Builder(this)
                  .setTitle("NFC 诊断")
                  .setView(scroll)
                  .setPositiveButton("关闭", null)
                  .show();
            })
        .show();
  }

  private void finishDialog() {
    waiting = false;
    keepScreen(false);
    disableReader();
    if (dialog != null) dialog.dismiss();
  }

  @Override
  protected void onResume() {
    super.onResume();
    enableReader();
  }

  @Override
  protected void onPause() {
    super.onPause();
    if (waiting) cancel();
    else keepScreen(false);
  }

  @Override
  protected void onDestroy() {
    cancelled.set(true);
    keepScreen(false);
    disableReader();
    worker.shutdownNow();
    super.onDestroy();
  }

  private static class SimpleSeek implements SeekBar.OnSeekBarChangeListener {
    public void onProgressChanged(SeekBar s, int p, boolean user) {}

    public void onStartTrackingTouch(SeekBar s) {}

    public void onStopTrackingTouch(SeekBar s) {}
  }

  private final class CanvasView extends View {
    Bitmap preview;
    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    RectF area = new RectF();
    float previousX, previousY;

    CanvasView() {
      super(MainActivity.this);
      setBackground(shape(BG, 14, 0));
      setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
      int w = MeasureSpec.getSize(widthSpec);
      setMeasuredDimension(w, Math.round((w - dp(24)) * 104 / 212f) + dp(24));
    }

    @Override
    protected void onDraw(Canvas c) {
      super.onDraw(c);
      area.set(dp(12), dp(12), getWidth() - dp(12), getHeight() - dp(12));
      paint.setColor(Color.WHITE);
      paint.setStyle(Paint.Style.FILL);
      c.drawRect(area, paint);
      if (preview != null) {
        paint.setFilterBitmap(false);
        c.drawBitmap(preview, null, area, paint);
      }
      paint.setColor(0xffd4dbd0);
      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeWidth(dp(1));
      c.drawRect(area, paint);
      paint.setStyle(Paint.Style.FILL);
    }

    @Override
    public boolean onTouchEvent(android.view.MotionEvent e) {
      if (waiting || running.get() || !isEnabled()) return true;
      if (e.getAction() == MotionEvent.ACTION_DOWN) {
        previousX = e.getX();
        previousY = e.getY();
        getParent().requestDisallowInterceptTouchEvent(true);
        return true;
      }
      if (e.getAction() == MotionEvent.ACTION_MOVE) {
        panX += (e.getX() - previousX) * 212 / Math.max(1, area.width());
        panY += (e.getY() - previousY) * 104 / Math.max(1, area.height());
        previousX = e.getX();
        previousY = e.getY();
        update();
        return true;
      }
      if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) {
        getParent().requestDisallowInterceptTouchEvent(false);
        performClick();
        return true;
      }
      return true;
    }

    @Override
    public boolean performClick() {
      super.performClick();
      return true;
    }
  }

  private final class Glyph extends Drawable {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int kind, size;

    Glyph(int type, int color, int side) {
      kind = type;
      size = dp(side);
      p.setColor(color);
      p.setStyle(Paint.Style.STROKE);
      p.setStrokeWidth(1.7f);
      p.setStrokeCap(Paint.Cap.ROUND);
      p.setStrokeJoin(Paint.Join.ROUND);
    }

    @Override
    public int getIntrinsicWidth() {
      return size;
    }

    @Override
    public int getIntrinsicHeight() {
      return size;
    }

    @Override
    public void draw(Canvas c) {
      c.save();
      Rect b = getBounds();
      c.translate(b.left, b.top);
      c.scale(b.width() / 24f, b.height() / 24f);
      if (kind == 0) {
        c.drawRoundRect(2, 4, 22, 20, 3, 3, p);
        c.drawLine(6, 9, 13, 9, p);
        c.drawLine(6, 13, 10, 13, p);
        c.drawLine(15, 12, 19, 16, p);
        c.drawLine(19, 12, 15, 16, p);
      } else if (kind == 1) {
        c.drawRoundRect(3, 3, 21, 21, 3, 3, p);
        c.drawCircle(8, 8, 1.3f, p);
        Path path = new Path();
        path.moveTo(4, 17);
        path.lineTo(10, 11);
        path.lineTo(14, 15);
        path.lineTo(17, 12);
        path.lineTo(21, 16);
        c.drawPath(path, p);
      } else if (kind == 2) {
        c.drawArc(3, 4, 19, 20, 90, 270, false, p);
        Path arrow = new Path();
        arrow.moveTo(15, 8);
        arrow.lineTo(19, 12);
        arrow.lineTo(23, 8);
        c.drawPath(arrow, p);
      } else {
        c.drawRoundRect(8, 3, 16, 21, 2, 2, p);
        c.drawLine(11, 18, 13, 18, p);
        c.drawArc(2, 6, 10, 18, 125, 110, false, p);
        c.drawArc(14, 6, 22, 18, -55, 110, false, p);
      }
      c.restore();
    }

    @Override
    public void setAlpha(int alpha) {
      p.setAlpha(alpha);
      invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter f) {
      p.setColorFilter(f);
      invalidateSelf();
    }

    @Override
    public int getOpacity() {
      return PixelFormat.TRANSLUCENT;
    }
  }
}
