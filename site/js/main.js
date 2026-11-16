"use strict";
const $=(q)=>document.querySelector(q);
const $$=(q)=>Array.from(document.querySelectorAll(q));
const iconPath=(variant,file)=>`assets/icons/${variant}/${file}`;
const THEMES=["sunshine","sky","twilight","contrast"];
const VARIANTS=["gate","lock","open"];
const ICON_LABELS={gate:"Gate + Question",lock:"Lock + Numbers",open:"Open Gate + Success"};
const safeGet=(k,fallback)=>{try{return localStorage.getItem(k)||fallback}catch{return fallback}};
const safeSet=(k,v)=>{try{localStorage.setItem(k,v)}catch{}};
function setTheme(theme){
 if(!THEMES.includes(theme)) theme="sunshine";
 document.documentElement.setAttribute("data-theme",theme);
 $$("[data-set-theme]").forEach(btn=>btn.setAttribute("aria-pressed",String(btn.dataset.setTheme===theme)));
 safeSet("qg-theme",theme);
}
function setVariant(variant){
 if(!VARIANTS.includes(variant)) variant="gate";
 $$("[data-set-variant]").forEach(btn=>btn.setAttribute("aria-pressed",String(btn.dataset.setVariant===variant)));
 $("#nav-icon").src=iconPath(variant,"favicon-96x96.png");
 $("#preview-icon").src=iconPath(variant,"pwa-192.png");
 $("#demo-icon").src=iconPath(variant,"favicon-96x96.png");
 $("#icon-caption").textContent=ICON_LABELS[variant];
 const favicon=$("#dynamic-favicon");favicon.href=iconPath(variant,"favicon.svg");
 safeSet("qg-variant",variant);
}
$$('[data-set-theme]').forEach(b=>b.addEventListener('click',()=>setTheme(b.dataset.setTheme)));
$$('[data-set-variant]').forEach(b=>b.addEventListener('click',()=>setVariant(b.dataset.setVariant)));
setTheme(safeGet('qg-theme','sunshine'));
setVariant(safeGet('qg-variant','gate'));
const quiz=[
 {q:'Which color is the sky on a sunny day?', choices:['Coral red','Sky blue','Sunny yellow'], correct:1},
 {q:'What is 1 + 1?', choices:['1','2','3'], correct:1},
 {q:'What number comes after 4?', choices:['3','5','7'], correct:1}
];
let current=0,solved=false;
function showQuestion(){
 solved=false;
 const data=quiz[current];
 $('#quiz-count').textContent=`Question ${current+1} of ${quiz.length}`;
 $('#quiz-question').textContent=data.q;
 const box=$('#quiz-options');box.innerHTML='';
 data.choices.forEach((val,i)=>{
   const b=document.createElement('button');b.type='button';b.className='quiz-answer';b.textContent=val;
   b.addEventListener('click',()=>answer(i));box.appendChild(b);
 });
 $('#quiz-feedback').textContent='Choose an answer to try the demo.';
 $('#quiz-next').hidden=true;
}
function answer(which){
 if(solved)return;
 const item=quiz[current],correct=which===item.correct;
 const options=$$('#quiz-options button');
 if(correct){
   solved=true;options.forEach((b,i)=>{b.disabled=true;if(i===which)b.classList.add('correct')});
   $('#quiz-feedback').textContent=current===quiz.length-1?'Great job! You earned three stars.':'You got it! Great thinking.';
   $('#quiz-next').textContent=current===quiz.length-1?'Start again':'Next question';
   $('#quiz-next').hidden=false;
 }else{
   options[which].classList.add('wrong');
   $('#quiz-feedback').textContent='Good try! Have another go.';
   setTimeout(()=>options[which]?.classList.remove('wrong'),750);
 }
}
$('#quiz-next').addEventListener('click',()=>{current=(current+1)%quiz.length;showQuestion()});
showQuestion();
if('serviceWorker' in navigator && (location.protocol==='https:'||location.hostname==='localhost')){
 window.addEventListener('load',()=>navigator.serviceWorker.register('./sw.js').catch(()=>{}));
}
