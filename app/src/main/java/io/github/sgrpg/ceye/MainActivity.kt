package io.github.sgrpg.ceye

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.*
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.*
import androidx.lifecycle.compose.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import coil3.compose.AsyncImage
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

private val Blue=Color(0xff4967c7)
private val Ink=Color(0xff292d36)
private val Muted=Color(0xff69717e)
private val Line=Color(0xffe7e9ee)
private val Soft=Color(0xfff0f2f6)
private val Canvas=Color(0xfff6f7f9)
class MainActivity:ComponentActivity(){override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);enableEdgeToEdge();setContent{CeYe()}}}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun CeYe(vm:AppModel=viewModel()) {
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current
    val photos by vm.photos.collectAsStateWithLifecycle()
    val logs by vm.operations.collectAsStateWithLifecycle()
    val nav=rememberNavController()
    val back by nav.currentBackStackEntryAsState()
    val page=back?.destination?.route?:"inbox"
    var grid by rememberSaveable{mutableStateOf(true)}
    var modal by remember{mutableStateOf("")}
    var viewing by remember{mutableStateOf<Photo?>(null)}
    var albumFilter by remember{mutableStateOf<Album?>(null)}
    var selectedTargetsOnly by remember{mutableStateOf(false)}
    BackHandler(enabled=page=="inbox"&&vm.inbox.isNotEmpty()){(context as? MainActivity)?.moveTaskToBack(true)}
    fun go(route:String){modal="";nav.navigate(route){launchSingleTop=true}}
    val readPermissions=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){vm.refresh(true)}
    fun requestRead(){readPermissions.launch(if(Build.VERSION.SDK_INT>=34)arrayOf(Manifest.permission.READ_MEDIA_IMAGES,Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) else if(Build.VERSION.SDK_INT>=33)arrayOf(Manifest.permission.READ_MEDIA_IMAGES) else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))}
    fun canRead():Boolean=context.checkSelfPermission(if(Build.VERSION.SDK_INT>=33)Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED || (Build.VERSION.SDK_INT>=34&&context.checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)==PackageManager.PERMISSION_GRANTED)
    val authorize=rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()){vm.authorized(it.resultCode==android.app.Activity.RESULT_OK)}
    var sent by rememberSaveable{mutableStateOf(0L)}
    val auth=vm.authorization
    LaunchedEffect(auth?.id){if(auth!=null&&sent!=auth.id){sent=auth.id;try {
        val uris=auth.photos.map{Uri.parse(it.uri)}
        val pending=if(auth.kind=="move")MediaStore.createWriteRequest(context.contentResolver,uris) else MediaStore.createTrashRequest(context.contentResolver,uris,auth.kind=="trash")
        authorize.launch(IntentSenderRequest.Builder(pending.intentSender).build())
    }catch(e:Exception){vm.authorizationFailed(e.message?:"无法请求系统授权")}}}
    DisposableEffect(lifecycle){
        val handler=Handler(Looper.getMainLooper())
        val update=Runnable{if(canRead())vm.refresh()}
        val observer=object:ContentObserver(handler){override fun onChange(selfChange:Boolean){handler.removeCallbacks(update);handler.postDelayed(update,400)}}
        val listener=LifecycleEventObserver{_,event->when(event){
            Lifecycle.Event.ON_START->{if(canRead())vm.refresh(true);context.contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,true,observer)}
            Lifecycle.Event.ON_STOP->{context.contentResolver.unregisterContentObserver(observer);handler.removeCallbacks(update)}
            else->Unit
        }}
        lifecycle.lifecycle.addObserver(listener)
        if(canRead())vm.refresh(true)
        onDispose{lifecycle.lifecycle.removeObserver(listener);context.contentResolver.unregisterContentObserver(observer);handler.removeCallbacks(update)}
    }
    LaunchedEffect(vm.session){if(vm.session.isNotEmpty()&&vm.inbox.isNotEmpty()&&vm.configured)go("inbox")}
    MaterialTheme(colorScheme=lightColorScheme(primary=Blue,onPrimary=Color.White,background=Color.White,surface=Color.White,onSurface=Ink,outline=Line),typography=Typography(bodyLarge=androidx.compose.ui.text.TextStyle(fontSize=14.sp),bodyMedium=androidx.compose.ui.text.TextStyle(fontSize=14.sp))) {
        Surface(color=Color.White,modifier=Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                if(!canRead()) {
                    Header("册页")
                    Column(Modifier.weight(1f).padding(24.dp),verticalArrangement=Arrangement.Center){Text("先允许册页访问照片",fontSize=24.sp);Spacer(Modifier.height(16.dp));Text("在你允许的范围内，发现新照片并整理到手机已有相册。",color=Muted);Spacer(Modifier.height(24.dp));Primary("选择照片访问范围",true){requestRead()}}
                } else if(!vm.configured) {
                    Header("欢迎使用册页")
                    Text("从现在开始，给新照片一个位置。",Modifier.padding(20.dp),color=Muted)
                    Text("选择归档相册",Modifier.padding(horizontal=20.dp),fontSize=20.sp)
                    Text("这些已有相册中的新图片会自动视为已归档。请不要把所有来源相册都选中。",Modifier.padding(20.dp),color=Muted)
                    AlbumChoices(vm,Modifier.weight(1f))
                    Box(Modifier.padding(20.dp)){Primary(if(vm.busy)"正在读取…" else "从现在开始",vm.targets.isNotEmpty()&&!vm.busy){vm.configure()}}
                } else {
                    val titles=mapOf("inbox" to "收件箱","albums" to "相册","photos" to "照片","settings" to "设置","history" to "历史整理","records" to "最近归档","repair" to "待修复")
                    if(page!="viewer")Header(titles[page]?:"册页",page !in listOf("inbox","albums","photos"),{if(!nav.popBackStack())go("albums")}) {
                        if(page=="inbox"){IconButton(onClick={grid=!grid}){Icon(if(grid)CeYeIcons.CropPortrait else CeYeIcons.GridView,"切换视图")};IconButton(onClick={modal="menu"}){Icon(CeYeIcons.MoreHoriz,"整理选项")}}
                        if(page in listOf("albums","photos"))IconButton(onClick={go("settings")}){Icon(CeYeIcons.Settings,"设置")}
                    }
                    if(vm.busy)LinearProgressIndicator(Modifier.fillMaxWidth(),color=Blue,trackColor=Soft)
                    if(Build.VERSION.SDK_INT>=34&&context.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES)!=PackageManager.PERMISSION_GRANTED)TextButton(onClick={requestRead()}){Text("部分图片模式 · 调整访问范围")}
                    if(vm.message.isNotEmpty())Row(Modifier.fillMaxWidth().background(Soft).padding(horizontal=20.dp),verticalAlignment=Alignment.CenterVertically){Text(vm.message,Modifier.weight(1f),fontSize=12.sp);IconButton(onClick={vm.dismissMessage()}){Icon(CeYeIcons.Close,"关闭提示")}}
                    Box(Modifier.weight(1f)) { NavHost(nav,startDestination="inbox") {
                        composable("inbox"){
                            val pending=photos.filter{it.key in vm.session&&it.status in listOf("pending","planned")}
                            if(pending.isEmpty())Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){Icon(CeYeIcons.Check,"完成",Modifier.size(48.dp),tint=Blue);Spacer(Modifier.height(20.dp));Text("这批照片，整理好了",fontSize=24.sp);Text("可以去看看你的相册了。",color=Muted,modifier=Modifier.padding(16.dp));Primary("浏览相册",true){go("albums")};if(vm.waiting>0)TextButton(onClick={vm.includeNew()}){Text("继续整理新增 ${vm.waiting} 张")}}
                            else Column(Modifier.fillMaxSize()) {
                                Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=8.dp),horizontalArrangement=Arrangement.SpaceBetween){Text("本次 ${vm.session.size} 张",color=Muted,fontSize=13.sp);Text("${pending.count{it.status=="planned"}} 张待提交",color=Muted,fontSize=13.sp)}
                                if(vm.waiting>0)TextButton(onClick={vm.includeNew()}){Text("另有 ${vm.waiting} 张新图片 · 加入本次")}
                                val current=pending.firstOrNull{it.status=="pending"}
                                if(grid) {
                                    Row(Modifier.fillMaxWidth().padding(horizontal=20.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){Text("已选 ${vm.selected.size} 张",fontSize=13.sp);TextButton(onClick={vm.select(if(pending.all{it.key in vm.selected})emptySet()else pending.map{it.key}.toSet())}){Text(if(pending.all{it.key in vm.selected})"取消全选" else "全选本次",fontSize=13.sp)}}
                                    Text("长按后滑动连续选择；从已选照片开始可连续取消。",Modifier.padding(horizontal=20.dp,vertical=6.dp),color=Muted,fontSize=12.sp)
                                    PhotoGrid(pending,vm.selected,{vm.select(it)},Modifier.weight(1f),true)
                                }else if(current!=null){
                                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())){AsyncImage(current.uri,"待分类照片",Modifier.padding(horizontal=20.dp).fillMaxWidth().aspectRatio(1.04f).clip(RoundedCornerShape(16.dp)).background(Canvas).clickable{viewing=current;go("viewer")},contentScale=ContentScale.Fit);Row(Modifier.fillMaxWidth().padding(20.dp),horizontalArrangement=Arrangement.SpaceBetween){Text(date(current.date),fontSize=12.sp,color=Muted);Text(current.path.trimEnd('/').substringAfterLast('/'),fontSize=12.sp,color=Muted)}}
                                }else Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center){Text("分类已选好，提交后完成归档",color=Muted)}
                                HorizontalDivider(color=Line)
                                Row(Modifier.fillMaxWidth().padding(horizontal=20.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){Text("归入相册");TextButton(onClick={if(!grid&&current!=null)vm.select(setOf(current.key));modal="destinations"}){Text("全部相册",fontSize=12.sp,color=Muted)}}
                                Row(Modifier.padding(horizontal=20.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                                    vm.albums.filter{it.key in vm.targets}.take(4).forEach{album->OutlinedButton(onClick={vm.decide(if(grid)vm.selected else setOfNotNull(current?.key),album)},enabled=!vm.busy&&(if(grid)vm.selected.isNotEmpty() else current!=null),modifier=Modifier.weight(1f).heightIn(min=48.dp),shape=RoundedCornerShape(12.dp),contentPadding=PaddingValues(3.dp),border=BorderStroke(1.dp,Line)){Text(album.name,maxLines=1,fontSize=13.sp)}}
                                }
                                Row(Modifier.padding(start=20.dp,end=20.dp,top=12.dp,bottom=8.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)){
                                    OutlinedIcon(CeYeIcons.Undo,"撤销分类"){vm.undoDecision()}
                                    OutlinedIcon(CeYeIcons.DeleteOutline,"移入回收站"){vm.decide(if(grid)vm.selected else setOfNotNull(current?.key),null)}
                                    Box(Modifier.weight(1f)){Primary("提交已选 · ${pending.count{it.status=="planned"}}",!vm.busy&&pending.any{it.status=="planned"}){modal="confirm"}}
                                }
                                Text("先选择相册，再提交移动",Modifier.fillMaxWidth().padding(bottom=12.dp),fontSize=11.sp,color=Muted,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        }
                        composable("albums"){
                            Column {
                                Row(Modifier.fillMaxWidth().padding(horizontal=20.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){Text("与手机已有相册保持一致",fontSize=13.sp,color=Muted);TextButton(onClick={modal="organize"}){Text("整理")}}
                                Row(Modifier.padding(horizontal=20.dp)){TextButton(onClick={selectedTargetsOnly=false}){Text("全部相册",color=if(!selectedTargetsOnly)Blue else Muted)};TextButton(onClick={selectedTargetsOnly=true}){Text("归档目标",color=if(selectedTargetsOnly)Blue else Muted)}}
                                LazyVerticalGrid(GridCells.Fixed(2),contentPadding=PaddingValues(20.dp),horizontalArrangement=Arrangement.spacedBy(14.dp),verticalArrangement=Arrangement.spacedBy(22.dp)){
                                    items(vm.albums.filter{!selectedTargetsOnly||it.key in vm.targets},key={it.key}){a->val content=photos.filter{it.volume==a.volume&&it.path==a.path&&it.status !in setOf("trashed","unavailable")};Column(Modifier.clickable{albumFilter=a;go("photos")}){AsyncImage(content.firstOrNull()?.uri,a.name,Modifier.fillMaxWidth().aspectRatio(1.12f).clip(RoundedCornerShape(16.dp)).background(Canvas),contentScale=ContentScale.Crop);Row(Modifier.padding(top=9.dp).fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(a.name,fontSize=15.sp);Text(content.size.toString(),fontSize=12.sp,color=Muted)}}}
                                }
                            }
                        }
                        composable("photos") {Column {
                            if(albumFilter!=null)Row(Modifier.padding(horizontal=20.dp),verticalAlignment=Alignment.CenterVertically){Text(albumFilter!!.name,Modifier.weight(1f));TextButton(onClick={albumFilter=null}){Text("全部图片")}}
                            BrowsingGrid(photos.filter{it.status !in setOf("trashed","unavailable")&&(albumFilter==null||Album(it.volume,it.path)==albumFilter)}){viewing=it;go("viewer")}
                        }}
                        composable("settings") {Column(Modifier.fillMaxSize().padding(horizontal=20.dp)){
                            Spacer(Modifier.height(16.dp));SettingRow("归档相册","已选 ${vm.targets.size} 个相册"){modal="targets"};SettingRow("照片权限",if(Build.VERSION.SDK_INT>=33&&context.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES)==PackageManager.PERMISSION_GRANTED)"全部图片" else if(Build.VERSION.SDK_INT<=32)"全部图片" else "部分图片"){requestRead()};Spacer(Modifier.weight(1f));Text("册页",Modifier.fillMaxWidth().padding(bottom=24.dp),color=Muted,fontSize=12.sp,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                        }}
                        composable("history"){Column{Text("选择旧照片，加入一次独立整理会话",Modifier.padding(20.dp),color=Muted);PhotoGrid(photos.filter{it.status !in setOf("trashed","unavailable")},vm.selected,{vm.select(it)},Modifier.weight(1f),false);Box(Modifier.padding(20.dp)){Primary("整理已选 ${vm.selected.size} 张",vm.selected.isNotEmpty()){vm.history(vm.selected);go("inbox")}}}}
                        composable("records"){LazyColumn(contentPadding=PaddingValues(20.dp)){items(logs,key={it.id}){op->Column(Modifier.padding(vertical=12.dp)){Text(if(op.kind=="trash")"移入回收站"else if(op.kind=="restore")"恢复图片"else"归档至 ${op.target}");Text("${date(op.time)} · ${op.state}",fontSize=12.sp,color=Muted);if(op.error.isNotEmpty())Text(op.error,fontSize=12.sp,color=Muted);if(op.state=="done")TextButton(onClick={vm.undo(op);go("inbox")}){Text("撤销")};HorizontalDivider(color=Line)}}}}
                        composable("repair"){LazyColumn(contentPadding=PaddingValues(20.dp)){items(photos.filter{it.status=="exception"},key={it.key}){p->Column(Modifier.padding(vertical=12.dp)){Text(p.name);Text(p.error,color=Muted,fontSize=12.sp);TextButton(onClick={vm.retry(p);go("inbox")}){Text("重新处理")};HorizontalDivider(color=Line)}}}}
                        composable("viewer"){viewing?.let{p->Viewer(p,{nav.popBackStack()},{
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type="image/*";putExtra(Intent.EXTRA_STREAM,Uri.parse(p.uri));addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)},"分享照片"))
                        },{vm.history(setOf(p.key));vm.select(setOf(p.key));go("inbox");modal="destinations"},{vm.trashFromBrowser(p);go("inbox")} )}}
                    } }
                    if(page in listOf("albums","photos"))Row(Modifier.fillMaxWidth().border(BorderStroke(1.dp,Line)),horizontalArrangement=Arrangement.SpaceEvenly){TextButton(onClick={albumFilter=null;go("photos")}){Icon(CeYeIcons.Image,null);Text("照片",Modifier.padding(8.dp))};TextButton(onClick={go("albums")}){Icon(CeYeIcons.Folder,null);Text("相册",Modifier.padding(8.dp))}}
                }
            }
            if(modal.isNotEmpty()) {
                ModalBottomSheet(onDismissRequest={modal=""},containerColor=Color.White,shape=RoundedCornerShape(topStart=24.dp,topEnd=24.dp)) {
                    Column(Modifier.fillMaxWidth().padding(start=20.dp,end=20.dp,bottom=24.dp)){
                        when(modal){
                            "targets"->{Text("归档相册",fontSize=20.sp);Text("这些已有相册中的新图片会自动归档",fontSize=13.sp,color=Muted);AlbumChoices(vm,Modifier.heightIn(max=400.dp))}
                            "destinations"->{Text("选择相册",fontSize=20.sp);LazyColumn(Modifier.heightIn(max=400.dp)){items(vm.albums,key={it.key}){a->SettingRow(a.name,a.path){vm.decide(vm.selected,a);modal=""}}}}
                            "confirm"->{Text("确认归档",fontSize=20.sp);Text("移动至手机已有相册",color=Muted,fontSize=13.sp);photos.filter{it.key in vm.session&&it.status=="planned"}.groupBy{if(it.intent=="trash")"系统回收站"else if(it.intent=="restore")"恢复图片"else it.target}.forEach{(name,items)->Row(Modifier.fillMaxWidth().padding(vertical=14.dp),horizontalArrangement=Arrangement.SpaceBetween){Text(name,Modifier.weight(1f));Text("${items.size} 张")}};Text("按系统提示确认后执行；取消会保留分类决定。",fontSize=12.sp,color=Muted,modifier=Modifier.padding(vertical=12.dp));Primary("确认归档",!vm.busy){modal="";vm.submit()}}
                            "organize"->{Text("整理",fontSize=20.sp);SettingRow("历史整理","选择旧照片重新分类"){vm.select(emptySet());go("history")};SettingRow("最近归档","查看记录或撤销归档"){go("records")};if(photos.any{it.status=="exception"})SettingRow("待修复","有 ${photos.count{it.status=="exception"}} 张需要处理"){go("repair")}}
                            "menu"->{Text("整理选项",fontSize=20.sp);SettingRow("设置","归档相册与照片权限"){go("settings")};SettingRow("待修复","查看暂时无法处理的图片"){go("repair")};SettingRow("暂停整理","决定已保存，下次打开继续"){modal="";(context as? MainActivity)?.moveTaskToBack(true)}}
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun Header(title:String,back:Boolean=false,onBack:()->Unit={},actions:@Composable RowScope.()->Unit={}){Row(Modifier.fillMaxWidth().padding(start=20.dp,end=20.dp,top=8.dp,bottom=4.dp).heightIn(min=52.dp),verticalAlignment=Alignment.CenterVertically){if(back)IconButton(onClick=onBack){Icon(CeYeIcons.ArrowBack,"返回")};Text(title,fontSize=26.sp,fontWeight=FontWeight.Medium,modifier=Modifier.weight(1f));actions()}}
@Composable private fun Primary(label:String,enabled:Boolean,onClick:()->Unit){Button(onClick,enabled=enabled,modifier=Modifier.fillMaxWidth().heightIn(min=50.dp),shape=RoundedCornerShape(12.dp),colors=ButtonDefaults.buttonColors(containerColor=Blue,contentColor=Color.White,disabledContainerColor=Soft,disabledContentColor=Muted),contentPadding=PaddingValues(12.dp)){Text(label,fontSize=14.sp,fontWeight=FontWeight.Medium)}}
@Composable private fun OutlinedIcon(icon:androidx.compose.ui.graphics.vector.ImageVector,label:String,onClick:()->Unit){OutlinedButton(onClick,modifier=Modifier.size(50.dp),shape=RoundedCornerShape(12.dp),contentPadding=PaddingValues(8.dp),border=BorderStroke(1.dp,Line)){Icon(icon,label,tint=Ink,modifier=Modifier.size(18.dp))}}
@Composable private fun SettingRow(title:String,subtitle:String,onClick:()->Unit){Column{Row(Modifier.fillMaxWidth().heightIn(min=76.dp).clickable(onClick=onClick).padding(vertical=16.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(title,fontSize=14.sp);Text(subtitle,color=Muted,fontSize=12.sp)};Icon(CeYeIcons.ChevronRight,null,tint=Muted,modifier=Modifier.size(18.dp))};HorizontalDivider(color=Line)}}
@Composable private fun AlbumChoices(vm:AppModel,modifier:Modifier){LazyColumn(modifier.padding(horizontal=20.dp)){items(vm.albums,key={it.key}){a->Row(Modifier.fillMaxWidth().clickable{vm.target(a,a.key !in vm.targets)}.padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(a.name);Text("${a.volume} / ${a.path}",fontSize=11.sp,color=Muted)};Checkbox(a.key in vm.targets,{vm.target(a,it)})}}}}
private fun date(time:Long)=SimpleDateFormat("MM月dd日 HH:mm",Locale.CHINA).format(Date(time))
private fun day(time:Long)=SimpleDateFormat("yyyy年MM月dd日",Locale.CHINA).format(Date(time))

@Composable private fun PhotoGrid(data:List<Photo>,selected:Set<String>,onSelect:(Set<String>)->Unit,modifier:Modifier,gestures:Boolean){
    val state=rememberLazyGridState();val density=LocalDensity.current;val scope=rememberCoroutineScope();val haptic=LocalHapticFeedback.current
    val selection by rememberUpdatedState(selected);val update by rememberUpdatedState(onSelect);val currentData by rememberUpdatedState(data)
    var point by remember{mutableStateOf<Offset?>(null)};var anchor by remember{mutableStateOf<String?>(null)};var baseline by remember{mutableStateOf(setOf<String>())};var adding by remember{mutableStateOf(true)}
    fun hit(p:Offset):String?=state.layoutInfo.visibleItemsInfo.firstOrNull{p.x>=it.offset.x&&p.x<it.offset.x+it.size.width&&p.y>=it.offset.y&&p.y<it.offset.y+it.size.height}?.key?.toString()?.takeIf{key->currentData.any{it.key==key}}
    fun extend(){val a=anchor?:return;val e=point?.let{hit(it)}?:return;update(selectionRange(currentData.map{it.key},baseline,a,e,adding))}
    LaunchedEffect(anchor){if(anchor!=null)while(true){val p=point;if(p!=null){val zone=with(density){56.dp.toPx()};val bottom=state.layoutInfo.viewportEndOffset;val speed=when{p.y<zone->-((zone-p.y)/zone).coerceIn(0f,1f);p.y>bottom-zone->((p.y-bottom+zone)/zone).coerceIn(0f,1f);else->0f};if(speed!=0f){state.scrollBy(speed*with(density){10.dp.toPx()});extend()}};delay(16)}}
    val config=LocalViewConfiguration.current
    CompositionLocalProvider(LocalViewConfiguration provides object:ViewConfiguration by config{override val longPressTimeoutMillis:Long=420}) {
    LazyVerticalGrid(GridCells.Fixed(3),state=state,contentPadding=PaddingValues(horizontal=20.dp,vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(5.dp),verticalArrangement=Arrangement.spacedBy(5.dp),modifier=modifier.then(if(gestures)Modifier.pointerInput(Unit){detectDragGesturesAfterLongPress(onDragStart={p->val key=hit(p);if(key!=null){baseline=selection;adding=key !in selection;anchor=key;point=p;extend();haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)}},onDragEnd={anchor=null;point=null},onDragCancel={if(anchor!=null)update(baseline);anchor=null;point=null},onDrag={change,_->point=change.position;if(anchor!=null){change.consume();extend()}})}else Modifier)){
        data.groupBy{day(it.date)}.forEach{(date,group)->item(key="date:$date",span={GridItemSpan(maxLineSpan)}){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){Text("$date · ${group.size} 张",fontSize=13.sp,color=Muted);TextButton(onClick={val keys=group.map{it.key}.toSet();onSelect(if(selected.containsAll(keys))selected-keys else selected+keys)}){Text(if(selected.containsAll(group.map{it.key}))"取消当天"else"选择当天",fontSize=12.sp)}}};items(group,key={it.key}){p->val yes=p.key in selected;Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(7.dp)).clickable{onSelect(if(yes)selected-p.key else selected+p.key)}.background(Canvas)){
            AsyncImage(p.uri,p.name,Modifier.fillMaxSize().alpha(if(yes).78f else 1f),contentScale=ContentScale.Crop)
            Box(Modifier.fillMaxSize().then(if(yes)Modifier.border(3.dp,Blue,RoundedCornerShape(7.dp))else Modifier))
            Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(21.dp).background(if(yes)Blue else Color(0x35252c38),RoundedCornerShape(50)).border(1.dp,if(yes)Blue else Color.White,RoundedCornerShape(50)),contentAlignment=Alignment.Center){if(yes)Icon(CeYeIcons.Check,"已选",Modifier.size(14.dp),tint=Color.White)}
            if(p.status=="planned")Text(if(p.intent=="trash")"待回收"else p.target.trimEnd('/').substringAfterLast('/'),Modifier.align(Alignment.BottomStart).padding(4.dp).background(Color.White,RoundedCornerShape(4.dp)).padding(horizontal=5.dp,vertical=2.dp),fontSize=11.sp,color=Blue)
        }}}
    }}
}
@Composable private fun BrowsingGrid(data:List<Photo>,onOpen:(Photo)->Unit){LazyVerticalGrid(GridCells.Fixed(3),contentPadding=PaddingValues(20.dp),horizontalArrangement=Arrangement.spacedBy(5.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){items(data,key={it.key}){p->AsyncImage(p.uri,p.name,Modifier.aspectRatio(1f).clip(RoundedCornerShape(7.dp)).background(Canvas).clickable{onOpen(p)},contentScale=ContentScale.Crop)}}}
@Composable private fun Viewer(p:Photo,onBack:()->Unit,onShare:()->Unit,onMove:()->Unit,onTrash:()->Unit){var scale by remember(p.key){mutableFloatStateOf(1f)};var offset by remember(p.key){mutableStateOf(Offset.Zero)};Column(Modifier.fillMaxSize().background(Color(0xff111318))){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){IconButton(onClick=onBack){Icon(CeYeIcons.ArrowBack,"返回",tint=Color.White)};Text(p.name,color=Color.White,fontSize=14.sp)};Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().pointerInput(p.key){detectTransformGestures{_,pan,zoom,_->scale=(scale*zoom).coerceIn(1f,5f);offset=if(scale==1f)Offset.Zero else offset+pan}}.pointerInput(p.key){detectTapGestures(onDoubleTap={scale=if(scale>1f)1f else 2.5f;offset=Offset.Zero})},contentAlignment=Alignment.Center){AsyncImage(p.uri,p.name,Modifier.fillMaxSize().graphicsLayer{scaleX=scale;scaleY=scale;translationX=offset.x;translationY=offset.y},contentScale=ContentScale.Fit)};Row(Modifier.fillMaxWidth().padding(vertical=12.dp),horizontalArrangement=Arrangement.SpaceEvenly){TextButton(onClick=onShare){Text("分享",color=Color.White)};TextButton(onClick=onMove){Text("调整相册",color=Color.White)};TextButton(onClick=onTrash){Text("回收站",color=Color.White)}}}}
