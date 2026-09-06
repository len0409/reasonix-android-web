# Reasonix Android

Reasonix 网页端的 Android App —— 一比一加载 reasonix 网页端（WebView 原版页面，功能 1:1），
**服务器地址可配置**，局域网 IP 变了随时改。

## 使用

1. 安装 APK 后打开
2. 首次启动输入 reasonix 服务器地址（如 `http://192.168.3.43:8787`）
3. 保存后自动加载 reasonix 网页端

**改地址**：页面上有浮动"改地址"按钮，随时可换。

## 服务器端要求

reasonix 服务需要监听局域网（不是仅 127.0.0.1）：

```bash
reasonix serve --addr 0.0.0.0:8787
```

查看本机局域网 IP：

```bash
ip addr show wlan0   # 或 ip addr
```

App 和服务器在同一 WiFi/局域网即可连接。

## 构建

GitHub Actions 自动构建。发布版本通过 tag 触发：

```bash
git tag v2.0.0 && git push origin v2.0.0
```

Release 产物：`app-release.apk`（签名版）。

## 签名

Secrets（仓库 Settings → Secrets and variables → Actions）：

| Secret | 说明 |
|---|---|
| `REASONIX_KEYSTORE_B64` | keystore 文件 base64 |
| `REASONIX_STORE_PASSWORD` | keystore 密码 |
| `REASONIX_KEY_ALIAS` | key 别名 |
| `REASONIX_KEY_PASSWORD` | key 密码 |

## 结构

- `app/src/main/java/io/reasonix/app/MainActivity.java` — 配置页 + WebView 宿主
- `.github/workflows/build.yml` — CI 构建 + 发布
