# mini閱讀器（MiniReader）

一個以 Kotlin 與 Jetpack Compose 開發的輕量 Android EPUB 閱讀器。所有書籍、封面、閱讀進度、標籤與書籤都保存在裝置本機，不需要帳號，也不依賴雲端服務。

目前提供 `1.0.0-prerelease` 測試版本，適合想要搶先體驗的使用者。此版本為 **Pre-release**，不是正式上線版本。

## 功能特色

- 匯入多本 EPUB，使用檔案 SHA-256 避免重複匯入。
- 自動解析書名、作者、語言、目錄與封面。
- 書架網格檢視，顯示封面、閱讀進度、未讀／已讀完狀態與最近閱讀時間。
- 搜尋書名、作者、標籤；排序支援加入時間、最近閱讀、書名、作者與閱讀進度。
- 書本詳細資訊：原書名、作者、語言、字數、標籤、閱讀進度、章節、檔案大小、加入時間、最近閱讀與書籤數。
- 自訂標籤與顏色，可依標籤篩選書籍。
- 閱讀器支援目錄跳轉、書籤新增／管理、章節切換與閱讀位置記憶。
- 閱讀工具列顯示全書進度百分比。
- 每篇文章底部提供「上一章／下一章」按鈕。
- 左右滑動切換章節：向左滑下一章、向右滑上一章；約滑動螢幕寬度 20% 或快速輕甩即可觸發。
- 顯示模式支援系統、淺色、深色；淺色與深色護眼模式分別記住選擇。
- 字體、字級、行距、字距、段落間距、左右邊距、對齊方式與首行縮排設定。
- 可設定閱讀時保持螢幕亮起。
- 完全離線，沒有廣告、追蹤或網路權限。

## 文件索引

- [使用指南](docs/使用指南.md)
- [系統架構與技術文件](docs/系統架構與技術文件.md)
- [系統設計文件](docs/系統設計文件.md)
- [開發與建置指南](docs/開發與建置指南.md)
- [發行與版本說明](docs/發行與版本說明.md)

## 下載與安裝

Pre-release APK：

- [app-prerelease.apk（v1.0.0-prerelease）](https://github.com/mark216tw/android-ebookreader-01/releases/tag/v1.0.0-prerelease)
- [所有 Releases](https://github.com/mark216tw/android-ebookreader-01/releases)

安裝前請確認 Android 裝置允許安裝來自未知來源的 APK。此 APK 使用 Android Debug 金鑰簽署，僅供測試，不適合正式上架或長期發行。

## 系統需求

- Android 8.0（API 26）以上
- 建議 Android 10 以上以獲得較完整的系統列與深色模式體驗
- 裝置需有足夠空間存放 EPUB 與封面圖

## 快速開始

```bash
# 建置 Debug APK
./gradlew :app:assembleDebug

# 執行單元測試
./gradlew :app:testDebugUnitTest

# 建置 Pre-release APK（R8 壓縮 + Debug 金鑰簽署）
./gradlew :app:assemblePrerelease
```

Windows PowerShell：

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assemblePrerelease
```

Pre-release APK 輸出位置：

```text
app/build/outputs/apk/prerelease/app-prerelease.apk
```

## 專案結構

```text
android-ebookreader-01/
├── app/
│   ├── src/main/java/com/minireader/app/
│   │   ├── data/                 # Room、DataStore、EPUB 解析與匯入
│   │   ├── ui/                   # Compose 畫面、主題與閱讀器 WebView
│   │   ├── MainActivity.kt
│   │   └── MiniReaderApp.kt
│   ├── src/test/                 # 單元測試
│   └── build.gradle.kts
├── docs/                         # 專案文件
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
├── LICENSE
└── README.md
```

## 技術棧

- Kotlin 2.1.0
- Jetpack Compose + Material 3
- Navigation Compose
- Room 2.6.1 + KSP
- DataStore Preferences
- Android WebView + WebViewAssetLoader
- Coil
- Kotlin Coroutines
- Gradle 8.11.1 / AGP 8.9.2 / JDK 17

## 隱私與安全

- 不需要網路權限，書籍與閱讀資料只存在本機。
- WebView 關閉檔案與內容存取，只允許載入應用程式內部產生的 EPUB 資產。
- 匯入的 HTML 會移除 script、iframe、object、embed、事件處理器與 `javascript:` URL。
- 外部連結不會直接在閱讀器內開啟，需經使用者確認後交給系統瀏覽器。
- 沒有分析、廣告或第三方追蹤 SDK。

詳細說明請見 [系統架構與技術文件](docs/系統架構與技術文件.md)。

## 版本資訊

- 目前版本：`1.0.0-prerelease`
- 版本代碼：`1`
- 狀態：Pre-release 測試版本
- 變更紀錄：[CHANGELOG.md](CHANGELOG.md)

## 授權

本專案採用 [MIT License](LICENSE)。

Copyright (c) 2026 mark216tw
