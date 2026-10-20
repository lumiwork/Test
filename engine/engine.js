// Gemeinsame Engine für alle Videos: Bühne, Strichmännchen, Umgebung, Untertitel, Karten, Sounds, Ablauf.
// Jede Seite definiert danach `scenes` (Objekt mit draw(t)) und `story()` und ruft boot(<erste Szene>) auf.
// ---------- Bühne ----------
const stage=document.getElementById('stage'),cv=document.getElementById('cv'),ctx=cv.getContext('2d');
const dk=document.createElement('canvas'),dctx=dk.getContext('2d');
const vgC=document.createElement('canvas');
const VW=900,VH=1600;let K=1;
function resize(){
  const w=Math.min(innerWidth,innerHeight*9/16),h=w*16/9,dpr=Math.min(devicePixelRatio||1,2);
  stage.style.width=w+'px';stage.style.height=h+'px';stage.style.fontSize=(w/9)+'px';
  cv.width=dk.width=vgC.width=Math.round(w*dpr);cv.height=dk.height=vgC.height=Math.round(h*dpr);K=cv.width/VW;
  const g=vgC.getContext('2d'),W=vgC.width,H=vgC.height,r=g.createRadialGradient(W/2,H/2,W*.35,W/2,H/2,H*.62);
  r.addColorStop(0,'rgba(0,0,0,0)');r.addColorStop(1,'rgba(0,0,0,.7)');g.fillStyle=r;g.fillRect(0,0,W,H);
}
addEventListener('resize',resize);resize();
const base=c=>c.setTransform(K,0,0,K,0,0);
const rnd=(a,b)=>a+Math.random()*(b-a);
const ease=x=>x<=0?0:x>=1?1:x*x*(3-2*x);
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
const pt=(p,a,len,d)=>[p[0]+Math.sin(a)*len*d,p[1]+Math.cos(a)*len];
function rr(c,x,y,w,h,r){c.beginPath();c.moveTo(x+r,y);c.arcTo(x+w,y,x+w,y+h,r);c.arcTo(x+w,y+h,x,y+h,r);c.arcTo(x,y+h,x,y,r);c.arcTo(x,y,x+w,y,r);c.closePath();}

// ---------- Texturen (flacher Stil) ----------
function mk(w,h,f){const c=document.createElement('canvas');c.width=w;c.height=h;f(c.getContext('2d'),w,h);return c;}
function planks(b,seam,h){return mk(320,h,(g,w,hh)=>{g.fillStyle=b;g.fillRect(0,0,w,hh);g.fillStyle=seam;g.fillRect(0,hh-4,w,4);g.fillRect(150,0,3,hh-4);
  g.strokeStyle='rgba(255,255,255,.04)';g.lineWidth=2;for(let i=0;i<4;i++){const y=8+i*(hh-14)/4;g.beginPath();g.moveTo(0,y);g.bezierCurveTo(110,y+3,210,y-3,320,y);g.stroke();}});}
const extC=planks('#273055','#1a213f',46),floorC=planks('#1b2140','#11162f',40);
const wallC=mk(72,72,(g)=>{g.fillStyle='#262d52';g.fillRect(0,0,72,72);g.fillStyle='#20274a';g.beginPath();g.moveTo(36,6);g.lineTo(62,36);g.lineTo(36,66);g.lineTo(10,36);g.closePath();g.fill();g.fillStyle='#303a68';g.beginPath();g.arc(36,36,5,0,7);g.fill();});
const stripeC=mk(36,36,(g)=>{g.strokeStyle='rgba(255,255,255,.035)';g.lineWidth=6;g.beginPath();for(const o of[-36,0,36]){g.moveTo(o-10,46);g.lineTo(o+46,-10);}g.stroke();});
const fogC=mk(256,128,(g,w,h)=>{const r=g.createRadialGradient(w/2,h/2,0,w/2,h/2,w/2);r.addColorStop(0,'rgba(120,140,200,.45)');r.addColorStop(1,'rgba(120,140,200,0)');g.fillStyle=r;g.scale(1,.5);g.fillRect(0,0,w,h*2);});
const noise=[0,1,2].map(()=>mk(200,200,(g,w,h)=>{const d=g.createImageData(w,h);for(let i=0;i<d.data.length;i+=4){const v=Math.random()*255;d.data[i]=d.data[i+1]=d.data[i+2]=v;d.data[i+3]=Math.random()*40;}g.putImageData(d,0,0);}));
const P={};const pat=(n,c)=>P[n]||(P[n]=ctx.createPattern(c,'repeat'));

