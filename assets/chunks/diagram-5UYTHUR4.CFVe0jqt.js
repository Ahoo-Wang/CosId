import{p as y}from"./chunk-353BL4L5.BT5FgFZ0.js";import{E as B,s as S,g as F,o as z,p as E,b as P,c as W,_ as l,l as v,F as w,G as T,v as D,K as _,k as A}from"../app.DOc9Wq5i.js";import{p as N}from"./treemap-6Y5VK53G.DVHHtcAO.js";import"./framework.Dg3afQf7.js";import"./theme.DsI75Nu7.js";import"./baseUniq.wSrfPWkb.js";import"./basePickBy.CLFxftNy.js";import"./clone.jDcTPt17.js";var x={packet:[]},m=structuredClone(x),L=B.packet,Y=l(()=>{const t=w({...L,...T().packet});return t.showBits&&(t.paddingY+=10),t},"getConfig"),G=l(()=>m.packet,"getPacket"),I=l(t=>{t.length>0&&m.packet.push(t)},"pushWord"),K=l(()=>{D(),m=structuredClone(x)},"clear"),u={pushWord:I,getPacket:G,getConfig:Y,clear:K,setAccTitle:S,getAccTitle:F,setDiagramTitle:z,getDiagramTitle:E,getAccDescription:P,setAccDescription:W},M=1e4,O=l(t=>{y(t,u);let e=-1,o=[],n=1;const{bitsPerRow:s}=u.getConfig();for(let{start:a,end:r,bits:c,label:f}of t.blocks){if(a!==void 0&&r!==void 0&&r<a)throw new Error(`Packet block ${a} - ${r} is invalid. End must be greater than start.`);if(a??(a=e+1),a!==e+1)throw new Error(`Packet block ${a} - ${r??a} is not contiguous. It should start from ${e+1}.`);if(c===0)throw new Error(`Packet block ${a} is invalid. Cannot have a zero bit field.`);for(r??(r=a+(c??1)-1),c??(c=r-a+1),e=r,v.debug(`Packet block ${a} - ${e} with label ${f}`);o.length<=s+1&&u.getPacket().length<M;){const[d,p]=H({start:a,end:r,bits:c,label:f},n,s);if(o.push(d),d.end+1===n*s&&(u.pushWord(o),o=[],n++),!p)break;({start:a,end:r,bits:c,label:f}=p)}}u.pushWord(o)},"populate"),H=l((t,e,o)=>{if(t.start===void 0)throw new Error("start should have been set during first phase");if(t.end===void 0)throw new Error("end should have been set during first phase");if(t.start>t.end)throw new Error(`Block start ${t.start} is greater than block end ${t.end}.`);if(t.end+1<=e*o)return[t,void 0];const n=e*o-1,s=e*o;return[{start:t.start,end:n,label:t.label,bits:n-t.start},{start:s,end:t.end,label:t.label,bits:t.end-s}]},"getNextFittingBlock"),R={parse:l(async t=>{const e=await N("packet",t);v.debug(e),O(e)},"parse")},U=l((t,e,o,n)=>{const s=n.db,a=s.getConfig(),{rowHeight:r,paddingY:c,bitWidth:f,bitsPerRow:d}=a,p=s.getPacket(),i=s.getDiagramTitle(),k=r+c,g=k*(p.length+1)-(i?0:r),b=f*d+2,h=_(e);h.attr("viewbox",`0 0 ${b} ${g}`),A(h,g,b,a.useMaxWidth);for(const[C,$]of p.entries())X(h,$,C,a);h.append("text").text(i).attr("x",b/2).attr("y",g-k/2).attr("dominant-baseline","middle").attr("text-anchor","middle").attr("class","packetTitle")},"draw"),X=l((t,e,o,{rowHeight:n,paddingX:s,paddingY:a,bitWidth:r,bitsPerRow:c,showBits:f})=>{const d=t.append("g"),p=o*(n+a)+a;for(const i of e){const k=i.start%c*r+1,g=(i.end-i.start+1)*r-s;if(d.append("rect").attr("x",k).attr("y",p).attr("width",g).attr("height",n).attr("class","packetBlock"),d.append("text").attr("x",k+g/2).attr("y",p+n/2).attr("class","packetLabel").attr("dominant-baseline","middle").attr("text-anchor","middle").text(i.label),!f)continue;const b=i.end===i.start,h=p-2;d.append("text").attr("x",k+(b?g/2:0)).attr("y",h).attr("class","packetByte start").attr("dominant-baseline","auto").attr("text-anchor",b?"middle":"start").text(i.start),b||d.append("text").attr("x",k+g).attr("y",h).attr("class","packetByte end").attr("dominant-baseline","auto").attr("text-anchor","end").text(i.end)}},"drawWord"),j={draw:U},q={byteFontSize:"10px",startByteColor:"black",endByteColor:"black",labelColor:"black",labelFontSize:"12px",titleColor:"black",titleFontSize:"14px",blockStrokeColor:"black",blockStrokeWidth:"1",blockFillColor:"#efefef"},J=l(({packet:t}={})=>{const e=w(q,t);return`
	.packetByte {
		font-size: ${e.byteFontSize};
	}
	.packetByte.start {
		fill: ${e.startByteColor};
	}
	.packetByte.end {
		fill: ${e.endByteColor};
	}
	.packetLabel {
		fill: ${e.labelColor};
		font-size: ${e.labelFontSize};
	}
	.packetTitle {
		fill: ${e.titleColor};
		font-size: ${e.titleFontSize};
	}
	.packetBlock {
		stroke: ${e.blockStrokeColor};
		stroke-width: ${e.blockStrokeWidth};
		fill: ${e.blockFillColor};
	}
	`},"styles"),nt={parser:R,db:u,renderer:j,styles:J};export{nt as diagram};
