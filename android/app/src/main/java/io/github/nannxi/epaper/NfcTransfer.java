package io.github.nannxi.epaper;

import android.nfc.Tag;
import android.nfc.tech.NfcA;
import android.os.SystemClock;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

public final class NfcTransfer {
  public interface Listener {
    void identified(String uid);

    void progress(int percent);

    void submitted();
  }

  private final AtomicBoolean cancelled;
  private final StringBuilder diagnostic = new StringBuilder();
  private int mailboxReads;
  private int mailboxPage = 0x90;

  public String diagnostics() {
    return diagnostic.toString();
  }

  private static String hex(byte[] bytes) {
    StringBuilder s = new StringBuilder();
    for (byte b : bytes) s.append(String.format(java.util.Locale.ROOT, "%02X", b & 255));
    return s.toString();
  }

  private void log(String text) {
    diagnostic.append(text).append("\n");
    android.util.Log.i("EPaperNFC", text);
  }

  public NfcTransfer(AtomicBoolean value) {
    cancelled = value;
  }

  private void check() throws IOException {
    if (cancelled.get()) throw new IOException("传输已取消");
  }

  private byte[] transceive(NfcA nfc, byte[] command, int expected) throws IOException {
    IOException last = null;
    for (int attempt = 0; attempt < 12; attempt++) {
      check();
      try {
        byte[] result = nfc.transceive(command);
        if (result.length == expected && (command[0] != (byte) 0xa2 || (result[0] & 15) == 10))
          return result;
        last = new IOException("NFC 暂忙，请保持位置");
        if (attempt == 0) log("CMD " + hex(command) + " reply=" + hex(result));
      } catch (android.nfc.TagLostException e) {
        log("TagLost command=" + hex(command) + " attempt=" + attempt);
        throw e;
      } catch (IOException e) {
        last = e;
        if (attempt == 0)
          log(
              "IOException command="
                  + hex(command)
                  + " "
                  + e.getClass().getSimpleName()
                  + ": "
                  + e.getMessage());
      }
      SystemClock.sleep(12);
    }
    throw last == null ? new IOException("NFC 无响应") : last;
  }

  private byte[] read(NfcA nfc) throws IOException {
    byte[] result = new byte[64];
    for (int i = 0; i < 4; i++) {
      byte[] part = transceive(nfc, new byte[] {0x30, (byte) (mailboxPage + i * 4)}, 16);
      System.arraycopy(part, 0, result, i * 16, 16);
      if (mailboxReads < 2) log("READ page=" + (mailboxPage + i * 4) + " " + hex(part));
    }
    mailboxReads++;
    SystemClock.sleep(90); /* Also yield after identity/ACK reads. */
    return result;
  }

  private void page(NfcA nfc, int page, byte[] data, int offset) throws IOException {
    byte[] c = new byte[6];
    c[0] = (byte) 0xa2;
    c[1] = (byte) page;
    System.arraycopy(data, offset, c, 2, 4);
    byte[] response = transceive(nfc, c, 1);
    if ((response[0] & 15) != 10) throw new IOException("NFC 拒绝写入，检查供电和固件");
  }

  private void write(NfcA nfc, byte[] data) throws IOException {
    page(nfc, mailboxPage, new byte[4], 0);
    for (int i = 1; i < 16; i++) page(nfc, mailboxPage + i, data, i * 4);
    page(nfc, mailboxPage, data, 0);
    /* Read the final mirror block to signal NDEF_DATA_READ, then stop
    RF traffic while the MCU consumes and acknowledges the packet. */
    transceive(nfc, new byte[] {0x30, (byte) (mailboxPage + 12)}, 16);
    SystemClock.sleep(90);
  }

  private byte[] exchange(NfcA nfc, byte[] request) throws IOException {
    for (int retry = 0; retry < 4; retry++) {
      write(nfc, request);
      long end = SystemClock.elapsedRealtime() + 2200;
      while (SystemClock.elapsedRealtime() < end) {
        check();
        byte[] reply = read(nfc);
        if (Wire.validReply(reply)
            && Wire.u32(reply, 8) == Wire.u32(request, 8)
            && Wire.u16(reply, 6) == Wire.u16(request, 6)
            && (reply[5] & 255) == ((request[5] & 255) | 128)) {
          if (reply[15] != 0) throw new IOException("固件校验失败，错误码 " + (reply[15] & 255));
          return reply;
        }
        SystemClock.sleep(90);
      }
    }
    throw new IOException("设备确认超时，请保持手机贴近 NFC 线圈");
  }