// ---------- Strichmännchen (breit, mit Knien & Ellbogen) ----------
const TOM={color:'#a9b8e2',back:'#7d8cb8',pack:1};
function man(o){
  const s=o.s||1,d=o.dir||1,t=o.t||0,w=o.walk||0,ph=o.phase||0,c=o.color||'#f1f3fa',cb=o.back||'#aeb6d0';
  const shake=o.scared?Math.sin(t*37)*1.8:0,breathe=Math.sin(t*2.1)*1.8*(1-w);
  const hip=[shake,-104*Math.cos(.5*w*Math.abs(Math.sin(ph)))+(o.crouch||0)];
  const lean=(o.lean||0)+w*.08;
  const neck=[hip[0]+Math.sin(lean)*84*d,hip[1]-Math.cos(lean)*84+breathe];
  const sh=[hip[0]+(neck[0]-hip[0])*.88,hip[1]+(neck[1]-hip[1])*.88];
  const hd=[neck[0]+Math.sin(lean)*30*d,neck[1]-34];
  const legs=[0,1].map(i=>{const lp=ph+i*Math.PI,ta=Math.sin(lp)*.5*w+(1-w)*(i?.09:-.09)+(o.legOff||0),b=Math.max(0,Math.cos(lp))*1.05*w,k=pt(hip,ta,52,d);return[k,pt(k,ta-b,52,d)];});
  let aim=null;
  const arms=[0,1].map(i=>{const ap=ph+i*Math.PI;let ua=-Math.sin(ap)*.55*w+(1-w)*(i?.14:-.12),fa=ua+.3+.3*w;
    if(o.scared&&!(i===1&&o.aim!=null)){ua=.35+i*.1;fa=2.4;}
    if(i===1&&o.aim!=null){ua=o.aim;fa=o.aim+.06;aim=fa;}
    const e=pt(sh,ua,44,d);return[e,pt(e,fa,42,d)];});
  const limb=(col,wd,...p)=>{ctx.strokeStyle=col;ctx.lineWidth=wd;ctx.beginPath();ctx.moveTo(p[0][0],p[0][1]);for(let i=1;i<p.length;i++)ctx.lineTo(p[i][0],p[i][1]);ctx.stroke();};
  ctx.save();ctx.translate(o.x,o.y);ctx.scale(s,s);ctx.lineCap=ctx.lineJoin='round';
  ctx.fillStyle='rgba(0,0,0,.35)';ctx.beginPath();ctx.ellipse(hip[0],2,46,9,0,0,7);ctx.fill();
  limb(cb,20,hip,...legs[0]);limb(cb,18,sh,...arms[0]);
  if(o.pack){const bx=hip[0]+(neck[0]-hip[0])*.55-22*d,by=hip[1]+(neck[1]-hip[1])*.55;ctx.fillStyle='#3a4670';rr(ctx,bx-18,by-34,36,66,12);ctx.fill();ctx.fillStyle='#2c375c';rr(ctx,bx-14,by+4,28,20,6);ctx.fill();}
  limb(c,30,hip,neck);limb(c,21,hip,...legs[1]);
  ctx.fillStyle=c;ctx.beginPath();ctx.arc(hd[0],hd[1],32,0,7);ctx.fill();
  // Augen
  const ex=o.front?[-10,10]:[4*d,17*d],ey=hd[1]-3+(o.look||0),blink=((t+(o.seed||0))%3.6)<.13&&!o.scared;
  ctx.fillStyle=ctx.strokeStyle=o.eye||'#141a33';
  for(const e of ex){if(blink){ctx.lineWidth=3;ctx.beginPath();ctx.moveTo(hd[0]+e-4,ey);ctx.lineTo(hd[0]+e+4,ey);ctx.stroke();}else{ctx.beginPath();ctx.arc(hd[0]+e,ey,o.scared?5:3.8,0,7);ctx.fill();}}
  if(o.scared){ctx.lineWidth=3;ctx.beginPath();ctx.arc(hd[0]+(o.front?0:11*d),hd[1]+13,4.5,0,7);ctx.stroke();}
  if(aim!=null){const H=arms[1][1];ctx.save();ctx.translate(H[0],H[1]);ctx.rotate(Math.atan2(Math.cos(aim),Math.sin(aim)*d));ctx.fillStyle='#39426a';rr(ctx,-4,-8,32,16,5);ctx.fill();ctx.fillStyle='#fff3c4';ctx.fillRect(26,-8,5,16);ctx.restore();}
  limb(c,19,sh,...arms[1]);
  ctx.restore();
  const head=[o.x+hd[0]*s,o.y+hd[1]*s];
  if(o.label&&!S.black)TAGS.push([head[0],head[1]-32*s-14,...o.label]);
  let tip=null,ang=null;
  if(aim!=null){const H=arms[1][1],dx=Math.sin(aim)*d,dy=Math.cos(aim);ang=Math.atan2(dy,dx);tip=[o.x+(H[0]+dx*30)*s,o.y+(H[1]+dy*30)*s];}
  return{tip,ang,head,r:30*s};
}
const DU=['DU','#c8f031','#111'],TOMTAG=['TOM','#5ab0ff','#06122a'];
function tag(x,y,txt,bg,fg){ctx.save();ctx.font='900 22px Montserrat, sans-serif';const w=ctx.measureText(txt).width+26;ctx.fillStyle=bg;rr(ctx,x-w/2,y-34,w,34,11);ctx.fill();
  ctx.beginPath();ctx.moveTo(x-8,y-1);ctx.lineTo(x+8,y-1);ctx.lineTo(x,y+8);ctx.fill();ctx.fillStyle=fg;ctx.textAlign='center';ctx.textBaseline='middle';ctx.fillText(txt,x,y-16);ctx.restore();}

