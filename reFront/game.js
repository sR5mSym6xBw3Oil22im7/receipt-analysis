const API_BASE_URL = window.APP_CONFIG?.API_BASE_URL ?? "http://localhost:8081";
const localModeButton=document.getElementById("local-mode"), roomModeButton=document.getElementById("room-mode"), roomPanel=document.getElementById("room-panel"), roomCodeLabel=document.getElementById("room-code-label"), playerName=document.getElementById("player-name"), roomCode=document.getElementById("room-code"), createRoom=document.getElementById("create-room"), joinRoom=document.getElementById("join-room"), roomInfo=document.getElementById("room-info"), apiKey=document.getElementById("game-api-key"), clearKey=document.getElementById("clear-game-key"), filesInput=document.getElementById("game-files"), fileNames=document.getElementById("file-names"), uploadButton=document.getElementById("upload-files"), statusElement=document.getElementById("game-status"), candidateList=document.getElementById("candidate-list"), monsterPanel=document.getElementById("monster-panel"), lockButton=document.getElementById("lock-button"), nextPlayer=document.getElementById("next-player"), playerLabel=document.getElementById("player-label"), stepLabel=document.getElementById("step-label"), resultCard=document.getElementById("battle-result"), battleContent=document.getElementById("battle-content"),gameDetailPanel=document.getElementById("game-detail-panel"),gameDetailMeta=document.getElementById("game-detail-meta"),gameDetailBubble=document.getElementById("game-detail-bubble");
let mode="local", phase="PLAYER1", candidates=[], selected=null, busy=false, room=null, pollTimer=null, objectUrls=[];
const escapeHtml=value=>String(value??"").replace(/[&<>'"]/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;","'":"&#39;",'"':"&quot;"}[c]));
function setStatus(text,error=false){statusElement.textContent=text;statusElement.classList.toggle("error-message",error);}
const QUOTA_EXCEEDED_MESSAGE="Gemini APIのクォータを超過しました。別のAPIキーを入力して、もう一度試してください。";
function isQuotaExceeded(error){return error?.status===429||error?.code==="GEMINI_QUOTA_EXCEEDED";}
function clearStoredApiKeys(){apiKey.value="";for(const storageName of ["localStorage","sessionStorage"]){try{const storage=window[storageName];for(let index=storage.length-1;index>=0;index--){const key=storage.key(index);if(key&&/api.?key|gemini/i.test(key))storage.removeItem(key);}}catch{}}try{for(const cookie of document.cookie.split(";")){const name=cookie.split("=")[0].trim();if(name&&/api.?key|gemini/i.test(name)){document.cookie=`${name}=; Max-Age=0; path=/`;document.cookie=`${name}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/`;}}}catch{}}
function url(path){return `${API_BASE_URL}${path}`;}
async function request(path,options={}){const response=await fetch(url(path),options);let body=null;try{body=await response.json();}catch{}if(!response.ok){const e=new Error(body?.message||"通信に失敗しました。");e.code=body?.code;e.status=response.status;throw e;}return body;}
function headers(){return room?.token?{"X-Receipt-Game-Token":room.token}:{};}
function clearObjects(){objectUrls.forEach(URL.revokeObjectURL);objectUrls=[];}
function resetPlayer(){clearObjects();candidates=[];selected=null;candidateList.replaceChildren();gameDetailPanel.classList.add("hidden");monsterPanel.classList.add("hidden");monsterPanel.replaceChildren();lockButton.classList.add("hidden");nextPlayer.classList.add("hidden");resultCard.classList.add("hidden");filesInput.value="";fileNames.textContent="未選択";stepLabel.textContent="候補レシートをアップロード";setStatus("");}
const GAME_ANALYZE_REQUEST_TIMEOUT_MS=210000;
const GAME_ANALYSIS_WAIT_MESSAGE_INTERVAL_MS=15000;
function formatGameDate(value){return value?new Date(value).toLocaleString("ja-JP"):"日時不明";}
function startGameAnalysisWaitMessage(fileNumber,totalFiles){let elapsedSeconds=0;return setInterval(()=>{elapsedSeconds+=GAME_ANALYSIS_WAIT_MESSAGE_INTERVAL_MS/1000;setStatus(`画像${fileNumber}/${totalFiles}を解析中です（${elapsedSeconds}秒経過）。Geminiの応答を待っています。ブラウザを閉じずにお待ちください。`);},GAME_ANALYSIS_WAIT_MESSAGE_INTERVAL_MS);}
async function requestGameCandidate(path,options={}){const controller=new AbortController();const timeoutId=setTimeout(()=>controller.abort(),GAME_ANALYZE_REQUEST_TIMEOUT_MS);try{return await request(path,{...options,signal:controller.signal});}catch(error){if(error?.name==="AbortError")throw new Error("バックエンドサーバーから応答がありません。通信状態またはRenderの稼働状態を確認して、再度解析してください。");throw error;}finally{clearTimeout(timeoutId);}}
async function expandGameFiles(sourceFiles){const files=[];for(const sourceFile of sourceFiles){files.push(...await receiptGameFileUtils.expandSelectedFile(sourceFile));}return files;}
function showGameDetail(detail){if(!detail)return;gameDetailMeta.textContent=`${detail.tableName} / ${detail.lineCount}行 / ${formatGameDate(detail.createdAt)}`;gameDetailBubble.replaceChildren();for(const line of detail.lines??[]){const lineElement=document.createElement("p");lineElement.className="receipt-line";lineElement.textContent=`${line.lineNo}. ${line.text}`;gameDetailBubble.append(lineElement);}gameDetailPanel.classList.remove("hidden");gameDetailPanel.scrollIntoView({behavior:"smooth",block:"start"});}
function renderCandidates(){candidateList.replaceChildren();gameDetailPanel.classList.add("hidden");for(const [index,c] of candidates.entries()){const row=document.createElement("div");row.className="receipt-list-row";const info=document.createElement("div");info.className="receipt-list-info";const title=document.createElement("strong");title.textContent=c.receiptTableName;const meta=document.createElement("span");meta.textContent=`${c.detail?.lineCount??c.itemCount}行 / ${formatGameDate(c.detail?.createdAt)}`;info.append(title,meta);const actions=document.createElement("div");actions.className="button-row";const referenceButton=document.createElement("button");referenceButton.type="button";referenceButton.className="secondary-button";referenceButton.textContent="参照";referenceButton.addEventListener("click",()=>showGameDetail(c.detail));const selectButton=document.createElement("button");selectButton.type="button";selectButton.textContent="このレシートを選ぶ";selectButton.addEventListener("click",()=>selectCandidate(index));actions.append(referenceButton,selectButton);row.append(info,actions);candidateList.append(row);}}
async function loadStoredReceipts(){try{const summaries=await request("/api/receipts");const loaded=await Promise.all((Array.isArray(summaries)?summaries:[]).map(async summary=>({receiptTableName:summary.tableName,itemCount:summary.lineCount,detail:await request("/api/receipts/"+encodeURIComponent(summary.tableName)),stored:true})));candidates=loaded;renderCandidates();setStatus(loaded.length?`${loaded.length}件の保存済みレシートを候補に表示しました。`:"保存済みレシートはありません。画像をアップロードしてください。");}catch(e){setStatus("保存済みレシートの読み込みに失敗しました: "+e.message,true);}}
async function selectCandidate(index){gameDetailPanel.classList.add("hidden");selected=candidates[index];candidateList.querySelectorAll("button").forEach(b=>b.disabled=true);setStatus("モンスターを生成中です。Geminiの応答を待っています。");try{const path=mode==="room"?`/api/receipt-game/rooms/${room.code}/monsters/${encodeURIComponent(selected.receiptTableName)}`:`/api/receipt-game/monsters/${encodeURIComponent(selected.receiptTableName)}`;selected.monster=await request(path,{method:"POST",headers:headers(),body:new URLSearchParams({geminiApiKey:apiKey.value})});renderMonster(selected.monster);setStatus("モンスターが準備できました。能力を確認してLOCKしてください。");}catch(e){if(isQuotaExceeded(e)){clearStoredApiKeys();setStatus(QUOTA_EXCEEDED_MESSAGE,true);apiKey.focus();}else setStatus(e.message,true);candidateList.querySelectorAll("button").forEach(b=>b.disabled=false);}}
function renderMonster(m){monsterPanel.classList.remove("hidden");monsterPanel.innerHTML=`<div class="monster-card"><div class="monster-image-wrap"><img id="monster-image" alt="${escapeHtml(m.monsterName)}"></div><div><p class="rarity">${escapeHtml(m.rarity)}</p><h3>${escapeHtml(m.monsterName)}</h3><p>${escapeHtml(m.species)}</p><div class="stat-bars"><span>POWER <b>${m.power}</b></span><span>GUARD <b>${m.guard}</b></span><span>SPEED <b>${m.speed}</b></span></div></div></div>`;const img=document.getElementById("monster-image");if(m.imageDataUrl) img.src=m.imageDataUrl; else if(mode==="room"){fetchImage(m.imageUrl,room.token).then(src=>img.src=src).catch(e=>setStatus(e.message,true));}lockButton.classList.remove("hidden");}
async function fetchImage(path,token){const r=await fetch(url(path),{headers:{"X-Receipt-Game-Token":token}});if(!r.ok)throw new Error("モンスター画像を取得できませんでした。");const src=URL.createObjectURL(await r.blob());objectUrls.push(src);return src;}
async function uploadFiles(){
  if(busy)return;
  const sourceFiles=[...filesInput.files];
  if(sourceFiles.length<1){setStatus("レシート画像を1枚以上選択してください。",true);return;}
  busy=true;
  uploadButton.disabled=true;
  let files;
  try{
    files=await expandGameFiles(sourceFiles);
  }catch(e){
    setStatus("解析エラー: "+e.message,true);
    busy=false;
    uploadButton.disabled=false;
    return;
  }
  if(files.some(f=>f.size>5242880)){setStatus("5MBを超える画像は選択できません。",true);busy=false;uploadButton.disabled=false;return;}
  if(candidates.filter(c=>!c.stored).length+files.length>10){
    setStatus("1人あたり10枚まで選択できます。",true);
    busy=false;
    uploadButton.disabled=false;
    return;
  }
  let success=0;
  let quotaExceeded=false;
  for(const [index,file] of files.entries()){
    try{
      setStatus("画像"+(index+1)+"/"+files.length+"を解析中です。");
      const data=new FormData();
      data.append("file",file);
      data.append("geminiApiKey",apiKey.value);
      const path=mode==="room"?"/api/receipt-game/rooms/"+room.code+"/candidates":"/api/receipt-game/candidates";
      const waitMessageTimer=startGameAnalysisWaitMessage(index+1,files.length);
      let c;
      try{
        c=await requestGameCandidate(path,{method:"POST",headers:headers(),body:data});
      }finally{
        clearInterval(waitMessageTimer);
      }
      c.detail=await request("/api/receipts/"+encodeURIComponent(c.receiptTableName));
      if(!candidates.some(x=>x.imageSha256===c.imageSha256)){
        c.preview=URL.createObjectURL(file);
        objectUrls.push(c.preview);
        candidates.push(c);
        success++;
      }
      renderCandidates();
      setStatus(success+"/"+files.length+"枚を候補に追加しました。");
    }catch(e){
      if(isQuotaExceeded(e)){quotaExceeded=true;clearStoredApiKeys();setStatus(QUOTA_EXCEEDED_MESSAGE,true);apiKey.focus();break;}
      setStatus(success+"/"+files.length+"枚成功。今回の画像は除外: "+e.message,true);
    }
  }
  busy=false;
  uploadButton.disabled=false;
  if(candidates.length===0&&!quotaExceeded)setStatus("対戦に使用できるレシートがありません。別のレシート画像をアップロードしてください。",true);
}
async function lock(){if(!selected?.monster||busy)return;busy=true;lockButton.disabled=true;try{if(mode==="room"){const state=await request(`/api/receipt-game/rooms/${room.code}/lock`,{method:"POST",headers:{...headers(),"Content-Type":"application/json"},body:JSON.stringify({monsterId:selected.monster.monsterId})});renderRoomState(state);setStatus(state.battle?"BATTLE COMPLETE":state.opponentLocked?"相手のLOCKを確認しました。":"LOCKしました。相手の選択を待っています。");startPolling();}else if(phase==="PLAYER1"){lockButton.classList.add("hidden");nextPlayer.classList.remove("hidden");stepLabel.textContent="Player 1 LOCK済み";setStatus("選択内容を非表示にしました。PLAYER 2へ交代してください。");apiKey.value="";}else{const battle=await request("/api/receipt-game/battles/local",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({player1MonsterId:window.p1MonsterId,player2MonsterId:selected.monster.monsterId})});showBattle(battle);}}catch(e){setStatus(e.message,true);lockButton.disabled=false;}busy=false;}
function next(){window.p1MonsterId=selected.monster.monsterId;phase="PLAYER2";playerLabel.textContent="PLAYER 2";resetPlayer();apiKey.value="";setStatus("Player 2のGemini APIキーを入力してください。");}
function showBattle(b){resultCard.classList.remove("hidden");battleContent.innerHTML=`<div class="battle-grid">${battleMonster(b.player1,b.player1BattleScoreX2/2,"PLAYER 1") }<div class="versus">VS<br><strong>${escapeHtml(b.winnerPlayer)} WINNER</strong></div>${battleMonster(b.player2,b.player2BattleScoreX2/2,"PLAYER 2")}</div>`;resultCard.scrollIntoView({behavior:"smooth"});}
function battleMonster(m,score,label){return `<article class="battle-monster"><p>${label}</p><img src="${m.imageDataUrl || ""}" alt="${escapeHtml(m.monsterName)}"><h3>${escapeHtml(m.monsterName)}</h3><p>${escapeHtml(m.rarity)} / ${escapeHtml(m.species)}</p><p>POWER ${m.power} / GUARD ${m.guard} / SPEED ${m.speed}</p><strong>BATTLE SCORE ${score}</strong></article>`;}
function renderRoomState(state){playerLabel.textContent=state.player;roomInfo.textContent=`ROOM CODE: ${state.roomCode}　状態: ${state.status}`;if(state.ownMonster){selected={monster:state.ownMonster};renderMonster(state.ownMonster);lockButton.classList.toggle("hidden",state.ownLocked);}if(state.battle)showBattleWithRoom(state.battle);}
function showBattleWithRoom(b){resultCard.classList.remove("hidden");const p1=b.player1,p2=b.player2;battleContent.innerHTML=`<div class="battle-grid"><article class="battle-monster"><p>PLAYER 1</p><img data-room-image="${p1.monsterId}" alt="${escapeHtml(p1.monsterName)}"><h3>${escapeHtml(p1.monsterName)}</h3><p>${p1.rarity} / ${p1.species}</p><p>POWER ${p1.power} / GUARD ${p1.guard} / SPEED ${p1.speed}</p><strong>BATTLE SCORE ${b.player1BattleScoreX2/2}</strong></article><div class="versus">VS<br><strong>${escapeHtml(b.winnerPlayer)} WINNER</strong></div><article class="battle-monster"><p>PLAYER 2</p><img data-room-image="${p2.monsterId}" alt="${escapeHtml(p2.monsterName)}"><h3>${escapeHtml(p2.monsterName)}</h3><p>${p2.rarity} / ${p2.species}</p><p>POWER ${p2.power} / GUARD ${p2.guard} / SPEED ${p2.speed}</p><strong>BATTLE SCORE ${b.player2BattleScoreX2/2}</strong></article></div>`;document.querySelectorAll("[data-room-image]").forEach(async img=>{try{img.src=await fetchImage(`/api/receipt-game/rooms/${room.code}/monsters/${img.dataset.roomImage}/image`,room.token);}catch{}});resultCard.scrollIntoView({behavior:"smooth"});}
function startPolling(){if(mode!=="room"||pollTimer)return;pollTimer=setInterval(async()=>{try{const state=await request(`/api/receipt-game/rooms/${room.code}`,{headers:headers()});renderRoomState(state);if(state.status==="BATTLE_COMPLETE")clearInterval(pollTimer),pollTimer=null;}catch(e){setStatus(e.message,true);}},3000);}
function switchMode(nextMode){mode=nextMode;localModeButton.classList.toggle("active",mode==="local");roomModeButton.classList.toggle("active",mode==="room");roomPanel.classList.toggle("hidden",mode!=="room");roomCodeLabel.classList.toggle("hidden",mode!=="room");resetPlayer();playerLabel.textContent="PLAYER 1";phase="PLAYER1";room=null;roomInfo.textContent="";if(pollTimer)clearInterval(pollTimer),pollTimer=null;}loadStoredReceipts();
localModeButton.addEventListener("click",()=>switchMode("local"));roomModeButton.addEventListener("click",()=>switchMode("room"));clearKey.addEventListener("click",()=>{apiKey.value="";apiKey.focus();});filesInput.addEventListener("change",()=>{const files=[...filesInput.files];fileNames.textContent=files.length?files.map(f=>f.name).join("、"):"未選択";});uploadButton.addEventListener("click",uploadFiles);document.getElementById("close-game-detail").addEventListener("click",()=>gameDetailPanel.classList.add("hidden"));lockButton.addEventListener("click",lock);nextPlayer.addEventListener("click",next);createRoom.addEventListener("click",async()=>{try{room=await request("/api/receipt-game/rooms",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({playerName:playerName.value})});roomInfo.textContent=`ROOM CODE: ${room.roomCode}（このコードと参加用画面を相手へ共有）`;setStatus("ルームを作成しました。候補をアップロードしてください。");}catch(e){setStatus(e.message,true);}});joinRoom.addEventListener("click",async()=>{try{room=await request(`/api/receipt-game/rooms/${roomCode.value.trim().toUpperCase()}/join`,{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({playerName:playerName.value})});roomInfo.textContent=`ROOM CODE: ${room.roomCode} に参加しました。`;setStatus("候補をアップロードしてください。");}catch(e){setStatus(e.message,true);}});
