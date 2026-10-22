# Android 应用：墨屏

原生 Java，Android 8/API26 起，目标 API35，无第三方运行库。当前 v0.1.4/versionCode 5，配套板端 v0.1.2。用户已确认 v0.1.2 手机上传成功；新界面和常亮待手机验收。

## 构建

需要 JDK17、SDK Platform35、Build Tools35。在本目录运行：

```powershell
.\build.ps1 -JavaHome 'C:\Tools\jdk17' -AndroidSdk 'C:\Android\sdk' -BuildToolsVersion '35.0.0'
```

无需 Android Studio 或 Gradle；脚本调用 javac、d8、aapt2、zipalign、apksigner，输出 `../releases/epaper-nfc-v0.1.4-debug.apk`，版本名自动读取 Manifest。

默认在忽略的 `build/android-debug.keystore` 生成开发签名；可通过 `-KeyStore` 指定自己的已有密钥。只有同证书才能覆盖安装已发布 APK。SDK/JDK/私钥不入库，`android` 是标准调试口令，非生产签名配置。发布文件身份见[版本说明](../docs/RELEASES.md)。

## 编辑与上传

- 固定顶部圆角预览；调整区可滚动，底部固定上传按钮。
- 系统文件选择器、顺时针90°旋转、100–300%裁切缩放与预览拖动。
- 亮度、对比度、黑白红/黑白、关闭/有序/扩散及0–100%抖动强度。
- 有序抖动缩放阈值偏移；扩散抖动缩放传播误差。0%等于不抖动，100%保持原算法输出。
- 透明区域叠白底后转换，预览按实际三色像素绘制。
- ReaderMode 仅点击上传后启用，跳过NDEF解析，识别签名/尺寸/UID/布局修订才写。
- 等待、传输、刷新期间 FLAG_KEEP_SCREEN_ON；成功/失败/取消/后台清理。编辑控件在上传期间禁用。
- 错误弹窗提供查看/复制本地诊断，不自动上传日志。

权限只有 NFC；不需要联网、存储、照片库、root、Shizuku 或 WAKE_LOCK。取消不能撤回已提交的物理刷新，退出后台会停止当前传输。

## 源码

| 文件 | 责任 |
|---|---|
| `MainActivity.java` | 界面、图片输入、裁切预览、状态与生命周期 |
| `ImageProcessor.java` | 色彩量化、抖动与双平面打包 |
| `Wire.java` | 帧、CRC与小端读写 |
| `NfcTransfer.java` | NfcA命令、识别、重试、进度与诊断 |

## 图标

[SVG源文件](artwork/icon.svg)是黑白红像素墨水屏，附[PNG预览](artwork/icon-preview.png)。`app/src/main/res` 已提交五档PNG与Adaptive Icon，正常构建不需要Python。

重新生成图标（本目录）：

```powershell
python -m pip install cairosvg
python tools/render_icon.py
```

CairoSVG 需要系统可用 Cairo 运行库。修改图标后检查不同桌面遮罩裁切，不把 PNG 预览当成 Android 运行截图。

测试入口见[测试说明](../tests/README.md)，UI/实机边界见[验证状态](../docs/HARDWARE_AND_VALIDATION.md)。