// Die Gestalt: groß, dünn, ohne Gesicht
function creature(x,y,s,t,o={}){
  ctx.save();ctx.translate(x,y);ctx.scale(s,s);ctx.lineCap=ctx.lineJoin='round';if(o.rim){ctx.shadowColor=o.rim;ctx.shadowBlur=28;}
  const C='#04060d',sw=Math.sin(t*.8)*5,hip=[sw*.3,-240],neck=[sw,-430],sh=[sw,-412];
  ctx.strokeStyle=C;
  const L=(wd,...p)=>{ctx.lineWidth=wd;ctx.beginPath();ctx.moveTo(p[0][0],p[0][1]);for(let i=1;i<p.length;i++)ctx.lineTo(p[i][0],p[i][1]);ctx.stroke();};
  L(18,[-30,0],[-20,-122],hip);L(18,[30,0],[20,-122],hip);L(26,hip,neck);
  const arm=(p,rest,dir)=>{const a=rest+(dir-rest)*p,e=pt(sh,a,135,1),h=pt(e,a+(p<.5?.12*Math.sign(rest):0),135,1);L(14,sh,e,h);
    const f=pt(h,a,26,1);L(6,h,f);L(6,h,pt(h,a+.35,24,1));L(6,h,pt(h,a-.35,24,1));return h;};
  const hl=arm(o.pL||0,-.12+Math.sin(t*.9)*.03,o.dirL||-.54);
  const hr=arm(o.pR||0,.12-Math.sin(t*.9)*.03,o.dirR||.54);
  if(o.pack&&(o.pR||0)<.05){ctx.fillStyle='#3a4670';rr(ctx,hr[0]-18,hr[1]-6,36,62,12);ctx.fill();}
  ctx.save();ctx.translate(neck[0],neck[1]-50);ctx.rotate(o.tilt||0);ctx.fillStyle=C;ctx.beginPath();ctx.ellipse(0,0,27,42,0,0,7);ctx.fill();ctx.restore();
  ctx.restore();
}

// ---------- Umgebung ----------
const stars=Array.from({length:70},()=>({x:rnd(0,VW),y:rnd(0,900),r:rnd(.8,2.2),p:rnd(0,6)}));
const fogs=Array.from({length:12},()=>({x:rnd(0,VW+700),dy:rnd(-60,60),s:rnd(1.6,3.2),v:rnd(8,22),a:rnd(.25,.6)}));
const dust=Array.from({length:60},()=>({x:rnd(0,VW),y:rnd(0,VH),v:rnd(4,14),p:rnd(0,6)}));
function sky(){const g=ctx.createLinearGradient(0,0,0,1150);g.addColorStop(0,'#0a1129');g.addColorStop(1,'#1c2a52');ctx.fillStyle=g;ctx.fillRect(0,0,VW,VH);
  ctx.fillStyle='#cfd8f5';for(const s of stars){ctx.globalAlpha=.35+.35*Math.sin(performance.now()/700+s.p);ctx.fillRect(s.x,s.y,s.r,s.r);}ctx.globalAlpha=1;}
