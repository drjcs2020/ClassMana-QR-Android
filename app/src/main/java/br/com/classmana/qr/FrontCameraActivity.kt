package br.com.classmana.qr

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Button
import android.widget.ImageView
import android.graphics.drawable.GradientDrawable
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class FrontCameraActivity : ComponentActivity() {
    private val done = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadExecutor()
    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
    )
    private lateinit var previewView: PreviewView
    private lateinit var status: TextView

    private fun dp(n:Int):Int=(n*resources.displayMetrics.density).toInt()
    private fun rounded(color:Int,radius:Int):GradientDrawable =
        GradientDrawable().apply {setColor(color);cornerRadius=dp(radius).toFloat()}
    private fun caption(message:String,size:Float,bold:Boolean=false):TextView =
        TextView(this).apply {
            text=message; textSize=size; gravity=Gravity.CENTER
            setTextColor(Color.rgb(30,59,83))
            if(bold)typeface=Typeface.DEFAULT_BOLD
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor=Color.rgb(227,247,251)
        // Em tablets na vertical, o leitor ocupa a maior parte da largura disponivel.
        // O ajuste fica restrito a esta tela: login e check-in nao sao alterados.
        val config = resources.configuration
        val tabletPortrait = config.smallestScreenWidthDp >= 600 &&
            config.screenHeightDp > config.screenWidthDp
        val cameraHeightDp = if (tabletPortrait) {
            minOf(config.screenWidthDp - 100, (config.screenHeightDp * 0.56f).toInt())
                .coerceAtLeast(360)
        } else 237
        val outer=FrameLayout(this).apply {setBackgroundColor(Color.rgb(235,248,251))}
        val card=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            gravity=Gravity.CENTER_HORIZONTAL
            setPadding(dp(if(tabletPortrait) 24 else 16),dp(if(tabletPortrait) 24 else 15),dp(if(tabletPortrait) 24 else 16),dp(if(tabletPortrait) 26 else 18))
            background=rounded(Color.WHITE,21)
        }
        val logo=ImageView(this).apply {
            setImageResource(R.drawable.classmana_logo)
            scaleType=ImageView.ScaleType.FIT_CENTER
            adjustViewBounds=true
        }
        card.addView(logo,LinearLayout.LayoutParams(dp(if(tabletPortrait) 280 else 225),dp(if(tabletPortrait) 110 else 85)))
        val heading=caption("ClassMana QR — leitor",if(tabletPortrait) 30f else 22f,true)
        val titleParams=LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(4)}
        card.addView(heading,titleParams)
        status=caption("Aponte a câmera para o QR Code do estudante",if(tabletPortrait) 20f else 14f)
        val statusParams=LinearLayout.LayoutParams(-1,-2).apply{
            topMargin=dp(if(tabletPortrait) 12 else 9);bottomMargin=dp(if(tabletPortrait) 22 else 15)
        }
        card.addView(status,statusParams)
        val cameraFrame=FrameLayout(this).apply {
            background=GradientDrawable().apply{
                setColor(Color.rgb(14,40,58))
                cornerRadius=dp(16).toFloat()
                setStroke(dp(2),Color.rgb(52,178,183))
            }
            clipToOutline=true
        }
        previewView=PreviewView(this).apply{
            scaleType=if(tabletPortrait) PreviewView.ScaleType.FILL_CENTER else PreviewView.ScaleType.FIT_CENTER
        }
        cameraFrame.addView(previewView,FrameLayout.LayoutParams(-1,-1))
        // A moldura permanece, mesmo quando a permissão da câmera ainda não foi concedida.
        val cameraParams=LinearLayout.LayoutParams(-1,dp(cameraHeightDp)).apply{
            bottomMargin=dp(if(tabletPortrait) 22 else 17)
        }
        card.addView(cameraFrame,cameraParams)
        val cancel=Button(this).apply {
            text="Cancelar";isAllCaps=false
            textSize=if(tabletPortrait) 21f else 15f
            setTextColor(Color.WHITE)
            background=rounded(Color.rgb(61,184,189),11)
            setOnClickListener{setResult(Activity.RESULT_CANCELED);finish()}
        }
        card.addView(cancel,LinearLayout.LayoutParams(dp(if(tabletPortrait) 230 else 160),dp(if(tabletPortrait) 66 else 50)))
        val lp=FrameLayout.LayoutParams(-1,-2,Gravity.CENTER).apply{
            marginStart=dp(if(tabletPortrait) 26 else 22);marginEnd=dp(if(tabletPortrait) 26 else 22)
        }
        outer.addView(card,lp)
        setContentView(outer)
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)
            startFrontCamera()
        else requestPermissions(arrayOf(Manifest.permission.CAMERA),303)
    }

    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<String>,grantResults:IntArray){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        if(requestCode==303){
            if(grantResults.firstOrNull()==PackageManager.PERMISSION_GRANTED)startFrontCamera()
            else { status.text="Permissão da câmera negada";setResult(Activity.RESULT_CANCELED) }
        }
    }

    private fun startFrontCamera(){
        val future=ProcessCameraProvider.getInstance(this)
        future.addListener({
            try{
                val provider=future.get()
                val isFront=intent.getBooleanExtra("useFrontCamera",true)
                val camera=if(isFront) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                if(!provider.hasCamera(camera)){
                    status.text=if(isFront) "Câmera frontal indisponível." else "Câmera traseira indisponível."
                    return@addListener
                }
                val preview=Preview.Builder().build()
                preview.setSurfaceProvider(previewView.surfaceProvider)
                val analysis=ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor){proxy->
                    val media=proxy.image
                    if(media==null||done.get()){proxy.close();return@setAnalyzer}
                    try {
                        val image=InputImage.fromMediaImage(media,proxy.imageInfo.rotationDegrees)
                        scanner.process(image)
                            .addOnSuccessListener { codes ->
                                val code=codes.firstOrNull { it.rawValue?.startsWith("CMQR1:")==true }
                                if(code!=null&&done.compareAndSet(false,true)){
                                    val result=Intent().putExtra("qr",code.rawValue)
                                    runOnUiThread { setResult(Activity.RESULT_OK,result);finish() }
                                }
                            }
                            .addOnCompleteListener {proxy.close()}
                    }catch(e:Exception){proxy.close()}
                }
                provider.unbindAll()
                provider.bindToLifecycle(this,camera,preview,analysis)
            }catch(e:Exception){status.text="Falha ao iniciar a câmera selecionada"}
        },ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy(){
        super.onDestroy()
        scanner.close()
        executor.shutdown()
    }
}
