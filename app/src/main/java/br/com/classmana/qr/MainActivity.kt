package br.com.classmana.qr

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.widget.EditText
import android.graphics.Color
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
import com.google.android.gms.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private val portal = "https://classmana.com/pt/student"
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
    private fun showWelcome(){
        generation++;scanBusy=false;loginSubmitted=false;confirmedLogin=false;account=null;secret=null
        val box=showCard("Leitor de Cartão de Herói","Aponte seu cartão para iniciar sua aventura.","📷 Escanear QR Code") { scan() }
        box.addView(Button(this).apply { text="⚙ Configurar check-in"; isAllCaps=false; setOnClickListener { configureEndpoint() } })
    }
    private fun configureEndpoint(){
        val value=EditText(this).apply { setSingleLine(true);hint="https://script.google.com/macros/s/.../exec";setText(getPreferences(0).getString("endpoint","")) }
        AlertDialog.Builder(this).setTitle("Google Apps Script").setMessage("Cole a URL /exec usada pela extensão. Não informe senhas ou tokens.")
            .setView(value).setNegativeButton("Cancelar",null).setPositiveButton("Salvar") { _,_ ->
                val url=value.text.toString().trim()
                if(Regex("https://script\\.google\\.com/macros/s/[^/]+/exec").matches(url)){
                    getPreferences(0).edit().putString("endpoint",url).apply()
                    Toast.makeText(this,"Endereço salvo",Toast.LENGTH_SHORT).show()
                }else Toast.makeText(this,"URL inválida; não foi salva",Toast.LENGTH_LONG).show()
            }.show()
    }
    private fun scan(){
        if(scanBusy)return
        scanBusy=true
        // O scanner do Google apresenta a camera automaticamente e retorna o QR sem armazenar imagem.
        val scanner=GmsBarcodeScanning.getClient(this)
        scanner.startScan().addOnSuccessListener { code ->
            scanBusy=false
            val decoded=decodeCard(code.rawValue)
            if(decoded==null){showCard("Cartão inválido","Use um cartão ClassMana QR válido.","Tentar novamente"){scan()}}
            else {account=decoded.first;secret=decoded.second;startLogin()}
        }.addOnCanceledListener {scanBusy=false;showWelcome()}
            .addOnFailureListener {e ->scanBusy=false;showCard("Câmera indisponível",e.localizedMessage?:"Não foi possível abrir a câmera.","Tentar novamente"){scan()}}
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
        val endpoint=getPreferences(0).getString("endpoint","")?:""
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
    private fun showResult(data:JSONObject){
        val ok=data.optBoolean("ok")
        val duplicate=data.optBoolean("jaRegistrado")
        val title=if(ok)"Recompensa do dia" else "Acesso ao ClassMana"
        val subtitle=if(!ok)data.optString("error","Não foi possível confirmar a presença.") else if(duplicate)"Presença já registrada hoje." else "Sua Presença foi Registrada!"
        val box=showCard(title,subtitle,null,null)
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