function moon(x,y,t){const g=ctx.createRadialGradient(x,y,30,x,y,300);g.addColorStop(0,'rgba(190,205,255,.3)');g.addColorStop(1,'rgba(190,205,255,0)');ctx.fillStyle=g;ctx.fillRect(x-300,y-300,600,600);
  ctx.fillStyle='#d6def5';ctx.beginPath();ctx.arc(x,y,58,0,7);ctx.fill();ctx.fillStyle='#b9c4e6';for(const[a,b,r]of[[-18,-12,12],[16,10,8],[-4,22,6],[22,-20,5]]){ctx.beginPath();ctx.arc(x+a,y+b,r,0,7);ctx.fill();}
  ctx.fillStyle='#0f1838';for(let i=0;i<3;i++){const cx=((t*14+i*380)%1300)-250;rr(ctx,cx,y-60+i*55,260,34,17);ctx.fill();}}
function fog(t,y,amt){ctx.save();for(const f of fogs){ctx.globalAlpha=f.a*amt;ctx.drawImage(fogC,((f.x+t*f.v)%(VW+700))-350,y+f.dy-64*f.s,256*f.s*1.5,128*f.s);}ctx.restore();}
function dusty(t,a=.5){ctx.fillStyle=`rgba(255,240,210,${a})`;for(const d of dust)ctx.fillRect((d.x+Math.sin(t*.4+d.p)*20+VW)%VW,(d.y+t*d.v)%VH,2.5,2.5);}
function ground(y){ctx.fillStyle='#0c1329';ctx.fillRect(0,y,VW,VH-y);ctx.fillStyle=pat('stripe',stripeC);ctx.fillRect(0,y,VW,VH-y);ctx.fillStyle='#1d2850';ctx.fillRect(0,y,VW,4);}
function tree(x,g,h,w,col,seed,rim){
  ctx.fillStyle=col;ctx.beginPath();ctx.moveTo(x-w/2,g);ctx.lineTo(x-w*.22,g-h);ctx.lineTo(x+w*.22,g-h);ctx.lineTo(x+w/2,g);ctx.fill();
  if(rim){ctx.fillStyle=rim;ctx.beginPath();ctx.moveTo(x+w*.3,g);ctx.lineTo(x+w*.13,g-h);ctx.lineTo(x+w*.22,g-h);ctx.lineTo(x+w/2,g);ctx.fill();}
  ctx.strokeStyle=col;ctx.lineCap='round';
  for(let i=0;i<5;i++){const by=g-h*(.42+i*.11),sd=(i+seed)%2?1:-1,len=50+((seed*7+i*13)%70);ctx.lineWidth=Math.max(4,w*.2);ctx.beginPath();ctx.moveTo(x,by);ctx.quadraticCurveTo(x+sd*len*.5,by-14,x+sd*len,by-44-((i*seed)%30));ctx.stroke();}
}
function treeLayer(t,sp,g,h,w,col,gap,seed,rim){const off=(t*sp)%gap,b=Math.floor(t*sp/gap);for(let i=-1;i<VW/gap+2;i++){const s=seed+i+b;tree(i*gap-off+((s*37)%60),g,h+((s*53)%120),w,col,Math.abs(s)%9,rim);}}
function beam(tip,ang,len,spread,a){ctx.save();ctx.globalCompositeOperation='lighter';const g=ctx.createRadialGradient(tip[0],tip[1],0,tip[0],tip[1],len);
  g.addColorStop(0,`rgba(255,244,210,${.32*a})`);g.addColorStop(1,'rgba(255,244,210,0)');ctx.fillStyle=g;ctx.beginPath();ctx.moveTo(tip[0],tip[1]);ctx.arc(tip[0],tip[1],len,ang-spread,ang+spread);ctx.closePath();ctx.fill();ctx.restore();}
