# BikeBike

Keep 动感单车控制器 - Android App

连接 Keep 动感单车（C1 mini / C2 Lite），实时显示骑行数据并控制档位。

## 功能

- 🔍 扫描并连接 Keep 动感单车蓝牙设备
- 📊 实时显示速度、踏频、功率、卡路里
- 🎛️ 控制单车档位 (1-24)
- 📋 BLE 通信日志（hex 格式，方便调试）

## 架构

```
┌─────────────────────────────┐
│        Jetpack Compose UI    │
│   (MainScreen / Metrics)     │
├─────────────────────────────┤
│         ViewModel            │
│   (BikeViewModel)            │
├──────────┬──────────────────┤
│ BleManager │  keep-core (Rust)│
│ (BLE传输层) │  (协议解析/构建)  │
├──────────┴──────────────────┤
│    Android BluetoothGatt     │
│    (Native BLE Stack)        │
└─────────────────────────────┘
```

## 项目结构

```
bikebike/
├── app/                          # Android App (Kotlin + Compose)
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/com/bikebike/app/
│           ├── MainActivity.kt
│           ├── ui/               # Compose UI
│           ├── ble/              # BLE transport layer
│           └── viewmodel/        # ViewModels
├── keep-core/                    # Rust protocol core
│   ├── src/
│   │   ├── lib.rs
│   │   ├── frame.rs             # Frame encode/decode
│   │   ├── proto.rs             # Protobuf parsing
│   │   ├── control.rs           # Control commands
│   │   └── hci_extractor.rs     # HCI log parser
│   └── tests/
├── docs/                         # Documentation
│   ├── protocol_notes.md
│   ├── btsnoop_capture_guide.md
│   └── test_cases.md
└── .github/workflows/            # CI/CD
    └── build.yml                # Auto APK build
```

## 使用前准备

### 1. 提取 Keep 认证信息

Keep 单车需要认证握手包，需从 Keep App 的蓝牙日志中提取：

1. 在 Android 开发者选项中开启 "蓝牙 HCI 监听日志"
2. 重启蓝牙
3. 打开 Keep App，连接单车，骑行 1-2 分钟
4. 结束运动，导出 HCI 日志
5. 使用工具提取握手包（详见 [btsnoop_capture_guide.md](docs/btsnoop_capture_guide.md)）

### 2. 安装 App

#### 从源码构建
```bash
# 需要 Android SDK + JDK 17 + Rust
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

#### GitHub Actions 自动构建
每次 push 到 main 分支会自动构建 APK，可在 Actions 页面下载。

## 技术栈

- **UI**: Kotlin + Jetpack Compose + Material 3
- **BLE**: Android 原生 BluetoothGatt API
- **协议核心**: Rust (keep-core crate)
- **FFI**: UniFFI (Rust ↔ Kotlin)
- **日志**: Timber + 自定义 hex 日志

## 协议详情

详见 [protocol_notes.md](docs/protocol_notes.md)

## 开发环境

- JDK 17+
- Android SDK (compileSdk 35)
- Rust (stable) + cargo-ndk
- Android NDK

## 许可证

GNU GPL v3
