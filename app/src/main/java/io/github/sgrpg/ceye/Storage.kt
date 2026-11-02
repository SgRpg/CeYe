package io.github.sgrpg.ceye

import android.content.*
import android.net.Uri
import android.provider.MediaStore
import androidx.room.*
import androidx.room.Entity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Entity(tableName="photos")
data class Photo(@PrimaryKey val key: String, val mediaId: Long, val volume: String, val uri: String,
    val path: String, val name: String, val added: Long, val modified: Long, val size: Long, val date: Long,
    val status: String="history", val target: String="", val intent: String="", val error: String="")
@Entity(tableName="checkpoints")
data class Checkpoint(@PrimaryKey val volume: String, val version: String, val baseline: Long)
@Entity(tableName="operations")
data class Operation(@PrimaryKey val id: String=UUID.randomUUID().toString(), val photoKey: String,
    val uri: String, val fromPath: String, val fromName: String, val target: String,
    val kind: String, val time: Long=System.currentTimeMillis(), val state: String="planned", val error: String="")
@Entity(tableName="decisions")
data class DecisionSnapshot(@PrimaryKey val id:String=UUID.randomUUID().toString(),val actionId:String,val photoKey:String,val oldTarget:String,val oldIntent:String,val oldStatus:String,val time:Long=System.currentTimeMillis())
@Dao
interface Store {
    @Query("SELECT * FROM photos ORDER BY date DESC, mediaId DESC") fun observe(): Flow<List<Photo>>
    @Query("SELECT * FROM photos") suspend fun all(): List<Photo>
    @Query("SELECT * FROM photos WHERE `key`=:key") suspend fun get(key:String): Photo?
    @Upsert suspend fun save(items:List<Photo>)
    @Upsert suspend fun save(item:Photo)
    @Query("SELECT * FROM checkpoints") suspend fun checkpoints():List<Checkpoint>
    @Upsert suspend fun checkpoint(c:Checkpoint)
    @Upsert suspend fun operation(o:Operation)
    @Query("SELECT * FROM operations ORDER BY time DESC") fun operations():Flow<List<Operation>>
    @Query("SELECT * FROM operations WHERE state IN ('planned','authorized','running')") suspend fun unfinished():List<Operation>
    @Upsert suspend fun snapshot(s:DecisionSnapshot)
    @Query("SELECT * FROM decisions WHERE actionId=(SELECT actionId FROM decisions ORDER BY time DESC LIMIT 1)") suspend fun lastDecision():List<DecisionSnapshot>
    @Query("DELETE FROM decisions WHERE actionId=:action") suspend fun removeDecision(action:String)
}
@androidx.room.Database(entities=[Photo::class,Checkpoint::class,Operation::class,DecisionSnapshot::class],version=1,exportSchema=true)
abstract class Database: RoomDatabase() { abstract fun store():Store }
data class Album(val volume:String,val path:String) { val name get()=path.trimEnd('/').substringAfterLast('/'); val key get()="$volume|$path" }

class MediaGateway(private val context:Context) {
    private val resolver=context.contentResolver
    fun scan():List<Photo> = buildList {
        for(volume in MediaStore.getExternalVolumeNames(context)) {
            val base=MediaStore.Images.Media.getContentUri(volume)
            val columns=arrayOf("_id","relative_path","_display_name","generation_added","generation_modified","_size","date_added")
            resolver.query(base,columns,"is_pending=0 AND is_trashed=0",null,null)?.use { c ->
                while(c.moveToNext()) {
                    val id=c.getLong(0)
                    add(Photo("$volume:$id",id,volume,ContentUris.withAppendedId(base,id).toString(),c.getString(1)?:"",c.getString(2)?:"",c.getLong(3),c.getLong(4),c.getLong(5),c.getLong(6)*1000))
                }
            }
        }
    }
    fun location(p:Photo,includeTrash:Boolean=false):Pair<String,String>? {
        val args=android.os.Bundle().apply { if(includeTrash)putInt(MediaStore.QUERY_ARG_MATCH_TRASHED,MediaStore.MATCH_INCLUDE) }
        return resolver.query(Uri.parse(p.uri),arrayOf("relative_path","_display_name","is_trashed"),args,null)?.use {
            if(!it.moveToFirst())null else Pair(if(it.getInt(2)==1)"@trash" else it.getString(0)?:"",it.getString(1)?:"")
        }
    }
    fun move(p:Photo,target:String) {
        require(target.isNotBlank() && !target.contains(".."))
        var candidate=p.name;var n=1
        val base=MediaStore.Images.Media.getContentUri(p.volume)
        while(resolver.query(base,arrayOf("_id"),"relative_path=? AND _display_name=? AND _id!=?",arrayOf(target,candidate,p.mediaId.toString()),null)?.use{it.count>0}==true) {
            val stem=p.name.substringBeforeLast('.',p.name);val ext=p.name.substringAfterLast('.',"")
            candidate="$stem (${n++})"+(if(ext.isEmpty())"" else ".$ext")
        }
        check(resolver.update(Uri.parse(p.uri),ContentValues().apply{put("relative_path",target);put("_display_name",candidate)},null,null)==1){"图片未能移动"}
    }
}
