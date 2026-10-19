// Rendert index.html?video als MP4 (1080x1920, TikTok-Format) Bild für Bild.
// Die Zeit im Browser läuft simuliert, dadurch ruckelt nichts und das Timing ist exakt.
// Aufruf: node render.cjs [ausgabe.mp4] [fps] [voice.json audio.mp3]
// Mit voice.json + Audiodatei richten sich die Untertitel nach den gesprochenen Wörtern,
// und jeder Satz wird genau dort ins Video gelegt, wo sein Untertitel erscheint.
const fs=require('fs'),path=require('path'),{spawn}=require('child_process');
let pw;try{pw=require('playwright');}catch{pw=require('/opt/node22/lib/node_modules/playwright');}
const OUT=path.resolve(process.argv[2]||'horror.mp4'),FPS=+(process.argv[3]||60),MAX_S=180;
const VOICE=process.argv[4]?JSON.parse(fs.readFileSync(process.argv[4],'utf8')):null,AUDIO=process.argv[5]&&path.resolve(process.argv[5]);
const PRE=.15,POST=.25; // Vorlauf/Nachlauf pro Satz in Sekunden

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

const run=(args)=>new Promise((res,rej)=>{const p=spawn('ffmpeg',args,{stdio:['ignore','inherit','inherit']});p.on('close',c=>c?rej(new Error('ffmpeg '+c)):res());});

(async()=>{
  const b=await pw.chromium.launch();
  const p=await b.newPage({viewport:{width:1080,height:1920},deviceScaleFactor:1});
  await p.addInitScript(VIRTUAL_TIME);
  if(VOICE)await p.addInitScript(lines=>{window.VOICE={lines,i:0,log:[]};},VOICE.lines.map(l=>({w:l.w,dur:l.e-l.s+POST+.35})));
  p.on('pageerror',e=>console.error('Seitenfehler:',e.message));
  await p.goto('file://'+path.join(__dirname,'index.html')+'?video');
  await p.evaluate(()=>document.fonts.ready);await p.waitForTimeout(500);
  const cdp=await p.context().newCDPSession(p);
  const VIDEO=AUDIO?OUT.replace(/\.mp4$/,'')+'.stumm.mp4':OUT;
  const ff=spawn('ffmpeg',['-y','-loglevel','error','-f','image2pipe','-c:v','mjpeg','-framerate',String(FPS),'-i','-',
    '-f','lavfi','-i','anullsrc=r=48000:cl=stereo','-shortest',
    '-c:v','libx264','-preset','medium','-b:v','8M','-maxrate','10M','-bufsize','16M','-pix_fmt','yuv420p','-r',String(FPS),'-c:a','aac','-b:a','128k','-movflags','+faststart',VIDEO],{stdio:['pipe','inherit','inherit']});
  await p.evaluate(()=>{story();});
  let n=0;
  for(let i=0;i<MAX_S*FPS;i++){
    await p.evaluate(t=>__advanceTo(t),i*1000/FPS);
    const{data}=await cdp.send('Page.captureScreenshot',{format:'jpeg',quality:92});
    if(!ff.stdin.write(Buffer.from(data,'base64')))await new Promise(r=>ff.stdin.once('drain',r));
    n=i+1;if(i%(FPS*5)===0)console.log(`${(i/FPS).toFixed(0)} s gerendert`);
    if(await p.evaluate(()=>window.DONE===true))break;
  }
  const log=VOICE?await p.evaluate(()=>VOICE.log):[];
  ff.stdin.end();await new Promise(r=>ff.on('close',r));await b.close();
  const dur=n/FPS;console.log(`Video fertig: ${dur.toFixed(2)} s, ${n} Frames`);
  if(!AUDIO){console.log('Video:',OUT);return;}

  // Tonspur: jeden Satz aus der Aufnahme schneiden und an den Start seines Untertitels legen
  if(log.length!==VOICE.lines.length)throw new Error(`Untertitel ${log.length} ≠ Sätze ${VOICE.lines.length}`);
  const parts=VOICE.lines.map((l,i)=>{const s=Math.max(l.s-PRE,i?VOICE.lines[i-1].e+.02:0),e=l.e+POST,d=Math.round((log[i]/1000-(l.s-s))*1000);
    return `[1:a]atrim=start=${s.toFixed(3)}:end=${e.toFixed(3)},asetpts=PTS-STARTPTS,afade=t=in:d=0.03,afade=t=out:st=${(e-s-.06).toFixed(3)}:d=0.06,adelay=${d}|${d}[a${i}]`;});
  const mix=`${parts.join(';')};${VOICE.lines.map((_,i)=>`[a${i}]`).join('')}amix=inputs=${VOICE.lines.length}:normalize=0,loudnorm=I=-14:TP=-1.5:LRA=11,apad[aout]`;
  await run(['-y','-loglevel','error','-i',VIDEO,'-i',AUDIO,'-filter_complex',mix,'-map','0:v','-map','[aout]','-c:v','copy','-c:a','aac','-b:a','192k','-ar','48000','-t',dur.toFixed(3),'-movflags','+faststart',OUT]);
  fs.writeFileSync(OUT.replace(/\.mp4$/,'')+'.untertitel.json',JSON.stringify(log.map((t,i)=>({start:+(t/1000).toFixed(3),text:VOICE.lines[i].text})),null,1));
  fs.unlinkSync(VIDEO);console.log('Video mit Ton:',OUT);
})();
