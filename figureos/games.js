// id|name|genre|icon  (icon is relative to https://download-oss.raccoongame.com/uploads/image/ unless it starts with images/)
// Order = library order (newest first).
const RAW=`bs0078|DRAGON BALL: Sparking! ZERO|Action|20260729/20260729175805537.jpg
bs0096|Black Myth: Wukong|Action|20260724/20260724162056936.jpg
bs0095|Red Dead Redemption|Action|20260724/20260724175308547.jpg
bs0094|PRAGMATA|Action|20260717/20260717173741180.jpg
bs0093|Battlefield 6|Shooting|20260731/20260731161441839.jpg
bs0092|Mafia: The Old Country|Shooting|20260717/20260717171729202.jpg
bs0087|Detroit: Become Human|Action|20260724/20260724172914224.jpg
bs0083|INDUSTRIA 2|Action|20260807/20260807163708404.jpg
bs0082|Far Cry 5|Action|20260731/20260731161251783.jpg
bs0077|Invincible VS|Action|20260724/20260724172724981.jpg
bs0074|Commandos: Origins|Action|20260807/20260807170417761.jpg
bs0070|Maid of Sker|Adventure|20260807/20260807161422252.jpg
bs0069|Transformers: Battlegrounds|Adventure|20260731/20260731172523208.jpg
bs0067|Persona 5 Strikers|RPG|20260807/20260807161230470.jpg
bs0064|Oddsparks: An Automation Adventure|Easy|20260710/20260710155543902.jpg
bs0063|Burkina Faso: Radical Insurgency|Action|20260710/20260710154415676.jpg
bs0062|World War Z - Aftermath|Shooting|20260724/20260724172550548.jpg
bs0061|GOD FORSAKEN|Action|20260703/20260703111731566.jpg
bs0060|Gym Manager|Simulation|20260703/20260703110509447.jpg
bs0059|FEROCIOUS|Action|20260626/20260626151723124.jpg
bs0058|Cavalry Girls|RPG|20260626/20260626144006357.jpg
bs0057|Quarantine Zone - The Last Check|Simulation|20260626/20260626144102556.jpg
bs0056|Diablo II - Resurrected|RPG|20260618/2026061814460915.jpg
bs0055|Resident Evil Requiem|RPG|20260618/20260618144515330.jpg
bs0054|Forza Horizon 6|Racing|20260618/20260618144542619.jpg
bs0053|God of War|Action|20260618/20260618144200465.jpg
bs0052|I Am Jesus Christ|Simulation|20260618/20260618144227530.jpg
bs0051|The Spirit of the Samurai|Action|20260618/20260618144252587.jpg
bs0050|Wicked Seed|Action|20260618/20260618144315606.jpg
bs0049|Taboo Trial|RPG|20260618/20260618144340798.jpg
bs0048|Tomb Raider - Definitive Edition|3A|20260618/20260618144407643.jpg
bs0047|Cricket 26|3A|20260618/20260618144430322.jpg
bs0046|Hollow Knight: Silksong|Indie|20260618/20260618144453541.jpg
bs0039|Little Nightmares III|Adventure|20260515/20260515162908395.jpg
dg0419|SAND LAND|RPG|20250903/20250903105440952.png
kj0495|X-Plane 11|Simulation|20250902/2025090217335483.jpg
kj0530|GUILTY GEAR -STRIVE-|RPG|20250901/20250901143139167.jpg
kj0531|Sim Racing Telemetry - F1 22|Racing|20260515/2026051516284867.jpg
bs0034|Clair Obscur: Expedition 33|RPG|20260515/20260515162820447.jpg
bs0035|Easy Red 2|Action|20250829/20250829153823174.png
bs0036|Wolf Mate|Action|20250829/20250829115558198.png
bs0026|JDM: Japanese Drift Master|Racing|20250930/20250930120300208.jpg
bs0023|The First Berserker: Khazan|RPG|20250627/20250627115659712.png
bs0021|Blue Prince|Puzzle|20250627/20250627112123936.png
bs0025|Only Up|Adventure|20250613/20250613113813585.jpg
bs0024|Schedule 1|Simulation|20250612/2025061214262743.png
kj0599|WWE 2K25|Simulation|20260407/20260407163551925.jpg
bs0022|RuneScape: Dragonwilds|Multiplayer|20250530/20250530162530751.png
dg0364|Cities: Skylines 2|Easy|20250530/2025053015334968.png
bs0020|Crashlands 2|RPG|20250627/20250627144457604.jpg
bs0019|Mandragora: Whispers of the Witch Tree|RPG|20250523/20250523151922656.png
dg0533|Frostpunk 2|Adventure|20250516/20250516155744357.png
kp0213|Manor Lords|RPG|20250430/20250430164222535.png
kj0620|inZOI|RPG|20250527/20250527115852814.jpg
bs0018|AI LIMIT|RPG|20250425/20250425165139248.png
dg0598|Horizon Zero Dawn|RPG|20250425/20250425162419325.png
kj0615|Dead Space 3|RPG|20250425/20250425153104777.jpg
kj0035|Deathloop|Shooting|20250425/20250425144627336.png
kj0461|Football Manager 2023|RPG|20250425/20250425111359553.png
kj0614|Dead Space 2|RPG|20250418/20250418160850720.jpg
kj0630|The Last of Us Part II|RPG|20260408/20260408171835296.jpg
kj0179|Inside the Backrooms|Adventure|20250418/20250418110314927.png
kj0529|Platform 8|Adventure|20250411/20250411114101787.jpg
dg0589|Five Hearts Under One Roof|Casual|20250930/202509301203287.jpg
kj0516|Ghost of Tsushima|RPG|20250527/20250527115454441.jpg
kj0483|The Callisto Protocol|RPG|20260410/20260410172113912.jpg
dg0730|Poppy Playtime: Chapter 4|RPG|20250402/20250402141532953.jpg
kj0209|Raft|Adventure|20250328/20250328180047198.png
dg0492|Mafia III: Definitive Edition|3A|20250801/20250801170443257.jpg
dg0491|Mafia II: Definitive Edition|3A|20250627/20250627144331837.jpg
kp0232|TCG Card Shop|RPG|20250331/20250331151732927.png
dg0248|Uncharted 4|RPG|20250930/20250930115220269.jpg
kj0306|Undertale|RPG|20250408/20250408111349799.png
dg0639|Yandere Simulator|RPG|20250320/20250320164718378.png
kj0177|Amanda the Adventurer|Adventure|20250408/20250408111140703.png
kj0138|Poppy Playtime: Chapter 2|Adventure|20250312/20250312142044488.png
kj0137|Poppy Playtime: Chapter 1|Adventure|20250312/20250312113635587.jpg
dg0676|MiSide|RPG|20250312/20250312161610449.jpg
bs0017|God of War: Ragnarök|3A|20251218/20251218002037369.jpg
kj0442|Poppy Playtime: Chapter 3|Adventure|20250226/20250226105957398.png
kj0328|FIFA23|Sports|20240527/20240527172935321.png
kj0222|SCUM|Challenge|20241224/20241224160908458.jpg
kj0458|WWE 2K24|Sports|20250331/20250331173717794.jpg
kj0426|Buckshot Roulette|Strategy|20240527/20240527171656277.jpg
kp0203|Supermarket Simulator|Strategy|20240527/2024052717080272.png
kj0202|The Long Drive|Easy|20240527/2024052717011280.jpg
kj0237|GigaBash|Action|20240527/20240527165429575.jpg
kp0208|Zoonomaly|Strategy|20240527/20240527163117749.png
kj0499|Hades II|RPG|20240527/20240527161909639.jpg
dg0380|Brotato|Easy|20240520/2024052018070993.png
kj0238|Wobbly Life|Multiplayer|20240411/20240411183825744.jpg
kp0133|Sniper: Ghost Warrior Contracts 2|3A|20240223/20240223104219285.jpg
kj0440|Tekken 8|Fighting|20250402/20250402141624654.jpg
kj0229|Halo: The Master Chief Collection|Shooting|20241217/20241217183311663.jpg
kj0357|Alan Wake 2|Adventure|20241108/20241108164656767.jpg
dg0359|Neon Abyss|Challenge|20250225/20250225111706535.jpg
kj0322|Stick Fight: The Game|Multiplayer|20230921/20230921170257932.jpg
kj0287|Ratchet & Clank|Adventure|20230831/20230831160505147.jpg
dg0315|The Last of Us Part I|Adventure|20260408/20260408152339290.jpg
kj0213|Resident Evil 4|Adventure|20260515/202605151629306.jpg
kj0135|Builder Simulator|Strategy|20230518/20230518222345800.jpg
kj0191|Minecraft: Legends|Adventure|20230504/20230504180952993.jpg
kj0181|Need for Speed: Payback|Adventure|20230329/20230329174621974.jpg
kj0161|SpongeBob: Battle for Bikini Bottom|Online|20230309/20230309154245933.jpg
kj0171|Super Bunny Man|Multiplayer|20230309/20230309143751602.jpg
kj0166|Hogwarts Legacy|3A|20250930/20250930145508266.jpg
kj0150|SpongeBob: The Cosmic Shake|RPG|20230208/20230208162229856.jpg
kj0141|Drift Racing Online|Sports|20230112/20230112141904622.jpg
dg0278|Choo-Choo Charles|Adventure|20250402/20250402140901381.jpg
kj0125|Marvel's Spider-Man: Miles Morales|3A|20251218/20251218002310690.jpg
kj0107|NBA 2K23|Sports|20230508/2023050818554376.jpeg
kj0085|Ranch Simulator 22|Strategy|20220905/20220905122011868.webp
kj0089|Marvel's Spider-Man Remastered|Adventure|20250930/20250930114154630.jpg
kj0214|Forza Horizon 5|Racing|20260305/20260305115627492.jpg
dg0183|Stray|Adventure|20250613/20250613153035148.jpg
kj0032|Pokémon Legends: Arceus|Adventure|20260122/20260122010947516.png
kj0025|Subnautica Zero|Strategy|20220601/20220601160738157.png
KJ0019|GhostWire: Tokyo|Shooting|20250103/20250103180827822.jpg
dg0174|THE KING OF FIGHTERS XV|Fighting|20250627/20250627181815843.png
dg0168|Hitman 3|Adventure|20250815/2025081515231470.jpg
kj0332|Nintendo Star Brawl|Multiplayer|20220308/20220308113443136.webp
jy0333|Red Dead Redemption 2|3A|20260407/20260407165500251.jpg
dg0170|Elden Ring|3A|20260515/2026051516303039.jpg
dg0163|LEGO The Incredibles|Multiplayer|20220218/20220218162726597.webp
dg0159|Watch Dogs: Legion|3A|20250402/20250402141702762.jpg
dg0154|God of War 4|3A|20250402/20250402141418448.jpg
dg0145|Cooking Simulator|Strategy|20220118/20220118160905685.webp
dg0139|FIFA19|Sports|20220106/20220106131728331.webp
dg0129|Grand Theft Auto: Vice City|3A|20250627/20250627144431471.jpg
dg0128|Grand Theft Auto: San Andreas|3A|20260515/20260515162949699.jpg
dg0127|Grand Theft Auto III|3A|20250627/20250627191826951.png
jy0408|The Legend of Zelda: Breath of the Wild|3A|20211202/20211202161034296.webp
dg0109|ARK: Survival Evolved|Adventure|20211116/20211116093752503.webp
kj0185|Cities: Skylines|Strategy|20211109/20211109153159749.webp
dg0054|Arma 3|Strategy|20211008/20211008154130946.webp
dg0062|Assassin's Creed: Brotherhood|Adventure|20250627/20250627144524899.jpg
dg0063|Assassin's Creed: Revelations|Challenge|20211008/20211008145828215.webp
dg0064|Assassin's Creed: Black Flag|Adventure|20230420/20230420211652688.jpg
dg0065|Assassin's Creed: Syndicate|Adventure|20250402/2025040214122974.jpg
dg0024|Football: PES 2021|Sports|20251218/20251218002137560.jpg
kj0190|Mount & Blade II: Bannerlord|Strategy|20241224/2024122416440565.jpg
dg0022|Fling to Finish|Multiplayer|20230322/20230322201636769.png
dg0021|A Way Out|Multiplayer|20230322/20230322203032216.jpg
jy0559|Slime Rancher|Strategy|20210909/20210909162030298.webp
jy0532|GTA V MOD version|Challenge|20260122/20260122002957664.png
jy0362|Alba: A Wildlife Adventure|Puzzle|20210827/20210827110503152.webp
jy0487|Days Gone|Adventure|20210826/20210826165254727.webp
jy0238|Assassin's Creed Odyssey: Fate of Atlantis|3A|20241217/20241217191626272.jpg
dg0020|Euro Truck Simulator 2|Strategy|20210819/20210819152350351.webp
jy0541|Forza Horizon 4|3A|20210819/20210819150927774.webp
dg0017|Tokyo 2020 Olympics|Sports|20210804/20210804170038384.webp
jy0482|SUPERHOT: Mind Control Delete|Challenge|20210729/20210729161026790.webp
kp0175|Resident Evil Village|Challenge|20260626/20260626144246541.jpg
jy0473|My Friend Pedro|Action|20210715/20210715203828208.webp
jy0488|Doom Eternal|Shooting|20260121/20260121175158608.png
jy0470|Party Hard 2|Adventure|20230824/20230824153232681.jpg
jy0186|The Amazing Spider-Man 2|Adventure|20210701/20210701110046951.webp
jy0435|Transformers: Devastation|Fighting|20210625/20210625155800236.webp
jy0417|Control|Challenge|20260121/20260121174633373.png
jy0371|Halo: Reach|Challenge|20260121/2026012117405271.png
jy0459|The Amazing Spider-Man|Adventure|20250815/20250815155732187.jpg
jy0310|Fallout 4|RPG|20210423/20210423120714547.webp
jy0440|It Takes Two|Adventure|20230323/20230323122354541.png
jy0402|Minecraft: Dungeons|Adventure|20210402/20210402153932908.webp
jy0361|Call of Duty: WWII|Shooting|20260121/202601211709089.png
jy0416|Little Nightmares II|Adventure|20210223/20210223113905296.webp
jy0172|Hand of Fate 2|RPG|20210701/2021070115544498.webp
jy0324|The Past Room|Puzzle|20210423/20210423111748763.webp
jy0325|Little Nightmares|Adventure|20250801/20250801170522640.jpg
jy0108|Grand Theft Auto V|Challenge|20260304/20260304175808508.jpg
jy0307|Mafia: Definitive Edition|3A|20250613/20250613152910794.jpg
jy0183|ONE PIECE: PIRATE WARRIORS 4|Fighting|20210827/20210827103721165.webp
jy0281|Overcooked 2|Puzzle|20230508/20230508200039339.png
jy0244|Boomerang Fu|Puzzle|20260121/20260121155951685.png
jy0112|Off-Road Heroes 4|Racing|images/games/cg0020044/b004_zunxiangtuijian_2.webp
jy0154|Far Cry Primal|3A|20211013/20211013103809191.webp
dg0090|Mirror's Edge Catalyst|Adventure|20210203/20210203172102804.webp
md0483|Far Cry 3|Shooting|20211013/20211013104959287.webp
md0176|COD6 Modern Warfare|3A|images/cloudgame/img/phone300_public/gamedetails/cg0020237/b004_remengyouxi.webp
jy0354|Cyberpunk 2077|3A|20260410/20260410170557349.jpg
jy0273|Batman: Arkham Origins|3A|20260410/20260410165139465.jpg
jy0272|Batman: Arkham Knight|3A|20260408/20260408115021808.jpg
jy0252|Far Cry New Dawn|3A|20250801/20250801170502249.jpg
jy0214|Tom Clancy's Ghost Recon Wildlands|3A|20251110/20251110142109170.png
jy0235|Resident Evil: Revelations 2|Adventure|images/cloudgame/img/phone300_public/gamedetails/cg0020147/a004_img_recommend_1.webp
jy0229|Stardew Valley|Strategy|20260407/20260407173529946.jpg
jy0038|Sekiro: Shadows Die Twice|3A|20230322/20230322195617171.jpg
jy0208|Attack On Titan 2|3A|20250815/20250815161718587.jpg
jy0219|Curse of the Dead Gods|Challenge|20220428/20220428154836446.png
jy0080|Bloodstained: Ritual of the Night|RPG|20260121/20260121153136443.png
jy0196|Forager|Puzzle|20260121/20260121151347543.png
jy0095|Resident Evil 7: Biohazard|3A|20250815/20250815172959772.jpg
jy0185|Titanfall 2|3A|20210910/20210910111018307.webp
jy0147|Watch Dogs 2|3A|20260416/20260416114647944.jpg
jy0146|Cuphead|Challenge|20260121/20260121145749473.png
jy0157|What Remains of Edith Finch|Adventure|20230608/20230608153228735.jpg
jy0124|Untitled Goose Game|Strategy|20250225/20250225112426902.png
jy0121|Rise of the Tomb Raider|Adventure|20250815/20250815162338597.jpg
jy0120|Katana ZERO|Challenge|20230907/20230907123321261.jpg
jy0122|Wizard of Legend|Challenge|20260121/2026012114475233.png
jy0059|One Piece: Burning Blood|Fighting|images/cloudgame/img/phone300_public/gamedetails/cg0020055/a004_img_recommend_1.webp
jy0126|Ori and the Will of the Wisps|Challenge|20210402/20210402165758426.webp
jy0118|Doraemon: Story of Seasons|Strategy|images/cloudgame/img/phone300_public/gamedetails/cg0020074/a004_img_recommend_1.webp
jy0123|Hades|Challenge|20260121/20260121144523682.png
jy0109|Ace Combat 7: Skies Unknown|3A|images/cloudgame/img/phone300_public/gamedetails/cg0020070/a004_img_recommend_1.webp
jy0091|The Witcher 3|3A|20230322/20230322194948461.jpg
jy0105|Terminator: Resistance|Shooting|20210428/20210428162111388.webp
jy0103|NieR:Automata|Challenge|20250613/2025061315324568.jpg
jy0094|Ori and the Blind Forest|Challenge|20210402/20210402165122253.webp
jy0084|Dragon Ball Z: Kakarot|3A|images/cloudgame/img/phone300_public/gamedetails/cg0020050/a004_img_recommend_1.webp
jy0075|For The King|Strategy|20210701/2021070111403285.webp
jy0046|Assassin's Creed: Odyssey|3A|20241119/20241119150446640.jpg
jy0040|Resident Evil 2: Remake|3A|images/cloudgame/img/phone300_public/gamedetails/cg0020017/a004_img_recommend_1.webp
jy0039|Devil May Cry 5|3A|20250815/20250815173456114.jpg
dg0158|FINAL FANTASY VII REMAKE INTERGRADE|RPG|20260429/20260429155011461.jpg
jy0015|How to Train Your Dragon: Dawn of New Riders|Challenge|images/cloudgame/img/phone300_public/gamedetails/cg0020008/a004_img_recommend_1.webp
jy0012|American Fugitive|Adventure|20260429/20260429153423938.jpg
jy0009|Naruto Shippuden: Ultimate Ninja Storm 4|Multiplayer|20260429/20260429151802428.jpg
kj0283|Baldur's Gate 3|Challenge|20260410/20260410173748437.jpg
jy0008|Devil May Cry 4|3A|20251217/20251217171617570.png
jy0013|Dead Cells|Challenge|20251218/20251218002204661.jpg
jy0017|PES 2017|Sports|20251217/20251217163308236.png
jy0005|Final Fantasy XIII|3A|20251217/20251217161425729.png
jy0018|Transformers: Rise of the Dark Spark|Shooting|20251217/20251217152321412.png
jy0011|Hollow Knight|Challenge|20250331/20250331165157210.jpg`;
const BASE='https://download-oss.raccoongame.com/';
const TOP_IDS='jy0108 dg0170 jy0333 bs0096 jy0354 bs0054 bs0053 bs0017 dg0183 kj0283 bs0046 bs0055 jy0091 bs0034 kj0630 dg0315 bs0095 bs0093 kj0516 dg0128 dg0129 dg0127 jy0408 bs0078 kj0214 jy0541 kj0213 jy0040 jy0095 kp0175 dg0170 kj0089 kj0125 dg0154 jy0039 jy0008 jy0038 jy0103 jy0123 kj0499 jy0146 jy0013 jy0011 bs0092 bs0087 bs0082 bs0053 dg0730 kj0138 kj0137 kj0442 jy0229 dg0364 kj0166 jy0440 dg0021 kj0440 jy0352'.split(' ');
const GAMES=RAW.split('\n').map((l,i)=>{const [id,name,tag,img]=l.split('|');return{id,name,tag,i,img:BASE+(img.startsWith('images/')?img:'uploads/image/'+img)}});
const seen=new Set();const TOP=[];TOP_IDS.forEach(id=>{const g=GAMES.find(x=>x.id===id);if(g&&!seen.has(id)){seen.add(id);TOP.push(g)}});
GAMES.forEach(g=>{g.rank=seen.has(g.id)?TOP.findIndex(x=>x.id===g.id):1000+g.i});
