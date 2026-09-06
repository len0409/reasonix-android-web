# Reasonix Android

Reasonix 网页端的 Android App —— 原生 WebView 直接加载本机 `reasonix serve` 提供的网页端
（`http://127.0.0.1:8787`），网页端所有功能 1:1 保留。

## 前提

- Termux 里运行着 reasonix 服务：

```bash
reasonix serve --addr 0.0.0.0:8787
```

App 打开后自动连接本机服务。若服务未启动，App 会显示提示页。

## 构建

GitHub Actions 自动构建（推 `main` 分支触发）。发布版本通过 tag 触发：

```bash
git tag v1.0.0 && git push origin v1.0.0
```

Release 产物：`app-release.apk`（签名版）。

## 本地签名（可选）

未配置 Secrets 时 CI 产出未签名/调试 APK。要产出签名 release APK，
在仓库 Settings → Secrets and variables → Actions 配置：

| Secret | 说明 |
|---|---|
| `REASONIX_KEYSTORE_B64` | keystore 文件的 base64 内容 |
| `REASONIX_STORE_PASSWORD` | keystore 密码 |
| `REASONIX_KEY_ALIAS` | key 别名 |
| `REASONIX_KEY_PASSWORD` | key 密码 |

## 结构

- `app/src/main/java/io/reasonix/app/MainActivity.java` — WebView 宿主
- `app/src/main/res/` — 主题、图标
- `.github/workflows/build.yml` — CI 构建 + 发布
