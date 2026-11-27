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
    private fun isTabletScreen(): Boolean =
        resources.configuration.smallestScreenWidthDp >= 600

    private fun isCompactTablet(): Boolean =
        isTabletScreen() && resources.configuration.screenHeightDp < 800

    // Apenas a tela de recompensa recebe a escala de tablet.
    // As telas de camera, login e a versao celular mantem suas medidas originais.
    private fun styleRewardCardForTablet(box: LinearLayout) {
        if (!isTabletScreen()) return
        val compact = isCompactTablet()
        box.setPadding(dp(if (compact) 28 else 44), dp(28),
            dp(if (compact) 28 else 44), dp(32))
        (box.getChildAt(0) as? ImageView)?.apply {
            adjustViewBounds = true
            maxHeight = dp(if (compact) 130 else 160)
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(
                dp(if (compact) 310 else 385),
                dp(if (compact) 130 else 160)
            )
        }
        (box.getChildAt(1) as? TextView)?.apply {
            textSize = if (compact) 28f else 34f
            typeface = Typeface.DEFAULT_BOLD
        }
        (box.getChildAt(2) as? TextView)?.textSize =
            if (compact) 18f else 22f
    }
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

    private fun showDailyMonsterThumbnail(box: LinearLayout) {
        val monster = monsterForToday() ?: return
        val bitmap = monsterBitmap(monster.first) ?: return
        // Somente a miniatura abaixo do botao OK: sem titulo, legenda ou painel extra.
        val thumbnail = ImageView(this).apply {
            setImageBitmap(bitmap)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = monster.second
        }
        val monsterSize = if (isTabletScreen()) {
            if (isCompactTablet()) 190 else 245
        } else 152
        box.addView(thumbnail, LinearLayout.LayoutParams(
            dp(monsterSize), dp(monsterSize)
        ).apply {
            topMargin = dp(if (isTabletScreen()) 16 else 8)
        })
    }

    private fun showWelcome(){
        generation++;scanBusy=false;loginSubmitted=false;confirmedLogin=false;account=null;secret=null
        val box=showCard("Leitor de Cartão de Herói","Escolha a câmera para ler o cartão do estudante.",null,null)
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
        showCard("Autenticando com segurança","Encerrando a sessão anterior e preparando o acesso...",null,null)
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
    private fun makeResultScrollable(box: LinearLayout) {
        // No celular, mantemos exatamente o layout anterior.
        // No tablet, a area util fica mais larga e o conteudo escala sem cortes.
        container.removeView(box)
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            setPadding(0, dp(if (isTabletScreen()) 12 else 8),
                0, dp(if (isTabletScreen()) 12 else 8))
        }
        if (isTabletScreen()) {
            val wrapper = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(10), dp(8), dp(10))
            }
            val contentWidth = minOf(740,
                (resources.configuration.screenWidthDp - 56).coerceAtLeast(420))
            wrapper.addView(box, LinearLayout.LayoutParams(dp(contentWidth), -2))
            scroll.addView(wrapper, android.view.ViewGroup.LayoutParams(-1, -2))
        } else {
            scroll.addView(box, android.view.ViewGroup.LayoutParams(-1, -2))
        }
        container.addView(scroll, FrameLayout.LayoutParams(-1, -1).apply {
            setMargins(dp(12), 0, dp(12), 0)
        })
    }

    private fun showResult(data:JSONObject){
        val ok=data.optBoolean("ok")
        val duplicate=data.optBoolean("jaRegistrado")
        val title=if(ok&&!duplicate)"Recompensa do dia" else if(ok)"Presença já registrada" else "Acesso ao ClassMana"
        val subtitle=if(!ok)data.optString("error","Não foi possível confirmar a presença.")
            else if(duplicate)"Presença já registrada hoje. Nenhuma recompensa nova foi gerada."
            else "Sua Presença foi Registrada!"
        val box=showCard(title,subtitle,null,null)
        styleRewardCardForTablet(box)
        makeResultScrollable(box)
        if(ok) {
            // O som original toca na confirmacao. A recompensa so e exibida se o servidor creditar no dia.
            EntrySound.play(this)
            // Moedas apenas para check-in novo; esperar o som de boas-vindas terminar.
            if(!duplicate) {
                val g=generation
                handler.postDelayed({ if(g==generation) CoinSound.play() }, 3100L)
            }
        }
        if(ok){
            if(!duplicate) {
                val reward = text("🪙 +10 Ouro   💎 +10 XP",
                    if (isTabletScreen()) {
                        if (isCompactTablet()) 25f else 30f
                    } else 19f)
                if (isTabletScreen()) reward.typeface = Typeface.DEFAULT_BOLD
                box.addView(reward)
            }
            val days=data.optJSONArray("dias")
            val s=StringBuilder()
            if(days!=null)for(i in 0 until days.length()){
                val d=days.getJSONObject(i);s.append(d.optString("rotulo")).append(if(d.optString("status")=="feito")" ✓ " else " – ")
            }
            box.addView(text(s.toString(),
                if (isTabletScreen()) {
                    if (isCompactTablet()) 17f else 20f
                } else 14f))
            box.addView(text("Sequência: ${data.optInt("sequencia")} dia(s)",
                if (isTabletScreen()) {
                    if (isCompactTablet()) 16f else 19f
                } else 13f))
        }
        val enterButton = button("OK — Entrar no ClassMana"){
            // O som e o feedback ja foram exibidos na confirmacao do registro.
            container.removeAllViews();container.addView(web,FrameLayout.LayoutParams(-1,-1))
            web.visibility=View.VISIBLE
            attachExitWatcher()
        }
        if (isTabletScreen()) enterButton.textSize =
            if (isCompactTablet()) 20f else 23f
        val buttonWidth = if (isTabletScreen()) dp(
            minOf(400, (resources.configuration.screenWidthDp - 125).coerceAtLeast(280))
        ) else -2
        box.addView(enterButton, LinearLayout.LayoutParams(
            buttonWidth,
            dp(if (isTabletScreen()) {
                if (isCompactTablet()) 66 else 78
            } else 55)
        ).apply {
            if (isTabletScreen()) topMargin = dp(12)
        })
        // A miniatura aparece APENAS depois do OK, quando houve nova recompensa.
        if(ok && !duplicate) showDailyMonsterThumbnail(box)
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
