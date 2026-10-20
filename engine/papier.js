// Papier-Look für die „Für Dummies“-Erklärvideos: zerknülltes Papier, Kritzel-Linien, Handschrift.
// Wird nach engine.js geladen und nutzt dessen Bühne (ctx, mk, rr, ease, man, VIG).
const INK='#1f1d1a',PAPER='#ebe4d3',GREEN='#25d366',BLUE='#34b7f1',RED='#e63946',YEL='#ffd23f';
VIG=.35;
// Zerknülltes Papier: Facetten mit Licht/Schatten, Knicke, kleine Falten, Faserrauschen (einmal erzeugt, immer gleich)
const paperC=mk(900,1600,(g,w,h)=>{let sd=11;const R=()=>(sd=(sd*16807)%2147483647)/2147483647;
  g.fillStyle=PAPER;g.fillRect(0,0,w,h);
  const cols=6,rows=10,P=[];
  for(let j=0;j<=rows;j++){P.push([]);for(let i=0;i<=cols;i++)P[j].push([i*w/cols+(i%cols?(R()-.5)*120:0),j*h/rows+(j%rows?(R()-.5)*120:0)]);}
  const tri=[];for(let j=0;j<rows;j++)for(let i=0;i<cols;i++){const a=P[j][i],b=P[j][i+1],c=P[j+1][i+1],d=P[j+1][i];if(R()<.5)tri.push([a,b,c],[a,c,d]);else tri.push([a,b,d],[b,c,d]);}
  g.filter='blur(5px)';
  for(const t of tri){const l=(R()-.5)*.24,cx=(t[0][0]+t[1][0]+t[2][0])/3,cy=(t[0][1]+t[1][1]+t[2][1])/3,c=l>0?'255,255,255':'70,52,25',a=Math.abs(l);
    const gr=g.createLinearGradient(t[0][0],t[0][1],cx,cy);gr.addColorStop(0,`rgba(${c},${a})`);gr.addColorStop(1,`rgba(${c},${a*.25})`);
    g.fillStyle=gr;g.beginPath();g.moveTo(...t[0]);g.lineTo(...t[1]);g.lineTo(...t[2]);g.closePath();g.fill();}
  g.filter='none';
  for(const t of tri)for(let k=0;k<3;k++){if(R()<.45)continue;const p=t[k],q=t[(k+1)%3];
    g.strokeStyle='rgba(80,60,30,.2)';g.lineWidth=2;g.beginPath();g.moveTo(...p);g.lineTo(...q);g.stroke();
    g.strokeStyle='rgba(255,255,255,.4)';g.lineWidth=1.5;g.beginPath();g.moveTo(p[0]+2,p[1]+2);g.lineTo(q[0]+2,q[1]+2);g.stroke();}
  for(let i=0;i<70;i++){const x=R()*w,y=R()*h,a=R()*Math.PI,l=20+R()*80;g.strokeStyle=`rgba(80,60,30,${.05+R()*.1})`;g.lineWidth=1.2;
    g.beginPath();g.moveTo(x,y);g.quadraticCurveTo(x+Math.cos(a)*l/2+(R()-.5)*24,y+Math.sin(a)*l/2+(R()-.5)*24,x+Math.cos(a)*l,y+Math.sin(a)*l);g.stroke();}
  const d=g.getImageData(0,0,w,h);for(let i=0;i<d.data.length;i+=4){const n=(R()-.5)*12;d.data[i]+=n;d.data[i+1]+=n;d.data[i+2]+=n;}g.putImageData(d,0,0);
});
let BOIL=0;
function paper(t){BOIL=Math.floor(t*8);ctx.drawImage(paperC,0,0);}
// „Kochende“ Linien: jedes Element wackelt 8× pro Sekunde minimal, wie handgezeichnet
function wob(k){ctx.save();ctx.translate(Math.sin(BOIL*12.9+k)*1.3,Math.cos(BOIL*7.3+k*1.7)*1.3);ctx.rotate(Math.sin(BOIL*3.1+k*2.3)*.006);}
const backOut=x=>{x=Math.min(1,Math.max(0,x));const c=1.7;return 1+(c+1)*(x-1)**3+c*(x-1)**2;};
const popAt=(t,t0,d=.4)=>t0==null||t<t0?0:backOut((t-t0)/d);
const fadeAt=(t,t0,d=.3)=>t0==null||t<t0?0:ease((t-t0)/d);
function at(x,y,s,k,f){if(s<=.001)return;wob(k);ctx.translate(x,y);ctx.scale(s,s);f();ctx.restore();}
function stroke(w=6,col=INK){ctx.strokeStyle=col;ctx.lineWidth=w;ctx.lineCap=ctx.lineJoin='round';}
function hand(txt,x,y,size=48,col=INK,align='center'){ctx.fillStyle=col;ctx.font=`700 ${size}px Caveat, cursive`;ctx.textAlign=align;ctx.textBaseline='middle';ctx.fillText(txt,x,y);}
function bold(txt,x,y,size=40,col=INK,align='center'){ctx.fillStyle=col;ctx.font=`900 ${size}px Montserrat, sans-serif`;ctx.textAlign=align;ctx.textBaseline='middle';ctx.fillText(txt,x,y);}
function marker(x,y,w,h){ctx.fillStyle='rgba(255,210,63,.55)';ctx.beginPath();ctx.moveTo(x,y+4);ctx.lineTo(x+w,y);ctx.lineTo(x+w-6,y+h);ctx.lineTo(x+4,y+h+3);ctx.closePath();ctx.fill();}
const qp=(a,c,b,p)=>[(1-p)**2*a[0]+2*(1-p)*p*c[0]+p*p*b[0],(1-p)**2*a[1]+2*(1-p)*p*c[1]+p*p*b[1]];
function dash(a,c,b,col='rgba(31,29,26,.45)'){ctx.save();ctx.setLineDash([14,12]);stroke(4,col);ctx.beginPath();ctx.moveTo(...a);ctx.quadraticCurveTo(...c,...b);ctx.stroke();ctx.restore();}
function phone(screen,o={}){
  ctx.fillStyle='rgba(0,0,0,.13)';rr(ctx,-140,-280,300,580,46);ctx.fill();
  ctx.fillStyle=o.off?'#2b2926':'#fbfaf5';rr(ctx,-150,-290,300,580,46);ctx.fill();
  stroke(8);rr(ctx,-150,-290,300,580,46);ctx.stroke();ctx.fillStyle=INK;rr(ctx,-40,-274,80,16,8);ctx.fill();
  if(!o.off&&screen){ctx.save();rr(ctx,-138,-252,276,522,28);ctx.clip();screen();ctx.restore();}
  stroke(5,o.off?'#555':INK);ctx.beginPath();ctx.moveTo(-40,272);ctx.lineTo(40,272);ctx.stroke();}
