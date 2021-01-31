package com.champignoom.paperant.ui.recent

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.room.*
import com.artifex.mupdf.fitz.RectI
import com.champignoom.paperant.MyDatabase
import java.time.Instant


@Entity(tableName = RecentItem.TABLE_NAME, indices = [Index(value = [RecentItem.COL_FILE_PATH], unique = true)])
data class RecentItem(
    @ColumnInfo(name = COL_TIMESTAMP) val timestamp: Long,
    @ColumnInfo(name = COL_FILE_PATH) val filePath: String,
    @ColumnInfo(name = COL_THUMBNAIL_PATH) val thumbnailPath: String,
    @PrimaryKey(autoGenerate = true) val uid: Long = 0,
) {
    companion object {
        const val TABLE_NAME = "recent_item"
        const val COL_TIMESTAMP = "timestamp"
        const val COL_FILE_PATH = "file_path"
        const val COL_THUMBNAIL_PATH = "thumbnail_path"
    }
}

@Dao
interface RecentItemDao {
    @Query("SELECT * FROM ${RecentItem.TABLE_NAME} ORDER BY ${RecentItem.COL_TIMESTAMP} DESC")
    fun getAll(): LiveData<List<RecentItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(recentItem: RecentItem)

    @Delete
    fun delete(recentItem: RecentItem)
}

class RecentViewModel(application: Application) : AndroidViewModel(application) {
    private val recentItemDao = MyDatabase.getInstance(application).recentItemDao
    val recentItems = recentItemDao.getAll()
    fun addRecentItem(filePath: String) = recentItemDao.insert(RecentItem(
        Instant.now().toEpochMilli(),
        filePath,
        ""))
}