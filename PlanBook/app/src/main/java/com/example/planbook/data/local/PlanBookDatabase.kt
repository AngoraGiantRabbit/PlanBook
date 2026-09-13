package com.example.planbook.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        NotebookEntity::class,
        TaskEntity::class,
        ReviewEntity::class,
        ReviewSettingEntity::class,
        TaskCompletionEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class PlanBookDatabase : RoomDatabase() {
    abstract fun notebookDao(): NotebookDao
    abstract fun taskDao(): TaskDao
    abstract fun reviewDao(): ReviewDao
    abstract fun reviewSettingDao(): ReviewSettingDao
    abstract fun taskCompletionDao(): TaskCompletionDao

    companion object {
        /**
         * v3 → v4（#7，ADR-0004）：notebooks 由「多个平级计划本」重构为「主计划本 + 子计划本」。
         * 数据保留迁移：
         * - 旧计划本全部转为唯一主计划本下的子计划本（全部可见、最早创建的设为活动）；
         * - 自动复盘待办（tasks.isAutoReview=1）、reviews、review_settings 的归属改指主计划本；
         * - 其余任务的归属不变（旧计划本 id 即子计划本 id）。
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val now = System.currentTimeMillis()
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `notebooks_new` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`parentId` INTEGER, " +
                        "`color` TEXT, " +
                        "`isVisible` INTEGER NOT NULL, " +
                        "`isActive` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
                // 旧计划本 → 子计划本（parentId 暂空，主计划本插入后回填）
                db.execSQL(
                    "INSERT INTO `notebooks_new` (`name`,`parentId`,`color`,`isVisible`,`isActive`,`createdAt`) " +
                        "SELECT `name`,NULL,NULL,1,0,`createdAt` FROM `notebooks`"
                )
                // 主计划本（createdAt=-1 作哨兵，便于取 id，最后修正）
                db.execSQL(
                    "INSERT INTO `notebooks_new` (`name`,`parentId`,`color`,`isVisible`,`isActive`,`createdAt`) " +
                        "VALUES ('主计划本',NULL,NULL,1,0,-1)"
                )
                // 多计划本 → 主计划本会折叠归属，先按主键去重，避免撞唯一索引/产生重复：
                // - review_settings（唯一索引 notebookId+reviewType）：每个 reviewType 保留最早一条
                // - reviews（无唯一索引）：同一日期保留 updatedAt 最新（并列取 id 最小）的一条
                db.execSQL(
                    "DELETE FROM `review_settings` WHERE `id` NOT IN " +
                        "(SELECT MIN(`id`) FROM `review_settings` GROUP BY `reviewType`)"
                )
                db.execSQL(
                    "DELETE FROM `reviews` WHERE `id` NOT IN (" +
                        "SELECT `r`.`id` FROM `reviews` `r` WHERE NOT EXISTS (" +
                        "SELECT 1 FROM `reviews` `w` WHERE `w`.`date` = `r`.`date` " +
                        "AND (`w`.`updatedAt` > `r`.`updatedAt` " +
                        "OR (`w`.`updatedAt` = `r`.`updatedAt` AND `w`.`id` < `r`.`id`))))"
                )
                db.execSQL("UPDATE `tasks` SET `notebookId`=(SELECT `id` FROM `notebooks_new` WHERE `createdAt`=-1) WHERE `isAutoReview`=1")
                db.execSQL("UPDATE `reviews` SET `notebookId`=(SELECT `id` FROM `notebooks_new` WHERE `createdAt`=-1)")
                db.execSQL("UPDATE `review_settings` SET `notebookId`=(SELECT `id` FROM `notebooks_new` WHERE `createdAt`=-1)")
                db.execSQL("UPDATE `notebooks_new` SET `parentId`=(SELECT `id` FROM `notebooks_new` WHERE `createdAt`=-1) WHERE `createdAt`<>-1")
                db.execSQL("UPDATE `notebooks_new` SET `isActive`=1 WHERE `id`=(SELECT MIN(`id`) FROM `notebooks_new` WHERE `createdAt`<>-1)")
                db.execSQL("UPDATE `notebooks_new` SET `createdAt`=$now WHERE `createdAt`=-1")
                db.execSQL("DROP TABLE `notebooks`")
                db.execSQL("ALTER TABLE `notebooks_new` RENAME TO `notebooks`")
            }
        }

        /**
         * v4 → v5（#11，ADR-0005）：notebooks 增加 importSource 列标记导入子计划本。
         * 可空列，无需 DEFAULT，存量行为 null（手建）。
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `notebooks` ADD COLUMN `importSource` TEXT")
            }
        }

        /**
         * v5 → v6：tasks 增加 location 列（教室，ICS LOCATION）。
         * 可空列零风险；块内显示教室、其余备注仅详情可见。
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `location` TEXT")
            }
        }
    }
}