function darkness(alpha,lights){
  dctx.setTransform(1,0,0,1,0,0);dctx.clearRect(0,0,dk.width,dk.height);base(dctx);
  dctx.globalCompositeOperation='source-over';dctx.fillStyle=`rgba(2,4,12,${alpha})`;dctx.fillRect(0,0,VW,VH);dctx.globalCompositeOperation='destination-out';
  for(const l of lights){if(!l.r)continue;const g=dctx.createRadialGradient(l.x,l.y,0,l.x,l.y,l.r);g.addColorStop(0,`rgba(0,0,0,${l.a??1})`);g.addColorStop(1,'rgba(0,0,0,0)');dctx.fillStyle=g;
    dctx.beginPath();if(l.cone){dctx.moveTo(l.x,l.y);dctx.arc(l.x,l.y,l.r,l.ang-l.spread,l.ang+l.spread);dctx.closePath();}else dctx.arc(l.x,l.y,l.r,0,7);dctx.fill();}
  ctx.save();ctx.setTransform(1,0,0,1,0,0);ctx.drawImage(dk,0,0);ctx.restore();
}


// ---------- Sounds (der Renderer legt sie an diese Zeitpunkte) ----------
function sfx(n,v=1){(window.SFX||(window.SFX=[])).push({n,t:performance.now(),v});}
function bed(n,v){(window.SFX||(window.SFX=[])).push({n,t:performance.now(),v,bed:1});}


// ---------- Wetter & Licht ----------
const drops=Array.from({length:180},()=>({x:rnd(0,VW+300),y:rnd(0,VH),v:rnd(900,1400),l:rnd(18,40)}));
function rain(t,a=1,x0=0,y0=0,w=VW,h=VH){ctx.save();ctx.beginPath();ctx.rect(x0,y0,w,h);ctx.clip();ctx.strokeStyle=`rgba(170,195,255,${.35*a})`;ctx.lineWidth=2;ctx.beginPath();
  for(const d of drops){const y=(d.y+t*d.v)%VH,x=((d.x-t*d.v*.18)%(VW+300)+VW+300)%(VW+300)-150;const px=x0+x*w/VW,py=y0+y*h/VH;ctx.moveTo(px,py);ctx.lineTo(px-d.l*.18,py+d.l);}ctx.stroke();ctx.restore();}
function glow(x,y,r,col,a){const g=ctx.createRadialGradient(x,y,0,x,y,r);g.addColorStop(0,`rgba(${col},${a})`);g.addColorStop(1,`rgba(${col},0)`);ctx.fillStyle=g;ctx.fillRect(x-r,y-r,r*2,r*2);}
const snowF=Array.from({length:150},()=>({x:rnd(0,VW),y:rnd(0,VH),v:rnd(60,160),r:rnd(1.5,4),p:rnd(0,6)}));
function snow(t,a=1,x0=0,y0=0,w=VW,h=VH,wind=60){ctx.save();ctx.beginPath();ctx.rect(x0,y0,w,h);ctx.clip();ctx.fillStyle=`rgba(235,242,255,${.8*a})`;
  for(const f of snowF){const y=(f.y+t*f.v)%VH,x=((f.x+t*wind+Math.sin(t+f.p)*25)%VW+VW)%VW;ctx.beginPath();ctx.arc(x0+x*w/VW,y0+y*h/VH,f.r,0,7);ctx.fill();}ctx.restore();}

// ---------- Zustand ----------
const S={};let scene=null,t0=0,frozen=null,dim=0,dimT=0;
let VIG=1;// Stärke der dunklen Bildränder (helle Seiten wie Papier setzen sie herunter)
const now=()=>(performance.now()-t0)/1000;
// ---------- Renderloop ----------
let nI=0;const NP=[],TAGS=[];
function frame(){
  const t=now();ctx.setTransform(1,0,0,1,0,0);ctx.fillStyle='#000';ctx.fillRect(0,0,cv.width,cv.height);
  TAGS.length=0;base(ctx);if(scene)scene.draw(frozen??t);base(ctx);for(const g of TAGS)tag(...g);
  dim+=(dimT-dim)*.08;base(ctx);if(dim>.01){ctx.fillStyle=`rgba(3,5,14,${dim})`;ctx.fillRect(0,0,VW,VH);}
  ctx.setTransform(1,0,0,1,0,0);ctx.globalAlpha=VIG;ctx.drawImage(vgC,0,0);ctx.globalAlpha=1;
  if(!NP.length)noise.forEach(n=>NP.push(ctx.createPattern(n,'repeat')));
  ctx.globalAlpha=.5;ctx.fillStyle=NP[(nI++>>2)%3];ctx.translate(rnd(0,200),rnd(0,200));ctx.fillRect(-200,-200,cv.width+200,cv.height+200);ctx.globalAlpha=1;
  requestAnimationFrame(frame);
}
function go(n){scene=scenes[n];t0=performance.now();frozen=null;dimT=0;}

