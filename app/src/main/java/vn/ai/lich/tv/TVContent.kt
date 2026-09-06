package vn.ai.lich.tv

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

data class TVEventSection(val title:String,val icon:String,val content:String)
data class TVEventSource(val title:String,val url:String)
data class TVEvent(val title:String,val subtitle:String,val category:String,val summary:String,val description:String,val meaning:String,val icon:String,val sections:List<TVEventSection>,val tags:List<String>,val sources:List<TVEventSource>)
data class TVPrayer(val title:String,val context:String,val body:String)
data class TVGuidance(val whenToUse:String,val preparationNote:String,val offerings:List<String>,val notes:List<String>)
data class TVRitual(val title:String,val summary:String,val guidance:TVGuidance,val prayers:List<TVPrayer>)

object TVContent {
    private var root: JSONObject? = null
    private fun data(ctx:Context):JSONObject {
        root?.let{return it}
        val s=ctx.assets.open("tv_content.json").bufferedReader(Charsets.UTF_8).use{it.readText()}
        return JSONObject(s).also{root=it}
    }
    private fun strings(a:JSONArray?):List<String>{ if(a==null)return emptyList(); return (0 until a.length()).mapNotNull{a.optString(it).takeIf(String::isNotBlank)} }
    fun events(ctx:Context,date:LocalDate,lunar:LunarDate):List<TVEvent>{
        val out= mutableListOf<TVEvent>(); val a=data(ctx).getJSONArray("events")
        for(i in 0 until a.length()){
            val e=a.getJSONObject(i); val cal=e.optString("calendar"); val d=e.optInt("day",-1); val m=e.optInt("month",-1)
            val hit=if(cal=="lunar") d==lunar.day&&m==lunar.month else d==date.dayOfMonth&&m==date.monthValue
            if(hit){
                val sections= mutableListOf<TVEventSection>(); val sa=e.optJSONArray("sections")?:JSONArray()
                for(j in 0 until sa.length()){ val s=sa.getJSONObject(j); sections+=TVEventSection(s.optString("title"),s.optString("icon"),s.optString("content")) }
                val sources= mutableListOf<TVEventSource>(); val so=e.optJSONArray("sources")?:JSONArray()
                for(j in 0 until so.length()){ val s=so.getJSONObject(j); sources+=TVEventSource(s.optString("title"),s.optString("url")) }
                out+=TVEvent(e.optString("title"),e.optString("subtitle"),e.optString("category"),e.optString("summary"),e.optString("description"),e.optString("meaning"),e.optString("icon"),sections,strings(e.optJSONArray("tags")),sources)
            }
        }
        return out
    }
    fun rituals(ctx:Context,date:LocalDate,lunar:LunarDate):List<TVRitual>{
        val raw= mutableListOf<Pair<JSONObject,TVRitual>>(); val a=data(ctx).getJSONArray("rituals")
        for(i in 0 until a.length()){
            val r=a.getJSONObject(i); val rule=r.getJSONObject("match"); val cal=rule.optString("calendar")
            val hit= when(cal){
                "lunar" -> matches(rule.opt("day"),lunar.day) && (!rule.has("month")||rule.isNull("month")||matches(rule.opt("month"),lunar.month))
                "lunar_year_end" -> { val n=VietnameseLunar.fromSolar(date.plusDays(1)); n.day==1&&n.month==1 }
                "solar" -> matches(rule.opt("day"),date.dayOfMonth) && (!rule.has("month")||rule.isNull("month")||matches(rule.opt("month"),date.monthValue))
                else -> false
            }
            if(hit){
                val ps= mutableListOf<TVPrayer>(); val pa=r.optJSONArray("prayers")?:JSONArray()
                for(j in 0 until pa.length()){ val p=pa.getJSONObject(j); ps+=TVPrayer(p.optString("title"),p.optString("context"),render(p.optString("body"),date,lunar)) }
                val g=r.optJSONObject("guidance")?:JSONObject()
                val guidance=TVGuidance(g.optString("when_to_use"),g.optString("preparation_note"),strings(g.optJSONArray("offerings")),strings(g.optJSONArray("notes")))
                raw+=r to TVRitual(r.optString("title"),r.optString("summary"),guidance,ps)
            }
        }
        val superseded= mutableSetOf<String>(); raw.forEach{(r,_)->val s=r.optJSONArray("supersedes")?:JSONArray();for(i in 0 until s.length())superseded+=s.getString(i)}
        return raw.filter{!superseded.contains(it.first.optString("id"))}.sortedByDescending{it.first.optInt("priority")}.map{it.second}
    }
    private fun matches(v:Any?,actual:Int):Boolean=when(v){is Number->v.toInt()==actual;is JSONArray->(0 until v.length()).any{v.optInt(it,-999)==actual};else->false}
    private fun render(body:String,date:LocalDate,l:LunarDate):String{
        val day=if(l.day==1)"mồng Một" else if(l.day==15)"Rằm" else "ngày ${l.day}"; val months=arrayOf("","Giêng","Hai","Ba","Tư","Năm","Sáu","Bảy","Tám","Chín","Mười","Mười Một","Chạp"); val month=months.getOrElse(l.month){l.month.toString()}+(if(l.leap)" nhuận" else "")
        return body.replace("{{lunar_day}}",l.day.toString()).replace("{{lunar_month}}",l.month.toString()).replace("{{lunar_year}}",l.year.toString()).replace("{{lunar_day_text}}",day).replace("{{lunar_month_text}}",month).replace("{{lunar_year_can_chi}}",VietnameseLunar.yearCanChi(l.year)).replace("{{lunar_date_full}}","ngày $day tháng $month năm ${VietnameseLunar.yearCanChi(l.year)}").replace("{{solar_date_full}}","%02d/%02d/%04d".format(date.dayOfMonth,date.monthValue,date.year)).replace("{{worshipper_name}}","................................").replace("{{address}}","................................")
    }
}
