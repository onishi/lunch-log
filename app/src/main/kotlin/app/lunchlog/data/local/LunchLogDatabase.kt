package app.lunchlog.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [RecordEntity::class], version = 1, exportSchema = true)
abstract class LunchLogDatabase : RoomDatabase() {

    abstract fun recordDao(): RecordDao

    companion object {
        fun create(context: Context): LunchLogDatabase =
            Room.databaseBuilder(context, LunchLogDatabase::class.java, "lunch-log.db")
                // 開発中はスキーマ変更のたびに作り直す (PLAN.md Phase 1-2)。
                // 自分が毎日使い始める前に、正式なマイグレーションへ切り替えること。
                .fallbackToDestructiveMigration()
                .build()
    }
}
