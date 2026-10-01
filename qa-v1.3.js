async(page)=>{
  const checks=[],errors=[];
  page.on('pageerror',error=>errors.push(error.message));
  const assert=(ok,label)=>{if(!ok)throw Error(label);checks.push(label);};
  const click=name=>page.getByRole('button',{name,exact:true}).click();
  const action=(name,id)=>page.locator(`[data-action="${name}"]${id?'[data-id="'+id+'"]':''}`).first().click();
  const seed=async(data)=>{await page.goto('http://127.0.0.1:8765/qa-blank');await page.evaluate(data=>localStorage.setItem('wordleaf-state',JSON.stringify(data)),data);await page.goto('http://127.0.0.1:8765/');};
  await page.goto('http://127.0.0.1:8765/');await page.setViewportSize({width:390,height:844});
  const fixture=await page.evaluate(()=>{const s=fresh();s.goal=5;s.planChosen=true;for(const w of WORDS.slice(0,2)){s.words[w.w]={...emptyProgress(),reps:1,stage:1,due:1,first:dayKey()};}return{version:3,activeMode:'cet6',books:{cet6:s}};});
  await seed(fixture);
  assert(await page.locator('.mode-trigger').innerText()==='CET-6'&&await page.locator('.mode-trigger span,.mode-trigger svg').count()===0,'Mode button contains only the current mode text');
  assert(await page.locator('.practice-card').count()===4,'Home presents new learning, review, spelling and reinforcement');
  await action('start','new');
  assert(await page.evaluate(()=>study.mode==='new'&&study.queue.length===5&&study.queue.every(w=>!progress(w).reps)),'New-only session excludes due review words and respects the daily quota');
  assert(await page.locator('[data-action="reveal"]').count()===1,'Recall card has a single reveal action');
  await click('这个词我已会，跳过');
  assert(await page.evaluate(()=>study.queue.length===6&&study.initial.length===6&&today().new.length===0),'Skipping a known word replenishes a new-only session without using the quota');
  await click('撤销上一次选择');
  assert(await page.evaluate(()=>study.queue.length===5&&!progress(study.queue[0]).known),'Undo restores the new-only queue and known status');
  await click('查看释义');await action('rate','good');
  await click('退出学习');await click('保存位置，回到今日');await page.reload();
  assert(await page.evaluate(()=>!readFailed&&state.session.mode==='new'&&state.session.index===1),'New-only session survives cold reload and validation');
  await click('继续上次学习');
  for(let i=0;i<4;i++){await click('查看释义');await action('rate','good');}
  assert(await page.getByRole('heading',{name:'这一轮学习，完成了。',exact:true}).count()===1,'Final card leads to a clearly labeled learning result');
  assert(await page.evaluate(()=>today().new.length===5&&dueWords().length===2),'New-only completion leaves due review words available and counts new words once');
  await click('测一测这组的拼写');
  const group=await page.evaluate(()=>study.queue.slice());
  await page.getByLabel('英文拼写').fill('incorrect');await page.getByLabel('英文拼写').press('Enter');
  await click('下一个单词');await click('首字母提示');await page.getByLabel('英文拼写').fill(group[1]);await click('提交拼写');
  for(let i=2;i<5;i++){await click('下一个单词');await page.getByLabel('英文拼写').fill(group[i]);await click('提交拼写');}
  await click('完成本轮拼写');
  assert(await page.getByRole('button',{name:'只练本轮薄弱拼写 (2)',exact:true}).count()===1,'Spelling result offers only wrong or assisted answers from this round');
  await click('只练本轮薄弱拼写 (2)');
  assert(await page.evaluate(group=>study.queue.join('|')===group.slice(0,2).join('|')&&study.returnCards.index===5,group),'Targeted retry keeps the exact parent learning position and excludes correct words');
  await page.getByLabel('英文拼写').fill('unfinished');
  await click('退出学习');await click('保存位置，回到今日');await action('mode');await action('switch-mode','cet4');await action('mode');await action('switch-mode','cet6');
  await click('我的');
  const downloaded=page.waitForEvent('download');await action('export');await(await downloaded).saveAs('output/playwright/v1.3-backup.json');
  await page.evaluate(()=>{state.goal=100;state.session=null;save();});
  await page.locator('#backup-file').setInputFiles('output/playwright/v1.3-backup.json');
  await page.getByRole('heading',{name:'恢复这份备份？',exact:true}).waitFor({state:'visible'});await click('确认恢复');
  assert(await page.evaluate(()=>state.goal===5&&state.session.returnCards.mode==='new'&&state.session.spell.answer==='unfinished'),'Backup restores the targeted retry and nested new-only session');
  await click('今日');await page.reload();await click('继续上次学习');
  assert(await page.getByLabel('英文拼写').inputValue()==='unfinished'&&await page.evaluate(()=>study.returnCards.mode==='new'),'Mode switching preserves retry input and the nested new-only session');
  for(let i=0;i<2;i++){await page.getByLabel('英文拼写').fill(group[i]);await click('提交拼写');await click(i===0?'下一个单词':'完成本轮拼写');}
  assert(await page.evaluate(()=>progress(study.initial[0]).spellWeak&&progress(study.initial[0]).spellWins===1),'Same-day retry remains scheduled for later independent reinforcement');
  await click('回到词义学习');await click('回到今日');
  await action('start','review');
  assert(await page.evaluate(()=>study.mode==='review'&&study.queue.length===2&&study.queue.every(w=>progress(w).reps>0)),'Dedicated review session still includes only the due learned words');
  await click('退出学习');await click('保存位置，回到今日');await action('weak-list');
  assert(await page.locator('.word-row').count()===2,'Targeted spelling errors remain visible in the reinforcement list');
  await click('今日');
  for(const width of [320,360,390]){
    await page.setViewportSize({width,height:844});
    assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'Home fits viewport width '+width);
    assert(await page.evaluate(()=>[...document.querySelectorAll('.practice-card,.mode-trigger,.primary')].every(el=>el.getBoundingClientRect().height>=44)),'Home touch targets stay usable at '+width);
  }
  await page.setViewportSize({width:390,height:844});
  await page.screenshot({path:'output/playwright/v1.3-flow-home.png'});
  assert(errors.length===0,'No browser runtime errors');
  return{passed:checks.length,checks};
}
