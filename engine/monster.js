// Monster-Entwürfe für die neue Horror-Serie. Jede Funktion zeichnet ein Monster mit den Füßen bei (0,0),
// etwa 620 Einheiten hoch, auf den globalen ctx. t = Zeit in Sekunden (für Atmen, Blinzeln, Leuchten).
const MINK='#07080f';
function mRR(x,y,w,h,r){ctx.beginPath();ctx.moveTo(x+r,y);ctx.arcTo(x+w,y,x+w,y+h,r);ctx.arcTo(x+w,y+h,x,y+h,r);ctx.arcTo(x,y+h,x,y,r);ctx.arcTo(x,y,x+w,y,r);ctx.closePath();}
function mGlow(x,y,r,col,a){const g=ctx.createRadialGradient(x,y,0,x,y,r);g.addColorStop(0,`rgba(${col},${a})`);g.addColorStop(1,`rgba(${col},0)`);ctx.fillStyle=g;ctx.fillRect(x-r,y-r,r*2,r*2);}
function mRim(col,b=26){ctx.shadowColor=col;ctx.shadowBlur=b;}
function mLimb(w,col,...p){ctx.strokeStyle=col;ctx.lineWidth=w;ctx.lineCap=ctx.lineJoin='round';ctx.beginPath();ctx.moveTo(...p[0]);for(const q of p.slice(1))ctx.lineTo(...q);ctx.stroke();}
const blink=(t,k=0)=>((t*.37+k)%1)>.96?.12:1;

