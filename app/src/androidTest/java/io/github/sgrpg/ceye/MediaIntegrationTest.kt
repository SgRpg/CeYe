package io.github.sgrpg.ceye

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
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
    @Test fun startupUsesSystemDialogAndDenialDoesNotRepeat(){
        val device=androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        // Seed an old target preference; the upgraded app must ignore it.
        runBlocking {
            val job=kotlinx.coroutines.SupervisorJob()
            val data=androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
                scope=kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO+job)
            ){context.preferencesDataStoreFile("settings")}
            data.edit{it[stringSetPreferencesKey("targets")]=setOf("external_primary|Pictures/CeYeTest-$unique/second/")}
            job.cancel()
            job.join()
        }
        androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java).use {
            val deny=device.wait(Until.findObject(By.res("com.android.permissioncontroller","permission_deny_button")),10000)
            assertNotNull("First launch should show Android's photo permission dialog",deny)
            deny!!.click()
            assertTrue(device.wait(Until.hasObject(By.text("收件箱")),5000))
            assertTrue(device.hasObject(By.text("暂无照片")))
            assertFalse(device.hasObject(By.text("先允许册页访问照片")))
        }
        // Closing and reopening the Activity loads the recorded request from disk.
        androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertTrue(device.wait(Until.hasObject(By.text("允许访问")),5000))
            assertFalse(device.wait(Until.hasObject(By.res("com.android.permissioncontroller","permission_deny_button")),1500))
            scenario.recreate()
            assertTrue(device.wait(Until.hasObject(By.text("允许访问")),5000))
            assertFalse(device.hasObject(By.res("com.android.permissioncontroller","permission_deny_button")))
            device.findObject(By.text("允许访问")).click()
            val allow=device.wait(Until.findObject(By.res("com.android.permissioncontroller","permission_allow_all_button")),5000)
                ?:device.wait(Until.findObject(By.res("com.android.permissioncontroller","permission_allow_button")),2000)
            assertNotNull("Retry should request Android permission again",allow)
            allow!!.click()
            assertTrue(device.wait(Until.hasObject(By.text("浏览相册")),10000))
            assertEquals(2,PhotoAccess.level(context))
            assertFalse(device.hasObject(By.text("选择归档相册")))
            val first=create("first","first.jpg",0xff112233.toInt())
            val second=create("second","second.jpg",0xff445566.toInt())
            device.findObject(By.text("浏览相册")).click()
            assertTrue(device.wait(Until.hasObject(By.text("first")),10000))
            assertTrue(device.wait(Until.hasObject(By.text("second")),10000))
            assertFalse(device.hasObject(By.text("归档目标")))
            device.findObject(By.desc("设置")).click()
            assertTrue(device.wait(Until.hasObject(By.text("照片权限")),5000))
            assertFalse(device.hasObject(By.text("归档相册")))
            // Existing local folders need no allow-list; new images still require classification.
            val incoming=create("second","new.jpg",0xff778899.toInt())
            val key=incoming.key
            val stateFile=context.getDatabasePath("ceye.db")
            assertTrue(stateFile.exists())
            val db=Room.databaseBuilder(context,Database::class.java,"ceye.db").build()
            try {
                val deadline=android.os.SystemClock.uptimeMillis()+10000
                var found:Photo?=null
                while(android.os.SystemClock.uptimeMillis()<deadline){
                    found=runBlocking{db.store().get(key)}
                    if(found!=null)break
                    android.os.SystemClock.sleep(100)
                }
                assertNotNull("New photo should be indexed",found)
                assertEquals("pending",found!!.status)
                assertEquals(setOf(first.path,second.path),runBlocking{db.store().all()}.filter{it.key in setOf(first.key,second.key)}.map{it.path}.toSet())
            } finally {db.close()}
            device.pressBack()
            device.pressBack()
        }
        androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java).use {
            assertTrue(device.wait(Until.hasObject(By.text("收件箱")),5000))
            assertTrue(device.wait(Until.hasObject(By.text("全选本次")),10000))
            device.findObject(By.text("全选本次")).click()
            device.findObject(By.text("全部相册")).click()
            assertTrue(device.wait(Until.hasObject(By.text("选择相册")),5000))
            assertTrue(device.hasObject(By.text("first")))
            assertTrue(device.hasObject(By.text("second")))
            assertFalse(device.hasObject(By.text("选择归档相册")))
            assertFalse(device.hasObject(By.res("com.android.permissioncontroller","permission_deny_button")))
        }
    }
}