// Telefonhörer: gebogener Griff mit Hör- und Sprechmuschel
function handset(r,col){ctx.save();ctx.rotate(-.55);ctx.strokeStyle=ctx.fillStyle=col;ctx.lineCap='round';ctx.lineWidth=r*.38;
  ctx.beginPath();ctx.arc(0,r*.75,r*.9,-Math.PI*.82,-Math.PI*.18);ctx.stroke();
  for(const sg of[-1,1]){ctx.save();ctx.translate(sg*r*.74,r*.32);ctx.rotate(sg*.6);rr(ctx,-r*.26,-r*.16,r*.52,r*.42,r*.16);ctx.fill();ctx.restore();}ctx.restore();}
function bubble(txt,col='#dcf8c6',w=150){ctx.fillStyle=col;rr(ctx,-w/2,-32,w,64,18);ctx.fill();stroke(4);rr(ctx,-w/2,-32,w,64,18);ctx.stroke();hand(txt,0,3,36);}
function padlock(col,open=0){ctx.save();ctx.translate(0,-open*22);stroke(11);ctx.beginPath();ctx.moveTo(-26,-26);ctx.lineTo(-26,-46);ctx.arc(0,-46,26,Math.PI,0);ctx.lineTo(26,-26);ctx.stroke();ctx.restore();
  ctx.fillStyle=col;rr(ctx,-42,-30,84,66,12);ctx.fill();stroke(6);rr(ctx,-42,-30,84,66,12);ctx.stroke();
  ctx.fillStyle=INK;ctx.beginPath();ctx.arc(0,-2,9,0,7);ctx.fill();ctx.fillRect(-4,0,8,20);}
function key(col){stroke(7);ctx.fillStyle=col;ctx.beginPath();ctx.arc(-42,0,24,0,7);ctx.fill();ctx.stroke();ctx.fillStyle=PAPER;ctx.beginPath();ctx.arc(-42,0,8,0,7);ctx.fill();ctx.stroke();
  ctx.fillStyle=col;rr(ctx,-20,-8,84,16,6);ctx.fill();ctx.stroke();rr(ctx,40,6,10,18,3);ctx.fill();ctx.stroke();rr(ctx,56,6,8,12,3);ctx.fill();ctx.stroke();}
function stick(x,y,s,t,o={}){return man({x,y,s,t,front:1,color:INK,back:'#4a4540',eye:PAPER,seed:o.seed||0,scared:o.scared,label:o.label});}
function zzz(x,y,t){for(let i=0;i<3;i++){const p=(t*.6+i/3)%1;ctx.globalAlpha=1-p;hand('z',x+i*18+p*30,y-p*70,30+i*8);}ctx.globalAlpha=1;}