const MONSTER={
  // 1 · Lichtschalter als Gesicht, wartet im dunklen Treppenhaus
  treppenlicht(t){const sw=Math.sin(t*1.3)*4;ctx.save();mRim('rgba(255,154,46,.9)');
    mLimb(16,MINK,[-26,0],[-18,-250],[0,-300]);mLimb(16,MINK,[26,0],[18,-250],[0,-300]);mLimb(30,MINK,[0,-300],[sw,-520]);
    mLimb(11,MINK,[sw,-505],[-70,-380],[-84+sw,-200],[-92,-150]);mLimb(11,MINK,[sw,-505],[70,-380],[84+sw,-200],[92,-150]);
    ctx.restore();ctx.save();ctx.translate(sw,-600);ctx.rotate(Math.sin(t*.7)*.06);
    ctx.fillStyle='#e9e2cf';mRR(-55,-75,110,150,14);ctx.fill();ctx.strokeStyle='#8c8470';ctx.lineWidth=4;ctx.stroke();
    const on=.6+.4*Math.sin(t*5);ctx.fillStyle=`rgba(255,154,46,${on})`;mRR(-30,-38,60,76,8);ctx.fill();mGlow(0,0,120,'255,154,46',.35*on);
    ctx.fillStyle='#3a2a10';ctx.beginPath();ctx.arc(0,-6,10,Math.PI,0);ctx.lineTo(6,10);ctx.lineTo(-6,10);ctx.closePath();ctx.fill();ctx.restore();},
  // 2 · Sprechblase mit Wellenform-Maul
  sprachnachricht(t){const y=-330+Math.sin(t*1.6)*12;ctx.save();ctx.translate(0,y);mRim('rgba(62,232,181,.9)',30);
    ctx.fillStyle='#10161a';mRR(-170,-200,340,330,90);ctx.fill();ctx.beginPath();ctx.moveTo(-60,120);ctx.lineTo(-120,300);ctx.lineTo(10,125);ctx.fill();ctx.shadowBlur=0;
    ctx.fillStyle='#e8fff8';ctx.beginPath();ctx.moveTo(-110,-120);ctx.lineTo(-60,-90);ctx.lineTo(-110,-60);ctx.closePath();ctx.fill();
    ctx.fillStyle='#3ee8b5';for(let i=0;i<14;i++){const h=12+Math.abs(Math.sin(t*9+i*.8))*70*(i>2&&i<12?1:.4);mRR(-120+i*18,30-h/2,10,h,5);ctx.fill();}
    ctx.fillStyle='#fff';for(let i=0;i<13;i++){ctx.beginPath();ctx.moveTo(-118+i*18,-6);ctx.lineTo(-109+i*18,8);ctx.lineTo(-100+i*18,-6);ctx.fill();}
    ctx.fillStyle='#9fb';ctx.font='700 30px JetBrains Mono, monospace';ctx.textAlign='right';ctx.fillText('0:13',130,-110);ctx.restore();},
  // 3 · Schaf aus Wolle mit Menschenbeinen und Menschengrinsen
  schaf(t){const b=Math.sin(t*2)*6;ctx.save();mRim('rgba(255,77,109,.7)');
    mLimb(14,'#d9c7b0',[-40,0],[-50,-130],[-30,-260]);mLimb(14,'#d9c7b0',[40,0],[50,-130],[30,-260]);
    ctx.fillStyle='#f3efe6';for(const[x,y,r]of[[-90,-330,80],[0,-360,95],[90,-330,80],[-50,-270,75],[50,-270,75],[-120,-400,60],[120,-400,60],[0,-440,80]]){ctx.beginPath();ctx.arc(x,y+b,r,0,7);ctx.fill();}
    ctx.shadowBlur=0;ctx.save();ctx.translate(0,-470+b);ctx.fillStyle='#1b1714';ctx.beginPath();ctx.ellipse(0,0,64,78,0,0,7);ctx.fill();
    ctx.fillStyle='#ffd23f';mRR(52,-60,46,30,6);ctx.fill();ctx.fillStyle='#111';ctx.font='900 20px Montserrat, sans-serif';ctx.textAlign='center';ctx.fillText('100',75,-38);
    const e=blink(t);ctx.fillStyle='#ff4d6d';ctx.beginPath();ctx.ellipse(-24,-20,9,9*e,0,0,7);ctx.ellipse(24,-20,9,9*e,0,0,7);ctx.fill();
    ctx.fillStyle='#fff';ctx.beginPath();ctx.moveTo(-48,22);ctx.quadraticCurveTo(0,62,48,22);ctx.quadraticCurveTo(0,40,-48,22);ctx.fill();
    ctx.strokeStyle='#1b1714';ctx.lineWidth=2;for(let i=-4;i<=4;i++){ctx.beginPath();ctx.moveTo(i*10,26+Math.abs(i));ctx.lineTo(i*10,44-Math.abs(i)*2);ctx.stroke();}ctx.restore();ctx.restore();},
  // 4 · Türspion als Kopf: ein Fischaugen-Auge
  spion(t){ctx.save();mRim('rgba(111,183,255,.8)');ctx.fillStyle=MINK;ctx.beginPath();ctx.moveTo(-110,0);ctx.lineTo(-80,-420);ctx.quadraticCurveTo(0,-460,80,-420);ctx.lineTo(110,0);ctx.closePath();ctx.fill();
    mLimb(12,MINK,[-80,-400],[-120,-250],[-110,-120]);mLimb(12,MINK,[80,-400],[120,-250],[110,-120]);ctx.shadowBlur=0;
    ctx.save();ctx.translate(0,-540);ctx.fillStyle='#b08a3e';ctx.beginPath();ctx.arc(0,0,105,0,7);ctx.fill();ctx.fillStyle='#d9e8ff';ctx.beginPath();ctx.arc(0,0,86,0,7);ctx.fill();
    const g=ctx.createRadialGradient(-20,-20,10,0,0,86);g.addColorStop(0,'rgba(255,255,255,.6)');g.addColorStop(1,'rgba(40,70,120,.5)');ctx.fillStyle=g;ctx.fill();
    const px=Math.sin(t*.9)*26,py=Math.cos(t*1.3)*14;ctx.fillStyle='#1d3f78';ctx.beginPath();ctx.arc(px,py,38,0,7);ctx.fill();ctx.fillStyle='#000';ctx.beginPath();ctx.arc(px,py,18*blink(t,.3)+2,0,7);ctx.fill();
    ctx.fillStyle='rgba(255,255,255,.8)';ctx.beginPath();ctx.arc(-34,-36,12,0,7);ctx.fill();mGlow(0,0,170,'111,183,255',.25);ctx.restore();ctx.restore();},
  // 5 · Frau, die rückwärts läuft: Haare vorne, Gesicht hinten
  rueckwaerts(t){const sw=Math.sin(t*1.1)*5;ctx.save();mRim('rgba(195,155,255,.8)');
    mLimb(10,'#cfc6d8',[-28,-10],[-26,-150]);mLimb(10,'#cfc6d8',[28,-10],[26,-150]);ctx.fillStyle='#cfc6d8';ctx.beginPath();ctx.ellipse(-12,-6,24,10,0,0,7);ctx.ellipse(44,-6,24,10,0,0,7);ctx.fill();
    ctx.fillStyle='#e9e4ef';ctx.beginPath();ctx.moveTo(-60,-140);ctx.lineTo(-80,-160);ctx.lineTo(-70,-400);ctx.quadraticCurveTo(0,-430,70,-400);ctx.lineTo(80,-160);ctx.lineTo(60,-140);ctx.closePath();ctx.fill();
    ctx.fillStyle=MINK;ctx.beginPath();ctx.ellipse(sw,-470,62,74,0,0,7);ctx.fill();ctx.beginPath();ctx.moveTo(-62+sw,-480);ctx.quadraticCurveTo(-90,-300,-70+sw,-170);ctx.lineTo(70+sw,-170);ctx.quadraticCurveTo(90,-300,62+sw,-480);ctx.fill();
    ctx.shadowBlur=0;ctx.strokeStyle='#1c1a26';ctx.lineWidth=3;for(let i=0;i<9;i++){ctx.beginPath();ctx.moveTo(-50+i*12+sw,-520);ctx.quadraticCurveTo(-56+i*13,-350,-58+i*15+sw,-180);ctx.stroke();}
    const e=blink(t,.6);ctx.fillStyle='#fff';ctx.beginPath();ctx.ellipse(-18+sw,-478,8,6*e,0,0,7);ctx.ellipse(18+sw,-478,8,6*e,0,0,7);ctx.fill();
    ctx.strokeStyle='#fff';ctx.lineWidth=3;ctx.beginPath();ctx.arc(sw,-450,22,.2,Math.PI-.2);ctx.stroke();ctx.restore();},
  // 6 · Paketbote aus Kartons mit Briefschlitz-Maul
  absender(t){ctx.save();mRim('rgba(224,160,80,.7)');mLimb(18,MINK,[-40,0],[-36,-200]);mLimb(18,MINK,[40,0],[36,-200]);
    const box=(x,y,w,h)=>{ctx.fillStyle='#b98a4e';ctx.fillRect(x,y,w,h);ctx.fillStyle='#d8b27a';ctx.fillRect(x+w/2-12,y,24,h);ctx.strokeStyle='#5c4022';ctx.lineWidth=4;ctx.strokeRect(x,y,w,h);};
    box(-90,-380,180,190);mLimb(14,MINK,[-90,-360],[-150,-260],[-140,-170]);mLimb(14,MINK,[90,-360],[150,-280],[170,-220]);ctx.shadowBlur=0;
    ctx.save();ctx.translate(170,-230);ctx.fillStyle='#222';mRR(-30,-50,60,90,10);ctx.fill();ctx.fillStyle='#ff3b3b';ctx.fillRect(-22,-40,44,14);ctx.restore();
    ctx.save();ctx.translate(Math.sin(t*1.5)*4,-500);ctx.rotate(Math.sin(t*.8)*.05);box(-80,-80,160,150);
    ctx.fillStyle='#0b0b0b';mRR(-50,10,100,26,6);ctx.fill();ctx.fillStyle='#fff';for(let i=0;i<8;i++){ctx.beginPath();ctx.moveTo(-46+i*12,12);ctx.lineTo(-40+i*12,24);ctx.lineTo(-34+i*12,12);ctx.fill();}
    const e=blink(t,.2);ctx.fillStyle='#0b0b0b';ctx.beginPath();ctx.arc(-34,-30,14,0,7);ctx.arc(34,-30,14,0,7);ctx.fill();ctx.fillStyle='#ffe9b0';ctx.beginPath();ctx.arc(-34,-30,4*e,0,7);ctx.arc(34,-30,4*e,0,7);ctx.fill();
    ctx.fillStyle='#e63946';ctx.save();ctx.rotate(-.2);ctx.fillRect(-90,-70,90,20);ctx.fillStyle='#fff';ctx.font='900 14px Montserrat, sans-serif';ctx.fillText('ZERBRECHLICH',-88,-55);ctx.restore();ctx.restore();ctx.restore();},
  // 7 · Standort-Pin mit Auge, pulsierender Radius am Boden
  punkt(t){ctx.save();for(let i=0;i<3;i++){const p=(t*.6+i/3)%1;ctx.strokeStyle=`rgba(61,139,255,${(1-p)*.7})`;ctx.lineWidth=4;ctx.beginPath();ctx.ellipse(0,0,40+p*190,(40+p*190)*.25,0,0,7);ctx.stroke();}
    const y=-40+Math.sin(t*2)*14;ctx.translate(0,y);mRim('rgba(61,139,255,.9)',34);ctx.fillStyle='#3d8bff';ctx.beginPath();ctx.moveTo(0,0);ctx.bezierCurveTo(-60,-120,-170,-250,-170,-380);ctx.arc(0,-380,170,Math.PI,0);ctx.bezierCurveTo(170,-250,60,-120,0,0);ctx.fill();
    ctx.shadowBlur=0;ctx.fillStyle='#fff';ctx.beginPath();ctx.arc(0,-385,92,0,7);ctx.fill();const px=Math.sin(t*1.1)*24;ctx.fillStyle='#0a1a3a';ctx.beginPath();ctx.arc(px,-385,46*blink(t,.5)+4,0,7);ctx.fill();
    ctx.fillStyle='#fff';ctx.beginPath();ctx.arc(px-16,-400,9,0,7);ctx.fill();ctx.restore();},
  // 8 · Nachtbusfahrer mit Scheinwerfer-Augen und Zielanzeige als Maul
  linie0(t){ctx.save();const a=.5+.5*Math.sin(t*3);ctx.globalCompositeOperation='lighter';for(const x of[-40,40]){const g=ctx.createLinearGradient(x,-470,x*6,-200);g.addColorStop(0,`rgba(255,224,102,${.35*a+.1})`);g.addColorStop(1,'rgba(255,224,102,0)');ctx.fillStyle=g;ctx.beginPath();ctx.moveTo(x,-470);ctx.lineTo(x*7-120,-120);ctx.lineTo(x*7+120,-120);ctx.closePath();ctx.fill();}
    ctx.globalCompositeOperation='source-over';mRim('rgba(255,224,102,.6)');ctx.fillStyle=MINK;ctx.beginPath();ctx.moveTo(-130,0);ctx.lineTo(-120,-300);ctx.quadraticCurveTo(-60,-420,0,-420);ctx.quadraticCurveTo(60,-420,120,-300);ctx.lineTo(130,0);ctx.closePath();ctx.fill();
    ctx.beginPath();ctx.ellipse(0,-470,85,75,0,0,7);ctx.fill();ctx.fillStyle='#1c2233';mRR(-100,-560,200,40,8);ctx.fill();ctx.fillRect(-70,-610,140,60);ctx.shadowBlur=0;
    ctx.fillStyle='#ffe066';ctx.beginPath();ctx.arc(-38,-480,20,0,7);ctx.arc(38,-480,20,0,7);ctx.fill();
    ctx.fillStyle='#111';mRR(-70,-445,140,36,6);ctx.fill();ctx.fillStyle='#ff8c1a';ctx.font='800 22px JetBrains Mono, monospace';ctx.textAlign='center';ctx.fillText((Math.floor(t*1.2)%2)?'0 ENDSTATION':'  0  ',0,-420);
    mLimb(14,MINK,[-110,-280],[-60,-200],[0,-220]);mLimb(14,MINK,[110,-280],[60,-200],[0,-220]);ctx.strokeStyle='#555';ctx.lineWidth=10;ctx.beginPath();ctx.arc(0,-210,60,Math.PI*1.1,Math.PI*1.9);ctx.stroke();ctx.restore();},
  // 9 · Fensterputzer mit überlangen Gliedern, presst sich ans Fenster
  fensterputzer(t){ctx.save();mRim('rgba(127,240,255,.7)');const k=Math.sin(t*2.2)*8;
    mLimb(9,MINK,[-20,0],[-140,-160],[-40,-300]);mLimb(9,MINK,[20,0],[140,-160],[40,-300]);mLimb(20,MINK,[0,-300],[0,-470]);
    mLimb(8,MINK,[0,-460],[-170,-380],[-200,-560],[-170+k,-640]);mLimb(8,MINK,[0,-460],[170,-420],[210,-600],[190-k,-660]);
    ctx.fillStyle='#bfefff';for(const[x,y]of[[-170+k,-640],[190-k,-660]]){ctx.fillRect(x-40,y-8,80,14);ctx.fillStyle=MINK;ctx.fillRect(x-4,y,8,22);ctx.fillStyle='#bfefff';}
    ctx.save();ctx.translate(0,-520);ctx.rotate(.5+Math.sin(t)*.08);ctx.fillStyle=MINK;ctx.beginPath();ctx.ellipse(0,0,40,58,0,0,7);ctx.fill();ctx.shadowBlur=0;
    ctx.fillStyle='#7ff0ff';ctx.beginPath();ctx.arc(-14,-8,7*blink(t,.8)+1,0,7);ctx.arc(14,-8,7*blink(t,.8)+1,0,7);ctx.fill();ctx.restore();
    ctx.shadowBlur=0;ctx.strokeStyle='rgba(200,240,255,.35)';ctx.lineWidth=6;ctx.strokeRect(-230,-720,460,720);ctx.beginPath();ctx.moveTo(0,-720);ctx.lineTo(0,0);ctx.moveTo(-230,-360);ctx.lineTo(230,-360);ctx.stroke();ctx.restore();},
  // 10 · Mitbewohner aus Klebezetteln, Gesicht ist ein Zettel
  mitbewohner(t){ctx.save();mRim('rgba(255,210,63,.6)');ctx.fillStyle=MINK;ctx.beginPath();ctx.moveTo(-100,0);ctx.lineTo(-90,-380);ctx.quadraticCurveTo(0,-430,90,-380);ctx.lineTo(100,0);ctx.closePath();ctx.fill();
    ctx.beginPath();ctx.ellipse(0,-480,70,80,0,0,7);ctx.fill();ctx.shadowBlur=0;const C=['#ffd23f','#ff8fb1','#8fe3ff','#b6f28a'];
    for(let i=0;i<22;i++){const x=-80+((i*53)%160),y=-60-((i*97)%330),r=((i*37)%20-10)*.02+Math.sin(t*3+i)*.03*(i%3===0);ctx.save();ctx.translate(x,y);ctx.rotate(r);ctx.fillStyle=C[i%4];ctx.fillRect(-26,-26,52,52);ctx.fillStyle='rgba(0,0,0,.25)';ctx.fillRect(-20,-12,40,3);ctx.fillRect(-20,-2,30,3);ctx.restore();}
    ctx.save();ctx.translate(0,-480);ctx.rotate(-.05+Math.sin(t*1.7)*.04);ctx.fillStyle='#ffd23f';ctx.fillRect(-62,-62,124,124);ctx.fillStyle='#111';ctx.beginPath();ctx.arc(-24,-14,10,0,7);ctx.arc(24,-14,10,0,7);ctx.fill();
    ctx.strokeStyle='#111';ctx.lineWidth=4;ctx.beginPath();ctx.moveTo(-30,28);ctx.quadraticCurveTo(0,44,30,28);ctx.stroke();ctx.font='700 18px Caveat, cursive';ctx.fillStyle='#e63946';ctx.textAlign='center';ctx.fillText('Bin dran ♥',0,56);ctx.restore();ctx.restore();},
};
