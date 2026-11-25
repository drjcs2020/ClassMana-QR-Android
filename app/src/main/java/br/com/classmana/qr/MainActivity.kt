package br.com.classmana.qr

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.content.Intent
import android.net.Uri
import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private val portal = "https://classmana.com/pt/student"
    private val checkinEndpoint = "https://script.google.com/macros/s/AKfycby2fACUJtKLgHQsB8rviToMvzc4d-9QzPNw6h9cyWEKTUZIiFGeGIk06ZdCYrAbOHSq/exec"
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var container: FrameLayout
    private lateinit var web: WebView
    private var account: String? = null
    private var secret: String? = null
    private var scanBusy = false
    private var loginSubmitted = false
    private var confirmedLogin = false
    private var generation = 0
    private var profileChecks = 0
    private var scannerOpen = false
    private val teal = Color.rgb(83, 186, 191)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(18, 56, 77)
        container = FrameLayout(this)
        setContentView(container)
        web = WebView(this)
        web.visibility = View.GONE
        container.addView(web, FrameLayout.LayoutParams(-1,-1))
        configureWeb()
        showWelcome()
        ClassManaUpdateChecker.check(this)
    }

    private fun rounded(color: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(15).toFloat()
    }
    private fun dp(n:Int):Int = (n*resources.displayMetrics.density).toInt()
    private fun button(label:String, click:()->Unit): Button = Button(this).apply {
        text=label; isAllCaps=false; textSize=16f; setTextColor(Color.WHITE)
        background=rounded(teal); setPadding(dp(18),dp(10),dp(18),dp(10))
        setOnClickListener { click() }
    }
    private fun column(): LinearLayout = LinearLayout(this).apply {
        orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER; setPadding(dp(22),dp(25),dp(22),dp(25))
        background=rounded(Color.WHITE)
    }
    private fun text(value:String, size:Float=16f):TextView=TextView(this).apply {
        text=value; textSize=size; setTextColor(Color.rgb(27,53,76));gravity=Gravity.CENTER
        setPadding(0,dp(7),0,dp(7))
    }
    private fun showCard(title:String, subtitle:String, action:String?, fn:(()->Unit)?):LinearLayout {
        web.visibility=View.GONE
        container.removeAllViews();container.addView(web,FrameLayout.LayoutParams(-1,-1))
        container.setBackgroundColor(Color.rgb(235,246,249))
        val box=column()
        val logo=ImageView(this).apply { setImageResource(R.drawable.classmana_logo); adjustViewBounds=true; maxHeight=dp(95) }
        box.addView(logo,LinearLayout.LayoutParams(dp(245),dp(100)))
        box.addView(text(title,23f));box.addView(text(subtitle,15f))
        if(action!=null&&fn!=null)box.addView(button(action,fn),LinearLayout.LayoutParams(-2,dp(55)))
        val l=FrameLayout.LayoutParams(-1,-2,Gravity.CENTER).apply{setMargins(dp(16),0,dp(16),0)}
        container.addView(box,l)
        return box
    }
    // Os sete personagens originais foram enviados pelo professor.
    // A ordem do atlas e: Fantasma, Golem, Rainha Aranha, Morcego,
    // Mestre da Colmeia, Hydra e Dragao de Fogo.
    private val monsterNames = arrayOf(
        "Fantasma", "Golem de Lava", "Rainha Aranha", "Morcego",
        "Mestre da Colmeia", "Hydra", "Dragão de Fogo"
    )
    private var monsterAtlas: Bitmap? = null

    private fun monsterForToday(): Pair<Int, String>? {
        // A data de referencia e a de Brasilia, mesmo se o tablet mudar de fuso.
        val today = LocalDate.now(ZoneId.of("America/Sao_Paulo"))
        if (today.dayOfWeek == DayOfWeek.SATURDAY ||
            today.dayOfWeek == DayOfWeek.SUNDAY) return null

        // O ciclo comeca em uma segunda-feira e continua apenas nos dias uteis.
        val elapsed = ChronoUnit.DAYS.between(LocalDate.of(2026, 11, 23), today)
        val fullWeeks = Math.floorDiv(elapsed, 7L)
        val remainingDays = Math.floorMod(elapsed, 7L).toInt()
        val workingDays = fullWeeks * 5L + minOf(remainingDays, 5).toLong()
        val index = Math.floorMod(workingDays, monsterNames.size.toLong()).toInt()
        return index to monsterNames[index]
    }

    private fun monsterBitmap(index: Int): Bitmap? {
        return try {
            val atlas = monsterAtlas ?: run {
                val encoded = assets.open("classmana_monsters_atlas.b64")
                    .bufferedReader(Charsets.US_ASCII).use { it.readText() }
                val data = Base64.decode(encoded.trim(), Base64.DEFAULT)
                BitmapFactory.decodeByteArray(data, 0, data.size)?.also {
                    monsterAtlas = it
                } ?: return null
            }
            val cell = atlas.width / monsterNames.size
            if (cell <= 0 || atlas.height < cell) return null
            Bitmap.createBitmap(atlas, index * cell, 0, cell, cell)
        } catch (e: Exception) {
            android.util.Log.w("ClassManaMonster", "Falha ao carregar monstro do dia", e)
            null
        }
    }

    private fun showDailyMonster(box: LinearLayout) {
        val monster = monsterForToday() ?: return
        val bitmap = monsterBitmap(monster.first) ?: return
        val imageDp = when {
            resources.configuration.smallestScreenWidthDp >= 600 -> 200
            resources.configuration.screenHeightDp < 690 -> 115
            else -> 160
        }
        val label = text("MONSTRO DO DIA  •  ${monster.second}", 16f).apply {
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(55, 113, 143))
        }
        box.addView(label)
        val image = ImageView(this).apply {
            setImageBitmap(bitmap)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Monstro do dia: ${monster.second}"
            alpha = 0f
            translationY = dp(12).toFloat()
        }
        box.addView(image, LinearLayout.LayoutParams(dp(imageDp), dp(imageDp)).apply {
            topMargin = dp(2)
            bottomMargin = dp(6)
        })
        image.animate().alpha(1f).translationY(0f).setDuration(650).start()
    }

    private fun showWelcome(){
        generation++;scanBusy=false;loginSubmitted=false;confirmedLogin=false;account=null;secret=null
        val box=showCard("Leitor de Cartão de Herói","Escolha a câmera para ler o cartão do estudante.",null,null)
        showDailyMonster(box)
        box.addView(button("📷 Câmera frontal") { scan(true) },LinearLayout.LayoutParams(-1,dp(55)))
        box.addView(button("📷 Câmera traseira") { scan(false) },LinearLayout.LayoutParams(-1,dp(55)).apply { topMargin=dp(12) })
    }
    private var lastFrontCamera=true
    private fun scan(front:Boolean){
        if(scanBusy)return
        scanBusy=true
        lastFrontCamera=front
        startActivityForResult(Intent(this, FrontCameraActivity::class.java).putExtra("useFrontCamera",front), 9001)
    }

    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode!=9001)return
        scanBusy=false
        if(resultCode!=RESULT_OK){
            showWelcome()
            return
        }
        val decoded=decodeCard(data?.getStringExtra("qr"))
        if(decoded==null){
            showCard("Cartão inválido","Use seu Cartão de Herói ClassMana.","Voltar à seleção de câmera"){showWelcome()}
        }else{
            account=decoded.first;secret=decoded.second;startLogin()
        }
    }
    private fun decodeCard(raw:String?):Pair<String,String>? = try{
        require(raw!=null&&raw.startsWith("CMQR1:")&&raw.length<4096)
        val data=Base64.decode(raw.substring(6), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        val o=JSONObject(String(data, Charsets.UTF_8))
        require(o.optString("type")=="classmana-qr-v1")
        val user=o.getString("student"); val pwd=o.getString("code")
        require(user.isNotBlank() && pwd.isNotBlank() && user.length<201 && pwd.length<201)
        user to pwd
    }catch(e:Exception){null}

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWeb(){
        web.settings.javaScriptEnabled=true
        web.settings.domStorageEnabled=true
        web.settings.allowFileAccess=false
        web.settings.allowContentAccess=false
        web.settings.javaScriptCanOpenWindowsAutomatically=false
        web.settings.setSupportMultipleWindows(false)
        web.webViewClient=object:WebViewClient(){
            override fun shouldOverrideUrlLoading(view:WebView?,request:WebResourceRequest?):Boolean {
                val u=request?.url?:return false
                if(u.scheme!="https" || !(u.host=="classmana.com"||u.host?.endsWith(".classmana.com")==true)){
                    // Não permitir que o site navegue silenciosamente para domínios externos.
                    return true
                }
                return false
            }
            override fun onPageFinished(view:WebView?,url:String?){
                if(url?.startsWith("https://classmana.com/")==true) { when {
                    account!=null&&!confirmedLogin -> checkLogin()
                    account==null&&scannerOpen -> showWelcome()
                    else -> attachExitWatcher()
                }}
            }
        }
    }
    private fun startLogin(){
        val g=++generation;loginSubmitted=false;confirmedLogin=false;profileChecks=0
        val loginBox = showCard("Autenticando com segurança","Encerrando a sessão anterior e preparando o acesso...",null,null)
        showDailyMonster(loginBox)
        // Remover sessão da WebView antes de cada login. A página não será exibida antes da confirmação.
        web.stopLoading()
        web.loadUrl("about:blank")
        CookieManager.getInstance().removeAllCookies {
            CookieManager.getInstance().flush()
            if(g==generation){web.clearHistory();web.clearCache(true);web.loadUrl(portal);handler.postDelayed({if(g==generation&&!confirmedLogin)checkLogin()},1800)}
        }
    }
    private fun checkLogin(){
        if(account==null||confirmedLogin)return
        val g=generation
        if(profileChecks++>65){showCard("Não foi possível entrar","A identidade do aluno não foi confirmada. Nenhuma presença foi registrada.","Voltar ao leitor"){showWelcome()};return}
        val check="""(function(){const t=document.body?.innerText?.toLowerCase()||''; const inputs=[...document.querySelectorAll('input')].filter(e=>e.offsetParent!==null);return JSON.stringify({form:inputs.length>=2&&(/login de estudante|código de login|digite seu nome|entrar/.test(t)),profile:/editar avatar|experiência|missões|informações da classe/.test(t)&&/ouro|mana|saúde|nível/.test(t)});})()"""
        evaluate(check) { value ->
            if(g!=generation)return@evaluate
            val o=try{JSONObject(value)}catch(e:Exception){JSONObject()}
            when {
                o.optBoolean("profile")&&loginSubmitted -> {confirmedLogin=true;secret?.let{ s -> postCheckin(account!!,s)};secret=null}
                o.optBoolean("profile")&&!loginSubmitted -> {showCard("Sessão anterior detectada","Por segurança, reinicie o leitor.","Reiniciar"){startLogin()}}
                o.optBoolean("form")&&!loginSubmitted -> submitLogin()
                else -> handler.postDelayed({if(g==generation)checkLogin()},550)
            }
        }
    }
    private fun evaluate(js:String,callback:(String)->Unit){
        web.evaluateJavascript(js){r->callback(try{org.json.JSONTokener(r).nextValue() as? String ?: r}catch(e:Exception){r})}
    }
    private fun submitLogin(){
        val user=JSONObject.quote(account ?: return)
        val pwd=JSONObject.quote(secret ?: return)
        val js="""(function(){const fs=[...document.querySelectorAll('input')].filter(e=>!e.disabled&&e.type!=='hidden');let label=e=>[e.placeholder,e.name,e.id,e.getAttribute('aria-label')].filter(Boolean).join(' ').toLowerCase();let u=fs.find(e=>/student|estudante|aluno|nome|name/.test(label(e)))||fs.find(e=>e.type==='text');let p=fs.find(e=>e!==u&&/código|codigo|senha|code|password/.test(label(e)))||fs.find(e=>e!==u);if(!u||!p)return false;const setter=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;for(let [el,v] of [[u,$user],[p,$pwd]]){setter.call(el,v);el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}));}const btn=[...document.querySelectorAll('button,input[type=submit]')].find(e=>/entrar|acessar|login/i.test(e.textContent||e.value||''));if(btn)btn.click();else if(u.form)u.form.requestSubmit();else return false;return true;})()"""
        loginSubmitted=true
        evaluate(js){r->if(r!="true")loginSubmitted=false;handler.postDelayed({checkLogin()},650)}
    }
    private fun postCheckin(user:String,pwd:String){
        val endpoint=checkinEndpoint
        if(!Regex("https://script\\.google\\.com/macros/s/[^/]+/exec").matches(endpoint)){
            showResult(JSONObject().put("ok",false).put("error","Configure a URL do Apps Script nas configurações."));return
        }
        showCard("Confirmando presença","Verificando o registro do dia...",null,null)
        thread {
            val result=try{
                val c=(URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod="POST";doOutput=true;connectTimeout=15000;readTimeout=20000
                    setRequestProperty("Content-Type","text/plain; charset=UTF-8")
                }
                c.outputStream.use {it.write(JSONObject().put("action","checkin").put("student",user).put("code",pwd).toString().toByteArray())}
                val body=(if(c.responseCode in 200..299)c.inputStream else c.errorStream).bufferedReader().use{it.readText()}
                c.disconnect(); JSONObject(body)
            }catch(e:Exception){JSONObject().put("ok",false).put("error","Falha na comunicação com a planilha.")}
            runOnUiThread{showResult(result)}
        }
    }
    private fun showConfirmationAnimation(box: LinearLayout, duplicate: Boolean) {
        // A confirmacao permanece visivel depois da animacao, inclusive em tablets.
        val banner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = GradientDrawable().apply {
                setColor(Color.rgb(224, 249, 232))
                cornerRadius = dp(20).toFloat()
                setStroke(dp(2), Color.rgb(42, 162, 94))
            }
            alpha = 0f
            translationY = dp(26).toFloat()
        }
        val symbol = text("✓", 49f).apply {
            setTextColor(Color.rgb(31, 145, 70))
            typeface = Typeface.DEFAULT_BOLD
            scaleX = 0.2f
            scaleY = 0.2f
        }
        val headline = text(
            if (duplicate) "ACESSO RECONHECIDO!" else "PRESENÇA CONFIRMADA!",
            20f
        ).apply {
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(17, 110, 51))
        }
        val detail = text(
            if (duplicate) "Você já recebeu a recompensa de hoje."
            else "Seu check-in foi computado com sucesso!",
            14f
        ).apply { setTextColor(Color.rgb(31, 96, 57)) }
        banner.addView(symbol)
        banner.addView(headline)
        banner.addView(detail)
        box.addView(banner, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(9)
            bottomMargin = dp(9)
        })
        banner.animate().alpha(1f).translationY(0f).setDuration(750).start()
        symbol.animate().scaleX(1f).scaleY(1f).setDuration(1150)
            .setInterpolator(android.view.animation.OvershootInterpolator(1.35f)).start()
    }

    private fun showResult(data:JSONObject){
        val ok=data.optBoolean("ok")
        val duplicate=data.optBoolean("jaRegistrado")
        val title=if(ok)"Bem-vindo ao ClassMana!" else "Acesso ao ClassMana"
        val subtitle=if(!ok)data.optString("error","Não foi possível confirmar a presença.")
            else if(duplicate)"Seu login foi reconhecido. Presença já registrada hoje."
            else "Sua entrada foi confirmada e a presença foi computada."
        val box=showCard(title,subtitle,null,null)
        if(ok) {
            // O som de entrada toca quando o registro e confirmado, e nao depois de clicar em OK.
            showConfirmationAnimation(box, duplicate)
            EntrySound.play()
            // Moedas apenas para check-in novo; esperar o som de boas-vindas terminar.
            if(!duplicate) {
                val g=generation
                handler.postDelayed({ if(g==generation) CoinSound.play() }, 1250L)
            }
        }
        if(ok){
            if(!duplicate)box.addView(text("🪙 +10 Ouro   💎 +10 XP",19f))
            val days=data.optJSONArray("dias")
            val s=StringBuilder()
            if(days!=null)for(i in 0 until days.length()){
                val d=days.getJSONObject(i);s.append(d.optString("rotulo")).append(if(d.optString("status")=="feito")" ✓ " else " – ")
            }
            box.addView(text(s.toString(),14f))
            box.addView(text("Sequência: ${data.optInt("sequencia")} dia(s)",13f))
        }
        box.addView(button("OK — Entrar no ClassMana"){
            // O som e o feedback ja foram exibidos na confirmacao do registro.
            container.removeAllViews();container.addView(web,FrameLayout.LayoutParams(-1,-1))
            web.visibility=View.VISIBLE
            attachExitWatcher()
        },LinearLayout.LayoutParams(-2,dp(55)))
    }
    private fun attachExitWatcher(){
        if(web.visibility!=View.VISIBLE)return
        // Detecta o Sair do próprio portal sem interceptar credenciais.
        val js="""(function(){if(window.__cmExitInstalled)return;window.__cmExitInstalled=true;document.addEventListener('click',e=>{let b=e.target.closest('button,a,[role=button]');if(b&&/^sair$/i.test((b.textContent||'').trim())){document.documentElement.dataset.cmExit='yes'}},true);})()"""
        web.evaluateJavascript(js,null)
        monitorExit(generation)
    }
    private fun monitorExit(g:Int){
        if(g!=generation||web.visibility!=View.VISIBLE)return
        web.evaluateJavascript("(function(){return document.documentElement.dataset.cmExit==='yes'||!!document.querySelector('input[placeholder*=\"nome\" i]')})()") {r ->
            if(g==generation&&r=="true"){
                web.loadUrl("about:blank");CookieManager.getInstance().removeAllCookies{showWelcome()}
            }else handler.postDelayed({monitorExit(g)},700)
        }
    }
    override fun onBackPressed(){
        if(web.visibility==View.VISIBLE){web.loadUrl("about:blank");CookieManager.getInstance().removeAllCookies{showWelcome()}}
        else showWelcome()
    }
}