// ---------- Text-Animationen ----------
const fx=document.getElementById('fx'),capEl=document.getElementById('cap'),hudEl=document.getElementById('hud'),clockEl=document.getElementById('clock'),locEl=document.getElementById('loc');
const show=e=>requestAnimationFrame(()=>requestAnimationFrame(()=>e.classList.add('in')));
function card(html,o={}){const d=document.createElement('div');d.className='card';d.innerHTML=html;
  if(o.top)d.style.setProperty('--top',o.top);if(o.ty)d.style.setProperty('--ty',o.ty);if(o.left)d.style.setProperty('--left',o.left);fx.appendChild(d);show(d);return d;}
async function out(d,ms=320){if(!d)return;d.classList.remove('in');d.classList.add('out');await sleep(ms);d.remove();}
function notif(ic,title,msg){const d=document.createElement('div');d.className='notif';d.innerHTML=`<div class="ic">${ic}</div><div><b>${title}</b><span>${msg}</span></div><em>jetzt</em>`;fx.appendChild(d);show(d);return d;}
async function outN(d){d.classList.remove('in');await sleep(500);d.remove();}
async function echo(word){const els=[];for(const[l,tp,o,f]of[[50,38,1,.62],[28,31,.7,.48],[72,27,.5,.4],[44,21,.3,.32]]){const e=document.createElement('div');e.className='echo';e.textContent=word;
  e.style.left=l+'%';e.style.top=tp+'%';e.style.fontSize=f+'em';e.style.setProperty('--o',o);fx.appendChild(e);show(e);els.push(e);await sleep(520);}
  await sleep(1200);els.forEach(e=>e.classList.remove('in'));await sleep(400);els.forEach(e=>e.remove());}
