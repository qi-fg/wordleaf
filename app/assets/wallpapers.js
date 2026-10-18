'use strict';
const WALLPAPERS=[
 {id:'lake',file:'wallpapers/lake.jpg'},
 {id:'forest',file:'wallpapers/forest.jpg'},
 {id:'coast',file:'wallpapers/coast.jpg'}
];
// Migrate former fixed/plain preferences to the automatic daily experience.
function cleanAppearance(){return{mode:'daily',wallpaper:'lake'};}
function wallpaperForDate(date=new Date()){
 const day=Math.floor(Date.UTC(date.getFullYear(),date.getMonth(),date.getDate())/86400000);
 return WALLPAPERS[((day%WALLPAPERS.length)+WALLPAPERS.length)%WALLPAPERS.length];
}
const wallpaperSources=new Map();
let displayedWallpaper=null,displayedWallpaperSrc='';
function wallpaperSource(w){
 const key=dayKey()+':'+w.id;
 if(wallpaperSources.has(key))return wallpaperSources.get(key);
 let source=w.file;
 try{if(window.Android&&typeof Android.wallpaperData==='function'){const data=Android.wallpaperData(w.id);if(typeof data==='string'&&data.startsWith('data:image/jpeg;base64,'))source=data;}}catch(e){/* Local URL remains a fallback. */}
 wallpaperSources.clear();wallpaperSources.set(key,source);return source;
}
function applyAppearance(){
 const scenic=['home','study','spell'].includes(page),learning=page==='study'||page==='spell';
 if(!displayedWallpaper||!learning){displayedWallpaper=wallpaperForDate();displayedWallpaperSrc=wallpaperSource(displayedWallpaper);}
 document.body.classList.toggle('scenic',scenic);
 document.body.classList.toggle('scenic-study',scenic&&learning);
 document.body.dataset.wallpaper=scenic?displayedWallpaper.id:'';
 const image=document.getElementById('wallpaper-image');
 if(scenic&&image.getAttribute('src')!==displayedWallpaperSrc)image.src=displayedWallpaperSrc;
 document.querySelector('meta[name="theme-color"]').content=scenic?'#142e39':'#f8f9f5';
 if(window.Android&&typeof Android.setScenicTheme==='function')Android.setScenicTheme(scenic);
}
window.wallpaperReady=()=>{
 wallpaperSources.clear();
 // A newly downloaded picture is staged until home, never changed mid-word.
 if(page==='home'&&!modal)applyAppearance();
};
