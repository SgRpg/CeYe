package io.github.sgrpg.ceye

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class MediaIntegrationTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val resolver=context.contentResolver
    private val created=mutableListOf<android.net.Uri>()
    private val unique=System.nanoTime().toString()
    private fun create(folder:String,name:String,color:Int):Photo {
        val uri=resolver.insert(MediaStore.Images.Media.getContentUri("external_primary"),ContentValues().apply{put("relative_path","Pictures/CeYeTest-$unique/$folder/");put("_display_name",name);put("mime_type","image/jpeg");put("is_pending",1)})!!
        created.add(uri)
        val bitmap=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888).apply{eraseColor(color)}
        resolver.openOutputStream(uri)!!.use{bitmap.compress(Bitmap.CompressFormat.JPEG,95,it)};bitmap.recycle()
        resolver.update(uri,ContentValues().apply{put("is_pending",0)},null,null)
        val id=android.content.ContentUris.parseId(uri)
        return Photo("external_primary:$id",id,"external_primary",uri.toString(),"Pictures/CeYeTest-$unique/$folder/",name,0,0,0,0)
    }
    private fun hash(p:Photo)=resolver.openInputStream(android.net.Uri.parse(p.uri))!!.use{MessageDigest.getInstance("SHA-256").digest(it.readBytes()).toList()}
    @After fun clean(){created.forEach{resolver.delete(it,null,null)}}
    @Test fun movingKeepsBytesAndChangesActualAlbum(){val p=create("source","photo.jpg",0xffff0000.toInt());val before=hash(p);val gateway=MediaGateway(context);val target="Pictures/CeYeTest-$unique/target/";gateway.move(p,target);assertEquals(target,gateway.location(p)!!.first);assertEquals(before,hash(p))}
    @Test fun collisionPreservesBothFiles(){val existing=create("target","photo.jpg",0xff00ff00.toInt());val p=create("source","photo.jpg",0xffff0000.toInt());val original=hash(existing);val incoming=hash(p);val gateway=MediaGateway(context);gateway.move(p,existing.path);assertNotEquals(existing.name,gateway.location(p)!!.second);assertEquals(original,hash(existing));assertEquals(incoming,hash(p))}
    @Test fun classificationDecisionSurvivesDatabaseReopen()=runBlocking {
        val name="ceye-test-$unique.db";val photo=create("source","saved.jpg",0xffff0000.toInt()).copy(status="planned",target="Pictures/Travel/",intent="move")
        val first=Room.databaseBuilder(context,Database::class.java,name).build();first.store().save(photo);first.close()
        val second=Room.databaseBuilder(context,Database::class.java,name).build();try{assertEquals(photo,second.store().get(photo.key))}finally{second.close();context.deleteDatabase(name)}
    }
    @Test fun nativeActivityStartsWithoutCrashing(){
        androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity -> assertTrue(activity.window.decorView.isShown) }
        }
    }
}