  public void upload(Tag tag, byte[] image, Listener listener) throws IOException {
    NfcA nfc = NfcA.get(tag);
    if (nfc == null) throw new IOException("这不是支持的 NFC-A 价签");
    try {
      log("App 0.1.5 / mailbox 90h..9Fh");
      log(
          "Android "
              + android.os.Build.VERSION.RELEASE
              + " / "
              + android.os.Build.MANUFACTURER
              + " "
              + android.os.Build.MODEL);
      log("Tech=" + Arrays.toString(tag.getTechList()));
      nfc.connect();
      nfc.setTimeout(1000);
      byte[] uid = tag.getId();
      if (uid.length != 7 || uid[0] != 4) throw new IOException("不是支持的 NXP 价签，未写入");
      log("UID=" + hex(uid));
      /* Read-only evidence before any write: lock bytes and UID/CC. */
      byte[] manufacturer = transceive(nfc, new byte[] {0x30, 0}, 16);
      log("Manufacturer=" + hex(manufacturer));
      byte[] dynamic = transceive(nfc, new byte[] {0x30, (byte) 0xe2}, 16);
      log("DynamicLocks=" + hex(dynamic));
      if ((dynamic[1] & 1) != 0) throw new IOException("目标 NFC 邮箱页被锁定，已停止且未写入");
      byte[] identity = new byte[64];
      long identityEnd = SystemClock.elapsedRealtime() + 6000;
      do {
        check();
        identity = read(nfc);
        if (Wire.validReply(identity)) break;
        SystemClock.sleep(120);
      } while (SystemClock.elapsedRealtime() < identityEnd);
      log("Mailbox=" + hex(identity));
      if (!Wire.validReply(identity)
          || identity[17] != 1
          || Wire.u16(identity, 18) != 212
          || Wire.u16(identity, 20) != 104
          || Wire.u16(identity, 22) != 5512
          || Wire.u32(identity, 28) != 0x31525742L)
        throw new IOException("未识别到 NFC 墨水屏固件。请检查主板供电并烧录配套固件");
      for (int i = 0; i < 7; i++)
        if (identity[32 + i] != uid[i]) throw new IOException("价签 UID 与固件不一致，未写入");
      if ((identity[39] & 255) != mailboxPage || (identity[40] & 255) != 2)
        throw new IOException("请先烧录配套 0.1.2 固件，已停止且未写入");
      StringBuilder id = new StringBuilder();
      for (byte value : uid) id.append(String.format("%02X", value & 255));
      log("Firmware identified");
      listener.identified(id.toString());
      long transfer = new java.security.SecureRandom().nextInt() & 0xffffffffL;
      if (transfer == 0) transfer = 1;
      byte[] begin = new byte[11];
      Wire.put16(begin, 0, 212);
      Wire.put16(begin, 2, 104);
      Wire.put32(begin, 4, Wire.crc(image, image.length));
      Wire.put16(begin, 8, image.length);
      begin[10] = 1;
      log("BEGIN transfer=" + Long.toHexString(transfer));
      exchange(nfc, Wire.packet(1, 0, transfer, 0, begin));
      log("BEGIN acknowledged");
      int sequence = 1;
      for (int offset = 0; offset < image.length; offset += 44) {
        int length = Math.min(44, image.length - offset);
        byte[] ack =
            exchange(
                nfc,
                Wire.packet(
                    2,
                    sequence++,
                    transfer,
                    offset,
                    Arrays.copyOfRange(image, offset, offset + length)));
        if (Wire.u16(ack, 12) != offset + length) throw new IOException("分块位置不一致，请重新上传");
        listener.progress((offset + length) * 100 / image.length);
      }
      byte[] commit = Wire.packet(3, sequence, transfer, image.length, new byte[0]);
      byte[] ack = exchange(nfc, commit);
      if (ack[16] != 2) throw new IOException("固件未确认开始屏幕刷新");
      listener.submitted();
    } finally {
      try {
        nfc.close();
      } catch (IOException ignored) {
      }
    }
  }
}
