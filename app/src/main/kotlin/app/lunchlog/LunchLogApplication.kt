package app.lunchlog

import android.app.Application
import app.lunchlog.sync.SyncWorker

class LunchLogApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // 起動時に依存を組み立てておく (Room の初期化を最初の画面描画から外す)。
        ServiceLocator.from(this)

        // 前回の未送信分と、他の端末で書かれた変更を取り込む (SPEC F-111, F-302)。
        // 通信は WorkManager に任せるので、ここでは積むだけ。
        SyncWorker.enqueue(this)
    }
}
