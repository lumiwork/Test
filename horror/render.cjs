// Rendert index.html?video als MP4 (1080x1920, TikTok-Format) Bild für Bild.
// Die Zeit im Browser läuft simuliert, dadurch ruckelt nichts und das Timing ist exakt.
// Aufruf: node render.cjs [ausgabe.mp4] [fps]
const path=require('path'),{spawn}=require('child_process');
let pw;try{pw=require('playwright');}catch{pw=require('/opt/node22/lib/node_modules/playwright');}
const OUT=path.resolve(process.argv[2]||'horror.mp4'),FPS=+(process.argv[3]||60),MAX_S=150;

// Ersetzt Uhr, Timer und requestAnimationFrame durch eine virtuelle Zeit
const VIRTUAL_TIME=()=>{
  const mc=new MessageChannel(),q=[];mc.port1.onmessage=()=>q.shift()();
  const flush=()=>new Promise(r=>{q.push(r);mc.port2.postMessage(0);});
  let vt=0,id=1,rafs=[];const timers=new Map();
  performance.now=()=>vt;const D0=Date.now();Date.now=()=>D0+vt;
  window.setTimeout=(fn,ms=0,...a)=>{const i=id++;timers.set(i,{t:vt+Math.max(0,+ms||0),fn,a});return i;};
  window.setInterval=(fn,ms=0,...a)=>{const i=id++;const tick=()=>{fn(...a);if(timers.has(i))timers.get(i).t+=Math.max(1,ms);};timers.set(i,{t:vt+Math.max(1,ms),fn:tick,a:[],rep:1});return i;};
  window.clearTimeout=window.clearInterval=i=>timers.delete(i);
  window.requestAnimationFrame=fn=>{rafs.push(fn);return rafs.length;};
  window.cancelAnimationFrame=()=>{};
  window.__advanceTo=async target=>{
    for(;;){let n=null;for(const[i,t]of timers)if(t.t<=target&&(!n||t.t<n[1].t))n=[i,t];if(!n)break;
      vt=Math.max(vt,n[1].t);if(!n[1].rep)timers.delete(n[0]);n[1].fn(...n[1].a);await flush();}
    vt=target;const r=rafs;rafs=[];r.forEach(f=>f(vt));await flush();
    // CSS-Animationen/Transitions an die virtuelle Zeit koppeln
    for(const a of document.getAnimations()){if(a.__vs==null){a.__vs=vt;a.pause();}
      const ct=vt-a.__vs,end=a.effect.getComputedTiming().endTime;
      if(isFinite(end)&&ct>=end)a.finish();else a.currentTime=ct;}
  };
};

(async()=>{
  const b=await pw.chromium.launch();
  const p=await b.newPage({viewport:{width:1080,height:1920},deviceScaleFactor:1});
  await p.addInitScript(VIRTUAL_TIME);
  p.on('pageerror',e=>console.error('Seitenfehler:',e.message));
  await p.goto('file://'+path.join(__dirname,'index.html')+'?video');
  await p.evaluate(()=>document.fonts.ready);await p.waitForTimeout(500);
  const cdp=await p.context().newCDPSession(p);
  const ff=spawn('ffmpeg',['-y','-loglevel','error','-f','image2pipe','-c:v','mjpeg','-framerate',String(FPS),'-i','-',
    '-f','lavfi','-i','anullsrc=r=48000:cl=stereo','-shortest',
    '-c:v','libx264','-preset','medium','-crf','18','-pix_fmt','yuv420p','-r',String(FPS),'-c:a','aac','-b:a','128k','-movflags','+faststart',OUT],{stdio:['pipe','inherit','inherit']});
  await p.evaluate(()=>{story();});
  for(let i=0;i<MAX_S*FPS;i++){
    await p.evaluate(t=>__advanceTo(t),i*1000/FPS);
    const{data}=await cdp.send('Page.captureScreenshot',{format:'jpeg',quality:92});
    if(!ff.stdin.write(Buffer.from(data,'base64')))await new Promise(r=>ff.stdin.once('drain',r));
    if(i%(FPS*5)===0)console.log(`${(i/FPS).toFixed(0)} s gerendert`);
    if(await p.evaluate(()=>window.DONE===true)){console.log(`fertig: ${((i+1)/FPS).toFixed(2)} s, ${i+1} Frames`);break;}
  }
  ff.stdin.end();await new Promise(r=>ff.on('close',r));await b.close();console.log('Video:',OUT);
})();
