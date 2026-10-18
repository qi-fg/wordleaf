async(page)=>{
 const checks=[],errors=[];
 page.on('pageerror',e=>errors.push(e.message));
 const assert=(ok,label)=>{if(!ok)throw Error(label);checks.push(label);};
 const lake='data:image/jpeg;base64,'+(await (await page.request.get('http://127.0.0.1:8765/wallpapers/lake.jpg')).body()).toString('base64');
 const forest='data:image/jpeg;base64,'+(await (await page.request.get('http://127.0.0.1:8765/wallpapers/forest.jpg')).body()).toString('base64');
 // Model the Android bridge while denying ALL JPEG requests: only packaged bytes can render.
 await page.route('**/wallpapers/*.jpg',r=>r.abort());
 await page.addInitScript(({lake})=>{
  window.wallpaperBytes=lake;window.wallpaperCalls=0;window.savedAndroidState='';
  window.Android={getState:()=>localStorage.getItem('wordleaf-state'),saveState:s=>{window.savedAndroidState=s;localStorage.setItem('wordleaf-state',s);},wallpaperData:()=>{window.wallpaperCalls++;return window.wallpaperBytes;},setScenicTheme:v=>window.nativeScenic=v};
 },{lake});
 await page.clock.setFixedTime(new Date('2026-10-18T12:00:00+08:00'));
 await page.goto('http://127.0.0.1:8765/qa-blank');await page.evaluate(()=>localStorage.clear());
 await page.goto('http://127.0.0.1:8765/');await page.setViewportSize({width:390,height:844});
 await page.locator('#wallpaper-image').evaluate(img=>img.decode());
 assert(await page.evaluate(()=>document.body.classList.contains('scenic')&&document.getElementById('wallpaper-image').naturalWidth>0),'Android bridge bytes render even when all JPEG URLs are blocked');
 assert((await page.locator('#wallpaper-image').getAttribute('src')).startsWith('data:image/jpeg;base64,'),'The Android image uses data bytes rather than CSS URL resolution');
 assert(await page.evaluate(()=>nativeScenic===true),'Native system bars receive scenic theme');
 assert(await page.evaluate(()=>wallpaperCalls===1),'Packaged image bytes are read only once, not on every redraw');
 await page.evaluate(()=>render());assert(await page.evaluate(()=>wallpaperCalls===1),'Rendering again reuses image bytes');
 await page.getByRole('button',{name:'我的',exact:true}).click();
 assert(await page.locator('[data-action=appearance]').count()===0,'Manual wallpaper settings entry is removed');
 assert(await page.evaluate(()=>!nativeScenic&&!document.body.classList.contains('scenic')),'Record page remains light');
 const legacy=await page.evaluate(()=>{state.planChosen=true;state.words.program={...emptyProgress(),reps:2,stage:2,due:123456,star:true};state.history[dayKey()]={...emptyHistory(),newTarget:20};const d=clone(snapshotLibrary());d.appearance={mode:'plain',wallpaper:'coast'};return d;});
 await page.goto('http://127.0.0.1:8765/qa-blank');await page.evaluate(d=>localStorage.setItem('wordleaf-state',JSON.stringify(d)),legacy);await page.goto('http://127.0.0.1:8765/');
 assert(await page.evaluate(()=>library.appearance.mode==='daily'&&document.body.classList.contains('scenic')),'An old plain preference automatically migrates to daily background');
 assert(await page.evaluate(()=>progress('program').reps===2&&progress('program').star&&today().newTarget===20),'Migration preserves learning progress, favorites and locked daily target');
 assert(await page.evaluate(()=>validateLibrary({...clone(snapshotLibrary()),appearance:{mode:'fixed',wallpaper:'forest'}}).appearance.mode==='daily'),'An old fixed preference also migrates automatically');
 const initial=await page.locator('#wallpaper-image').getAttribute('src');
 await page.evaluate(()=>{state.planChosen=true;state.goal=5;save();});
 await page.locator('[data-action=start][data-id=new]').click();
 assert(await page.evaluate(()=>page==='study'&&document.body.classList.contains('scenic-study')),'Study uses the stronger contrast mask');
 await page.evaluate(data=>{window.wallpaperBytes=data;wallpaperReady();},forest);
 assert(await page.locator('#wallpaper-image').getAttribute('src')===initial,'A new background download does not replace the image mid-word');
 await page.locator('[data-action=reveal]').click();
 assert(await page.locator('#wallpaper-image').getAttribute('src')===initial,'Revealing meaning keeps the frozen image despite a newly cached picture');
 assert(await page.locator('.definition>p').isVisible(),'Chinese meaning remains visible with the background');
 await page.setViewportSize({width:320,height:740});await page.screenshot({path:'output/playwright/v1.3.5-study.png'});
 await page.evaluate(()=>pauseHome());
 assert(await page.locator('#wallpaper-image').getAttribute('src')===forest,'Returning home applies the verified downloaded background');
 const before=await page.evaluate(()=>JSON.stringify(state));
 await page.evaluate(()=>wallpaperReady());
 assert(await page.evaluate(s=>JSON.stringify(state)===s,before),'Background updates do not change queue, review counts or daily target');
 await page.clock.setFixedTime(new Date('2026-10-19T12:00:00+08:00'));await page.evaluate(()=>window.resumeLearningClock(Date.now()));
 assert(await page.evaluate(()=>document.body.dataset.wallpaper===wallpaperForDate().id),'Returning to home on a new date refreshes the daily selection');
 assert(await page.evaluate(()=>validateLibrary(clone(snapshotLibrary())).appearance.mode==='daily'),'Learning backup round-trip remains compatible');
 for(const width of [320,390]){await page.setViewportSize({width,height:844});assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),`Home fits ${width}px`);}
 await page.screenshot({path:'output/playwright/v1.3.5-home.png'});
 await page.getByRole('button',{name:'我的',exact:true}).click();await page.locator('[data-action=about]').click();
 assert((await page.locator('.about').innerText()).includes('每天在后台检查'),'About page explains automatic background maintenance');
 assert(errors.length===0,'No runtime errors in the Android bridge flow');
 // Exercise the plain-browser local URL fallback separately, with no Android bridge.
 await page.unroute('**/wallpapers/*.jpg');
 await page.goto('http://127.0.0.1:8765/qa-blank');
 await page.evaluate(()=>{delete window.Android;});
 await page.addInitScript(()=>{delete window.Android;});
 await page.goto('http://127.0.0.1:8765/');await page.locator('#wallpaper-image').evaluate(img=>img.decode());
 assert(await page.evaluate(()=>!window.Android&&document.getElementById('wallpaper-image').naturalWidth>0),'Browser preview still loads its local image without Android');
 assert((await page.locator('#wallpaper-image').getAttribute('src')).startsWith('wallpapers/'),'Browser fallback uses only the packaged relative asset');
 return{passed:checks.length,checks};
}
