package com.vivekroy.blescanning

import androidx.room.*

@Entity(tableName = "beacons")
data class BeaconEntity (
    @ColumnInfo(name = "uid") val uid: String,
    @ColumnInfo(name = "major") val major: String,
    @ColumnInfo(name = "minor") val minor: String,
    @ColumnInfo(name = "rssi") val rssi: Int,
    @ColumnInfo(name = "timestamp") val timestamp: Long
) {
    @PrimaryKey(autoGenerate = true) var sl: Long = 0
}

@Dao
interface BeaconDao {
    @Insert
    suspend fun insertBeacons(vararg beacons: BeaconEntity)
}