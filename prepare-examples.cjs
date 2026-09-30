const fs=require('node:fs'),path=require('node:path');
global.window={};require('./app/assets/words.js');
const map=new Map(window.WORDLEAF_WORDS.map(w=>[w.w,w]));
const examples={};
for(const line of fs.readFileSync(path.join(__dirname,'examples.tsv'),'utf8').trim().split(/\r?\n/)){
  if(line.startsWith('#'))continue;
  const [word,meaning,en,zh]=line.split('\t');
  if(!map.has(word)||!meaning||!en||!zh||examples[word])throw Error('Invalid example row: '+word);
  const escaped=word.replace(/[.*+?^${}()|[\]\\]/g,'\\$&');
  if(!new RegExp('\\b'+escaped+'\\b','i').test(en))throw Error('Target word absent: '+word);
  examples[word]={m:meaning,en,zh};
}
fs.writeFileSync(path.join(__dirname,'app/assets/examples.js'),'window.WORDLEAF_EXAMPLES='+JSON.stringify(examples)+';\n');
fs.writeFileSync(path.join(__dirname,'examples-source.json'),JSON.stringify({count:Object.keys(examples).length,source:'Original short learning sentences written for Wordleaf; not quotations or CET-6 exam sentences.',coverage:'First batch; missing words explicitly show no example in the app.',checks:'Every target exists in the vocabulary, occurs as a whole word in the English sentence, has a Chinese translation and a focused meaning; each pair reviewed for usage and translation.',preparedDate:'2026-09-30'},null,2));
console.log('Original examples: '+Object.keys(examples).length);
