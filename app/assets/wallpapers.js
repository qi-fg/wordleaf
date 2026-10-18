'use strict';
// Original AI-generated scenic assets, shipped locally; no image network requests.
const WALLPAPERS=[
 {id:'lake',name:'湖山薄雾',file:'wallpapers/lake.jpg'},
 {id:'forest',name:'林间晨光',file:'wallpapers/forest.jpg'},
 {id:'coast',name:'海岸余晖',file:'wallpapers/coast.jpg'}
];
function cleanAppearance(value){
 const v=value&&typeof value==='object'?value:{};
 return{mode:['daily','fixed','plain'].includes(v.mode)?v.mode:'daily',wallpaper:WALLPAPERS.some(w=>w.id===v.wallpaper)?v.wallpaper:'lake'};
}
function wallpaperForDate(date=new Date()){
 const day=Math.floor(Date.UTC(date.getFullYear(),date.getMonth(),date.getDate())/86400000);
 return WALLPAPERS[((day%WALLPAPERS.length)+WALLPAPERS.length)%WALLPAPERS.length];
}
let displayedWallpaper=null;
function applyAppearance(){
 const preferences=cleanAppearance(library.appearance);
 const scenic=preferences.mode!=='plain'&&['home','study','spell'].includes(page);
 if(!displayedWallpaper||!['study','spell'].includes(page))displayedWallpaper=preferences.mode==='fixed'?WALLPAPERS.find(w=>w.id===preferences.wallpaper):wallpaperForDate();
 document.body.classList.toggle('scenic',scenic);
 document.body.classList.toggle('scenic-study',scenic&&page!=='home');
 document.body.dataset.wallpaper=scenic?displayedWallpaper.id:'';
 document.documentElement.style.setProperty('--wallpaper-image',`url("${displayedWallpaper.file}")`);
 const color=scenic?'#142e39':'#f8f9f5';
 document.querySelector('meta[name="theme-color"]').content=color;
 if(window.Android&&typeof Android.setScenicTheme==='function')Android.setScenicTheme(scenic);
}
function appearanceHtml(){
 const p=cleanAppearance(library.appearance),selected=p.mode==='fixed'?p.wallpaper:wallpaperForDate().id;
 return `<div class="appearance-dialog"><h2>壁纸与外观</h2><p class="subtle">图片保存在应用内，不联网、不消耗流量。</p><div class="appearance-modes">${[['daily','每日壁纸','每天换一张，当天保持一致'],['fixed','固定壁纸','保留你选择的风景'],['plain','纯色背景','使用清爽的浅色界面']].map(([id,name,note])=>`<button class="mode-option ${p.mode===id?'selected':''}" data-action="appearance-mode" data-id="${id}" aria-pressed="${p.mode===id}"><span><strong>${name}</strong><small>${note}</small></span>${p.mode===id?icon('check'):''}</button>`).join('')}</div><h3>内置风景</h3><div class="wallpaper-options">${WALLPAPERS.map(w=>`<button class="wallpaper-option ${p.mode!=='plain'&&selected===w.id?'selected':''}" data-action="wallpaper-select" data-id="${w.id}" aria-label="固定壁纸：${w.name}" aria-pressed="${p.mode==='fixed'&&p.wallpaper===w.id}"><img src="${w.file}" alt="" loading="lazy"><span>${w.name}${p.mode!=='plain'&&selected===w.id?' · 当前':''}</span></button>`).join('')}</div><p class="small-note">首版三张风景按手机本地日期循环，每三天一轮，并非每天下载新图。点击风景可设为固定壁纸。学习中不换图，返回首页后应用当天壁纸。</p><p class="small-note">首页与学习页使用壁纸，词库和记录页保留浅色底，便于阅读。背景为 AI 生成的风景图。</p></div>`;
}
function changeAppearance(mode,wallpaper){
 if(readFailed){toast('请先恢复有效备份。');return;}
 const p=cleanAppearance(library.appearance);
 library.appearance=cleanAppearance({mode,wallpaper:wallpaper||p.wallpaper});
 displayedWallpaper=null;
 save();render();
}
