import{p as U}from"./chunk-TMUBEWPD.B2N_1Uwe.js";import{X as x,O as z,aF as j,D as q,q as X,r as Z,s as H,g as J,c as K,b as Q,_ as u,l as F,x as Y,d as tt,E as et,I as at,a5 as rt,k as nt}from"../app.zwwMQxkH.js";import{p as it}from"./gitGraph-YCYPL57B.CBz6SDsq.js";import{d as N}from"./arc.sQiZf3gG.js";import{o as st}from"./ordinal.BYWQX77i.js";import"./framework.DA3SO-Rd.js";import"./theme.ZyT2Ramh.js";import"./baseUniq.CW5Xg6eV.js";import"./basePickBy.Bd2tY0Vg.js";import"./clone.BowbtCtU.js";import"./init.Gi6I4Gst.js";function ot(t,a){return a<t?-1:a>t?1:a>=t?0:NaN}function lt(t){return t}function ct(){var t=lt,a=ot,m=null,o=x(0),p=x(z),y=x(0);function i(e){var r,l=(e=j(e)).length,g,A,h=0,c=new Array(l),n=new Array(l),v=+o.apply(this,arguments),w=Math.min(z,Math.max(-z,p.apply(this,arguments)-v)),f,T=Math.min(Math.abs(w)/l,y.apply(this,arguments)),$=T*(w<0?-1:1),d;for(r=0;r<l;++r)(d=n[c[r]=r]=+t(e[r],r,e))>0&&(h+=d);for(a!=null?c.sort(function(S,D){return a(n[S],n[D])}):m!=null&&c.sort(function(S,D){return m(e[S],e[D])}),r=0,A=h?(w-l*$)/h:0;r<l;++r,v=f)g=c[r],d=n[g],f=v+(d>0?d*A:0)+$,n[g]={data:e[g],index:r,value:d,startAngle:v,endAngle:f,padAngle:T};return n}return i.value=function(e){return arguments.length?(t=typeof e=="function"?e:x(+e),i):t},i.sortValues=function(e){return arguments.length?(a=e,m=null,i):a},i.sort=function(e){return arguments.length?(m=e,a=null,i):m},i.startAngle=function(e){return arguments.length?(o=typeof e=="function"?e:x(+e),i):o},i.endAngle=function(e){return arguments.length?(p=typeof e=="function"?e:x(+e),i):p},i.padAngle=function(e){return arguments.length?(y=typeof e=="function"?e:x(+e),i):y},i}var P=q.pie,G={sections:new Map,showData:!1,config:P},b=G.sections,O=G.showData,ut=structuredClone(P),pt=u(()=>structuredClone(ut),"getConfig"),gt=u(()=>{b=new Map,O=G.showData,Y()},"clear"),dt=u(({label:t,value:a})=>{b.has(t)||(b.set(t,a),F.debug(`added new section: ${t}, with value: ${a}`))},"addSection"),ft=u(()=>b,"getSections"),mt=u(t=>{O=t},"setShowData"),ht=u(()=>O,"getShowData"),R={getConfig:pt,clear:gt,setDiagramTitle:X,getDiagramTitle:Z,setAccTitle:H,getAccTitle:J,setAccDescription:K,getAccDescription:Q,addSection:dt,getSections:ft,setShowData:mt,getShowData:ht},vt=u((t,a)=>{U(t,a),a.setShowData(t.showData),t.sections.map(a.addSection)},"populateDb"),St={parse:u(async t=>{const a=await it("pie",t);F.debug(a),vt(a,R)},"parse")},xt=u(t=>`
  .pieCircle{
    stroke: ${t.pieStrokeColor};
    stroke-width : ${t.pieStrokeWidth};
    opacity : ${t.pieOpacity};
  }
  .pieOuterCircle{
    stroke: ${t.pieOuterStrokeColor};
    stroke-width: ${t.pieOuterStrokeWidth};
    fill: none;
  }
  .pieTitleText {
    text-anchor: middle;
    font-size: ${t.pieTitleTextSize};
    fill: ${t.pieTitleTextColor};
    font-family: ${t.fontFamily};
  }
  .slice {
    font-family: ${t.fontFamily};
    fill: ${t.pieSectionTextColor};
    font-size:${t.pieSectionTextSize};
    // fill: white;
  }
  .legend text {
    fill: ${t.pieLegendTextColor};
    font-family: ${t.fontFamily};
    font-size: ${t.pieLegendTextSize};
  }
`,"getStyles"),yt=xt,At=u(t=>{const a=[...t.entries()].map(o=>({label:o[0],value:o[1]})).sort((o,p)=>p.value-o.value);return ct().value(o=>o.value)(a)},"createPieArcs"),wt=u((t,a,m,o)=>{F.debug(`rendering pie chart
`+t);const p=o.db,y=tt(),i=et(p.getConfig(),y.pie),e=40,r=18,l=4,g=450,A=g,h=at(a),c=h.append("g");c.attr("transform","translate("+A/2+","+g/2+")");const{themeVariables:n}=y;let[v]=rt(n.pieOuterStrokeWidth);v??(v=2);const w=i.textPosition,f=Math.min(A,g)/2-e,T=N().innerRadius(0).outerRadius(f),$=N().innerRadius(f*w).outerRadius(f*w);c.append("circle").attr("cx",0).attr("cy",0).attr("r",f+v/2).attr("class","pieOuterCircle");const d=p.getSections(),S=At(d),D=[n.pie1,n.pie2,n.pie3,n.pie4,n.pie5,n.pie6,n.pie7,n.pie8,n.pie9,n.pie10,n.pie11,n.pie12],C=st(D);c.selectAll("mySlices").data(S).enter().append("path").attr("d",T).attr("fill",s=>C(s.data.label)).attr("class","pieCircle");let W=0;d.forEach(s=>{W+=s}),c.selectAll("mySlices").data(S).enter().append("text").text(s=>(s.data.value/W*100).toFixed(0)+"%").attr("transform",s=>"translate("+$.centroid(s)+")").style("text-anchor","middle").attr("class","slice"),c.append("text").text(p.getDiagramTitle()).attr("x",0).attr("y",-400/2).attr("class","pieTitleText");const M=c.selectAll(".legend").data(C.domain()).enter().append("g").attr("class","legend").attr("transform",(s,k)=>{const E=r+l,_=E*C.domain().length/2,B=12*r,V=k*E-_;return"translate("+B+","+V+")"});M.append("rect").attr("width",r).attr("height",r).style("fill",C).style("stroke",C),M.data(S).append("text").attr("x",r+l).attr("y",r-l).text(s=>{const{label:k,value:E}=s.data;return p.getShowData()?`${k} [${E}]`:k});const L=Math.max(...M.selectAll("text").nodes().map(s=>(s==null?void 0:s.getBoundingClientRect().width)??0)),I=A+e+r+l+L;h.attr("viewBox",`0 0 ${I} ${g}`),nt(h,g,I,i.useMaxWidth)},"draw"),Dt={draw:wt},Wt={parser:St,db:R,renderer:Dt,styles:yt};export{Wt as diagram};
