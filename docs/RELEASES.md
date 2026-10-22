# 版本、发布包与兼容

## 推荐组合

| 组件 | 版本 / 入口 | 说明 |
|---|---|---|
| Android | [v0.1.4](https://github.com/NannXi/SAMR21-213-NFC/releases/tag/v0.1.4) | versionCode 5，墨屏固定预览与精简编辑页 |
| 板端固件 | [v0.1.2](https://github.com/NannXi/SAMR21-213-NFC/releases/tag/v0.1.2) | RF90h..9Fh 邮箱、布局修订 2 |
| 已有成功反馈 | Android v0.1.2 + 固件 v0.1.2 | 用户在手机端确认可上传 |
| v0.1.3 / v0.1.4 | 兼容上述固件，NFC 时序未改 | 构建和核心测试通过，新 UI/常亮等待手机验收 |

v0.1.0/v0.1.1 是保留的故障诊断版本，旧邮箱及仲裁存在问题。不要用它们的固件搭配当前 APK。显示示例仓库的固件没有 NFC 协议，也不能供此应用上传。

## 下载文件

- `epaper-nfc-v0.1.4-debug.apk`：直接安装；保持与旧版同证书可覆盖升级。
- `samr21-nfc-v0.1.2.bin/.hex`：板端程序；位于 v0.1.2 Release。
- `SAMR21-213-NFC-v0.1.4-source.zip`：该发布时刻的源码快照。
- `icon.svg`、`icon-preview.png`：原创像素图标源文件与预览。
- `SHA256SUMS.txt`：各 Release 附件的哈希；对应各自版本，不混用清单。

主分支文档可比发布快照更新；现有 Release 附件不因文档整理而替换。

## 已发布二进制身份

| 文件 | SHA-256 |
|---|---|
| APK v0.1.4 | `b7cddf0c448169515f5a34b47203571f9801a6b088642ec52b2b9cb93513fb9d` |
| 固件 BIN v0.1.2 | `820c3cc38be04efa9ddf91563e0750b07f23227f682ea5bbdace806cc2cad479` |
| 原厂恢复 Flash | `023efc81fb12f6bbfec844374120924c8c1e8597ea3e2884af3306df3d8639c8` |

APK 签名证书 SHA-256：

```text
b798b530ff90aa9e5ab52a83fb5f4613b01988647c206fec0d625a4bc57d848b
```

```powershell
Get-FileHash .\epaper-nfc-v0.1.4-debug.apk -Algorithm SHA256
Get-FileHash .\samr21-nfc-v0.1.2.bin -Algorithm SHA256
```

本地重新构建若未指定原密钥，会生成自己的开发签名，不能覆盖现有发布 APK。`android` 是 Android 标准调试密钥口令，不是生产签名方案。私钥不入库。

所有现有版本保留预发布标记：单块板和一次用户成功反馈不能替代多机、掉电与长期稳定性验证。APK/固件版本独立更新，文档更新不提升二进制版本。
