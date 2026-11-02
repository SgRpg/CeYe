const fs=require('node:fs');
const vm=require('node:vm');
const assert=require('node:assert/strict');
const path=require('node:path');
const html=fs.readFileSync(path.join(__dirname,'approved-ui-v1.html'),'utf8');
for(const match of html.matchAll(/<script>([\s\S]*?)<\/script>/g))new Function(match[1]);
const range=html.slice(html.indexOf(' function selectionRange('),html.indexOf(' function paintSelection('));
const gesture=html.slice(html.indexOf(' function installGridGesture('),html.indexOf(' installGridGesture();'));
function harness(initial=[]){
 let now=0,seq=0,captured=false,paintCount=0;
 const timers=new Map(),frames=new Map(),handlers={root:{},window:{},document:{}};
 const listen=(scope)=>(name,fn)=>{(handlers[scope][name]??=[]).push(fn);};
 const hint={textContent:'',classList:{toggle(){}}},live={textContent:''};
 const scroller={scrollTop:0,classList:{add(){},remove(){}},getBoundingClientRect:()=>({left:0,right:300,top:0,bottom:300}),contains:el=>!!el?.dataset?.photo,setPointerCapture(){captured=true;},hasPointerCapture:()=>captured,releasePointerCapture(){captured=false;}};
 const cells=Array.from({length:36},(_,id)=>({dataset:{photo:String(id)},closest(selector){return selector==='[data-photo]'?this:selector==='#pa-grid-scroll'?scroller:null;}}));
 const state={selected:[...initial],done:[],view:'grid',modal:null};
 const ctx={state,photos:cells.map((_,id)=>({id})),root:{addEventListener:listen('root')},screen:{querySelector:s=>s==='#pa-selection-hint'?hint:s==='#pa-selection-live'?live:null},window:{addEventListener:listen('window')},document:{hidden:false,addEventListener:listen('document'),elementFromPoint(x,y){const index=Math.floor((y+scroller.scrollTop)/100)*3+Math.floor(x/100);return cells[Math.min(35,Math.max(0,index))];}},paintSelection:()=>paintCount++,persist(){},performance:{now:()=>now},setTimeout(fn,ms){const id=++seq;timers.set(id,{fn,at:now+ms});return id;},clearTimeout:id=>timers.delete(id),requestAnimationFrame(fn){const id=++seq;frames.set(id,fn);return id;},cancelAnimationFrame:id=>frames.delete(id)};
 vm.createContext(ctx);vm.runInContext(range+gesture+'\nthis.compute=selectionRange;installGridGesture();',ctx);
 const event=(id=0,x=50,y=50)=>({target:cells[id],pointerType:'mouse',pointerId:7,button:0,clientX:x,clientY:y,cancelable:true,prevented:false,stopped:false,preventDefault(){this.prevented=true;},stopImmediatePropagation(){this.stopped=true;}});
 const fire=(scope,type,e)=>{for(const fn of handlers[scope][type]||[])fn(e);return e;};
 const advance=(ms)=>{now+=ms;for(const [id,t] of [...timers])if(t.at<=now){timers.delete(id);t.fn();}};
 const frame=(ms=16)=>{now+=ms;const callbacks=[...frames.values()];frames.clear();callbacks.forEach(fn=>fn(now));};
 return {ctx,state,event,fire,advance,frame,scroller,hint,live,get paintCount(){return paintCount;}};
}
let checks=0;
function check(name,fn){fn();console.log('PASS',name);checks++;}
check('range selection preserves unrelated choices and fills skipped cells',()=>{const h=harness();assert.deepEqual(Array.from(h.ctx.compute([0,1,2,3,4,5],[5],0,3,true)),[5,0,1,2,3]);});
check('reverse direction deselects the range while preserving unrelated choices',()=>{const h=harness();assert.deepEqual(Array.from(h.ctx.compute([0,1,2,3,4,5],[0,1,2,3,5],3,1,false)),[0,5]);});
check('long press selects once and suppresses release click',()=>{const h=harness();h.fire('root','pointerdown',h.event());h.advance(430);assert.deepEqual(Array.from(h.state.selected),[0]);h.fire('window','pointerup',h.event());const click=h.fire('root','click',h.event());assert(click.prevented&&click.stopped);assert.equal(h.live.textContent,'已选 1 张照片');});
check('dragging after long press extends then shrinks a contiguous range',()=>{const h=harness([10]);h.fire('root','pointerdown',h.event(1,150,50));h.advance(430);const move=h.fire('root','pointermove',h.event(7,150,250));assert(move.prevented);assert.deepEqual(Array.from(h.state.selected),[10,1,2,3,4,5,6,7]);h.fire('root','pointermove',h.event(2,250,50));assert.deepEqual(Array.from(h.state.selected),[10,1,2]);h.fire('window','pointerup',h.event());});
check('starting on a selected cell latches deselection for the entire gesture',()=>{const h=harness([0,1,2,3,9]);h.fire('root','pointerdown',h.event());h.advance(430);h.fire('root','pointermove',h.event(4,150,150));assert.deepEqual(Array.from(h.state.selected),[9]);h.fire('root','pointermove',h.event(0,50,50));assert.deepEqual(Array.from(h.state.selected),[1,2,3,9]);});
check('movement before the threshold leaves normal scrolling available',()=>{const h=harness();h.fire('root','pointerdown',h.event());const move=h.fire('root','pointermove',h.event(0,50,80));h.advance(1000);assert(!move.prevented);assert.equal(h.paintCount,0);assert.deepEqual(Array.from(h.state.selected),[]);h.fire('window','pointerup',h.event());});
check('brief taps remain available to native click selection',()=>{const h=harness();h.fire('root','pointerdown',h.event());h.advance(100);h.fire('window','pointerup',h.event());const click=h.fire('root','click',h.event());assert(!click.prevented&&!click.stopped);});
check('holding near the bottom scrolls and selects newly revealed rows',()=>{const h=harness();h.fire('root','pointerdown',h.event(6,50,295));h.advance(430);for(let i=0;i<40;i++)h.frame();assert(h.scroller.scrollTop>250);assert(h.state.selected.some(id=>id>8));h.fire('window','pointerup',h.event());const end=h.scroller.scrollTop;h.frame();assert.equal(h.scroller.scrollTop,end);});
check('holding near the top scrolls upward',()=>{const h=harness();h.scroller.scrollTop=400;h.fire('root','pointerdown',h.event(12,50,5));h.advance(430);for(let i=0;i<10;i++)h.frame();assert(h.scroller.scrollTop<400);h.fire('window','pointerup',h.event());});
check('touch cancellation restores the selection from before the gesture',()=>{const h=harness([9]);const touch=(id,x,y)=>({...h.event(id,x,y),touches:[{clientX:x,clientY:y}]});h.fire('root','touchstart',touch(0,50,50));h.advance(430);const move=h.fire('root','touchmove',touch(5,250,150));assert(move.prevented);assert(h.state.selected.length>1);h.fire('root','touchcancel',touch(5,250,150));assert.deepEqual(Array.from(h.state.selected),[9]);assert.equal(h.live.textContent,'已取消本次滑选');});
check('a second finger cancels selection without destroying previous choices',()=>{const h=harness([9]);const e=h.event();e.touches=[{clientX:50,clientY:50}];h.fire('root','touchstart',e);h.advance(430);e.touches.push({clientX:80,clientY:50});h.fire('root','touchstart',e);assert.deepEqual(Array.from(h.state.selected),[9]);});
console.log(`${checks} gesture checks passed; inline JavaScript syntax OK.`);
