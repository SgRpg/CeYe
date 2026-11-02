package io.github.sgrpg.ceye

import android.app.Application
import android.os.Build
import android.provider.MediaStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.room.withTransaction
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import androidx.compose.runtime.*

private val android.content.Context.settings by preferencesDataStore("settings")
data class Authorization(val id:Long,val kind:String,val photos:List<Photo>,val logs:List<Operation>)
class AppModel(app:Application):AndroidViewModel(app) {
    private val db=Room.databaseBuilder(app,Database::class.java,"ceye.db").build()
    private val store=db.store()
    private val media=MediaGateway(app)
    val photos=store.observe().stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
    val operations=store.operations().stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
    var targets by mutableStateOf(setOf<String>());private set
    var configured by mutableStateOf(false);private set
    var session by mutableStateOf(setOf<String>());private set
    var selected by mutableStateOf(setOf<String>())
    var message by mutableStateOf("");private set
    var busy by mutableStateOf(false);private set
    var authorization by mutableStateOf<Authorization?>(null);private set
    private val queue=ArrayDeque<Authorization>()
    private val sync=Mutex()
    private var sessionStarted=false
    private val targetKey=stringSetPreferencesKey("targets")
    private val configuredKey=booleanPreferencesKey("configured")
    val albums get()=photos.value.filter{it.path.isNotBlank()&&it.status !in setOf("trashed","unavailable")}.map{Album(it.volume,it.path)}.distinctBy{it.key}.sortedBy{it.name}
    val inbox get()=photos.value.filter{it.key in session && it.status in setOf("pending","planned")}
    val waiting get()=photos.value.count{it.key !in session && it.status in setOf("pending","planned")}
    init { viewModelScope.launch { val settings=getApplication<Application>().settings.data.first();targets=settings[targetKey]?:emptySet();configured=settings[configuredKey]?:false } }
    fun select(keys:Set<String>) { selected=keys }
    fun target(album:Album,enabled:Boolean) { viewModelScope.launch {
        targets=if(enabled)targets+album.key else targets-album.key
        getApplication<Application>().settings.edit{it[targetKey]=targets}
    } }
    fun configure() {viewModelScope.launch { configured=true;getApplication<Application>().settings.edit{it[configuredKey]=true};refresh(true) } }
    fun refresh(newSession:Boolean=false) {viewModelScope.launch {
        if(authorization!=null)return@launch
        sync.withLock {
            busy=true
            try {
                val app=getApplication<Application>()
                val checkpoints=withContext(Dispatchers.IO){store.checkpoints().associateBy{it.volume}}
                val edges=withContext(Dispatchers.IO){MediaStore.getExternalVolumeNames(app).associateWith{Checkpoint(it,MediaStore.getVersion(app,it),MediaStore.getGeneration(app,it))}}
                val scanned=withContext(Dispatchers.IO){media.scan()}
                withContext(Dispatchers.IO) { db.withTransaction {
                    val old=store.all().associateBy{it.key}
                    val changes=scanned.map { p ->
                        val prev=old[p.key];val cp=checkpoints[p.volume];val version=edges[p.volume]
                        val auto=Album(p.volume,p.path).key in targets
                        if(prev!=null && (cp==null||cp.version==version?.version)) {
                            val recovered=prev.status=="exception"
                            p.copy(status=if(auto&&prev.status in setOf("pending","planned","exception"))"archived" else if(recovered)"pending" else if(prev.status=="unavailable")"history" else prev.status,target=if(auto)"" else prev.target,intent=if(auto)"" else prev.intent,error=if(recovered)"" else prev.error)
                        } else p.copy(status=when{auto->"archived";cp==null->if(p.added>(version?.baseline?:Long.MAX_VALUE))"pending" else "history";cp.version!=version?.version->"pending";p.added>cp.baseline->"pending";else->"history"})
                    }
                    store.save(changes)
                    val visible=scanned.map{it.key}.toSet()
                    for(p in old.values.filter{it.key !in visible&&it.status !in setOf("trashed","unavailable")}) {
                        val loc=runCatching{media.location(p,true)}.getOrNull()
                        store.save(if(loc?.first=="@trash")p.copy(status="trashed",target="",intent="") else p.copy(status=if(p.status in setOf("pending","planned","exception"))"exception" else "unavailable",error="图片暂不可访问，请检查权限或在系统相册中核对"))
                    }
                    for((v,c) in edges)if(checkpoints[v]==null||checkpoints[v]?.version!=c.version)store.checkpoint(c)
                } }
                recover()
                val pending=withContext(Dispatchers.IO){store.all().filter{it.status in setOf("pending","planned")}.map{it.key}.toSet()}
                if(newSession||!sessionStarted){session=pending;sessionStarted=true}
            } catch(e:Exception){message="无法读取照片：${e.message ?: "请检查照片权限"}"}
            finally{busy=false}
        }
    } }
    fun includeNew(){session=session+photos.value.filter{it.status in setOf("pending","planned")}.map{it.key}}
    fun history(keys:Set<String>){session=keys;selected=emptySet();viewModelScope.launch{withContext(Dispatchers.IO){keys.forEach{key->store.get(key)?.let{store.save(it.copy(status="pending",error=""))}}}}}
    fun decide(keys:Set<String>,target:Album?){if(keys.isEmpty())return;viewModelScope.launch {
        val actionId=java.util.UUID.randomUUID().toString()
        withContext(Dispatchers.IO){db.withTransaction{keys.forEach{key->store.get(key)?.let{p->
            store.snapshot(DecisionSnapshot(actionId=actionId,photoKey=p.key,oldTarget=p.target,oldIntent=p.intent,oldStatus=p.status))
            if(target!=null&&target.volume!=p.volume){store.save(p.copy(status="exception",error="首版不支持跨存储卷移动"))}
            else store.save(p.copy(status="planned",target=target?.path?:"",intent=if(target==null)"trash" else "move",error=""))
        }}}};selected=emptySet()
    }}
    fun undoDecision(){viewModelScope.launch {withContext(Dispatchers.IO){db.withTransaction{val last=store.lastDecision();for(s in last){store.get(s.photoKey)?.let{p->if(p.status=="planned")store.save(p.copy(status=s.oldStatus,target=s.oldTarget,intent=s.oldIntent))}};last.firstOrNull()?.let{store.removeDecision(it.actionId)}}}}}
    fun submit(){if(busy||authorization!=null)return;viewModelScope.launch {
        val ready=withContext(Dispatchers.IO){store.all().filter{it.key in session&&it.status=="planned"}}
        queue.clear()
        for(kind in listOf("move","trash","restore"))for(chunk in ready.filter{it.intent==kind}.chunked(200)){
            val logs=chunk.map{Operation(photoKey=it.key,uri=it.uri,fromPath=it.path,fromName=it.name,target=it.target,kind=kind)}
            withContext(Dispatchers.IO){logs.forEach{store.operation(it)}}
            queue.add(Authorization(System.nanoTime(),kind,chunk,logs))
        }
        authorization=queue.removeFirstOrNull()
    }}
    fun authorizationFailed(reason:String){message=reason;authorized(false)}
    fun authorized(granted:Boolean){val request=authorization?:run{refresh();return};viewModelScope.launch {
        busy=true
        withContext(Dispatchers.IO){request.photos.zip(request.logs).forEach{(p,log)->
            if(!granted){store.operation(log.copy(state="cancelled"));return@forEach}
            try {
                store.operation(log.copy(state="running"))
                if(request.kind=="move")media.move(p,p.target)
                val at=media.location(p,true)?:error("照片暂不可访问")
                check(if(request.kind=="trash")at.first=="@trash" else if(request.kind=="restore")at.first!="@trash" else at.first==p.target){"操作结果尚未确认"}
                store.save(p.copy(path=if(at.first=="@trash")p.path else at.first,name=at.second,status=if(request.kind=="trash")"trashed" else "archived",intent="",target="",error=""))
                store.operation(log.copy(state="done"))
            }catch(e:Exception){store.save(p.copy(status="exception",error=e.message?:"操作失败"));store.operation(log.copy(state="failed",error=e.message?:"操作失败"))}
        }}
        selected=emptySet();busy=false
        if(!granted){queue.clear();message="未获得授权，已保留分类决定"}
        else message="本批处理结束，失败项保留在待修复中"
        authorization=queue.removeFirstOrNull()
    }}
    private suspend fun recover(){withContext(Dispatchers.IO){for(op in store.unfinished()){
        val p=store.get(op.photoKey)?:continue
        try {val loc=media.location(p,true);val complete=loc!=null&&when(op.kind){"trash"->loc.first=="@trash";"restore"->loc.first!="@trash";else->loc.first==op.target}
            if(complete){store.save(p.copy(status=if(op.kind=="trash")"trashed" else "archived",path=if(loc!!.first=="@trash")p.path else loc.first,name=loc.second,target="",intent=""));store.operation(op.copy(state="done"))}
            else store.operation(op.copy(state="interrupted",error="操作中断，分类决定已保留"))
        }catch(_:Exception){store.operation(op.copy(state="interrupted"))}
    }}}
    fun retry(p:Photo){viewModelScope.launch{withContext(Dispatchers.IO){store.save(p.copy(status=if(p.intent.isBlank())"pending" else "planned",error=""))};session=session+p.key}}
    fun undo(op:Operation){viewModelScope.launch {
        val p=withContext(Dispatchers.IO){store.get(op.photoKey)}?:return@launch
        withContext(Dispatchers.IO){db.withTransaction{store.save(p.copy(status="planned",intent=when(op.kind){"trash"->"restore";"restore"->"trash";else->"move"},target=op.fromPath));store.operation(op.copy(state="undo_planned"))}}
        session=session+p.key;message="撤销计划已保存，请在收件箱提交"
    }}
    fun dismissMessage(){message=""}
    fun trashFromBrowser(p:Photo){session=setOf(p.key);selected=emptySet();viewModelScope.launch{withContext(Dispatchers.IO){store.save(p.copy(status="planned",intent="trash",target="",error=""))}}}
}
