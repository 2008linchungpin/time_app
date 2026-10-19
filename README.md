# TimePlanner（功能 1–8）

把 `app/src/main/**`、`app/src/test/**`、`app/src/androidTest/**` 以及 `app/build.gradle.kts`、
`AndroidManifest.xml`、`res/` 覆蓋進你現有的 Android Studio 專案（package: com.example.timeplanner），
`gradle/libs.versions.toml` 沿用你原本的（不需要新增依賴）。舊的 PlannerApp.kt 會被這版取代。

## 測試
- 純 JVM：`./gradlew testDebugUnitTest`（SchedulingTest：重疊/緩衝順延/超時判定/彙總）
- 裝置：對 `RepositoryTest.kt` 按 Run（新增/修改/刪除/連帶刪除/計時/衝突/緩衝/批次/回顧）

## 注意
- 資料庫 version = 2（含任務分類）。從 v1 升級會自動 migration；若異常可解除安裝再執行。
- 提醒用 AlarmManager（目錄沒有 WorkManager/DataStore）；Android 12+ 到「設定」頁允許精準鬧鐘。
- 設定頁可管理分類（由上到下 = 重要 → 不重要）；新增／編輯任務時可選擇。