function hud(on){hudEl.classList.toggle('on',on);}
function clock(t,loc){clockEl.textContent=t;locEl.textContent=loc;clockEl.classList.remove('tick');void clockEl.offsetWidth;clockEl.classList.add('tick');}
// Untertitel im TikTok-Stil: Häppchen aus bis zu drei Wörtern stehen komplett da, das gesprochene Wort leuchtet auf.
// Marker im Text: *lime  #rot  ~gold
function chunkWords(text){
  const words=text.split(/\s+/).filter(Boolean).map(w=>{const m={'*':'hl','#':'red','~':'gold'}[w[0]];return{w:m?w.slice(1).replace(/\*/g,''):w,c:m||''};});
  const chunks=[[]];for(const w of words){const c=chunks[chunks.length-1];c.push(w);if(c.length>=3||/[.,?!…:]["“]?$/.test(w.w))chunks.push([]);}
  return chunks.filter(c=>c.length);
}
function showChunk(ch){capEl.innerHTML='';const box=document.createElement('div');box.className='ch';
  const spans=ch.map(w=>{const s=document.createElement('span');s.className='w '+w.c;s.textContent=w.w;box.appendChild(s);return s;});
  capEl.appendChild(box);
  // Lange Wörter weniger vergrößern, damit sie den Nachbarn nicht berühren
  spans.forEach(s=>{const m=parseFloat(getComputedStyle(s).marginLeft)||0;s.style.setProperty('--s',Math.min(1.16,1+2*m/Math.max(1,s.offsetWidth)).toFixed(3));});
  return spans;}
const light=(spans,i)=>spans.forEach((s,j)=>s.classList.toggle('on',j===i));
async function cap(text,o={}){
  const chunks=chunkWords(text);
  capEl.style.top=o.top||'';capEl.className=o.big?'xl':'';
  // Mit Sprachaufnahme (vom Renderer gesetzt): jedes Häppchen erscheint mit seinem ersten Wort und bleibt bis zum nächsten stehen
  const L=window.VOICE&&VOICE.lines[VOICE.i];
  if(L){VOICE.i++;VOICE.log.push(performance.now());const t0=performance.now();let k=0;
    for(const ch of chunks){
      await sleep(Math.max(0,t0+L.w[k]*1000-performance.now()));
      const k0=k,sp=showChunk(ch);ch.forEach((_,i)=>setTimeout(()=>light(sp,i),(L.w[k0+i]-L.w[k0])*1000));
      k+=ch.length;
    }
    await sleep(Math.max(0,t0+L.dur*1000-performance.now()));capEl.innerHTML='';return;
  }
  for(const ch of chunks){
    const sp=showChunk(ch),step=330*(o.slow||1);ch.forEach((_,i)=>setTimeout(()=>light(sp,i),i*step));
    await sleep(Math.max(900,ch.length*step+420));
  }
  capEl.innerHTML='';
}

// Untertitel, bei dem an einem bestimmten Wort etwas passiert (z. B. Schock-Sound)
async function capAt(text,idx,fn,o){const L=window.VOICE&&VOICE.lines[VOICE.i];setTimeout(fn,L?L.w[idx]*1000:30+idx*330);await cap(text,o);}
// Untertitel, bei dem zu jedem gesprochenen Wort fn(i) aufgerufen wird (z. B. Stockwerke mitzählen)
async function capWords(text,fn,o){const L=window.VOICE&&VOICE.lines[VOICE.i];const n=text.split(/\s+/).filter(Boolean).length;
  for(let i=0;i<n;i++)setTimeout(()=>fn(i),L?L.w[i]*1000:30+i*400);await cap(text,o);}
let tickOn=false,ringOn=false;
async function ticker(){tickOn=true;let iv=1000;while(tickOn){sfx('tick',.5);await sleep(iv);iv=Math.max(380,iv*.9);}}
async function ringer(){ringOn=true;while(ringOn){sfx('buzz',.7);await sleep(1300);}}
const rule=(n,a,b,top='13%')=>card(`<span class="ln pill">REGEL #${n}</span><div class="ln big" style="--d:.12s">${a}</div><div class="ln big" style="--d:.24s">${b}</div>`,{top});

// ---------- Indizien-Zähler (falls die Seite #ev hat) ----------
function ev(on){const e=document.getElementById('ev');if(e)e.classList.toggle('on',on);}
function found(n){S.found=n;S['m'+n]=now();const e=document.getElementById('ev');if(!e)return;e.querySelectorAll('.dots i').forEach((d,i)=>d.classList.toggle('on',i<n));
  e.classList.remove('bump');void e.offsetWidth;e.classList.add('bump');}

// ---------- Bausteine für das Ende ----------
function choice(a,b,top='32%'){return card(`<div class="ln opt2" style="--c:var(--lime)"><b>A</b>${a}</div><div class="ln opt2" style="--c:var(--red);--d:.15s"><b>B</b>${b}</div>`,{top});}
function question(top='11%'){return card(`<span class="ln pill">DEINE WAHL</span><div class="ln title" style="--d:.12s">ENTWEDER…</div><div class="ln title hl" style="--d:.26s">ODER?</div>`,{top});}
function endCard(pill,mid,big){return card(`<span class="ln pill lime">${pill}</span><div class="ln big" style="--d:.15s;font-size:.8em">${mid}</div>
  <div class="ln mega" style="--d:.3s">${big}</div><div class="ln" style="--d:.5s"><span class="follow">❤ FOLGEN</span></div>`,{top:'22%'});}
// Abspann: „Folgen für …“ groß, darunter die Aufforderung zum Kommentieren
function outro(pill,big,ask){return card(`<span class="ln pill lime">${pill}</span><div class="ln big" style="--d:.12s;font-size:.8em">FOLGEN FÜR</div>
  <div class="ln mega" style="--d:.25s">${big}</div><div class="ln" style="--d:.4s"><span class="follow">❤ FOLGEN</span></div>
  ${ask?`<div class="ln box" style="--c:var(--gold);--d:.55s"><div class="mid">${ask}</div></div>`:''}`,{top:'15%'});}

// ---------- Start ----------
const VIDEO=new URLSearchParams(location.search).has('video');
function boot(first){
  if(VIDEO){document.getElementById('start').remove();document.getElementById('hint').remove();}
  requestAnimationFrame(frame);go(first);
  if(!VIDEO)document.getElementById('start').onclick=e=>{e.target.remove();document.getElementById('hint').remove();story();};
  document.getElementById('again').onclick=()=>location.reload();
}
function finish(){window.DONE=true;if(!VIDEO)document.getElementById('again').style.display='block';}
