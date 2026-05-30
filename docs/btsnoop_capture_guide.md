# BTSnoop Capture Guide (HCI 日志提取指南)

## 目的

Keep 动感单车需要认证握手包才能正常通信。这些包需要从 Keep 官方 App
的蓝牙通信日志中提取。

## 步骤

### 1. 开启 Android 开发者选项

1. 打开 **设置** → **关于手机**
2. 找到 **版本号** 或 **软件版本号**
3. 连续点击 7 次，直到提示 "已进入开发者模式"

### 2. 启用 HCI 日志

1. 打开 **设置** → **开发者选项**
2. 找到 **启用蓝牙 HCI 监听日志** 并打开
3. **重启蓝牙**（关闭再打开）

### 3. 使用 Keep App 连接单车

1. 打开 Keep App
2. 连接你的动感单车
3. 开始骑行，持续 1-2 分钟
4. 结束运动
5. 关闭 Keep App

### 4. 导出 HCI 日志

#### 方法 A: 直接获取 (需要 root)
```
adb pull /data/misc/bluetooth/logs/btsnoop_hci.log
```

#### 方法 B: Bug Report (无需 root)
```bash
adb bugreport bugreport.zip
# 解压后在 FS/data/misc/bluetooth/logs/btsnoop_hci.log
```

### 5. 提取握手包

使用 BikeCon 的 identity_gen.py 或本项目的内置工具：

```bash
python3 identity_gen.py btsnoop_hci.log
```

输出 `identity.json` 包含:
- `bike_name`: 单车名称
- `bike_mac`: 单车 MAC 地址
- `handshake_packets`: 握手包列表 (hex strings)

## 重要提示

- **单车必须断网**: 如果单车连接了 WiFi，数据可能走 WiFi 而不是 BLE
- **每次固件更新**: 可能需要重新提取握手包
- **隐私**: HCI 日志包含所有蓝牙通信，不要分享给他人

## 在 BikeBike App 中使用

1. 将 `identity.json` 复制到手机
2. 在 App 中选择 "导入身份文件"
3. 选择 `identity.json`
4. App 会自动加载握手包

## 未来计划

- 直接在 App 中解析 HCI 日志文件
- 支持从 Keep App 数据目录自动读取
