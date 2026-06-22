package com.example.danallacalendar.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Calendar

@Database(entities = [CalendarCategory::class, Event::class, DeadlineDate::class, EstimatePdf::class, TrashItem::class], version = 12, exportSchema = false)
abstract class CalendarDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
    abstract fun estimatePdfDao(): EstimatePdfDao
    abstract fun trashDao(): TrashDao

    companion object {
        @Volatile
        private var INSTANCE: CalendarDatabase? = null

        val MIGRATION_9_10 = object : androidx.room.migration.Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE events ADD COLUMN linkedEstimateId TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_10_11 = object : androidx.room.migration.Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE events ADD COLUMN teamId INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE events ADD COLUMN slotPosition TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_11_12 = object : androidx.room.migration.Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `trash_items` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `itemType` TEXT NOT NULL, 
                        `originalId` TEXT NOT NULL, 
                        `title` TEXT NOT NULL, 
                        `detailText` TEXT NOT NULL, 
                        `serializedJson` TEXT NOT NULL, 
                        `deletedAt` INTEGER NOT NULL
                    )
                """)
            }
        }

        fun getDatabase(context: Context, scope: CoroutineScope): CalendarDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CalendarDatabase::class.java,
                    "calendar_database"
                )
                .addCallback(CalendarDatabaseCallback(scope))
                .addMigrations(MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class CalendarDatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {
        private val dbMutex = Mutex()
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    populateDatabase(database.eventDao())
                }
            }
        }

        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    ensureHolidaysPopulated(database.eventDao())
                }
            }
        }

        private suspend fun populateDatabase(dao: EventDao) {
            // Default categories in Korean
            val myCalId = dao.insertCategory(CalendarCategory(name = "내 캘린더", colorHex = "#1c62f2", accountName = "내 전화기", isVisible = true)).toInt()
            val holidayId = dao.insertCategory(CalendarCategory(name = "공휴일", colorHex = "#ff3b30", accountName = "기타", isVisible = true)).toInt()

            val calendar = Calendar.getInstance()

            // 1. Today's events
            calendar.set(Calendar.HOUR_OF_DAY, 12)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            val todayStart = calendar.timeInMillis
            calendar.set(Calendar.HOUR_OF_DAY, 13)
            val todayEnd = calendar.timeInMillis
            dao.insertEvent(
                Event(
                    title = "민호와 점심 약속",
                    startMillis = todayStart,
                    endMillis = todayEnd,
                    isAllDay = false,
                    location = "강남 한식당",
                    notes = "휴가 계획 논의.",
                    calendarId = myCalId
                )
            )

            // Test events for filters: 견적, 계약
            calendar.setTimeInMillis(System.currentTimeMillis())
            calendar.set(Calendar.HOUR_OF_DAY, 14)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            val estimateStart = calendar.timeInMillis
            calendar.set(Calendar.HOUR_OF_DAY, 15)
            val estimateEnd = calendar.timeInMillis
            dao.insertEvent(
                Event(
                    title = "오후 견적 상담",
                    startMillis = estimateStart,
                    endMillis = estimateEnd,
                    isAllDay = false,
                    location = "고객 사무실",
                    notes = "신규 인테리어 견적 문의 대응.",
                    calendarId = myCalId
                )
            )

            // Seed holidays
            ensureHolidaysPopulated(dao)
        }

        private suspend fun ensureHolidaysPopulated(dao: EventDao) {
            dbMutex.withLock {
                val categories = dao.getAllCategoriesList()
                val holidayCategories = categories.filter { it.name == "공휴일" }
                
                val holidayId = if (holidayCategories.isEmpty()) {
                    dao.insertCategory(CalendarCategory(name = "공휴일", colorHex = "#ff3b30", accountName = "기타", isVisible = true)).toInt()
                } else {
                    val mainHoliday = holidayCategories.first()
                    if (holidayCategories.size > 1) {
                        val duplicateIds = holidayCategories.drop(1).map { it.id }
                        dao.updateEventsCalendarId(duplicateIds, mainHoliday.id)
                        dao.deleteCategories(holidayCategories.drop(1))
                    }
                    mainHoliday.id
                }

                val count = dao.getEventCountForCategory(holidayId)
                if (count == 0) {
                    val calendar = Calendar.getInstance()
                    for (year in 2025..2028) {
                        val holidays = getKoreanHolidaysList(year)
                        for (holiday in holidays) {
                            val hMonth = holiday.first
                            val hDay = holiday.second
                            val hTitle = holiday.third
                            
                            calendar.clear()
                            calendar.set(Calendar.YEAR, year)
                            calendar.set(Calendar.MONTH, hMonth - 1)
                            calendar.set(Calendar.DAY_OF_MONTH, hDay)
                            calendar.set(Calendar.HOUR_OF_DAY, 0)
                            calendar.set(Calendar.MINUTE, 0)
                            calendar.set(Calendar.SECOND, 0)
                            calendar.set(Calendar.MILLISECOND, 0)
                            val startMillis = calendar.timeInMillis
                            
                            calendar.set(Calendar.HOUR_OF_DAY, 23)
                            calendar.set(Calendar.MINUTE, 59)
                            calendar.set(Calendar.SECOND, 59)
                            calendar.set(Calendar.MILLISECOND, 999)
                            val endMillis = calendar.timeInMillis
                            
                            dao.insertEvent(
                                Event(
                                    title = hTitle,
                                    startMillis = startMillis,
                                    endMillis = endMillis,
                                    isAllDay = true,
                                    notes = "법정 공휴일",
                                    calendarId = holidayId
                                )
                            )
                        }
                    }
                }
            }
        }

        private fun getKoreanHolidaysList(year: Int): List<Triple<Int, Int, String>> {
            val list = mutableListOf(
                Triple(1, 1, "신정"),
                Triple(3, 1, "삼일절"),
                Triple(5, 5, "어린이날"),
                Triple(6, 6, "현충일"),
                Triple(8, 15, "광복절"),
                Triple(10, 3, "개천절"),
                Triple(10, 9, "한글날"),
                Triple(12, 25, "성탄절")
            )
            
            when (year) {
                2025 -> {
                    list.add(Triple(1, 28, "설날 연휴"))
                    list.add(Triple(1, 29, "설날"))
                    list.add(Triple(1, 30, "설날 연휴"))
                    list.add(Triple(3, 3, "대체공휴일(삼일절)"))
                    list.add(Triple(5, 6, "대체공휴일(부처님오신날)"))
                    list.add(Triple(10, 5, "추석 연휴"))
                    list.add(Triple(10, 6, "추석"))
                    list.add(Triple(10, 7, "추석 연휴"))
                    list.add(Triple(10, 8, "대체공휴일(추석)"))
                }
                2026 -> {
                    list.add(Triple(2, 16, "설날 연휴"))
                    list.add(Triple(2, 17, "설날"))
                    list.add(Triple(2, 18, "설날 연휴"))
                    list.add(Triple(3, 2, "대체공휴일(삼일절)"))
                    list.add(Triple(5, 24, "부처님오신날"))
                    list.add(Triple(5, 25, "대체공휴일(부처님오신날)"))
                    list.add(Triple(8, 17, "대체공휴일(광복절)"))
                    list.add(Triple(9, 24, "추석 연휴"))
                    list.add(Triple(9, 25, "추석"))
                    list.add(Triple(9, 26, "추석 연휴"))
                    list.add(Triple(9, 28, "대체공휴일(추석)"))
                    list.add(Triple(10, 5, "대체공휴일(개천절)"))
                }
                2027 -> {
                    list.add(Triple(2, 6, "설날 연휴"))
                    list.add(Triple(2, 7, "설날"))
                    list.add(Triple(2, 8, "설날 연휴"))
                    list.add(Triple(2, 9, "대체공휴일(설날)"))
                    list.add(Triple(5, 13, "부처님오신날"))
                    list.add(Triple(8, 16, "대체공휴일(광복절)"))
                    list.add(Triple(9, 14, "추석 연휴"))
                    list.add(Triple(9, 15, "추석"))
                    list.add(Triple(9, 16, "추석 연휴"))
                    list.add(Triple(10, 4, "대체공휴일(개천절)"))
                    list.add(Triple(10, 11, "대체공휴일(한글날)"))
                    list.add(Triple(12, 27, "대체공휴일(성탄절)"))
                }
                2028 -> {
                    list.add(Triple(1, 26, "설날 연휴"))
                    list.add(Triple(1, 27, "설날"))
                    list.add(Triple(1, 28, "설날 연휴"))
                    list.add(Triple(5, 2, "부처님오신날"))
                    list.add(Triple(10, 2, "추석 연휴"))
                    list.add(Triple(10, 3, "추석/개천절"))
                    list.add(Triple(10, 4, "추석 연휴"))
                    list.add(Triple(10, 5, "대체공휴일"))
                }
            }
            return list
        }
    }
}
