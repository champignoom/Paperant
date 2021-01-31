package com.champignoom.paperant

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.champignoom.paperant.ui.recent.RecentItem
import com.champignoom.paperant.ui.recent.RecentItemDao

@Database(entities = [RecentItem::class], version = 20200125)
abstract class MyDatabase: RoomDatabase() {
    abstract val recentItemDao: RecentItemDao
    companion object {
        const val DATABASE_NAME = "PaperantDatabase"

        @Volatile private var _instance: MyDatabase? = null

        fun getInstance(context: Context): MyDatabase {
            synchronized(this) {
                if (_instance == null) {
                    _instance = Room.databaseBuilder(
                        context.applicationContext,
                        MyDatabase::class.java,
                        DATABASE_NAME,
                    ).fallbackToDestructiveMigration().build()
                }

                return _instance!!
            }
        }
    }
}
